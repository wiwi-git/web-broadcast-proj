"""autoscale/script.md에서 나레이션을 뽑아 Edge TTS로 음성을 만들고, 자막·장면 타이밍을 timeline.json에 쓴다.

uv run --with edge-tts python autoscale/tools/build_narration.py
"""

import asyncio
import json
import re
import subprocess
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "script.md"
OUT = ROOT / "video" / "assets" / "audio"
TIMELINE = ROOT / "video" / "timeline.json"

VOICE = "ko-KR-SunHiNeural"
RATE = "+5%"
SAMPLE_RATE = 48000

LEAD_IN = 0.35  # 장면이 뜨고 나레이션이 시작되기까지
SENTENCE_GAP = 0.3
SCENE_TAIL = 0.45  # 장면 마지막 문장 뒤 여유
PART_BREATH = {2: 0.5, 4: 0.6, 8: 0.6, 12: 0.5}  # 기존 구조 · 변화 · 결과 · 정리로 넘어갈 때 더 쉬는 시간
MAX_CHUNK = 34  # 자막 한 줄 최대 글자 수

# 대본은 읽는 대로 적혀 있다. 자막에는 원래 표기를 보여 준다. 긴 것부터.
DISPLAY = [
    ("웹 브로드캐스트의", "Web Broadcast의"),
    ("포 업데이트 스킵 락드로", "FOR UPDATE SKIP LOCKED로"),
    ("스프링 세션 JDBC로", "Spring Session JDBC로"),
    ("포스트그레스큐엘에", "PostgreSQL에"),
    ("도커 데스크톱의", "Docker Desktop의"),
    ("도커 데스크톱", "Docker Desktop"),
    ("에이치피에이는", "HPA는"),
    ("에이치피에이", "HPA"),
    ("에이피아이", "API"),
    ("씨피유", "CPU"),
    ("케이식스로", "k6로"),
    ("비크립트로", "bcrypt로"),
    ("에스쓰리", "S3"),
    ("리드미", "README"),
    ("에이 서버", "A 서버"),
    ("비 서버", "B 서버"),
    ("서비스 이름이 서버라서", "서비스 이름이 server라서"),
    ("60퍼센트", "60%"),
    ("369퍼센트", "369%"),
    ("4퍼센트", "4%"),
]

# TTS에는 영어 표기를 읽는 대로 바꿔 넣는다. 긴 것부터.
SPEAK = [
    ("JDBC", "제이디비씨"),
    ("ffmpeg", "에프에프엠펙"),
    ("1080p", "천팔십피"),
]


def replace_all(text, pairs):
    for a, b in pairs:
        text = text.replace(a, b)
    return text


def parse_scenes():
    scenes, current = [], None
    for line in SCRIPT.read_text().splitlines():
        m = re.match(r"^## (\d+)\. (.+)$", line)
        if m:
            current = {"id": int(m.group(1)), "title": m.group(2), "lines": []}
            scenes.append(current)
        elif line.startswith("> ") and current:
            current["lines"].append(line[2:].strip())
    return scenes


def split_sentences(line):
    parts = re.split(r"(?<=[.?!])\s+", line)
    return [p for p in parts if p]


# 자막을 나눌 때 갈라 놓지 않을 말 묶음
PROTECT = [a for a, _ in DISPLAY if " " in a] + [
    "변환 워커", "공유 볼륨", "점유 토큰", "로드밸런서 서비스", "세션 쿠키", "가상 사용자", "주기 작업",
    "낙관적 락으로", "헬스 체크를", "오브젝트 스토리지로", "메모리 대기열에서", "16,719건", "39,015건",
]
ENDINGS = ("는", "은", "가", "을", "를", "에", "로", "고", "며", "서", "면", "데", "도", "마다", "까지", "부터", "와", "과", "만", "고,")
NBSP = " "


def display(text):
    return replace_all(text.replace(NBSP, " "), DISPLAY)


def chunk(sentence):
    """자막용으로 쉼표·띄어쓰기에서 나눈다. 원문(읽는 표기) 기준 조각을 돌려준다."""
    for phrase in PROTECT:
        sentence = sentence.replace(phrase, phrase.replace(" ", NBSP))
    return _chunk(sentence)


def _chunk(sentence):
    if len(display(sentence)) <= MAX_CHUNK:
        return [sentence]
    words = sentence.split(" ")
    if len(words) == 1:
        return [sentence]
    best, best_score = None, None
    for i in range(1, len(words)):
        left, right = " ".join(words[:i]), " ".join(words[i:])
        ll, rl = len(display(left)), len(display(right))
        score = abs(ll - rl) - (14 if left.endswith(",") else 6 if left.endswith(ENDINGS) else 0)
        if best_score is None or score < best_score:
            best, best_score = i, score
    left, right = " ".join(words[:best]), " ".join(words[best:])
    return _chunk(left) + _chunk(right)


async def synthesize(text, path):
    communicate = edge_tts.Communicate(text, VOICE, rate=RATE, boundary="WordBoundary")
    words = []
    with open(path, "wb") as f:
        async for event in communicate.stream():
            if event["type"] == "audio":
                f.write(event["data"])
            elif event["type"] == "WordBoundary":
                words.append((event["offset"] / 1e7, (event["offset"] + event["duration"]) / 1e7, event["text"]))
    return words


def duration_of(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    ).stdout
    return float(out)


def to_wav(src, dst):
    trim_tail = "areverse,silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.08,areverse"
    subprocess.run(
        ["ffmpeg", "-y", "-v", "error", "-i", str(src), "-af", trim_tail, "-ar", str(SAMPLE_RATE), "-ac", "1", str(dst)],
        check=True,
    )


def chunk_times(chunks, words, total):
    """각 자막 조각의 (시작, 끝)을 TTS 단어 경계에서 찾는다. 단어 수가 안 맞으면 글자 수 비례."""
    counts = [len(replace_all(c, SPEAK).split()) for c in chunks]
    if len(words) == sum(counts) and words:
        times, i = [], 0
        for n in counts:
            times.append((words[i][0], words[i + n - 1][1]))
            i += n
        return times, True
    lengths = [len(c) for c in chunks]
    start = words[0][0] if words else 0.0
    end = words[-1][1] if words else total
    span, acc, times = end - start, 0, []
    for n in lengths:
        a = start + span * acc / sum(lengths)
        acc += n
        times.append((a, start + span * acc / sum(lengths)))
    return times, False


async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    work = OUT / "parts"
    work.mkdir(exist_ok=True)
    scenes = parse_scenes()

    t = 0.0
    concat_list, subs, scene_out, mismatched = [], [], [], []
    silence_cache = {}

    def silence(seconds):
        key = round(seconds, 3)
        if key not in silence_cache:
            p = work / f"silence_{int(key * 1000)}.wav"
            subprocess.run(
                ["ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", f"anullsrc=r={SAMPLE_RATE}:cl=mono",
                 "-t", str(key), str(p)],
                check=True,
            )
            silence_cache[key] = p
        concat_list.append(silence_cache[key])
        return key

    for scene in scenes:
        scene_start = t
        t += silence(LEAD_IN + PART_BREATH.get(scene["id"], 0))
        sentences = [s for line in scene["lines"] for s in split_sentences(line)]
        for n, sentence in enumerate(sentences):
            name = f"s{scene['id']:02d}_{n:02d}"
            mp3, wav = work / f"{name}.mp3", work / f"{name}.wav"
            words = await synthesize(replace_all(sentence, SPEAK), mp3)
            to_wav(mp3, wav)
            dur = duration_of(wav)
            chunks = chunk(sentence)
            times, exact = chunk_times(chunks, words, dur)
            if not exact:
                mismatched.append(name)
            for c, (a, b) in zip(chunks, times):
                subs.append({"start": round(t + a, 3), "end": round(t + b, 3), "text": display(c)})
            concat_list.append(wav)
            t += dur
            if n < len(sentences) - 1:
                t += silence(SENTENCE_GAP)
        t += silence(SCENE_TAIL)
        scene_out.append({"id": scene["id"], "title": scene["title"], "start": round(scene_start, 3),
                          "duration": round(t - scene_start, 3)})

    # 자막 사이 짧은 틈은 이어 붙여 깜빡이지 않게 한다
    for a, b in zip(subs, subs[1:]):
        if 0 < b["start"] - a["end"] < 0.35:
            a["end"] = b["start"]

    listing = work / "concat.txt"
    listing.write_text("".join(f"file '{p}'\n" for p in concat_list))
    subprocess.run(
        ["ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", str(listing),
         "-c:a", "pcm_s16le", str(OUT / "narration.wav")],
        check=True,
    )
    TIMELINE.write_text(json.dumps({"total": round(t, 3), "scenes": scene_out, "subtitles": subs},
                                   ensure_ascii=False, indent=1))
    print(f"total {t:.1f}s, scenes {len(scene_out)}, subtitles {len(subs)}, proportional fallback: {mismatched}")


asyncio.run(main())
