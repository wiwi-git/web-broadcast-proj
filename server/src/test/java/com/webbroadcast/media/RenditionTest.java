package com.webbroadcast.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RenditionTest {

	@Test
	void fullHdSourceGetsAllThreeRenditions() {
		assertThat(Rendition.ladderFor(new MediaInfo(1920, 1080, 10.0, true)))
			.extracting(Rendition::name)
			.containsExactly("1080p", "720p", "480p");
	}

	@Test
	void neverUpscalesBeyondOriginal() {
		assertThat(Rendition.ladderFor(new MediaInfo(1280, 720, 10.0, true)))
			.extracting(Rendition::name)
			.containsExactly("720p", "480p");
	}

	@Test
	void fourKSourceTopsOutAt1080p() {
		assertThat(Rendition.ladderFor(new MediaInfo(3840, 2160, 10.0, true)))
			.extracting(Rendition::name)
			.containsExactly("1080p", "720p", "480p");
	}

	@Test
	void portraitSourceUsesShortSide() {
		assertThat(Rendition.ladderFor(new MediaInfo(1080, 1920, 10.0, true)))
			.extracting(Rendition::name)
			.containsExactly("1080p", "720p", "480p");
	}

	@Test
	void sourceSmallerThan480pKeepsItsOwnEvenHeight() {
		assertThat(Rendition.ladderFor(new MediaInfo(427, 241, 10.0, false)))
			.extracting(Rendition::height)
			.containsExactly(240);
	}
}
