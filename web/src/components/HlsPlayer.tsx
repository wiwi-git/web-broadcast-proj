import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'
import { needsWebRtc, watch } from '../live/whep.ts'

interface Level {
  index: number
  height: number
}

/** 화질 선택 값. AUTO면 네트워크 상황에 따라 hls.js가 고른다. */
const AUTO = -1
const RETRY_DELAY_MS = 2000

/** 바로 재생한다. 브라우저가 소리 있는 자동 재생을 막으면 소리를 끄고 재생한다 */
function startPlaying(video: HTMLVideoElement) {
  video.play().catch(() => {
    video.muted = true
    video.play().catch(() => {})
  })
}

interface Props {
  src: string
  poster?: string
  /** 라이브는 화면에 들어오자마자 재생한다 */
  autoPlay?: boolean
  /** 라이브의 WHEP 주소. 송출 소리(Opus)를 HLS로 재생하지 못하는 기기(iOS)는 이걸로 본다 (ADR-0006) */
  webrtcUrl?: string
}

/**
 * HLS 재생기. 대부분의 브라우저는 hls.js로 재생하고,
 * hls.js를 못 쓰는 Safari(iOS)는 자체 HLS 지원으로 재생한다 (이때는 화질 선택 없음).
 * 라이브는 iOS에서 WebRTC로 본다.
 */
export default function HlsPlayer({ src, poster, autoPlay = false, webrtcUrl }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const hlsRef = useRef<Hls | null>(null)
  const [levels, setLevels] = useState<Level[]>([])
  const [selected, setSelected] = useState(AUTO)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return

    if (webrtcUrl && needsWebRtc()) {
      return watch(webrtcUrl, video, () => startPlaying(video))
    }

    if (!Hls.isSupported()) {
      video.src = src
      if (autoPlay) startPlaying(video)
      return
    }

    const hls = new Hls()
    hlsRef.current = hls
    hls.on(Hls.Events.MANIFEST_PARSED, (_, data) => {
      setLevels(
        data.levels
          .map((level, index) => ({ index, height: Math.min(level.width, level.height) }))
          .sort((a, b) => b.height - a.height),
      )
      if (autoPlay) startPlaying(video)
    })
    // 라이브 플레이리스트는 시작 직후나 네트워크가 흔들릴 때 잠깐 실패할 수 있으므로 다시 시도한다
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    hls.on(Hls.Events.ERROR, (_, data) => {
      if (!data.fatal) return
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        retryTimer = setTimeout(() => hls.startLoad(), RETRY_DELAY_MS)
      } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
        hls.recoverMediaError()
      }
    })
    hls.loadSource(src)
    hls.attachMedia(video)

    return () => {
      clearTimeout(retryTimer)
      hls.destroy()
      hlsRef.current = null
      setLevels([])
      setSelected(AUTO)
    }
  }, [src, autoPlay, webrtcUrl])

  function changeLevel(index: number) {
    setSelected(index)
    if (hlsRef.current) hlsRef.current.currentLevel = index
  }

  return (
    <div className="player">
      <video ref={videoRef} controls playsInline poster={poster} />
      {levels.length > 1 && (
        <label className="quality">
          화질
          <select value={selected} onChange={(e) => changeLevel(Number(e.target.value))}>
            <option value={AUTO}>자동</option>
            {levels.map((level) => (
              <option key={level.index} value={level.index}>
                {level.height}p
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  )
}
