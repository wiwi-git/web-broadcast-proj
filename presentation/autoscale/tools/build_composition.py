"""autoscale/video/timeline.json으로 오토스케일 영상의 HyperFrames 컴포지션(video/index.html)을 만든다.

python3 autoscale/tools/build_composition.py

화면 요소는 나레이션 문장이 시작하는 시각(sentence_starts)에 맞춰 나타난다.
차트 수치는 load-test/results/*.txt에서 값이 바뀐 시점만 옮겼다.
"""

import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VIDEO = ROOT / "video"
SHARED_CSS = ROOT.parent / "tools" / "composition.css"
TL = json.loads((VIDEO / "timeline.json").read_text())
SCENES = {s["id"]: s for s in TL["scenes"]}
# 마지막 인사가 끝난 뒤 화면을 조금 더 붙잡아 둔다 (나레이션은 그대로)
OUTRO_HOLD = 1.6
SCENES[max(SCENES)]["duration"] += OUTRO_HOLD
TL["total"] += OUTRO_HOLD
TL["subtitles"][-1]["end"] += OUTRO_HOLD
SUBS = TL["subtitles"]


def sentence_starts(scene):
    """장면 안 문장들의 시작 시각(절대). 자막 조각이 마침표로 끝나면 문장이 끝난다."""
    a, b = scene["start"], scene["start"] + scene["duration"]
    starts, new = [], True
    for s in SUBS:
        if a <= s["start"] < b:
            if new:
                starts.append(s["start"])
            new = s["text"].rstrip().endswith((".", "?", "!"))
    return starts


class Cues:
    """S[i]: i번째 문장 시작 시각. 문장 수보다 큰 i는 마지막 문장으로 맞춘다."""

    def __init__(self, sid):
        self.scene = SCENES[sid]
        self.starts = sentence_starts(self.scene)

    def __getitem__(self, i):
        return self.starts[min(i, len(self.starts) - 1)]

    @property
    def st(self):
        return self.scene["start"]


def esc(t):
    return html.escape(t, quote=True)


# ---------- 부품 ----------

def rv(at, cls="", inner="", style="", tag="div", out=None, extra=""):
    o = f' data-out="{out:.2f}"' if out is not None else ""
    return f'<{tag} class="rv {cls}" data-at="{at:.2f}"{o} style="{style}" {extra}>{inner}</{tag}>'


PART_COLOR = {"도입": "var(--muted)", "기존 구조": "var(--danger)", "바뀐 구조": "var(--accent)",
              "결과": "var(--blue)", "정리": "var(--muted)"}


def header(part, num, title, at):
    return (f'<div class="head">{rv(at, "part", f"<b style=\'background:{PART_COLOR[part]}\'></b>{part}")}'
            f'{rv(at + 0.1, "title", f"<span>{num:02d}</span>{esc(title)}", tag="h2")}</div>')


SVG_DEFS = ('<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
            'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="context-stroke"/></marker></defs>')


def arrow(x1, y1, x2, y2, at, label="", color="var(--muted)", dash=False, lx=None, ly=None):
    d = ' stroke-dasharray="8 8"' if dash else ""
    lab = ""
    if label:
        lx = (x1 + x2) / 2 if lx is None else lx
        ly = (y1 + y2) / 2 - 14 if ly is None else ly
        lab = f'<text x="{lx}" y="{ly}" text-anchor="middle">{esc(label)}</text>'
    return (f'<g class="rv" data-at="{at:.2f}"><line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="3"{d} marker-end="url(#ah)"/>{lab}</g>')


def node(x, y, w, h, at, title, sub="", cls=""):
    return rv(at, f"node {cls}", f"<b>{esc(title)}</b><small>{esc(sub)}</small>",
              style=f"left:{x}px;top:{y}px;width:{w}px;height:{h}px")


def canvas(arrows, nodes=""):
    """화면 전체 좌표계(1920×1080)의 화살표 + 노드."""
    return (f'<div class="dg" style="left:0;top:0;width:1920px;height:1080px">'
            f'<svg width="1920" height="1080">{SVG_DEFS}{"".join(arrows)}</svg>{nodes}</div>')


def pods(n, cls="", ghost=0, label="Pod"):
    out = "".join(f'<div class="pod {cls}">{label}</div>' for _ in range(n))
    out += "".join(f'<div class="pod {cls} ghost">+</div>' for _ in range(ghost))
    return f'<div class="pod-row">{out}</div>'


def piece(title, sub, cls=""):
    return f'<div class="piece {cls}"><b>{esc(title)}</b><small>{esc(sub)}</small></div>'


METHOD_CLASS = {"GET": "get", "POST": "post"}


def req(method, path, note=""):
    n = f'<span class="badge">{esc(note)}</span>' if note else ""
    return f'<div class="req"><span class="m {METHOD_CLASS[method]}">{method}</span><code>{esc(path)}</code>{n}</div>'


def tag(at, text, cls="", style="", out=None):
    return rv(at, f"tag {cls}", esc(text), style=style, out=out)


# ---------- 차트 ----------

def sec(t):
    h, m, s = map(int, t.split(":"))
    return h * 3600 + m * 60 + s


def step_path(points):
    d = ""
    for i, (x, y) in enumerate(points):
        d += f"M{x:.1f},{y:.1f}" if i == 0 else f"H{x:.1f}V{y:.1f}"
    return d


def chart(cid, left, top, w, h, start, end, series, pods_rows, pod_color, pod_max, pod_min, target, x_ticks,
          wipe_at, wipe_dur, cpu_label):
    """위: CPU 사용률, 아래: Pod 수. 선은 wipe_at부터 wipe_dur 동안 왼쪽에서 오른쪽으로 그려진다."""
    L, R, T = 92, 34, 46
    cpu_h, gap, pod_h = 300, 74, 170
    pw = w - L - R
    t0, t1 = sec(start), sec(end)
    x = lambda t: L + (sec(t) - t0) / (t1 - t0) * pw
    cpu_y = lambda v: T + cpu_h - v / 400 * cpu_h
    pod_top = T + cpu_h + gap
    pod_y = lambda v: pod_top + pod_h - v / pod_max * pod_h
    g = []
    g.append(f'<text class="lbl" x="{L}" y="{T - 16}">{esc(cpu_label)}</text>')
    for v in (0, 100, 200, 300, 400):
        g.append(f'<line class="grid" x1="{L}" x2="{L + pw}" y1="{cpu_y(v)}" y2="{cpu_y(v)}"/>'
                 f'<text x="{L - 12}" y="{cpu_y(v) + 6}" text-anchor="end">{v}%</text>')
    g.append(f'<line class="tgt" x1="{L}" x2="{L + pw}" y1="{cpu_y(target)}" y2="{cpu_y(target)}"/>'
             f'<text x="{L + pw - 4}" y="{cpu_y(target) - 10}" text-anchor="end">목표 {target}%</text>')
    g.append(f'<text class="lbl" x="{L}" y="{pod_top - 16}">Pod 수</text>')
    for v in range(0, pod_max + 1):
        g.append(f'<line class="grid" x1="{L}" x2="{L + pw}" y1="{pod_y(v)}" y2="{pod_y(v)}"/>'
                 f'<text x="{L - 12}" y="{pod_y(v) + 6}" text-anchor="end">{v}</text>')
    g.append(f'<line class="tgt" x1="{L}" x2="{L + pw}" y1="{pod_y(pod_min)}" y2="{pod_y(pod_min)}"/>')
    base = pod_top + pod_h
    for t in x_ticks:
        g.append(f'<text x="{x(t)}" y="{base + 30}" text-anchor="middle">{t[:5]}</text>')
    lines = []
    for s in series:
        pts = [(x(t), cpu_y(v)) for t, v in s["rows"]]
        lines.append(f'<path d="{step_path(pts)}" fill="none" stroke="{s["color"]}" stroke-width="{s.get("width", 4)}" '
                     f'stroke-linejoin="round"/>')
    pts = [(x(t), pod_y(v)) for t, v in pods_rows]
    lines.append(f'<path d="{step_path(pts)}" fill="none" stroke="{pod_color}" stroke-width="5" stroke-linejoin="round"/>')
    for i, (t, v) in enumerate(pods_rows):
        if i == 0 or v != pods_rows[i - 1][1]:
            lines.append(f'<circle cx="{x(t):.1f}" cy="{pod_y(v):.1f}" r="7" fill="{pod_color}"/>'
                         f'<text x="{x(t) + 12:.1f}" y="{pod_y(v) - 12:.1f}" style="fill:{pod_color};font-weight:700">{v}</text>')
    clip = (f'<clipPath id="{cid}"><rect class="wipe" x="{L - 10}" y="0" width="0" height="{h}" '
            f'data-w="{pw + 40}" data-at="{wipe_at:.2f}" data-dur="{wipe_dur:.2f}"/></clipPath>')
    svg = (f'<svg width="{w}" height="{h}"><defs>{clip}</defs>{"".join(g)}'
           f'<g clip-path="url(#{cid})">{"".join(lines)}</g></svg>')
    return svg, f"left:{left}px;top:{top}px;width:{w}px;height:{h}px"


# 원본: load-test/results/server-scale.txt, worker-scale.txt (값이 바뀐 시점만)
SERVER = [("09:26:15", 7, 2), ("09:27:08", 369, 2), ("09:27:18", 369, 3), ("09:27:23", 369, 4),
          ("09:28:11", 311, 4), ("09:29:09", 294, 4), ("09:30:07", 195, 4), ("09:31:11", 3, 3),
          ("09:32:09", 2, 2), ("09:32:14", 2, 2)]
WORKER = [("09:32:37", 0, 1, 2), ("09:33:09", 399, 2, 2), ("09:35:11", 1, 1, 4), ("09:35:16", 1, 1, 4)]


# ---------- 장면 ----------

def s0():
    S = Cues(0)
    st = S.st
    return f"""
      <div class="hero">
        <div class="eyebrow">Web Broadcast</div>
        <h1 class="logo">로컬 오토스케일</h1>
        {rv(S[1], "tagline", "부하가 오면 서버가 늘고,<br>한가해지면 다시 줄어든다")}
        <div class="hero-row">{"".join(rv(S[2] + i * 0.15, "chip", t, tag="span") for i, t in enumerate(["기존 구조와 달라진 점", "생긴 장점", "부하 테스트 결과"]))}</div>
      </div>
      <div class="pods-hero">
        {rv(st + 0.5, "pod-row", '<span class="lbl">API 서버</span><div class="pod">Pod</div><div class="pod">Pod</div>'
            + rv(S[1] + 0.9, "pod", "Pod", out=S[2] + 3.2) + rv(S[1] + 1.3, "pod", "Pod", out=S[2] + 2.8))}
        {rv(st + 0.7, "pod-row", '<span class="lbl">변환 워커</span><div class="pod w">Pod</div>'
            + rv(S[1] + 1.8, "pod w", "Pod", out=S[2] + 3.6))}
        {rv(S[1] + 2.2, "badge accent", "CPU 사용률을 보고 Pod 수를 자동으로 조절")}
      </div>
    """


def s1():
    S = Cues(1)
    return f"""
      {header("도입", 1, "오토스케일의 두 가지 일", S.st)}
      <div class="halves">
        {rv(S[1], "half", '<span class="n">① 플랫폼이 하는 일</span><b>지표를 보고<br>서버 수를 늘리고 줄인다</b>'
            '<p>쿠버네티스 HPA · CPU 사용률 기준</p>' + rv(S[4], "badge", "설정 몇 줄"))}
        {rv(S[3], "half hot", '<span class="n">② 앱이 갖춰야 할 것</span><b>몇 대로 늘어나도<br>문제없이 동작한다</b>'
            '<p>상태를 서버 밖에 둔다 (stateless)</p>' + rv(S[4] + 1.6, "badge accent", "손이 많이 가는 쪽"))}
      </div>
      <div class="split-state">
        {rv(S[5], "mini ok", "<b>서버 A의 메모리</b><small>로그인 세션 있음 · 대기열 3개</small>")}
        {rv(S[5] + 0.8, "verdict", "다음 요청이 B로 가면 →", style="color:var(--muted)")}
        {rv(S[5] + 1.4, "mini bad", "<b>서버 B의 메모리</b><small>세션 없음 · 대기열 0개</small>")}
        {rv(S[5] + 2.2, "verdict bad", "로그인이 풀리고 일이 갈라진다")}
      </div>
    """


def s2():
    S = Cues(2)
    box = rv(S[0] + 0.4, "box before",
             '<div class="box-h">Spring 서버 <small>1대</small></div>'
             f'<div class="part-row">{rv(S[1], "piece", "<b>API 요청 처리</b><small>로그인 · 방송 · 댓글 · 후원</small>")}'
             f'{rv(S[2], "piece hotspot", "<b>영상 변환</b><small>스레드 1개 · 메모리 대기열 · ffmpeg</small><div class=queue><i></i><i></i><i></i></div>")}</div>'
             f'<div class="part-row">{rv(S[3], "piece", "<b>로컬 디스크</b><small>업로드 원본 · 변환된 HLS</small>")}</div>',
             style="left:100px;top:220px;width:1080px")
    db = node(1380, 300, 440, 170, S[1] + 0.8, "PostgreSQL", "세션 · 방송 · 댓글 · 후원", "store")
    return f"""
      {header("기존 구조", 2, "서버 한 대가 모든 일을", S.st)}
      {box}
      {canvas([arrow(1185, 385, 1372, 385, S[1] + 0.8, "JPA · 세션")], db)}
      {rv(S[4], "badge accent", "서버 1대일 때는 단순하고 잘 동작하는 구조", style="position:absolute;left:100px;top:700px;font-size:26px")}
    """


def s3():
    S = Cues(3)

    def server(name, top):
        return rv(S[0] + (0.3 if name == "서버 A" else 0.7), "box before",
                  f'<div class="box-h">{name}</div><div class="part-row">'
                  + piece("API", "요청 처리") + piece("변환 스레드", "메모리 대기열", "hotspot")
                  + piece("로컬 디스크", "자기 파일만") + '</div>',
                  style=f"left:100px;top:{top}px;width:860px")

    probs = [
        (S[1], "1", "변환이 API를 느리게 한다", "같은 서버의 CPU를 나눠 쓴다"),
        (S[2], "2", "일을 나눠 갖지 못한다", "대기열이 각 서버의 메모리에 있다"),
        (S[3], "3", "같은 영상을 중복 변환한다", "재시작할 때마다 처리 중 영상을 다시 넣는다"),
        (S[4], "4", "A에 올린 영상을 B가 못 찾는다", "파일이 각 서버의 로컬 디스크에 있다"),
    ]
    cards = "".join(rv(a, "problem", f"<span>{n}</span><b>{esc(t)}</b><small>{esc(s)}</small>") for a, n, t, s in probs)
    cards += rv(S[5], "problem good", "<span>✓</span><b>세션과 데이터는 이미 DB에</b><small>고칠 곳은 영상 변환과 파일 저장</small>")
    return f"""
      {header("기존 구조", 3, "그대로 늘리면 생기는 문제", S.st)}
      {server("서버 A", 200)}
      {server("서버 B", 470)}
      <div class="problems">{cards}</div>
    """


def s4():
    S = Cues(4)
    nodes = (node(100, 430, 250, 130, S[0], "브라우저", "영상 업로드", "plain")
             + node(470, 410, 340, 170, S[2], "API 서버", "저장 · 처리 중 기록", "server")
             + node(950, 410, 340, 170, S[2] + 0.7, "PostgreSQL", "video · PROCESSING", "store")
             + node(1460, 250, 360, 130, S[1], "워커 1", "같은 이미지 · 웹 서버 없음", "worker")
             + node(1460, 430, 360, 130, S[4] + 0.6, "워커 2", "같은 이미지 · 웹 서버 없음", "worker")
             + node(1460, 610, 360, 130, S[5] + 0.6, "워커 3", "같은 이미지 · 웹 서버 없음", "worker"))
    arrows = [
        arrow(355, 495, 462, 495, S[2], "업로드"),
        arrow(815, 495, 942, 495, S[2] + 0.7, "처리 중"),
        arrow(1455, 315, 1295, 450, S[4], "가져감", "var(--donation)", True, lx=1390, ly=360),
        arrow(1455, 495, 1297, 495, S[4] + 0.6, "", "var(--donation)", True),
        arrow(1455, 675, 1295, 545, S[5] + 0.6, "", "var(--donation)", True),
    ]
    return f"""
      {header("바뀐 구조", 4, "변환 워커 분리", S.st)}
      {canvas(arrows, nodes)}
      {tag(S[3], "일을 넘기지도, 결과를 돌려받지도 않는다", "accent", "left:470px;top:620px")}
      {tag(S[1] + 1.2, "SPRING_PROFILES_ACTIVE=worker", "donation", "left:1460px;top:180px")}
      {tag(S[4] + 0.3, "1초마다 DB를 보고 스스로 가져감", "donation", "left:950px;top:620px")}
      {tag(S[5] + 1.4, "서버와 워커는 서로를 모른다 → 따로 늘리고 줄인다", "accent", "left:100px;top:780px")}
    """


SQL = [
    ("update video", ""),
    ("   set claim_token = :token,", ""),
    ("       claim_expires_at = now() + 60초", ""),
    (" where id = (", ""),
    ("   select id from video", ""),
    ("    where status = 'PROCESSING'", ""),
    ("      and (claim_expires_at is null", ""),
    ("           or claim_expires_at &lt; now())", ""),
    ("    order by created_at limit 1", ""),
    ("    for update skip locked)", "hl"),
    ("returning id", ""),
]


def s5():
    S = Cues(5)
    code = "".join(f'<div class="ln {c}">{t}</div>' for t, c in SQL)
    code_card = rv(S[1], "code", '<div class="code-h"><i></i><i></i><i></i><span>VideoJobs.claimNext()</span></div>'
                   f'<pre style="font-size:22px">{code}</pre>', style="position:absolute;left:100px;top:200px;width:800px")

    def row(name, claim):
        return (f'<div class="qrow"><code>{name}</code><span class="st proc">PROCESSING</span>'
                f'<div class="claimcell">{claim}</div></div>')

    a_out = S[4]
    claim1 = (rv(S[2], "claim", '<span class="who a">워커 A</span><span class="lease">기한 60초 · 20초마다 연장</span>', out=a_out)
              + rv(a_out, "claim lost", '<span class="who">워커 A</span><span class="lease">연장 못 함 · 기한 지남</span>', out=a_out + 3.4)
              + rv(a_out + 3.4, "claim", '<span class="who c">워커 C</span><span class="lease">이어받아 처음부터 변환</span>'))
    claim2 = rv(S[2] + 0.8, "claim", '<span class="who b">워커 B</span><span class="lease">잠긴 행은 건너뛰고 다음 행</span>')
    table = rv(S[1] + 0.4, "qtable",
               '<div class="qrow"><span>영상</span><span>상태</span><span>점유</span></div>'
               + row("영상 1", claim1) + row("영상 2", claim2) + row("영상 3", ""))
    notes = (rv(S[3], "card", '<div class="card-h">점유 기한</div>60초 · 변환하는 동안 20초마다 연장<br>'
                '<span class="muted">워커가 죽으면 기한이 지난 뒤 다른 워커가 가져간다</span>')
             + rv(S[5], "card accent-card", '<div class="card-h">결과 기록</div><code>where claim_token = :token</code>'
                  '<br><span class="muted">점유를 잃은 워커는 DB 상태를 덮어쓰지 못한다</span>')
             )
    return f"""
      {header("바뀐 구조", 5, "DB 대기열과 점유", S.st)}
      {code_card}
      {table}
      <div class="lease-note" style="top:520px">{notes}</div>
      {rv(S[6], "stat", '<span class="big ok">약 57초</span><small>로컬에서 워커를 강제로 종료한 뒤 다른 워커가 이어받기까지</small>',
          style="position:absolute;left:100px;top:740px;width:800px")}
    """


def s6():
    S = Cues(6)
    server_box = rv(S[0] + 0.3, "box server", '<div class="box-h">API 서버 <small>Pod 2~4개</small></div>' + pods(3),
                    style="left:100px;top:200px;width:820px")
    worker_box = rv(S[0] + 0.6, "box worker", '<div class="box-h">변환 워커 <small>Pod 1~2개</small></div>' + pods(2, "w"),
                    style="left:1000px;top:200px;width:820px")
    db = rv(S[1], "box store", '<div class="box-h">PostgreSQL</div><div class="part-row">'
            + piece("로그인 세션", "Spring Session JDBC", "ok") + piece("방송 · 댓글 · 후원", "JPA", "ok")
            + piece("변환 대기열", "video 행 + 점유", "ok") + "</div>", style="left:100px;top:520px;width:820px")
    vol = rv(S[2], "box store", '<div class="box-h">공유 볼륨 <small>media-data</small></div><div class="part-row">'
             + piece("업로드 원본", "/data/originals", "ok") + piece("변환된 HLS · 썸네일", "/data/public", "ok")
             + "</div>", style="left:1000px;top:520px;width:820px")
    arrows = [
        arrow(400, 395, 400, 512, S[1], "읽기 · 쓰기", "var(--accent)", lx=480, ly=460),
        arrow(1300, 395, 650, 512, S[1] + 0.4, "", "var(--accent)", True),
        arrow(760, 395, 1250, 512, S[2], "", "var(--accent)", True),
        arrow(1500, 395, 1500, 512, S[2] + 0.3, "읽기 · 쓰기", "var(--accent)", lx=1580, ly=460),
    ]
    return f"""
      {header("바뀐 구조", 6, "상태를 서버 밖으로", S.st)}
      {server_box}{worker_box}
      {canvas(arrows)}
      {db}{vol}
      {rv(S[3], "card", '<div class="card-h">주기 작업</div>방송 동기화 · 후원 정리는 Pod마다 돈다<br><span class="muted">낙관적 락(@Version)으로 하나만 반영된다</span>', style="position:absolute;left:100px;top:760px;width:820px")}
      {rv(S[4], "card accent-card", '<div class="card-h">헬스 체크</div><code>/actuator/health/readiness</code><br><span class="muted">준비가 끝난 Pod에만 요청이 간다</span>', style="position:absolute;left:1000px;top:760px;width:820px")}
    """


def s7():
    S = Cues(7)
    nodes = (node(100, 440, 240, 130, S[2], "localhost:8080", "브라우저 요청", "plain")
             + node(420, 430, 290, 150, S[2] + 0.5, "로드밸런서", "Service · 준비된 Pod로", "plain"))
    server = rv(S[1], "box server", '<div class="box-h">API 서버 <small>Deployment</small></div>' + pods(2, ghost=2),
                style="left:790px;top:220px;width:580px")
    worker = rv(S[1] + 0.6, "box worker", '<div class="box-h">변환 워커 <small>Deployment</small></div>' + pods(1, "w", ghost=1),
                style="left:790px;top:560px;width:580px")
    hpa_s = rv(S[3], "hpa server", '<b>HPA · API 서버</b><span class="rng">2 ~ 4대</span><small>CPU 60% 기준</small>',
               style="position:absolute;left:1440px;top:220px;width:380px")
    hpa_w = rv(S[3] + 0.6, "hpa worker", '<b>HPA · 변환 워커</b><span class="rng">1 ~ 2대</span><small>CPU 70% 기준</small>',
               style="position:absolute;left:1440px;top:560px;width:380px")
    arrows = [
        arrow(345, 505, 412, 505, S[2] + 0.5),
        arrow(715, 480, 782, 330, S[2] + 1.0, "", "var(--blue)"),
        arrow(1435, 300, 1378, 300, S[3], "", "var(--muted)", True),
        arrow(1435, 640, 1378, 640, S[3] + 0.6, "", "var(--muted)", True),
    ]
    return f"""
      {header("바뀐 구조", 7, "쿠버네티스와 HPA", S.st)}
      {server}{worker}
      {canvas(arrows, nodes)}
      {hpa_s}{hpa_w}
      {rv(S[4], "res", "최소 2대 · 한 대가 내려가도 다른 서버가 받음", style="left:790px;top:840px")}
      {rv(S[4] + 1.6, "res", "최대값 · CPU 10개 · 메모리 8GB 안", style="left:1360px;top:840px")}
      {rv(S[5], "badge accent", "k8s/up.sh 한 번으로 다시 배포", style="position:absolute;left:100px;top:660px;font-size:24px;font-family:var(--mono)")}
    """


def s8():
    S = Cues(8)
    W, H, L, B, T = 820, 470, 90, 400, 70
    pw = W - L - 40
    x = lambda s: L + s / 230 * pw
    y = lambda v: B - v / 30 * (B - T)
    pts = f"M{x(0)},{y(0)} L{x(30)},{y(30)} L{x(210)},{y(30)} L{x(230)},{y(0)}"
    grid = "".join(f'<line class="grid" x1="{L}" x2="{L + pw}" y1="{y(v)}" y2="{y(v)}"/>'
                   f'<text x="{L - 12}" y="{y(v) + 6}" text-anchor="end">{v}</text>' for v in (0, 10, 20, 30))
    ticks = "".join(f'<text x="{x(s)}" y="{B + 34}" text-anchor="middle">{lbl}</text>'
                    for s, lbl in ((0, "0:00"), (30, "0:30"), (210, "3:30"), (230, "3:50")))
    stage = (f'<svg width="{W}" height="{H}"><defs><clipPath id="k6"><rect class="wipe" x="{L - 6}" y="0" width="0" '
             f'height="{H}" data-w="{pw + 12}" data-at="{S[1]:.2f}" data-dur="3.6"/></clipPath></defs>'
             f'<text class="lbl" x="{L}" y="44">가상 사용자 수</text>{grid}{ticks}'
             f'<g clip-path="url(#k6)"><path d="{pts}" fill="rgba(77,163,255,.14)" stroke="var(--blue)" stroke-width="5"/></g></svg>')
    load = rv(S[2], "card", '<div class="card-h">부하용 · 매 반복</div>'
              + req("GET", "/api/auth/me", "CSRF 토큰") + req("POST", "/api/auth/login", "bcrypt · CPU 많이 씀")
              + req("GET", "/api/broadcasts") + req("GET", "/api/videos"))
    state = rv(S[4], "card accent-card", '<div class="card-h">상태 확인용 · 세션 쿠키 하나로</div>'
               + req("GET", "/api/auth/me", "로그인 유지") + req("GET", "/api/broadcasts/{id}", "방송 유지")
               + req("GET", "/api/broadcasts/{id}/comments", "댓글 유지"))
    return f"""
      {header("결과", 8, "부하 테스트 시나리오", S.st)}
      {rv(S[0] + 0.3, "chart", stage, style="left:100px;top:210px;width:820px;height:470px")}
      <div class="iter">{load}{state}</div>
      {tag(S[6], "서버 수가 늘어나는 동안에도 계속 조회 · 어느 서버가 받든 같은 결과", "accent", "left:100px;top:730px")}
    """


def s9():
    S = Cues(9)
    svg, style = chart("c9", 100, 200, 1180, 650, "09:26:15", "09:32:20",
                       [{"rows": [(t, c) for t, c, _ in SERVER], "color": "var(--blue)"}],
                       [(t, p) for t, _, p in SERVER], "var(--blue)", 4, 2, 60,
                       ["09:27:00", "09:28:00", "09:29:00", "09:30:00", "09:31:00", "09:32:00"],
                       S[1], S[3] + 3.0 - S[1], "API 서버 CPU 사용률 (요청량 250m 대비)")
    stats = (rv(S[1] + 0.5, "stat", '<span class="big server">369%</span><small>최고 CPU 사용률 · 목표 60%</small>')
             + rv(S[2], "stat", '<span class="big server">2 → 4 → 2</span><small>서버 Pod 수</small>')
             + rv(S[4], "stat", '<span class="big ok">16,719 / 16,719</span><small>로그인 · 방송 · 댓글 유지</small>')
             + rv(S[4] + 1.6, "stat", '<span class="big ok">0 / 39,015</span><small>실패한 요청</small>'))
    return f"""
      {header("결과", 9, "API 서버 2 → 4 → 2", S.st)}
      {rv(S[0] + 0.3, "chart", svg, style=style)}
      <div class="stat-col" style="left:1320px;top:200px;width:500px">{stats}</div>
      {tag(S[5], "줄어든 것은 부하가 끝난 뒤 · 줄어드는 동안의 요청은 확인하지 않음", "", "left:100px;top:870px")}
    """


def s10():
    S = Cues(10)
    svg, style = chart("c10", 100, 200, 1180, 650, "09:32:37", "09:35:20",
                       [{"rows": [(t, s) for t, _, _, s in WORKER], "color": "var(--blue)", "width": 3},
                        {"rows": [(t, c) for t, c, _, _ in WORKER], "color": "var(--donation)"}],
                       [(t, p) for t, _, p, _ in WORKER], "var(--donation)", 2, 1, 70,
                       ["09:33:00", "09:34:00", "09:35:00"],
                       S[2], S[3] + 2.0 - S[2], "워커 CPU (노랑) · API 서버 CPU (파랑)")
    stats = (rv(S[1], "stat", '<span class="big">6개</span><small>1080p · 60초 영상 업로드</small>')
             + rv(S[2] + 0.5, "stat", '<span class="big worker">1 → 2 → 1</span><small>워커 Pod 수 · 최대 2</small>')
             + rv(S[3], "stat", '<span class="big ok">6 / 6 공개</span><small>변환 실패 0개</small>')
             + rv(S[4], "stat", '<span class="big server">2 ~ 4%</span><small>같은 시간 API 서버 CPU</small>'))
    return f"""
      {header("결과", 10, "변환 워커 1 → 2 → 1", S.st)}
      {rv(S[0] + 0.3, "chart", svg, style=style)}
      <div class="stat-col" style="left:1320px;top:200px;width:500px">{stats}</div>
    """


def s11():
    S = Cues(11)
    rows = [
        (S[1], "서버 Pod가 계속 재시작", "쿠버네티스가 넣은 <code>SERVER_PORT=tcp://…</code>를 Spring이 포트로 읽음",
         "<code>enableServiceLinks: false</code>", "서비스 주소는 DNS 이름으로 찾는다"),
        (S[2], "1080p 변환이 모두 실패", "ffmpeg 종료 코드 137 · 메모리 한도 1GiB · JVM 최대 힙 75% 설정",
         "메모리 부족으로 보고 조정", "JVM 최대 힙 20% · 한도 2GiB → 6개 모두 정상 변환"),
        (S[4], "Docker Desktop 전체가 멈춤", "최대 서버 5 · 워커 4 → 한도 합 CPU 약 13개 · 메모리 약 15GB",
         "최대 서버 4 · 워커 2", "한도 합이 CPU 10개 · 메모리 8GB 안으로"),
    ]
    out = "".join(rv(a, "issue", f'<div class="sym"><b>{esc(t)}</b><small>{c}</small></div><i>→</i>'
                     f'<div class="fix"><b>{f}</b><small>{esc(fs)}</small></div>') for a, t, c, f, fs in rows)
    return f"""
      {header("결과", 11, "확인하면서 고친 것", S.st)}
      <div class="issues">{out}</div>
      {rv(S[5], "badge accent", "원인과 조치를 보고서와 README에 기록", style="position:absolute;left:100px;top:700px;font-size:24px")}
    """


def s12():
    S = Cues(12)
    rows = [
        (S[1], "API 서버 CPU", "변환이 같은 서버의 CPU를 나눠 씀", "변환은 워커 프로세스에서 · 영상 6개 변환 중 API CPU 2~4%"),
        (S[3], "부하별 확장", "서버를 늘려도 각자 자기 업로드만 변환", "요청이 많으면 서버 2~4대 · 업로드가 많으면 워커 1~2대"),
        (S[5], "장애 대비", "서버 1대뿐", "서버 최소 2대 · 강제 종료한 워커의 일은 약 57초 뒤 다른 워커가 이어받음"),
        (S[7], "자원 효율", "가장 바쁠 때 기준으로 늘 켜 둬야 함", "부하가 끝나면 1분쯤 뒤부터 줄어듦"),
        (S[8], "상태 유지", "대기열은 메모리에 · 파일은 서버 디스크에", "세션 · 데이터 · 영상 파일은 DB와 공유 볼륨에 · 16,719건 확인 통과"),
    ]
    body = "".join(rv(a, "cmp-r", f'<div class="k"><span>{i + 1}</span>{esc(k)}</div><div class="b">{esc(b)}</div>'
                      f'<div class="a">{esc(af)}</div>') for i, (a, k, b, af) in enumerate(rows))
    return f"""
      {header("정리", 12, "무엇이 좋아졌나", S.st)}
      <div class="cmp">
        {rv(S[0] + 0.3, "cmp-h", '<div></div><div class="b">기존</div><div class="a">이번 변경 뒤</div>')}
        {body}
      </div>
    """


def s13():
    S = Cues(13)
    limits = (rv(S[1], "limit", "<b>노드가 하나</b><small>Pod가 늘어도 같은 맥북의 CPU를 나눠 쓴다. "
                 "그래서 최대 4대로 묶었고, 4대에서도 CPU는 목표 60%보다 높았다.</small>"
                 + rv(S[2] + 0.6, "next", "→ 실제 클러스터에서는 노드 자동 확장과 함께"))
              + rv(S[3], "limit", "<b>공유 볼륨은 노드 하나에서만</b><small>ReadWriteOnce 볼륨이라 노드가 여러 대면 함께 쓸 수 없다.</small>"
                   + rv(S[4], "next", "→ S3 같은 오브젝트 스토리지 · 파일 경로를 쓰는 업로드·변환과 재생 방식도 함께 변경")))
    return f"""
      {header("정리", 13, "한계와 다음 단계", S.st)}
      <div class="limits">{limits}</div>
      {rv(S[5], "outro", '<div class="eyebrow">Web Broadcast</div><h1 class="logo">로컬 오토스케일</h1><p>감사합니다</p>')}
    """


# ---------- 조립 ----------

def subtitles():
    return "".join(
        f'<div id="sub{i}" class="clip sub" data-start="{s["start"]:.3f}" data-duration="{max(0.2, s["end"] - s["start"]):.3f}">'
        f'<span>{esc(s["text"])}</span></div>'
        for i, s in enumerate(SUBS))


CSS = SHARED_CSS.read_text() + (ROOT / "tools" / "autoscale.css").read_text()
FONTS = """
@font-face { font-family: 'Apple SD Gothic Neo'; src: local('Apple SD Gothic Neo'), local('AppleSDGothicNeo-Regular'); font-weight: 400; }
@font-face { font-family: 'Apple SD Gothic Neo'; src: local('AppleSDGothicNeo-SemiBold'); font-weight: 600; }
@font-face { font-family: 'Apple SD Gothic Neo'; src: local('AppleSDGothicNeo-Bold'); font-weight: 700; }
@font-face { font-family: 'Apple SD Gothic Neo'; src: local('AppleSDGothicNeo-ExtraBold'); font-weight: 800; }
@font-face { font-family: 'JetBrainsMonoNL Nerd Font'; src: url('../assets/fonts/JetBrainsMonoNLNerdFont-Regular.ttf'); font-weight: 400; }
@font-face { font-family: 'JetBrainsMonoNL Nerd Font'; src: url('../assets/fonts/JetBrainsMonoNLNerdFont-Bold.ttf'); font-weight: 700; }
"""
GSAP = '<script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script>'

SCENE_SCRIPT = """
<script>
(() => {
  const id = %(id)s, OFFSET = %(offset).3f, D = %(dur).3f;
  const root = document.querySelector('[data-composition-id="' + id + '"]');
  const tl = gsap.timeline({ paused: true });
  // 첫 장면은 첫 프레임(썸네일)부터 보이도록 서서히 나타나지 않는다
  if (OFFSET > 0) tl.fromTo(root.querySelector('.scene'), { opacity: 0 }, { opacity: 1, duration: 0.45, ease: 'power1.out' }, 0);
  tl.to(root.querySelector('.scene'), { opacity: 0, duration: 0.35, ease: 'power1.in' }, D - 0.35);
  root.querySelectorAll('.rv').forEach((el) => {
    const at = Math.max(0, +el.dataset.at - OFFSET);
    tl.fromTo(el, { opacity: 0, y: 22 }, { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out' }, at);
    if (el.dataset.out) tl.to(el, { opacity: 0, duration: 0.2 }, Math.max(0, +el.dataset.out - OFFSET) - 0.2);
  });
  // 차트 선: 클립 사각형을 넓혀 왼쪽에서 오른쪽으로 그린다
  root.querySelectorAll('.wipe').forEach((el) => {
    const at = Math.max(0, +el.dataset.at - OFFSET);
    tl.fromTo(el, { attr: { width: 0 } }, { attr: { width: +el.dataset.w }, duration: +el.dataset.dur, ease: 'none' }, at);
  });
  window.__timelines = window.__timelines || {};
  window.__timelines[id] = tl;
})();
</script>"""

SCENE_FUNCS = [s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13]
COMP = VIDEO / "compositions"
COMP.mkdir(exist_ok=True)
mounts = []
for f in SCENE_FUNCS:
    sid = int(f.__name__[1:])
    sc = SCENES[sid]
    cid = f"scene{sid}"
    doc = f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="UTF-8">
{GSAP}
<style>{FONTS}{CSS}</style>
</head>
<body>
<div id="{cid}-root" data-composition-id="{cid}" data-width="1920" data-height="1080" data-duration="{sc['duration']:.3f}">
  <section class="scene">{f()}</section>
  {SCENE_SCRIPT % {'id': json.dumps(cid), 'offset': sc['start'], 'dur': sc['duration']}}
</div>
</body>
</html>
"""
    (COMP / f"{cid}.html").write_text(doc)
    mounts.append(f'<div id="{cid}" class="clip" data-composition-id="{cid}" data-composition-src="compositions/{cid}.html" '
                  f'data-start="{sc["start"]:.3f}" data-duration="{sc["duration"]:.3f}"></div>')

total = TL["total"]
page = f"""<!doctype html>
<html lang="ko" data-resolution="landscape">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=1920, height=1080">
{GSAP}
<style>{FONTS.replace("../assets", "assets")}{CSS}</style>
</head>
<body>
<div id="root" data-composition-id="main" data-start="0" data-duration="{total:.3f}" data-width="1920" data-height="1080">
  <audio id="narration" class="clip" src="assets/audio/narration.wav" data-start="0" data-duration="{total:.3f}"></audio>
  {''.join(mounts)}
  <div class="subs">{subtitles()}</div>
</div>
<script>
  const tl = gsap.timeline({{ paused: true }});
  document.querySelectorAll('.sub').forEach((el) => {{
    tl.fromTo(el, {{ opacity: 0 }}, {{ opacity: 1, duration: 0.12 }}, +el.dataset.start);
  }});
  window.__timelines = window.__timelines || {{}};
  window.__timelines["main"] = tl;
  tl.seek(0);
</script>
</body>
</html>
"""
(VIDEO / "index.html").write_text(page)
print(f"wrote index.html + {len(SCENE_FUNCS)} scenes", f"{total:.1f}s", len(SUBS), "subtitles")
