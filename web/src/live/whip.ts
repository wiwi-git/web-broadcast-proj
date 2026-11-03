/**
 * WHIP(WebRTC-HTTP Ingestion Protocol)로 MediaStream을 미디어 서버에 송출한다.
 * 같은 네트워크 안의 미디어 서버를 가정하므로 STUN/TURN 없이 연결한다.
 */

const MAX_VIDEO_BITRATE = 2_500_000
const ICE_GATHERING_TIMEOUT_MS = 3000

export class WhipError extends Error {}

export interface WhipSession {
  readonly connection: RTCPeerConnection
  close(): void
}

export async function publish(whipUrl: string, stream: MediaStream): Promise<WhipSession> {
  const connection = new RTCPeerConnection()
  try {
    for (const track of stream.getTracks()) {
      const transceiver = connection.addTransceiver(track, { direction: 'sendonly', streams: [stream] })
      if (track.kind === 'video') preferH264(transceiver)
    }

    await connection.setLocalDescription(await connection.createOffer())
    await iceGatheringComplete(connection)

    const response = await fetch(whipUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/sdp' },
      body: connection.localDescription!.sdp,
    })
    if (response.status === 401 || response.status === 403) {
      throw new WhipError('미디어 서버가 송출을 거부했습니다.')
    }
    if (!response.ok) throw new WhipError(`미디어 서버 연결에 실패했습니다. (${response.status})`)
    await connection.setRemoteDescription({ type: 'answer', sdp: await response.text() })

    await limitVideoBitrate(connection)
    return { connection, close: () => connection.close() }
  } catch (e) {
    connection.close()
    throw e
  }
}

/** HLS는 VP8/VP9를 담지 못하므로 H.264로 송출해야 한다 (ADR-0002). */
function preferH264(transceiver: RTCRtpTransceiver) {
  const codecs = RTCRtpSender.getCapabilities('video')?.codecs ?? []
  const h264 = codecs.filter((codec) => codec.mimeType.toLowerCase() === 'video/h264')
  if (h264.length === 0) throw new WhipError('이 브라우저는 H.264 영상 송출을 지원하지 않습니다.')
  transceiver.setCodecPreferences(h264)
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
    // 일부 네트워크에서는 complete가 늦게 오므로, 그때까지 모인 후보로 진행한다
    const timer = setTimeout(done, ICE_GATHERING_TIMEOUT_MS)
    connection.addEventListener('icegatheringstatechange', onChange)
  })
}

async function limitVideoBitrate(connection: RTCPeerConnection) {
  for (const sender of connection.getSenders()) {
    if (sender.track?.kind !== 'video') continue
    const parameters = sender.getParameters()
    if (!parameters.encodings?.length) continue
    parameters.encodings[0].maxBitrate = MAX_VIDEO_BITRATE
    await sender.setParameters(parameters).catch(() => {
      // 지원하지 않는 브라우저는 기본 비트레이트로 송출한다
    })
  }
}
