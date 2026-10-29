package com.example.livetripupdates.security;

import java.util.Optional;
import java.util.regex.Pattern;

public record Topic(TopicType type, String id) {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    public static Optional<Topic> parse(String rawTopic) {
        if (rawTopic == null || rawTopic.isBlank()) {
            return Optional.empty();
        }
        String[] parts = rawTopic.split(":", 2);
        if (parts.length != 2 || !SAFE_ID.matcher(parts[1]).matches()) {
            return Optional.empty();
        }
        return switch (parts[0]) {
            case "trip" -> Optional.of(new Topic(TopicType.TRIP, parts[1]));
            case "cab" -> Optional.of(new Topic(TopicType.CAB, parts[1]));
            case "office" -> Optional.of(new Topic(TopicType.OFFICE, parts[1]));
            default -> Optional.empty();
        };
    }
}
