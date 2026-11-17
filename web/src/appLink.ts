/**
 * 데스크톱·모바일 앱(../app)을 다시 여는 딥링크 (app/Dioxus.toml의 [deep_links]).
 * 앱은 결제창을 앱 안에 띄울 수 없어서 결제 토큰으로 이 웹의 /checkout 화면을 기본 브라우저로 연다 (ADR-0005).
 * 결제가 끝나면 이 주소로 앱을 다시 연다.
 */
export const APP_DONATIONS_LINK = 'webbroadcast://donations'
