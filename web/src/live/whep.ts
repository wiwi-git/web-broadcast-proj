/**
 * WHEP(WebRTC-HTTP Egress Protocol)로 미디어 서버에서 라이브를 받아 본다 (ADR-0006).
 * 송출 소리(Opus)를 HLS로 재생하지 못하는 기기(iOS)만 쓴다. 나머지는 LL-HLS를 hls.js로 본다.
 * 송출(whip.ts)처럼 같은 네트워크 안의 미디어 서버를 가정해 STUN/TURN 없이 연결한다.
 */

const ICE_GATHERING_TIMEOUT_MS = 3000
const RETRY_DELAY_MS = 2000

/**
 * 이 기기가 LL-HLS를 소리까지 재생할 수 있는지. 미디어 서버의 HLS는 송출 소리를 Opus 그대로 담는데,
 * iOS는 HLS(자체 재생)와 Media Source(hls.js) 어느 쪽에서도 Opus를 풀지 못한다.
 */
export function needsWebRtc(): boolean {
  if (typeof RTCPeerConnection === 'undefined') return false
  const source =
    (window as unknown as { ManagedMediaSource?: typeof MediaSource }).ManagedMediaSource ?? window.MediaSource
  return !source?.isTypeSupported('audio/mp4; codecs="opus"')
}

/** 연결이 끊기면 다시 붙는다. 돌려준 함수로 멈춘다 */
export function watch(whepUrl: string, video: HTMLVideoElement, onPlaying: () => void): () => void {
  let stopped = false
  let connection: RTCPeerConnection | null = null
  let retryTimer: ReturnType<typeof setTimeout> | undefined

  const retry = () => {
    connection?.close()
    connection = null
    if (!stopped) retryTimer = setTimeout(connect, RETRY_DELAY_MS)
  }

  async function connect() {
    const pc = new RTCPeerConnection()
    connection = pc
    const stream = new MediaStream()
    pc.addTransceiver('video', { direction: 'recvonly' })
    pc.addTransceiver('audio', { direction: 'recvonly' })
    pc.ontrack = (event) => {
      stream.addTrack(event.track)
      if (video.srcObject !== stream) {
        video.srcObject = stream
        onPlaying()
      }
    }
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') retry()
    }
    try {
      await pc.setLocalDescription(await pc.createOffer())
      await iceGatheringComplete(pc)
      const response = await fetch(whepUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/sdp' },
        body: pc.localDescription!.sdp,
      })
      // 방송 직후에는 미디어 서버에 스트림이 아직 없을 수 있다
      if (!response.ok) throw new Error(`WHEP ${response.status}`)
      if (stopped) return pc.close()
      await pc.setRemoteDescription({ type: 'answer', sdp: await response.text() })
    } catch {
      if (connection === pc) retry()
    }
  }

  connect()
  return () => {
    stopped = true
    clearTimeout(retryTimer)
    connection?.close()
    video.srcObject = null
  }
}

/** 후보 주소를 SDP에 모두 담아 한 번에 보낸다 (trickle ICE를 쓰지 않음). */
function iceGatheringComplete(connection: RTCPeerConnection): Promise<void> {
  if (connection.iceGatheringState === 'complete') return Promise.resolve()
  return new Promise((resolve) => {
    const done = () => {
      clearTimeout(timer)
      connection.removeEventListener('icegatheringstatechange', onChange)
      resolve()
    }
    const onChange = () => connection.iceGatheringState === 'complete' && done()
    const timer = setTimeout(done, ICE_GATHERING_TIMEOUT_MS)
    connection.addEventListener('icegatheringstatechange', onChange)
  })
}
