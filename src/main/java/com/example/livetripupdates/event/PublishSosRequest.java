package com.example.livetripupdates.event;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record PublishSosRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}", message = "eventId contains unsupported characters")
        String eventId,

        @NotNull
        Instant occurredAt,

        @NotNull
        JsonNode payload
) {
}
