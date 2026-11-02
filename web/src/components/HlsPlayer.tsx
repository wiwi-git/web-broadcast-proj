import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'

interface Level {
  index: number
  height: number
}

/** 화질 선택 값. AUTO면 네트워크 상황에 따라 hls.js가 고른다. */
const AUTO = -1

/**
 * HLS 재생기. 대부분의 브라우저는 hls.js로 재생하고,
 * hls.js를 못 쓰는 Safari(iOS)는 자체 HLS 지원으로 재생한다 (이때는 화질 선택 없음).
 */
export default function HlsPlayer({ src, poster }: { src: string; poster?: string }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const hlsRef = useRef<Hls | null>(null)
  const [levels, setLevels] = useState<Level[]>([])
  const [selected, setSelected] = useState(AUTO)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return

    if (!Hls.isSupported()) {
      video.src = src
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
    })
    hls.loadSource(src)
    hls.attachMedia(video)

    return () => {
      hls.destroy()
      hlsRef.current = null
      setLevels([])
      setSelected(AUTO)
    }
  }, [src])

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
