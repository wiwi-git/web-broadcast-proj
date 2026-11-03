package com.webbroadcast.broadcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BroadcastTest {

	private static final Instant T0 = Instant.parse("2026-11-03T10:00:00Z");

	private final String key = StreamKey.generate();
	private final Broadcast broadcast = Broadcast.pending(UUID.randomUUID(), "첫 방송", StreamKey.hash(key), T0);

	@Test
	void pendingGoesLiveWhenMediaArrives() {
		assertThat(broadcast.syncWithMedia(true, T0.plusSeconds(3))).isTrue();

		assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.LIVE);
		assertThat(broadcast.getStartedAt()).isEqualTo(T0.plusSeconds(3));
	}

	@Test
	void pendingWaitsUntilTimeout() {
		assertThat(broadcast.syncWithMedia(false, T0.plusSeconds(30))).isFalse();
		assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.PENDING);
	}

	@Test
	void pendingEndsWhenMediaNeverArrives() {
		assertThat(broadcast.syncWithMedia(false, T0.plusSeconds(31))).isTrue();

		assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.ENDED);
		assertThat(broadcast.getStartedAt()).isNull();
		assertThat(broadcast.getEndedAt()).isEqualTo(T0.plusSeconds(31));
	}

	@Test
	void liveEndsAsSoonAsMediaStops() {
		broadcast.syncWithMedia(true, T0.plusSeconds(2));

		assertThat(broadcast.syncWithMedia(false, T0.plusSeconds(60))).isTrue();
		assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.ENDED);
		assertThat(broadcast.getEndedAt()).isEqualTo(T0.plusSeconds(60));
	}

	@Test
	void endedNeverComesBack() {
		broadcast.end(T0.plusSeconds(1));

		assertThat(broadcast.syncWithMedia(true, T0.plusSeconds(2))).isFalse();
		assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.ENDED);
	}

	@Test
	void endingTwiceKeepsFirstEndTime() {
		broadcast.end(T0.plusSeconds(1));
		broadcast.end(T0.plusSeconds(9));

		assertThat(broadcast.getEndedAt()).isEqualTo(T0.plusSeconds(1));
	}

	@Test
	void onlyTheIssuedStreamKeyIsAccepted() {
		assertThat(broadcast.acceptsStreamKey(key)).isTrue();
		assertThat(broadcast.acceptsStreamKey(StreamKey.generate())).isFalse();
		assertThat(broadcast.acceptsStreamKey(null)).isFalse();
		assertThat(broadcast.acceptsStreamKey("")).isFalse();
	}

	@Test
	void mediaPathRoundTripsAndRejectsOtherPaths() {
		UUID id = UUID.randomUUID();

		assertThat(MediaPath.parse(MediaPath.of(id))).contains(id);
		assertThat(MediaPath.parse("live/not-a-uuid")).isEmpty();
		assertThat(MediaPath.parse("other/" + id)).isEmpty();
		assertThat(MediaPath.parse(null)).isEmpty();
	}
}
