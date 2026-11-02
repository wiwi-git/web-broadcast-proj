package com.webbroadcast.media;

import java.util.ArrayList;
import java.util.List;

/**
 * Original을 특정 해상도로 변환한 결과물 하나. height는 짧은 변 기준(1080p 등).
 */
public record Rendition(int height, int videoBitrateKbps) {

	private static final List<Rendition> LADDER = List.of(
			new Rendition(1080, 5000),
			new Rendition(720, 2800),
			new Rendition(480, 1400));

	public String name() {
		return height + "p";
	}

	/**
	 * Original 해상도를 넘지 않는 Rendition 목록을 높은 화질부터 돌려준다.
	 * 480p보다 작은 Original은 원래 해상도 그대로 하나만 만든다.
	 */
	public static List<Rendition> ladderFor(MediaInfo source) {
		int shortSide = source.shortSide();
		List<Rendition> renditions = new ArrayList<>();
		for (Rendition rendition : LADDER) {
			if (rendition.height <= shortSide) {
				renditions.add(rendition);
			}
		}
		if (renditions.isEmpty()) {
			int evenHeight = shortSide - (shortSide % 2);
			renditions.add(new Rendition(evenHeight, 800));
		}
		return renditions;
	}
}
