package com.webbroadcast.media;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("media")
public record MediaProperties(Path storageRoot, String ffmpegPath, String ffprobePath) {
}
