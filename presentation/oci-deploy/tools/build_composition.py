"""oci-deploy/video/timeline.json과 캡처로 HyperFrames 컴포지션(video/index.html)을 만든다.

python3 oci-deploy/tools/build_composition.py

화면 요소는 나레이션 문장이 시작하는 시각(Cues)에 맞춰 나타나고(data-at), 필요하면 사라진다(data-out).
캡처: video/assets/screens/*.png, 녹화: video/assets/media/*.mp4 (git에 없음, README 참고).
Windows 녹화본(video/assets/media/windows-run.mp4)이 있으면 장면 6·7이 그 영상을 쓰고, 없으면 "확인 예정" 자리를 보여 준다.
"""

import html
import json
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VIDEO = ROOT / "video"
ASSETS = VIDEO / "assets"
SHARED_CSS = ROOT.parent / "tools" / "composition.css"
TL = json.loads((VIDEO / "timeline.json").read_text())
SCENES = {s["id"]: s for s in TL["scenes"]}
OUTRO_HOLD = 1.6
SCENES[max(SCENES)]["duration"] += OUTRO_HOLD
TL["total"] += OUTRO_HOLD
TL["subtitles"][-1]["end"] += OUTRO_HOLD
SUBS = TL["subtitles"]
WINDOWS_CLIP = ASSETS / "media" / "windows-run.mp4"
HAVE_WINDOWS = WINDOWS_CLIP.exists()
TERM = {(c["scene"], c["cmd"]): c["out"] for c in json.loads((ROOT / "tools" / "terminal-captures.json").read_text())}
OFFSET = [0.0]  # 지금 만드는 장면의 시작 시각 (video 요소의 data-start는 장면 기준 상대 시각)


def sentence_starts(scene):
    a, b = scene["start"], scene["start"] + scene["duration"]
    starts, new = [], True
    for s in SUBS:
        if a <= s["start"] < b:
            if new:
                starts.append(s["start"])
            new = s["text"].rstrip().endswith((".", "?", "!"))
    return starts


class Cues:
    """S[i]: i번째 문장 시작 시각(절대). S.end: 장면 끝."""

    def __init__(self, sid):
        self.scene = SCENES[sid]
        self.starts = sentence_starts(self.scene)

    def __getitem__(self, i):
        return self.starts[min(i, len(self.starts) - 1)]

    @property
    def st(self):
        return self.scene["start"]

    @property
    def end(self):
        return self.scene["start"] + self.scene["duration"]


def esc(t):
    return html.escape(t, quote=True)


def png_size(name):
    with open(ASSETS / "screens" / f"{name}.png", "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


# ---------- 부품 ----------

def rv(at, cls="", inner="", style="", tag="div", out=None, extra=""):
    o = f' data-out="{out:.2f}"' if out is not None else ""
    return f'<{tag} class="rv {cls}" data-at="{at:.2f}"{o} style="{style}" {extra}>{inner}</{tag}>'


PART_COLOR = {"도입": "var(--muted)", "도메인": "var(--blue)", "서버": "var(--blue)", "배포": "var(--accent)",
              "데스크톱 앱": "var(--donation)", "정리": "var(--muted)"}


def header(part, num, title, at):
    return (f'<div class="head">{rv(at, "part", f"<b style=\'background:{PART_COLOR[part]}\'></b>{part}")}'
            f'{rv(at + 0.1, "title", f"<span>{num:02d}</span>{esc(title)}", tag="h2")}</div>')


def frame(at, left, top, width, layers, url="", label="", out=None, bar=True, extra=""):
    """캡처 이미지를 비율 그대로 보여 주는 창. layers = [이름, (이름, 나타날 시각), ...]"""
    first = layers[0]
    w, h = png_size(first)
    ph = width * h / w
    imgs = f'<img src="../assets/screens/{first}.png">'
    for name, t in layers[1:]:
        imgs += rv(t, "layer", tag="img", extra=f'src="../assets/screens/{name}.png"')
    top_bar = (f'<div class="bar"><i></i><i></i><i></i>' + (f'<span>{esc(url)}</span>' if url else "")
               + (f'<em>{esc(label)}</em>' if label else "") + '</div>') if bar else ""
    return rv(at, "frame", f'{top_bar}<div class="pic" style="height:{ph:.0f}px">{imgs}{extra}</div>',
              style=f"left:{left}px;top:{top}px;width:{width}px", out=out)


def video_frame(at, left, top, width, src, size, start, duration, media_start=0.0, label="", out=None):
    w, h = size
    ph = width * h / w
    rel = max(0.0, start - OFFSET[0])
    vid = (f'<video id="v-{Path(src).stem}-{int(start)}" class="clip" src="../assets/media/{src}" muted playsinline '
           f'data-start="{rel:.2f}" data-duration="{duration:.2f}" data-media-start="{media_start}"></video>')
    bar = f'<div class="bar"><i></i><i></i><i></i><em>{esc(label)}</em></div>'
    return rv(at, "frame", f'{bar}<div class="pic" style="height:{ph:.0f}px">{vid}</div>',
              style=f"left:{left}px;top:{top}px;width:{width}px", out=out)


def term(left, top, width, rows, out=None, title=""):
    """rows = [(나타날 시각, 장면 번호, 명령), ...] 출력은 terminal-captures.json의 실제 값"""
    body = ""
    for at, scene, cmd, shown in rows:
        res = TERM[(scene, cmd)]
        cls = "out ok" if re.search(r"Let's Encrypt|308|301", res) else "out"
        if res.strip() in ("404",) or "닫힘" in res:
            cls = "out bad"
        body += rv(at, "cmd", esc(shown)) + rv(at + 0.7, cls, esc(res))
    first = rows[0][0] - 0.2
    return rv(first, "term", f'<div class="bar"><i></i><i></i><i></i></div><div class="body">{body}</div>',
              style=f"left:{left}px;top:{top}px;width:{width}px", out=out)


def tag(at, text, cls="", style="", out=None):
    return rv(at, f"tag {cls}", esc(text), style=style, out=out)


def card(at, head, body, cls="", style="", out=None):
    return rv(at, f"card {cls}", f'<div class="card-h">{esc(head)}</div>{body}', style=style, out=out)


def chips(at, items, step=0.15, cls="chip"):
    return "".join(rv(at + i * step, cls, esc(t), tag="span") for i, t in enumerate(items))


def hl_line(line, lang):
    """Caddyfile·YAML 간단 강조"""
    m = re.match(r"^(\s*)(.*?)(\s+#.*|^#.*)?$", line)
    indent, body, comment = m.group(1), m.group(2), m.group(3) or ""
    if body.startswith("#"):
        body, comment = "", body
    out = esc(indent)
    if lang == "yaml":
        km = re.match(r"^(- )?([\w.-]+)(:)(.*)$", body)
        if km and not body.startswith("- \""):
            out += esc(km.group(1) or "") + f'<span class="t">{esc(km.group(2))}</span>{esc(km.group(3))}'
            rest = km.group(4)
        else:
            rest = body
        rest = esc(rest)
        rest = re.sub(r"(&quot;[^&]*&quot;)", r'<span class="s">\1</span>', rest)
        rest = re.sub(r"(!reset|!override)", r'<span class="k">\1</span>', rest)
        rest = re.sub(r"(\$\{[^}]*\})", r'<span class="a">\1</span>', rest)
        out += rest
    else:
        # 원문을 토큰으로 나눠 감싼다 (이미 넣은 태그를 다시 건드리지 않게)
        directives = {"handle", "handle_path", "reverse_proxy", "respond", "root", "try_files",
                      "file_server", "header_down", "redir", "encode"}
        tok = re.compile(r"(?P<host>^[\w.]+\.dev\b)|(?P<addr>127\.0\.0\.1:\d+|\b404\b)|(?P<path>/[\w/*.{}-]*)"
                         r"|(?P<word>^[a-z_]+\b)")
        i = 0
        for m in tok.finditer(body):
            out += esc(body[i:m.start()])
            t = m.group(0)
            if m.group("host"):
                out += f'<span class="t">{esc(t)}</span>'
            elif m.group("addr"):
                out += f'<span class="n">{esc(t)}</span>'
            elif m.group("path"):
                out += f'<span class="s">{esc(t)}</span>'
            elif t in directives:
                out += f'<span class="k">{esc(t)}</span>'
            else:
                out += esc(t)
            i = m.end()
        out += esc(body[i:])
    if comment:
        out += f'<span class="c">{esc(comment)}</span>'
    return out or "&nbsp;"


def code_card(at, left, top, width, filename, code, lang, size=19, marks=(), out=None):
    """marks = [(첫 줄, 끝 줄, 나타날 시각, 사라질 시각|None)] 줄 번호는 0부터"""
    lines = code.strip("\n").split("\n")
    lh = size * 1.55
    pre = "".join(f'<div class="ln">{hl_line(l, lang)}</div>' for l in lines)
    bars = "".join(rv(a, "mark", out=o, style=f"top:{40 + 16 + i * lh:.1f}px;height:{(j - i + 1) * lh:.1f}px")
                   for i, j, a, o in marks)
    return rv(at, "code", f'<div class="code-h"><i></i><i></i><i></i><span>{esc(filename)}</span></div>{bars}'
              f'<pre style="font-size:{size}px">{pre}</pre>', style=f"left:{left}px;top:{top}px;width:{width}px",
              out=out)


SVG_DEFS = ('<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
            'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="context-stroke"/></marker></defs>')


def arrow(x1, y1, x2, y2, at, label="", color="var(--muted)", out=None):
    o = f' data-out="{out:.2f}"' if out is not None else ""
    lab = f'<text x="{(x1 + x2) / 2}" y="{(y1 + y2) / 2 - 14}" text-anchor="middle">{esc(label)}</text>' if label else ""
    return (f'<g class="rv" data-at="{at:.2f}"{o}><line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="3" marker-end="url(#ah)"/>{lab}</g>')


def canvas(arrows):
    return (f'<svg class="arrows" width="1920" height="1080" style="position:absolute;left:0;top:0;overflow:visible">'
            f'{SVG_DEFS}{"".join(arrows)}</svg>')


def node(x, y, w, h, at, title, sub="", cls="", out=None):
    return rv(at, f"node {cls}", f"<b>{esc(title)}</b><small>{esc(sub)}</small>",
              style=f"left:{x}px;top:{y}px;width:{w}px;height:{h}px", out=out)


# ---------- 장면 ----------

def s0():
    S = Cues(0)
    return f"""
      <div class="title-hero" style="top:300px;width:800px">
        <div class="eyebrow">치지직 만들기 ⑥</div>
        <h1 class="logo" style="font-size:88px">도메인·OCI 배포와<br>데스크톱 앱</h1>
        {rv(S[1], "tagline", "duse.dev · HTTPS · Windows / macOS")}
        <div class="hero-row">{chips(S[2], ["① 도메인", "② 서버", "③ 배포", "④ 데스크톱 앱"])}</div>
      </div>
      {frame(S.st + 0.3, 960, 230, 860, ["duse-home", ("live-watch", S[1] + 1.0)], url="duse.dev")}
      {tag(S[1] + 1.0, "live.duse.dev · 라이브 방송 중", "live", "left:1000px;top:800px")}
    """


def s1():
    S = Cues(1)
    left = "left:100px;top:200px;width:780px"
    reg = card(S[1], "도메인 구매", '<b class="big">Cloudflare Registrar</b>'
               '<ul><li>도메인을 원가에 판매 · 웃돈 없음</li><li>DNS 관리 무료</li></ul>'
               + rv(S[3], "muted", "첫해 할인만 큰 곳이 많아 <b>갱신 가격</b>까지 비교"), style="width:780px")
    dev = card(S[4], ".dev 도메인의 특징", '<b class="big">HTTPS만 접속된다</b>'
               '<ul><li>브라우저에 HSTS 사전 등록(preload)된 최상위 도메인</li><li>http:// 로는 열리지 않는다</li></ul>'
               + rv(S[6], "muted", "→ 인증서가 처음부터 필수 · 뒤에서 <b>Caddy</b>가 자동 발급"), cls="warn", style="width:780px")
    phase1 = rv(S[1], "col", reg + dev, style=left, out=S[7])
    recs = card(S[7], "A 레코드 두 개", '<div class="kv">'
                f'{rv(S[8], "", "<code>@</code>", tag="span")}{rv(S[8], "", "duse.dev 자체", tag="span")}'
                f'{rv(S[8] + 2.0, "", "<code>*</code>", tag="span")}{rv(S[8] + 2.0, "", "live.duse.dev 같은 모든 서브도메인", tag="span")}</div>'
                + rv(S[9], "muted", "* 는 도메인 자체(@)를 포함하지 않아서 둘 다 필요", style="margin-top:12px"),
                style="width:780px")
    proxy = card(S[10], "프록시 끔 (DNS only)", '<ul>'
                 + rv(S[11], "", "WebRTC 미디어(UDP 8189)는 프록시를 통과하지 못한다", tag="li")
                 + rv(S[11] + 3.0, "", "엔터프라이즈가 아닌 요금제는 약관상 영상 대량 전달이 제한된다", tag="li") + '</ul>', style="width:780px")
    phase2 = rv(S[7], "col", recs + proxy, style=left, out=S[12])
    dig = term(100, 260, 780, [(S[12], 1, "dig +short duse.dev", "dig +short duse.dev"),
                               (S[12] + 0.8, 1, "dig +short live.duse.dev", "dig +short live.duse.dev"),
                               (S[12] + 1.6, 1, "dig +short anything.duse.dev", "dig +short anything.duse.dev")])
    return f"""
      {header("도메인", 1, "도메인과 DNS", S.st)}
      {phase1}{phase2}{dig}
      {frame(S[7], 1010, 170, 620, ["cloudflare-dns"], url="dash.cloudflare.com · DNS 레코드")}
      {tag(S[12] + 2.4, "모든 이름이 같은 서버 주소로", "accent", "left:100px;top:600px")}
    """


def s2():
    S = Cues(2)
    end_a = S[16]
    # 겹친 자원 구조
    nest = (rv(S[3], "nest region", '<div class="lb">리전<b>도쿄 ap-tokyo-1</b></div>',
               style="left:100px;top:190px;width:900px;height:700px", out=end_a)
            + rv(S[4], "nest ad", '<div class="lb">가용성 도메인<b>AD-1 (도쿄는 하나뿐)</b></div>',
                 style="left:124px;top:244px;width:852px;height:630px", out=end_a)
            + rv(S[6], "nest comp", '<div class="lb">컴파트먼트<b>루트 하나</b></div>',
                 style="left:148px;top:298px;width:804px;height:560px", out=end_a)
            + rv(S[8], "nest vcn", '<div class="lb">VCN<b>10.0.0.0/16</b></div>',
                 style="left:172px;top:352px;width:756px;height:490px", out=end_a)
            + rv(S[9], "nest subnet", '<div class="lb">공인 서브넷</div>',
                 style="left:196px;top:406px;width:560px;height:420px", out=end_a)
            + rv(S[9] + 1.2, "igw", "인터넷 게이트웨이", style="left:780px;top:440px", out=end_a)
            + rv(S[11], "inst", "<b>인스턴스</b><small>VM.Standard.A1.Flex · ARM</small>",
                 style="left:222px;top:460px;width:500px", out=end_a)
            + rv(S[15], "inst", "<b style='font-size:21px'>부트 볼륨 · 공인 IP · SSH 키</b>"
                 "<small>비밀번호 로그인 없음</small>", style="left:222px;top:580px;width:500px", out=end_a)
            + rv(S[14], "tag accent", "Docker 이미지: linux/arm64", style="left:222px;top:700px", out=end_a))
    wizard = rv(S[10], "err", "인스턴스 화면: 공인 IP 스위치 잠김 → VCN 마법사로 네트워크 먼저", style="left:1060px;top:190px;width:760px;font-family:var(--font)", out=S[11])
    capacity = rv(S[13], "err", "Out of capacity for shape VM.Standard.A1.Flex in AD-1", style="left:1060px;top:190px", out=end_a)
    detail = frame(S[11], 1060, 260, 760, ["oci-instance-detail", ("oci-instance-shape", S[13] + 1.5)],
                   url="cloud.oracle.com · 인스턴스", out=end_a)
    shape_note = rv(S[13] + 1.5, "note", "⑥에서는 1 OCPU · 6GB로 시작 — 화면은 나중에 2 OCPU · 12GB로 늘린 상태",
                    style="left:1060px;top:880px;width:760px;font-size:20px", out=end_a)
    # 방화벽 두 겹
    walls = (node(100, 400, 200, 110, S[16], "인터넷", "", "plain")
             + rv(S[17], "wall out", "<b>보안 목록</b><small>OCI · 서버 바깥</small>", style="left:380px;top:380px")
             + rv(S[17] + 2.5, "wall in", "<b>iptables</b><small>Ubuntu · 서버 안</small>", style="left:690px;top:380px")
             + node(1000, 400, 200, 110, S[17] + 3.0, "서비스", "", "accent"))
    arrows = canvas([arrow(305, 455, 372, 455, S[17]), arrow(615, 455, 682, 455, S[17] + 2.5),
                     arrow(925, 455, 992, 455, S[17] + 3.0)])
    ports = rv(S[18], "ports", '<span class="port">22 SSH (기본)</span>'
               + rv(S[18] + 2.0, "port open", "80", tag="span") + rv(S[18] + 2.4, "port open", "443", tag="span")
               + rv(S[18] + 2.8, "port open", "8189 TCP/UDP 방송", tag="span"), style="left:100px;top:600px;width:1100px")
    both = tag(S[18] + 4.0, "두 곳 모두 열어야 접속된다", "accent", "left:100px;top:700px")
    sec = frame(S[17], 1260, 230, 560, ["oci-security-list"], url="보안 목록 · 수신 규칙")
    return f"""
      {header("서버", 2, "OCI 서버의 기본 개념", S.st)}
      {tag(S[1], "상시 무료(Always Free)", "accent", "left:1060px;top:120px", out=S[3])}
      {nest}{wizard}{capacity}{detail}{shape_note}
      {arrows}{walls}{ports}{both}{sec}
    """


def s3():
    S = Cues(3)
    g = [("1", "할당량 정책", "무료 셰이프(A1.Flex)만 만들 수 있게 막는다"),
         ("2", "예산 알림", "1센트라도 청구되면 메일이 온다 ($1 예산의 1%)"),
         ("3", "송신 상한 29 Mbit/s", "한 달 내내 보내도 약 9.7TB · 무료 10TB 안")]
    cards = "".join(rv(S[1] + i * 3.0, "guard", f"<span>{n}</span><b>{esc(t)}</b><small>{esc(d)}</small>")
                    for i, (n, t, d) in enumerate(g))
    return f"""
      {header("서버", 3, "비용 안전장치", S.st)}
      <div class="guards">{cards}</div>
    """


CADDY = """
live.duse.dev {
  handle /api/internal/* {
    respond 404
  }
  handle /api/* {
    reverse_proxy 127.0.0.1:8180
  }
  handle /media/* {
    reverse_proxy 127.0.0.1:8180
  }
  handle_path /hls/* {
    reverse_proxy 127.0.0.1:8888 {
      header_down Location ^/ /hls/
    }
  }
  handle_path /whip/* {
    reverse_proxy 127.0.0.1:8889
  }
  handle {
    root * /var/www/live.duse.dev
    try_files {path} /index.html
    file_server
  }
}
"""

COMPOSE = """
services:
  postgres:
    ports: !reset []          # 외부로 열지 않는다
  mediamtx:
    ports: !override
      - "127.0.0.1:8888:8888"  # HLS
      - "127.0.0.1:8889:8889"  # WHIP
      - "8189:8189/udp"        # WebRTC 미디어 (공개)
      - "8189:8189/tcp"
      - "127.0.0.1:9997:9997"
  server:
    environment:
      DB_PASSWORD: ${DB_PASSWORD:?}
    ports: !override
      - "127.0.0.1:8180:8180"
"""


def s4():
    S = Cues(4)
    a_end, b_end, c_end = S[8], S[15], S[19]
    # A. Caddy와 자동 HTTPS
    nodes = (node(100, 330, 250, 120, S[1], "브라우저", "https://", "plain", out=a_end)
             + node(480, 300, 300, 180, S[1] + 0.5, "Caddy", ":80 · :443", "accent", out=a_end)
             + node(920, 210, 300, 100, S[3] + 1.0, "React 앱", "정적 파일", out=a_end)
             + node(920, 340, 300, 100, S[3] + 1.4, "Spring 서버", "127.0.0.1:8180", out=a_end)
             + node(920, 470, 300, 100, S[3] + 1.8, "MediaMTX", "HLS · WHIP", out=a_end))
    arrows = canvas([arrow(355, 390, 472, 390, S[3], "443", out=a_end),
                     arrow(785, 360, 912, 260, S[3] + 1.0, out=a_end), arrow(785, 390, 912, 390, S[3] + 1.4, out=a_end),
                     arrow(785, 420, 912, 520, S[3] + 1.8, out=a_end)])
    https = card(S[4], "자동 HTTPS", '<b class="big">도메인만 적으면 끝</b>'
                 "<ul><li>Let's Encrypt 같은 공개 인증 기관에서 발급</li><li>만료 전 자동 갱신</li></ul>", cls="accent-card",
                 style="position:absolute;left:100px;top:640px;width:1120px", out=a_end)
    t1 = term(1280, 200, 540, [(S[6], 4, "openssl s_client -connect duse.dev:443 | openssl x509 -noout -issuer -enddate",
                                "openssl … duse.dev:443"),
                               (S[7], 4, "curl -sI http://duse.dev", "curl -sI http://duse.dev"),
                               (S[7] + 2.5, 4, "curl -sI https://www.duse.dev", "curl -sI https://www.duse.dev")],
              out=a_end)
    # B. 경로 나누기
    caddy = code_card(S[8], 100, 170, 760, "Caddyfile (live.duse.dev)", CADDY, "caddy", size=18, out=b_end,
                      marks=[(4, 9, S[9], S[10]), (10, 14, S[10], S[12]), (15, 17, S[12], S[13]),
                             (1, 3, S[13], S[13] + 3.5), (18, 22, S[13] + 3.5, b_end)])
    routes = rv(S[8], "routes", "".join([
        rv(S[9], "route", "<code>/api · /media</code><i>→</i><span>Spring 서버<small>127.0.0.1:8180</small></span>"),
        rv(S[10], "route", "<code>/hls</code><i>→</i><span>MediaMTX HLS (시청)<small>접두사 떼고 넘김 · Location에 /hls 다시 붙임</small></span>"),
        rv(S[12], "route", "<code>/whip</code><i>→</i><span>MediaMTX WHIP (송출)<small>접두사 떼고 넘김</small></span>"),
        rv(S[13], "route block", "<code>/api/internal</code><i>→</i><span>404<small>MediaMTX만 쓰는 내부 API</small></span>"),
        rv(S[13] + 3.5, "route", "<code>그 밖</code><i>→</i><span>React 앱<small>index.html</small></span>")]),
        style="left:920px;top:190px;width:900px", out=b_end)
    t404 = term(920, 700, 900, [(S[13] + 1.5, 4, "curl -s -o /dev/null -w '%{http_code}' https://live.duse.dev/api/internal/simulcast",
                                 "curl -s -o /dev/null -w '%{http_code}' …/api/internal/simulcast")], out=S[14])
    nginx = rv(S[14], "versus", '<span class="no">nginx + certbot</span><span class="arrow">→</span>'
               '<span class="yes">Caddy<small>인증서 관리 없음 · 설정이 짧다</small></span>',
               style="position:absolute;left:920px;top:720px", out=b_end)
    # C. docker compose
    compose = code_card(S[15], 100, 170, 860, "compose.prod.yaml (운영용 덮어쓰기)", COMPOSE, "yaml", size=19, out=c_end,
                        marks=[(1, 2, S[16], S[16] + 3.0), (3, 9, S[16] + 3.0, S[18]), (13, 14, S[16] + 3.0, S[18]),
                               (11, 12, S[18], c_end)])
    nc = term(1020, 190, 800, [(S[16] + 4.0, 2, "nc -z live.duse.dev 5432 8180 8888 8889 9997",
                                "nc -z live.duse.dev 5432 8180 8888 8889 9997")], out=c_end)
    bypass = card(S[17], "왜 127.0.0.1에 묶나", "Docker가 연 포트는 iptables INPUT 규칙을 <b>우회</b>한다.<br>"
                  "127.0.0.1에 묶어야 <b>Caddy를 거쳐서만</b> 들어온다.", cls="warn",
                  style="position:absolute;left:1020px;top:520px;width:800px", out=c_end)
    env = card(S[18], "비밀값", "DB 비밀번호 · 결제 키는 서버의 <code>.env</code>에만<br><span class='muted'>저장소에는 예시 파일만 (deploy/env.example)</span>",
               cls="accent-card", style="position:absolute;left:1020px;top:720px;width:800px", out=c_end)
    # D. 배포 결과
    result = (frame(S[19], 100, 180, 840, ["live-watch"], url="live.duse.dev/live/…", label="라이브 시청")
              + frame(S[19] + 2.5, 980, 180, 840, ["vod-detail"], url="live.duse.dev/videos/…", label="동영상 재생"))
    return f"""
      {header("배포", 4, "Caddy와 배포", S.st)}
      {arrows}{nodes}{https}{t1}
      {caddy}{routes}{t404}{nginx}
      {compose}{nc}{bypass}{env}
      {result}
    """


def s5():
    S = Cues(5)
    block = """
# 새 서비스는 블록 하나 (deploy/Caddyfile)
s.duse.dev {
  encode gzip
  reverse_proxy 127.0.0.1:8190
}
"""
    return f"""
      {header("배포", 5, "도메인 하나, 여러 서비스", S.st)}
      {frame(S.st + 0.3, 100, 180, 1000, ["duse-home"], url="duse.dev")}
      {tag(S[1] + 3.0, "Short URL은 다른 프로젝트", "", "left:620px;top:560px")}
      {code_card(S[2], 1160, 200, 660, "Caddyfile", block, "caddy", size=22)}
      {rv(S[2] + 2.5, "card", '<div class="card-h">켜고 끄기</div><code>live-on</code> <code>live-off</code> <code>live-status</code>', style="position:absolute;left:1160px;top:440px;width:660px")}
    """


def s6():
    S = Cues(6)
    mac_size = (800, 624)
    a_end = S[5]
    intro = (card(S[1], "Rust · Dioxus 0.7", '<b class="big">코드 하나로 두 OS</b>'
                  '<div class="row" style="margin-top:12px">' + chips(S[1] + 1.5, ["Windows .msi", "macOS .dmg"]) + "</div>",
                  style="width:760px")
             + rv(S[2], "card", '<div class="card-h">화면과 데이터</div>'
                  '<ul><li>WebView2(Windows) · WKWebView(macOS)가 화면을 그린다</li>'
                  '<li>웹과 같은 서버 API · 라이브는 HLS</li></ul>', style="width:760px"))
    col = rv(S[1], "col", intro, style="left:100px;top:200px;width:760px", out=S[4])
    actions = frame(S[3], 920, 190, 900, ["actions-run"], url="GitHub Actions · desktop", out=a_end)
    tag_note = tag(S[3] + 2.0, "desktop-v* 태그 → 빌드 → Release에 첨부", "accent", "left:920px;top:560px", out=a_end)
    download = frame(S[4], 100, 190, 760, ["download"], url="live.duse.dev/download/", out=a_end)
    dl_note = tag(S[4] + 3.0, "파일은 GitHub Releases에서 → 서버 송신량 0", "", "left:920px;top:640px", out=a_end)
    # 녹화본에서 라이브 시청(약 5~11초)과 동영상 재생(약 27초부터)만 이어 붙인다
    cut = S[5] + 5.6
    mac = (video_frame(S[5], 100, 200, 840, "macos-run.mp4", mac_size, S[5], cut - S[5], media_start=4.5,
                       label="macOS 앱 · 라이브 시청", out=cut + 0.3)
           + video_frame(cut, 100, 200, 840, "macos-run.mp4", mac_size, cut, S.end - cut + 0.5, media_start=27.0,
                         label="macOS 앱 · 동영상 재생"))
    if HAVE_WINDOWS:
        import subprocess
        wh = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
                             "-of", "csv=p=0", str(WINDOWS_CLIP)], capture_output=True, text=True).stdout.strip()
        win_size = tuple(int(v) for v in wh.split(",")[:2])
        # 녹화본에서 라이브 시청(약 16초부터)과 동영상 재생(약 42초부터)을 이어 붙인다
        wcut = S[6] + (S.end - S[6]) / 2
        win = (video_frame(S[6], 980, 200, 840, "windows-run.mp4", win_size, S[6], wcut - S[6], media_start=16.0,
                           label="Windows 앱 · 라이브 시청", out=wcut + 0.3)
               + video_frame(wcut, 980, 200, 840, "windows-run.mp4", win_size, wcut, S.end - wcut + 0.5,
                             media_start=42.0, label="Windows 앱 · 동영상 재생"))
    else:
        win = rv(S[6], "slot", "<b>Windows 실행 화면</b><small>설치 파일(.msi) 빌드 완료<br>실행 화면은 확인한 뒤 추가 예정</small>",
                 style="left:980px;top:240px;width:840px;height:560px")
    return f"""
      {header("데스크톱 앱", 6, "Windows와 macOS 앱", S.st)}
      {col}{actions}{tag_note}{download}{dl_note}
      {mac}{win}
    """


def s7():
    S = Cues(7)
    steps = [("도메인", "Cloudflare · DNS A 레코드 2개"), ("OCI 무료 서버", "보안 목록 + iptables"),
             ("Caddy", "자동 HTTPS · 경로 나누기"), ("docker compose", "내부 포트는 127.0.0.1")]
    flow = ""
    for i, (t, d) in enumerate(steps):
        if i:
            flow += rv(S[0] + i * 1.6 - 0.2, "step-arrow", "→")
        flow += rv(S[0] + i * 1.6, "step", f"<b>{esc(t)}</b><small>{esc(d)}</small>")
    win_text = ("Windows 앱에서 라이브 · 동영상 재생" if HAVE_WINDOWS else "Windows 앱 실행 화면 (확인 예정)")
    checks = (card(S[1], "실제로 확인한 것", "<ul><li>배포한 사이트에서 라이브 방송 · 동영상</li>"
                   "<li>macOS 앱에서 라이브 · 동영상 재생</li>"
                   + ("<li>" + win_text + "</li>" if HAVE_WINDOWS else "") + "</ul>", cls="accent-card")
              + card(S[1] + 1.0, "다음", "<ul>" + ("" if HAVE_WINDOWS else f"<li>{win_text}</li>")
                     + "<li>다음 영상: K3s와 오토스케일로 옮긴 운영 서버</li></ul>"))
    return f"""
      {header("정리", 7, "정리", S.st)}
      <div class="sumflow">{flow}</div>
      {rv(S[1], "step", "<b>Windows · macOS 앱</b><small>GitHub Actions → .msi · .dmg</small>", style="position:absolute;left:100px;top:410px;width:1720px;flex-direction:row;gap:20px;align-items:baseline")}
      <div class="checks">{checks}</div>
      {rv(S[2], "outro", '<div class="eyebrow">치지직 만들기 ⑥</div><h1 class="logo" style="font-size:80px;text-align:center">도메인·OCI 배포와<br>데스크톱 앱</h1><p>감사합니다</p>')}
    """


# ---------- 조립 ----------

def subtitles():
    return "".join(
        f'<div id="sub{i}" class="clip sub" data-start="{s["start"]:.3f}" data-duration="{max(0.2, s["end"] - s["start"]):.3f}">'
        f'<span>{esc(s["text"])}</span></div>'
        for i, s in enumerate(SUBS))


CSS = SHARED_CSS.read_text() + (ROOT / "tools" / "oci.css").read_text()
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
  if (OFFSET > 0) tl.fromTo(root.querySelector('.scene'), { opacity: 0 }, { opacity: 1, duration: 0.45, ease: 'power1.out' }, 0);
  tl.to(root.querySelector('.scene'), { opacity: 0, duration: 0.35, ease: 'power1.in' }, D - 0.35);
  root.querySelectorAll('.rv').forEach((el) => {
    const at = Math.max(0, +el.dataset.at - OFFSET);
    tl.fromTo(el, { opacity: 0, y: 22 }, { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out' }, at);
    if (el.dataset.out) tl.to(el, { opacity: 0, duration: 0.3 }, Math.max(0, +el.dataset.out - OFFSET) - 0.3);
  });
  window.__timelines = window.__timelines || {};
  window.__timelines[id] = tl;
})();
</script>"""

SCENE_FUNCS = [s0, s1, s2, s3, s4, s5, s6, s7]
COMP = VIDEO / "compositions"
COMP.mkdir(exist_ok=True)
mounts = []
for f in SCENE_FUNCS:
    sid = int(f.__name__[1:])
    sc = SCENES[sid]
    OFFSET[0] = sc["start"]
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
print(f"wrote index.html + {len(SCENE_FUNCS)} scenes", f"{total:.1f}s", len(SUBS), "subtitles",
      "windows clip" if HAVE_WINDOWS else "windows slot")
