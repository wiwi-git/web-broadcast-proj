// 방송하기 화면의 카메라와 WHIP 송출. web/src/pages/BroadcastPage.tsx, web/src/live/whip.ts, useWakeLock.ts를 옮겼다.
// MediaStream은 WebView 안에만 있으므로 카메라와 송출은 여기서 하고, 방송 시작·종료 API와 화면 상태는 Rust가 맡는다.
// 같은 네트워크 안의 미디어 서버를 가정하므로 STUN/TURN 없이 연결한다.
(() => {
  if (window.__broadcaster) return

  const MAX_VIDEO_BITRATE = 2_500_000
  const ICE_GATHERING_TIMEOUT_MS = 3000

  let stream = null
  let connection = null
  let wakeLock = null
  let wakeWanted = false

  function cameraErrorMessage(error) {
    const name = error && error.name
    if (name === 'NotAllowedError') return '카메라와 마이크 권한을 허용해 주세요.'
    if (name === 'NotFoundError') return '사용할 수 있는 카메라나 마이크가 없습니다.'
    if (name === 'NotReadableError') return '다른 앱이 카메라를 사용 중입니다.'
    return '카메라를 열 수 없습니다.'
  }

  function stopTracks() {
    if (stream) stream.getTracks().forEach((track) => track.stop())
    stream = null
  }

  /** HLS는 VP8/VP9를 담지 못하므로 H.264로 송출해야 한다 (ADR-0002). */
  function preferH264(transceiver) {
    const codecs = (RTCRtpSender.getCapabilities('video') || {}).codecs || []
    const h264 = codecs.filter((codec) => codec.mimeType.toLowerCase() === 'video/h264')
    if (h264.length === 0) throw new Error('이 기기는 H.264 영상 송출을 지원하지 않습니다.')
    transceiver.setCodecPreferences(h264)
  }

  /** 후보 주소를 SDP에 모두 담아 한 번에 보낸다 (trickle ICE를 쓰지 않음). */
  function iceGatheringComplete(pc) {
    if (pc.iceGatheringState === 'complete') return Promise.resolve()
    return new Promise((resolve) => {
      const done = () => {
        clearTimeout(timer)
        pc.removeEventListener('icegatheringstatechange', onChange)
        resolve()
      }
      const onChange = () => pc.iceGatheringState === 'complete' && done()
      // 일부 네트워크에서는 complete가 늦게 오므로, 그때까지 모인 후보로 진행한다
      const timer = setTimeout(done, ICE_GATHERING_TIMEOUT_MS)
      pc.addEventListener('icegatheringstatechange', onChange)
    })
  }

  async function limitVideoBitrate(pc) {
    for (const sender of pc.getSenders()) {
      if (!sender.track || sender.track.kind !== 'video') continue
      const parameters = sender.getParameters()
      if (!parameters.encodings || !parameters.encodings.length) continue
      parameters.encodings[0].maxBitrate = MAX_VIDEO_BITRATE
      await sender.setParameters(parameters).catch(() => {})
    }
  }

  async function acquireWakeLock() {
    if (!wakeWanted || !('wakeLock' in navigator)) return
    try {
      wakeLock = await navigator.wakeLock.request('screen')
      if (!wakeWanted) wakeLock.release()
    } catch {
      // 배터리 절약 모드 등으로 거부될 수 있다. 송출은 계속한다
    }
  }
  document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && acquireWakeLock())

  window.__broadcaster = {
    /** 카메라를 열어 videoId의 <video>에 보여 준다. 이미 열려 있으면 닫고 다시 연다. 실패하면 문구를 돌려준다 */
    async openCamera(videoId, facing) {
      stopTracks()
      if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        return { error: '이 화면에서는 카메라를 쓸 수 없습니다. (보안 연결 필요)' }
      }
      try {
        const opened = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } },
          audio: { echoCancellation: true, noiseSuppression: true },
        })
        stopTracks()
        stream = opened
        const video = document.getElementById(videoId)
        if (video) video.srcObject = opened
        return { ok: true }
      } catch (e) {
        return { error: cameraErrorMessage(e) }
      }
    },

    closeCamera() {
      stopTracks()
    },

    hasCamera() {
      return !!stream
    },

    /** WHIP으로 송출한다. 실패하면 문구를 돌려준다 */
    async publish(whipUrl) {
      if (!stream) return { error: '카메라가 열려 있지 않습니다.' }
      const pc = new RTCPeerConnection()
      try {
        for (const track of stream.getTracks()) {
          const transceiver = pc.addTransceiver(track, { direction: 'sendonly', streams: [stream] })
          if (track.kind === 'video') preferH264(transceiver)
        }
        await pc.setLocalDescription(await pc.createOffer())
        await iceGatheringComplete(pc)

        const response = await fetch(whipUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/sdp' },
          body: pc.localDescription.sdp,
        })
        if (response.status === 401 || response.status === 403) throw new Error('미디어 서버가 송출을 거부했습니다.')
        if (!response.ok) throw new Error(`미디어 서버 연결에 실패했습니다. (${response.status})`)
        await pc.setRemoteDescription({ type: 'answer', sdp: await response.text() })
        await limitVideoBitrate(pc)
        connection = pc
        return { ok: true }
      } catch (e) {
        pc.close()
        return { error: e.message || '미디어 서버 연결에 실패했습니다.' }
      }
    },

    /** 송출 연결이 끊겼는지 */
    failed() {
      return !!connection && connection.connectionState === 'failed'
    },

    stopPublish() {
      if (connection) connection.close()
      connection = null
    },

    /** 송출하는 동안 화면이 꺼지지 않게 한다. 휴대폰 화면이 꺼지면 송출이 끊기기 때문이다 */
    keepAwake(active) {
      wakeWanted = active
      if (active) acquireWakeLock()
      else if (wakeLock) {
        wakeLock.release()
        wakeLock = null
      }
    },
  }
})()
