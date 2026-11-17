/**
 * 데스크톱·모바일 앱(../app)에서 넘어온 후원.
 * 앱은 결제창을 앱 안에 띄울 수 없어서 이 웹의 후원 화면을 기본 브라우저로 `?app=1`을 붙여 연다.
 * 결제가 끝나면 이 딥링크로 앱을 다시 연다 (app/Dioxus.toml의 [deep_links]).
 */
export const APP_DONATIONS_LINK = 'webbroadcast://donations'

/** 앱에서 넘어온 요청인지 (쿼리 `app=1`) */
export function isFromApp(params: URLSearchParams): boolean {
  return params.get('app') === '1'
}
