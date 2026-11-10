"""timeline.json과 캡처 화면으로 HyperFrames 컴포지션(video/index.html)을 만든다.

python3 tools/build_composition.py
"""

import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VIDEO = ROOT / "video"
TL = json.loads((VIDEO / "timeline.json").read_text())
STATE = json.loads((ROOT / "tools/capture/state.local.json").read_text())
RECTS = STATE.get("rects", {})

SCENES = {s["id"]: s for s in TL["scenes"]}
OFFSET = 0.0  # 지금 만드는 장면의 시작 시각. 장면 안 시간은 이만큼 뺀 상대 시각이다
VID = [0]
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


def esc(t):
    return html.escape(t, quote=True)


# ---------- 부품 ----------

def rv(at, cls="", inner="", style="", tag="div", extra=""):
    return f'<{tag} class="rv {cls}" data-at="{at:.2f}" style="{style}" {extra}>{inner}</{tag}>'


def browser(src, width, url="localhost:5173", overlay=""):
    h = width / 1.6
    return (f'<div class="browser" style="width:{width}px">'
            f'<div class="bar"><i></i><i></i><i></i><span>{esc(url)}</span></div>'
            f'<div class="shot" style="height:{h:.0f}px"><img src="../assets/screens/{src}.png">{overlay}</div></div>')


def phone(src, width, overlay=""):
    h = width * 844 / 390
    return (f'<div class="phone" style="width:{width + 24}px">'
            f'<div class="shot" style="width:{width}px;height:{h:.0f}px"><img src="../assets/screens/{src}.png">{overlay}</div></div>')


def video_over(rect_key, frame_width, start, duration, media_start=0, fit="cover", src="java-class.mp4", trim_bottom=0):
    r = RECTS.get(rect_key)
    if not r:
        return ""
    r = dict(r, height=r["height"] - trim_bottom)
    k = frame_width / r["vw"]
    VID[0] += 1
    rel = max(0.0, start - OFFSET)
    return (f'<video id="v{VID[0]}" class="clip over" src="../assets/media/{src}" muted playsinline data-start="{rel:.2f}" '
            f'data-duration="{duration:.2f}" data-media-start="{media_start}" '
            f'style="left:{r["x"] * k:.1f}px;top:{r["y"] * k:.1f}px;width:{r["width"] * k:.1f}px;'
            f'height:{r["height"] * k:.1f}px;object-fit:{fit}"></video>')


METHOD_CLASS = {"GET": "get", "POST": "post", "PATCH": "patch", "DELETE": "del"}


def api(method, path, desc=""):
    d = f'<div class="api-desc">{desc}</div>' if desc else ""
    return (f'<div class="api"><span class="m {METHOD_CLASS[method]}">{method}</span>'
            f'<code>{esc(path)}</code></div>{d}')


def api_card(at, rows, title="API"):
    body = "".join(f'<div class="api-row">{api(*r)}</div>' for r in rows)
    return rv(at, "card", f'<div class="card-h">{title}</div>{body}')


def chips(at, items, step=0.12):
    return "".join(rv(at + i * step, "chip", esc(t), tag="span") for i, t in enumerate(items))


JAVA_KW = set("""public private static final class void return if else for try catch new switch case default
boolean int long byte String var throw throws record""".split())


def java(code):
    out = []
    for line in code.strip("\n").split("\n"):
        hl = line.startswith("!")
        if hl:
            line = line[1:]
        parts, i = [], 0
        for m in re.finditer(r'(//.*$)|("[^"]*")|(@\w+)|(\b\d[\d_]*\b)|(\b[A-Za-z_]\w*\b)', line):
            parts.append(esc(line[i:m.start()]))
            tok = m.group(0)
            if m.group(1):
                parts.append(f'<span class="c">{esc(tok)}</span>')
            elif m.group(2):
                parts.append(f'<span class="s">{esc(tok)}</span>')
            elif m.group(3):
                parts.append(f'<span class="a">{esc(tok)}</span>')
            elif m.group(4):
                parts.append(f'<span class="n">{esc(tok)}</span>')
            elif tok in JAVA_KW:
                parts.append(f'<span class="k">{esc(tok)}</span>')
            elif tok[0].isupper():
                parts.append(f'<span class="t">{esc(tok)}</span>')
            else:
                parts.append(esc(tok))
            i = m.end()
        parts.append(esc(line[i:]))
        out.append(f'<div class="ln{" hl" if hl else ""}">{"".join(parts) or "&nbsp;"}</div>')
    return "".join(out)


def code_card(at, filename, code, width=820, size=21):
    return rv(at, "code", f'<div class="code-h"><i></i><i></i><i></i><span>{esc(filename)}</span></div>'
              f'<pre style="font-size:{size}px">{java(code)}</pre>', style=f"width:{width}px")


def header(part, num, title, at):
    color = {"도입": "var(--muted)", "방송자 시점": "var(--live)", "시청자 시점": "var(--accent)",
             "마무리": "var(--muted)"}[part]
    return (f'<div class="head">{rv(at, "part", f"<b style=\'background:{color}\'></b>{part}", tag="div")}'
            f'{rv(at + 0.1, "title", f"<span>{num:02d}</span>{esc(title)}", tag="h2")}</div>')


def scene_wrap(sid, inner):
    s = SCENES[sid]
    return sid, inner


def arrow(x1, y1, x2, y2, at, label="", color="var(--muted)", dash=False, lx=None, ly=None):
    d = ' stroke-dasharray="8 8"' if dash else ""
    lab = ""
    if label:
        lx = (x1 + x2) / 2 if lx is None else lx
        ly = (y1 + y2) / 2 - 12 if ly is None else ly
        lab = f'<text x="{lx}" y="{ly}" text-anchor="middle">{esc(label)}</text>'
    return (f'<g class="rv" data-at="{at:.2f}"><line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="3"{d} marker-end="url(#ah)"/>{lab}</g>')


def node(x, y, w, h, at, title, sub="", cls=""):
    return rv(at, f"node {cls}", f"<b>{esc(title)}</b><small>{esc(sub)}</small>",
              style=f"left:{x}px;top:{y}px;width:{w}px;height:{h}px")


SVG_DEFS = ('<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
            'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="context-stroke"/></marker></defs>')


def layered(kind, layers, width, overlay="", url="localhost:5173"):
    """한 프레임 안에서 캡처를 시간에 맞춰 바꾼다. layers = [(src, at), ...] 첫 장은 처음부터 보인다."""
    imgs = f'<img src="../assets/screens/{layers[0][0]}.png">'
    for src, at in layers[1:]:
        imgs += f'<img class="rv layer" data-at="{at:.2f}" src="../assets/screens/{src}.png">'
    if kind == "phone":
        h = width * 844 / 390
        return (f'<div class="phone" style="width:{width + 24}px">'
                f'<div class="shot" style="width:{width}px;height:{h:.0f}px">{imgs}{overlay}</div></div>')
    h = width / 1.6
    return (f'<div class="browser" style="width:{width}px">'
            f'<div class="bar"><i></i><i></i><i></i><span>{esc(url)}</span></div>'
            f'<div class="shot" style="height:{h:.0f}px">{imgs}{overlay}</div></div>')


def tag(at, text, cls="", style="", out=None):
    extra = f'data-out="{out:.2f}"' if out is not None else ""
    return rv(at, f"tag {cls}", esc(text), style=style, extra=extra)



# ---------- 장면 ----------

def s0():
    s = SCENES[0]
    S = sentence_starts(s)
    st = s["start"]
    return scene_wrap(0, f"""
      <div class="title-hero">
        {rv(st + 0.1, "eyebrow", "PORTFOLIO PROJECT")}
        {rv(st + 0.2, "logo", "Web Broadcast", tag="h1")}
        {rv(st + 0.4, "tagline", "휴대폰으로 방송하고, 누구나 보는 웹 방송 플랫폼")}
        <div class="hero-row">{chips(S[1], ["방송자 시점", "시청자 시점", "화면 → 기능 → API · 기술"])}</div>
      </div>
      {rv(st + 0.5, "hero-shot left", browser("live-watch", 820, overlay=video_over("viewerPlayer", 820, st, s["duration"], 15, fit="contain", trim_bottom=52)))}
      {rv(st + 0.8, "hero-shot right", phone("broadcast-live", 250, video_over("broadcasterCamera", 250, st, s["duration"], 15)))}
      {tag(st + 0.6, "시청자 · 라이브 재생", style="left:1000px;top:168px")}
      {tag(st + 0.9, "방송자 · 휴대폰 송출", "live", style="left:1590px;top:360px")}
      {tag(st + 1.2, "가상 카메라로 송출한 데모 · 재생 장면 편집", "", style="left:980px;top:800px")}
    """)


def s1():
    s = SCENES[1]
    S = sentence_starts(s)
    g = S[1]
    m = S[3]
    f = S[4]
    nodes = [
        node(80, 150, 270, 110, m, "방송자 휴대폰", "카메라 · 마이크", "live"),
        node(80, 520, 270, 110, g, "시청자 브라우저", "React + Vite", ""),
        node(560, 520, 330, 120, g, "Spring Boot 4", "Java 25 · REST API", "accent"),
        node(1120, 520, 300, 110, g, "PostgreSQL 18", "Flyway · JPA", ""),
        node(560, 150, 330, 120, m, "MediaMTX", "WHIP 수신 · LL-HLS", "live"),
        node(560, 790, 330, 100, f, "ffmpeg", "화질별 HLS · 썸네일", ""),
        node(1120, 790, 300, 100, f, "토스페이먼츠", "테스트 결제 · 승인 API", "donation"),
    ]
    arrows = [
        arrow(350, 575, 555, 575, g, "/api · 세션 쿠키"),
        arrow(890, 575, 1115, 575, g, "JPA"),
        arrow(350, 205, 555, 205, m, "WHIP (WebRTC)", "var(--live)"),
        arrow(640, 275, 640, 515, m, "인증 훅", "var(--live)", lx=575, ly=395),
        arrow(810, 515, 810, 275, m, "2초 주기 조회", "var(--live)", True, lx=900, ly=395),
        arrow(560, 250, 355, 545, m, "LL-HLS", "var(--live)", lx=420, ly=360),
        arrow(725, 645, 725, 785, f, "변환", "var(--muted)", lx=770, ly=720),
        arrow(890, 615, 1115, 830, f, "승인 · 조회", "var(--donation)", lx=1040, ly=700),
    ]
    return scene_wrap(1, f"""
      {header("도입", 1, "아키텍처", s["start"])}
      <div class="diagram" style="left:200px;top:120px;width:1520px;height:900px">
        <svg width="1520" height="900">{SVG_DEFS}{''.join(arrows)}</svg>
        {''.join(nodes)}
        {rv(m + 1.5, "callout", "미디어는 MediaMTX가 · 상태와 권한은 Spring이", style="left:1010px;top:170px")}
      </div>
    """)


def s2():
    s = SCENES[2]
    S = sentence_starts(s)
    return scene_wrap(2, f"""
      {header("방송자 시점", 2, "로그인과 세션", s["start"])}
      {rv(s["start"] + 0.3, "frame left-frame", browser("login", 1000, "localhost:5173/login"))}
      <div class="side">
        {api_card(S[1], [("POST", "/api/auth/signup"), ("POST", "/api/auth/login"), ("POST", "/api/auth/logout", "세션을 바로 무효화"), ("GET", "/api/auth/me", "로그인 상태 · CSRF 토큰 쿠키 발급")])}
        {rv(S[2], "versus", '<div class="no">JWT</div><div class="arrow">→</div><div class="yes">서버 세션 <small>Spring Session JDBC · PostgreSQL</small></div>')}
        <div class="chips">{chips(S[3], ["Spring Security", "HttpOnly 쿠키", "재시작해도 유지 (만료 전)", "즉시 로그아웃"])}</div>
        {rv(S[4], "card mono-card", '<div class="card-h">CSRF · 웹 요청</div><div>쿠키 <code>XSRF-TOKEN</code> → 헤더 <code>X-XSRF-TOKEN</code></div><div class="muted">웹에서 상태를 바꾸는 요청에 함께 보냄 (MediaMTX 인증 훅은 예외)</div>')}
      </div>
    """)


def s3():
    s = SCENES[3]
    S = sentence_starts(s)
    resp = ('<div class="card-h">201 Created</div><pre class="json">{\n  "broadcast": { "status": <span class="s">"PENDING"</span>, … },\n'
            '  "streamKey": <span class="s">"q3J…Xw"</span>,\n  "whipUrl": <span class="s">"/whip/live/{id}/whip"</span>\n}</pre>')
    return scene_wrap(3, f"""
      {header("방송자 시점", 3, "방송 시작 · 방송 키", s["start"])}
      {rv(s["start"] + 0.3, "frame phone-frame", layered("phone", [("broadcast-preview", 0), ("broadcast-starting", S[0] + 1.6)], 340))}
      {tag(S[0] + 1.6, "방송 시작 클릭 → 연결 중", "live", style="left:200px;top:930px")}
      <div class="side wide">
        {api_card(S[0] + 1.5, [("POST", "/api/broadcasts", "준비 상태의 방송 + 방송 키 발급")])}
        <div class="row">
          {rv(S[0] + 2.5, "card", resp, style="flex:1.3")}
          {rv(S[1], "card warn", '<div class="card-h">409 Conflict</div><div>진행 중인 방송이 있으면</div><code>activeBroadcastId</code>', style="flex:1")}
        </div>
        <div class="row">
          {rv(S[3], "chip", "원문은 발급 응답에만", tag="span")}{rv(S[3] + 0.2, "chip", "DB에는 해시", tag="span")}{rv(S[3] + 0.4, "chip", "재발급하면 이전 키 무효", tag="span")}
        </div>
        {code_card(S[2], "StreamKey.java", '''
static String generate() {
    byte[] bytes = new byte[32];
!    RANDOM.nextBytes(bytes);          // SecureRandom
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
}
static boolean matches(String key, String expectedHash) {
!    return MessageDigest.isEqual(hash(key).getBytes(US_ASCII),   // SHA-256
        expectedHash.getBytes(US_ASCII));
}''', width=960, size=16)}
      </div>
    """)


def s4():
    s = SCENES[4]
    S = sentence_starts(s)
    ov = video_over("broadcasterCamera", 340, s["start"], s["duration"], 8)
    flow = [
        ("휴대폰 브라우저", "getUserMedia()"), ("WHIP POST", "SDP offer · ?key="), ("MediaMTX", "인증 훅 호출"),
        ("Spring", "방송 키 해시 비교"),
    ]
    steps = "".join(rv(S[0] + 0.6 * i if i < 2 else S[2] + 0.6 * (i - 2), "step", f"<b>{a}</b><small>{esc(b)}</small>")
                    + (f'<span class="rv step-arrow" data-at="{(S[0] if i < 2 else S[2]) + 0.6 * (i % 2) + 0.3:.2f}">→</span>' if i < 3 else "")
                    for i, (a, b) in enumerate(flow))
    return scene_wrap(4, f"""
      {header("방송자 시점", 4, "휴대폰 송출 · 송출 허가", s["start"])}
      {tag(s["start"] + 0.6, "가상 카메라로 송출한 데모 · 재생 장면 편집", "", style="left:1300px;top:70px")}
      {rv(s["start"] + 0.3, "frame phone-frame", phone("broadcast-live", 340, ov))}
      <div class="side wide">
        <div class="flow">{steps}</div>
        <div class="chips">{chips(S[1], ["WebRTC", "WHIP", "WHIP 송출 코덱 H.264 고정", "지원 브라우저에서 최대 2.5Mbps"])}</div>
        {code_card(S[2] + 1.0, "MediaServerAuthController.java", '''
@PostMapping("/api/media-server/auth")
ResponseEntity<Void> authorize(@RequestBody AuthRequest request) {
    boolean allowed = switch (request.action()) {
!        case "publish" -> broadcastService.canPublish(request.path(), streamKeyOf(request));
!        case "read"    -> broadcastService.canWatch(request.path());
        default        -> false;
    };
    return ResponseEntity.status(allowed ? OK : UNAUTHORIZED).build();
}''', width=1080, size=19)}
      </div>
    """)


def s5():
    s = SCENES[5]
    S = sentence_starts(s)
    st = s["start"]
    end_at = S[2] + 1.0       # "동기화 도중 소유자가 방송을 종료하면"
    ended_at = end_at + 1.6
    states = (rv(st + 0.3, "state pending", "<b>준비</b><small>PENDING</small>") + rv(st + 0.5, "st-arrow", "영상 도착 →")
              + rv(st + 0.7, "state live", "<b>방송 중</b><small>LIVE</small>") + rv(end_at, "st-arrow", "종료 · 끊김 →")
              + rv(ended_at, "state ended", "<b>종료</b><small>ENDED</small>"))
    ov = video_over("broadcasterCamera", 300, st, end_at - st + 0.3, 40)
    return scene_wrap(5, f"""
      {header("방송자 시점", 5, "방송 상태 동기화", st)}
      {rv(st + 0.3, "frame phone-frame small", layered("phone", [("broadcast-live", 0), ("broadcast-confirm-end", end_at), ("broadcast-ended", ended_at)], 300, ov))}
      {tag(end_at, "방송 종료 → 종료 확인", "live", style="left:230px;top:900px")}
      <div class="side wide">
        <div class="states">{states}</div>
        {rv(S[1], "card accent-card", '<div class="card-h">훅 대신 주기적 조회</div><div>조회에 성공하면 실제 송출 상태를 DB에 반영 · 이벤트를 놓쳐도 다음 조회에서 맞춰진다</div>')}
        {code_card(S[1] + 1.5, "BroadcastSync.java", '''
!@Scheduled(fixedDelay = 2000)
void sync() {
    var active = mediaServer.activePaths();          // MediaMTX 제어 API
    for (Broadcast b : repository.findByStatusIn(EnumSet.of(PENDING, LIVE))) {
        if (b.syncWithMedia(active.containsKey(MediaPath.of(b.getId())), now)) {
            try { repository.save(b); }
!            catch (OptimisticLockingFailureException e) { /* 다음 주기에 다시 */ }
        }
    }
!    // 종료된 방송으로 들어오는 송출은 mediaServer.disconnect()
}''', width=1080, size=19)}
        {rv(end_at, "api-inline", api("POST", "/api/broadcasts/{id}/end") + api("DELETE", "/api/broadcasts/{id}"))}
      </div>
    """)


def s6():
    s = SCENES[6]
    S = sentence_starts(s)
    st = s["start"]
    hls = (rv(S[2] + 0.8, "pnode", "<b>원본</b><small>업로드 원본 보관</small>") + rv(S[2] + 1.1, "st-arrow", "→")
           + rv(S[2] + 1.4, "pnode accent", "<b>ffmpeg · HLS</b><small>원본 짧은 변 이하 화질만</small>") + rv(S[2] + 1.7, "st-arrow", "→")
           + '<div class="renditions">' + "".join(rv(S[3] + 0.3 + i * 0.2, "rend", r) for i, r in enumerate(["1080p", "720p", "480p"])) + "</div>")
    thumb = (rv(S[3] + 2.0, "pnode", "<b>원본</b><small>같은 파일</small>") + rv(S[3] + 2.2, "st-arrow", "→")
             + rv(S[3] + 2.4, "pnode", "<b>ffmpeg · 썸네일</b><small>별도 실행</small>"))
    return scene_wrap(6, f"""
      {header("방송자 시점", 6, "동영상 업로드 · HLS 변환", st)}
      {rv(st + 0.3, "frame left-frame", layered("browser", [("upload-form", 0), ("video-processing", S[2]), ("video-detail", S[4])], 940, url="localhost:5173/upload → /videos/…"))}
      {tag(S[2], "처리 중 (PROCESSING)", style="left:110px;top:840px", out=S[4])}
      {tag(S[4], "변환 완료 후 · 공개 (PUBLISHED) · 재생", "accent", style="left:110px;top:840px")}
      <div class="side" style="left:1090px;width:730px">
        {api_card(S[0] + 1.0, [("POST", "/api/videos", "multipart · 201 처리 중(PROCESSING)")])}
        <div class="pipe">{hls}</div>
        <div class="pipe">{thumb}</div>
        {rv(S[3] + 0.3, "muted small-note", "1080p·720p·480p는 1080p 원본일 때의 예")}
        {code_card(S[5], "VideoProcessingQueue.java", '''
!ExecutorService executor = Executors.newSingleThreadExecutor(...);
void enqueue(UUID videoId) {
    executor.submit(() -> processor.process(videoId));
}
!@EventListener(ApplicationReadyEvent.class)
void resumeUnfinished() {          // 처리 중이던 동영상을 다시 큐에
    for (Video v : repository.findByStatus(PROCESSING))
        enqueue(v.getId());
}''', width=720, size=16)}
      </div>
    """)


def s7():
    s = SCENES[7]
    S = sentence_starts(s)
    ov = video_over("viewerPlayer", 1000, S[3], s["start"] + s["duration"] - S[3], 30, fit="contain", trim_bottom=52)
    return scene_wrap(7, f"""
      {header("시청자 시점", 7, "라이브 목록 · 라이브 시청", s["start"])}
      {rv(s["start"] + 0.3, "frame left-frame", layered("browser", [("live-list", 0), ("live-watch", S[3])], 1000, ov, url="localhost:5173 → /live/…"))}
      {tag(S[1], "로그인하지 않은 브라우저", "accent", style="left:110px;top:880px")}
      {tag(S[3], "가상 카메라 송출 · 재생 장면 편집", "", style="left:420px;top:880px")}
      <div class="side">
        {rv(S[1], "card accent-card", '<div class="card-h">로그인 없이 누구나</div><div>무언가를 만들거나 바꿀 때만 로그인</div>')}
        {api_card(S[2], [("GET", "/api/broadcasts", "live(방송 중) · recentlyEnded(최근 종료)"), ("GET", "/api/broadcasts/{id}", "방송 중이면 playbackUrl (LL-HLS)")])}
        <div class="chips">{chips(S[3] + 0.5, ["LL-HLS", "hls.js"])}</div>
        {rv(S[4], "card", '<div class="card-h">역할 나누기</div><div>MediaMTX 인증 훅 <code>read</code>: 종료된 방송 거부</div><div>웹: <code>LIVE</code>일 때만 플레이어</div>')}
      </div>
    """)


def s8():
    s = SCENES[8]
    S = sentence_starts(s)
    ov = video_over("videoPlayer", 1000, S[1] + 1.2, s["start"] + s["duration"] - S[1] - 1.2, 25, fit="contain", src="sql-video.mp4", trim_bottom=52)
    q = RECTS.get("quality")
    menu = ""
    if q and "videoPlayer" in RECTS:
        k = 1000 / RECTS["videoPlayer"]["vw"]
        items = "".join(f'<li class="{"on" if o == "720p" else ""}">{o}</li>' for o in STATE.get("qualityOptions", ["자동", "1080p", "720p", "480p"]))
        menu = rv(S[1] + 0.6, "qmenu", f"<ul>{items}</ul>", style=f"left:{(q['x'] + q['width']) * k - 96:.0f}px;top:{q['y'] * k - 150:.0f}px")
    return scene_wrap(8, f"""
      {header("시청자 시점", 8, "동영상 시청 · 화질 선택", s["start"])}
      {rv(s["start"] + 0.3, "frame left-frame", layered("browser", [("video-list", 0), ("video-detail-auto", S[0] + 2.5), ("video-detail", S[1] + 1.2)], 1000, ov + menu, url="localhost:5173/videos → /videos/…"))}
      {tag(S[0] + 2.5, "화질: 자동", style="left:110px;top:880px", out=S[1] + 1.2)}
      {tag(S[1] + 1.2, "화질: 자동 → 720p · 열린 메뉴와 재생 영상은 편집 화면", "accent", style="left:110px;top:880px")}
      <div class="side">
        {api_card(S[0] + 0.5, [("GET", "/api/videos?page=0", "공개(PUBLISHED)만 · 최신순 20개"), ("GET", "/api/videos/{id}", "playbackUrl · thumbnailUrl"), ("GET", "/media/videos/{id}/…", "HLS 플레이리스트 · 세그먼트")])}
        <div class="chips">{chips(S[1] + 0.3, ["적응형 HLS", "hls.js 화질 전환", "master.m3u8"])}</div>
      </div>
    """)


def s9():
    s = SCENES[9]
    S = sentence_starts(s)
    cursor = ('<div class="card-h">커서 페이지네이션</div><div class="cursor">'
              '<span class="pg">20개</span><span class="cur">nextCursor</span><span class="pg">20개</span><span class="cur">nextCursor</span><span class="pg">…</span></div>'
              '<code>GET /api/videos/{id}/comments?before=&lt;cursor&gt;</code><div class="muted">마지막으로 본 댓글보다 오래된 댓글 · 새 댓글이 달려도 페이지 경계 유지</div>')
    posted = S[0] + 2.6
    return scene_wrap(9, f"""
      {header("시청자 시점", 9, "댓글", s["start"])}
      {rv(s["start"] + 0.3, "frame left-frame", layered("browser", [("video-comment-typing", 0), ("video-comments", posted)], 1000, url="localhost:5173/videos/…"))}
      {tag(s["start"] + 0.6, "댓글 입력", style="left:110px;top:880px", out=posted)}
      {tag(posted, "등록 → 목록에 표시 · 내 댓글에만 수정·삭제", "accent", style="left:110px;top:880px")}
      <div class="side">
        {api_card(S[0] + 0.5, [("POST", "/api/videos/{id}/comments", "로그인 필요 · 500자 이하"), ("PATCH", "/api/comments/{id}", "작성자만"), ("DELETE", "/api/comments/{id}", "작성자만 · 콘텐츠를 지우면 댓글도 함께 삭제")])}
        {rv(S[2], "card warn", '<div class="card-h">권한은 서버가 검사</div><div>로그인 필요 <code>401</code> · 권한 없음·CSRF 없음 <code>403</code> · ProblemDetail</div>')}
        {rv(S[3], "card", cursor)}
      </div>
    """)


def s10():
    s = SCENES[10]
    S = sentence_starts(s)
    toss = S[2] + 1.5
    return scene_wrap(10, f"""
      {header("시청자 시점", 10, "후원 시작 · 결제창", s["start"])}
      {rv(s["start"] + 0.3, "frame left-frame", layered("browser", [("donate-form", 0), ("toss-window", toss)], 1000, url="localhost:5173/live/…/donate"))}
      {tag(toss, "토스페이먼츠 테스트 결제창", "donation", style="left:110px;top:880px")}
      <div class="side">
        <div class="chips">{chips(S[1], ["1,000 ~ 500,000원", "1,000원 단위", "메시지 100자", "자기 방송 후원 ✕", "방송 중일 때만"])}</div>
        {api_card(S[2], [("POST", "/api/broadcasts/{id}/donations", "결제 대기(PENDING) 생성 · id = orderId")])}
        {rv(toss, "card donation-card", '<div class="card-h">토스페이먼츠 SDK v2</div><div>카드/간편결제 통합결제창 · <b>테스트 모드</b></div><div class="muted">실제로 돈이 빠져나가지 않음</div>')}
      </div>
    """)


def s11():
    s = SCENES[11]
    S = sentence_starts(s)
    seq = ('<div class="seq">'
           + rv(S[0], "lane", "<b>① 저장 금액 검증</b><small>paymentKey · amount</small>")
           + rv(S[2], "st-arrow", "→")
           + rv(S[2] + 0.3, "lane donation", "<b>② 멱등키로 승인 요청</b><small>Idempotency-Key: confirm-{orderId}</small>")
           + rv(S[3], "st-arrow", "→")
           + rv(S[3] + 0.3, "lane", "<b>③ 타임아웃 → 503</b><small>결제 대기 유지 · 같은 키로 재요청</small>")
           + "</div>")
    states = (rv(S[0], "state pending", "<b>결제 대기</b><small>PENDING</small>")
              + '<div class="branches">'
              + rv(S[2] + 1.0, "state done", "<b>완료</b><small>DONE</small>")
              + rv(S[1], "state failed", "<b>실패</b><small>FAILED · AMOUNT_MISMATCH</small>")
              + rv(S[1] + 0.3, "state canceled", "<b>취소</b><small>CANCELED</small>")
              + rv(S[5], "state expired", "<b>만료</b><small>EXPIRED</small>")
              + "</div>")
    sweep = ('<div class="card-h">StaleDonationSweeper · 1분마다 · 30분 지난 결제 대기</div>'
             '<div class="fork"><div><b>승인 요청 전</b><span>→ 만료 (EXPIRED)</span></div>'
             '<div><b>승인 요청 후</b><span>→ orderId로 토스 조회해 결과 반영</span></div></div>')
    return scene_wrap(11, f"""
      {header("시청자 시점", 11, "결제 승인 · 멱등성 · 정리 작업", s["start"])}
      {tag(s["start"] + 0.3, "서버 승인 로직", "donation", style="left:1220px;top:70px")}
      <div class="pay-grid">
        <div class="pay-left">
          <div class="states vertical">{states}</div>
          {seq}
          {rv(S[4], "card", sweep)}
        </div>
        {code_card(S[0] + 1.5, "DonationService.java · 핵심 로직 요약", '''
public Donation approve(UUID id, UUID userId, String paymentKey, Long amount) {
    Donation donation = sentBy(id, userId);          // 후원자 본인만
    if (!donation.isPending()) return donation;      // 이미 끝난 결과 그대로
!    if (amount == null || amount != donation.getAmount()) {
!        if (donation.getPaymentKey() != null) throw invalid(...); // 요청 후: 거절
!        donation.fail("AMOUNT_MISMATCH", ..., now);         // 요청 전: 실패
        return saveOrCurrent(donation);
    }
    donation.requestApproval(paymentKey);
!    return settle(saveOrCurrent(donation));          // 승인 API (멱등)
}

void settleStale() {                                 // 1분마다 · 30분 지난 결제 대기
!    if (donation.getPaymentKey() == null) donation.expire(now);
!    else reconcile(donation);                        // orderId로 조회
}''', width=900, size=17)}
      </div>
    """)


def s12():
    s = SCENES[12]
    S = sentence_starts(s)
    st = s["start"]
    ov1 = video_over("viewerPlayer", 1000, st, s["duration"], 70, fit="contain", trim_bottom=52)
    return scene_wrap(12, f"""
      {header("시청자 시점", 12, "후원 알림 — 시청자 화면과 방송자 화면", st)}
      {tag(st + 0.3, "알림 UI 데모 · 완료 처리한 샘플 후원", "donation", style="left:1180px;top:84px")}
      <div class="split">
        <div>{tag(st + 0.4, "시청자 · 후원 기록", "", style="position:relative;margin-bottom:12px")}{rv(st + 0.3, "", browser("live-watch-donation", 1000, "localhost:5173/live/…", ov1))}</div>
        <div>{tag(st + 0.7, "방송자 · 후원 알림", "live", style="position:relative;margin-bottom:12px")}{rv(st + 0.6, "", phone("broadcast-alert", 300))}</div>
      </div>
      {rv(S[1], "api-inline center", api("GET", "/api/broadcasts/{id}/donations") + '<span class="muted">방송 중 · 완료된 후원만 · 3초 주기 조회</span>')}
    """)


def s13():
    s = SCENES[13]
    S = sentence_starts(s)
    cards = [
        (S[0] + 1.5, "책임 분리", "미디어는 MediaMTX, Spring은 권한과 상태"),
        (S[1] + 2.5, "지금 규모에 맞는 선택", "DB 세션 + CSRF · 큐 없는 서버 내 변환"),
        (S[3], "놓쳐도 맞춰지는 동기화", "이벤트 훅 대신 2초 간격 조회 · DB 상태 반영"),
        (S[3] + 1.8, "결과를 받지 못해도 다시 맞추는 결제", "금액 검증 · 같은 멱등키 재요청 · 정리 작업"),
    ]
    body = "".join(rv(a, "decision", f"<span>{i + 1}</span><b>{esc(t)}</b><small>{esc(d)}</small>") for i, (a, t, d) in enumerate(cards))
    scenario = ('<div class="card-h">기존 테스트 시나리오 · DonationTest.unknownResultStaysPendingUntilAskedAgain</div>'
                '<div class="scenario"><span>승인 응답 타임아웃</span><i>→</i><span>503 · PENDING 유지</span><i>→</i>'
                '<span>같은 결제 정보로 재시도</span><i>→</i><span class="ok">DONE</span></div>')
    return scene_wrap(13, f"""
      {header("마무리", 13, "설계 결정과 테스트", s["start"])}
      <div class="decisions">{body}</div>
      <div class="tests">{chips(S[4], ["JUnit 5", "Testcontainers · PostgreSQL", "AccessControlTest", "DonationTest"])}</div>
      {rv(S[4] + 3.0, "card scenario-card", scenario)}
      {rv(S[-2], "outro", '<h1 class="logo">Web Broadcast</h1><p>감사합니다</p>')}
    """)


def subtitles():
    return "".join(
        f'<div id="sub{i}" class="clip sub" data-start="{s["start"]:.3f}" data-duration="{max(0.2, s["end"] - s["start"]):.3f}"><span>{esc(s["text"])}</span></div>'
        for i, s in enumerate(SUBS))


CSS = (ROOT / "tools/composition.css").read_text()
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
  tl.fromTo(root.querySelector('.scene'), { opacity: 0 }, { opacity: 1, duration: 0.45, ease: 'power1.out' }, 0);
  tl.to(root.querySelector('.scene'), { opacity: 0, duration: 0.35, ease: 'power1.in' }, D - 0.35);
  root.querySelectorAll('.shot img').forEach((img) => tl.fromTo(img, { scale: 1 }, { scale: 1.035, duration: D, ease: 'none' }, 0));
  root.querySelectorAll('.rv').forEach((el) => {
    const at = Math.max(0, +el.dataset.at - OFFSET);
    tl.fromTo(el, { opacity: 0, y: 22 }, { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out' }, at);
    if (el.dataset.out) tl.to(el, { opacity: 0, duration: 0.2 }, Math.max(0, +el.dataset.out - OFFSET) - 0.2);
  });
  window.__timelines = window.__timelines || {};
  window.__timelines[id] = tl;
})();
</script>"""

COMP = ROOT / "video" / "compositions"
COMP.mkdir(exist_ok=True)
mounts = []
for f in [s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13]:
    sid = int(f.__name__[1:])
    sc = SCENES[sid]
    globals()['OFFSET'] = sc["start"]
    _, inner = f()
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
  <section class="scene">{inner}</section>
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
print("wrote index.html + 14 scenes", f"{total:.1f}s", len(SUBS), "subtitles")
