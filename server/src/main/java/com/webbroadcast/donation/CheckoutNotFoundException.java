package com.webbroadcast.donation;

/** 결제 토큰이 없거나 틀렸거나 결제 화면을 열 수 있는 시간이 지났다. 어느 경우인지는 구분하지 않는다. */
public class CheckoutNotFoundException extends RuntimeException {
}
