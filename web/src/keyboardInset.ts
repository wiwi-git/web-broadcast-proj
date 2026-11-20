/**
 * 휴대폰 화면 키보드에 입력칸 아래 버튼이 가리지 않게 한다.
 *
 * iOS Safari는 키보드가 올라와도 페이지 높이가 그대로라, 화면이 짧으면 키보드에 가린 버튼을 올려 볼 수 없다.
 * iOS 26은 키보드 위에 이동·완료 막대와 주소창까지 띄우고, 그 높이는 visualViewport로 정확히 잴 수 없었다
 * (iOS 26.3 시뮬레이터). 그래서 키보드 높이를 재지 않는다. 터치 기기에서 입력칸에 초점이 있으면 index.css가
 * 본문 아래에 빈 자리를 두고, 여기서는 입력칸을 화면 위쪽으로 올린다. 그러면 입력칸 바로 아래 내용이 키보드 위에 보인다.
 */

const KEYBOARD_WAIT_MS = 350
/** 화면 맨 위(상태 표시줄) 아래로 띄울 거리 */
const TOP_GAP_PX = 72

/** 입력칸에 초점이 가면 키보드가 올라온 뒤 입력칸을 화면 위쪽으로 올린다 */
export function liftAboveKeyboard(input: HTMLElement) {
  if (!window.matchMedia('(pointer: coarse)').matches) return
  setTimeout(() => {
    const top = input.getBoundingClientRect().top + window.scrollY - TOP_GAP_PX
    window.scrollTo(0, Math.max(0, top))
  }, KEYBOARD_WAIT_MS)
}
