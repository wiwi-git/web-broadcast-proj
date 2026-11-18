/**
 * 데스크톱·모바일 앱(../app)을 다시 여는 딥링크 (app/Dioxus.toml의 [deep_links]).
 * 앱은 결제창을 앱 안에 띄울 수 없어서 결제 토큰으로 이 웹의 /checkout 화면을 기본 브라우저로 연다 (ADR-0005).
 * 결제가 끝나면 후원한 방송 화면으로 앱을 다시 연다. 방송을 모르면(삭제됨, 결제 정보 없음) 후원 내역을 연다.
 */
export const APP_DONATIONS_LINK = 'webbroadcast://donations'

export function appLink(broadcastId: string | null | undefined): string {
  return broadcastId ? `webbroadcast://live/${encodeURIComponent(broadcastId)}` : APP_DONATIONS_LINK
}
