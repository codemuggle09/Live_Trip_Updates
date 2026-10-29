package com.example.livetripupdates.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RedisTripEventStore implements TripEventStore {

    private static final int SCHEMA_VERSION = 1;
    private static final String SCRIPT = """
            local existingHash = redis.call('HGET', KEYS[1], 'requestHash')
            if existingHash then
              if existingHash ~= ARGV[1] then
                return {'CONFLICT'}
              end
              return {
                'DUPLICATE',
                redis.call('HGET', KEYS[1], 'sequenceNumber'),
                redis.call('HGET', KEYS[1], 'acceptedAt'),
                redis.call('HGET', KEYS[1], 'streamId')
              }
            end

            local sequenceNumber = redis.call('INCR', KEYS[2])
            local streamId = redis.call(
              'XADD',
              KEYS[3],
              'MAXLEN',
              '~',
              ARGV[11],
              '*',
              'schemaVersion',
              ARGV[12],
              'eventId',
              ARGV[2],
              'eventType',
              ARGV[3],
              'tenantId',
              ARGV[4],
              'tripId',
              ARGV[5],
              'cabId',
              ARGV[6],
              'sequenceNumber',
              tostring(sequenceNumber),
              'occurredAt',
              ARGV[7],
              'acceptedAt',
              ARGV[8],
              'priority',
              ARGV[9],
              'payload',
              ARGV[10]
            )
            redis.call(
              'HSET',
              KEYS[1],
              'requestHash',
              ARGV[1],
              'sequenceNumber',
              tostring(sequenceNumber),
              'acceptedAt',
              ARGV[8],
              'streamId',
              streamId
            )
            redis.call('EXPIRE', KEYS[1], ARGV[13])
            return {'CREATED', tostring(sequenceNumber), ARGV[8], streamId}
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<List> appendScript = RedisScript.of(SCRIPT, List.class);
    private final int streamMaxLength;
    private final int idempotencyTtlSeconds;

    public RedisTripEventStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.events.stream-max-length:10000}") int streamMaxLength,
            @Value("${app.events.idempotency-ttl-seconds:86400}") int idempotencyTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.streamMaxLength = streamMaxLength;
        this.idempotencyTtlSeconds = idempotencyTtlSeconds;
    }

    @Override
    public AcceptedTripEvent append(EventAppendCommand command) {
        String payloadJson = writePayload(command.payload());
        List<String> keys = List.of(
                "trip:%s:event:%s".formatted(command.tripId(), command.eventId()),
                "trip:%s:sequence".formatted(command.tripId()),
                "trip:%s:events".formatted(command.tripId())
        );
        List<?> result = redisTemplate.execute(
                appendScript,
                keys,
                command.requestHash(),
                command.eventId(),
                command.eventType().name(),
                command.tenantId(),
                command.tripId(),
                command.cabId(),
                command.occurredAt().toString(),
                command.acceptedAt().toString(),
                command.priority().name(),
                payloadJson,
                String.valueOf(streamMaxLength),
                String.valueOf(SCHEMA_VERSION),
                String.valueOf(idempotencyTtlSeconds)
        );
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Redis did not return an append result.");
        }
        String status = String.valueOf(result.getFirst());
        if ("CONFLICT".equals(status)) {
            throw new TripEventConflictException("eventId already exists with different content.");
        }
        if (result.size() < 3) {
            throw new IllegalStateException("Redis returned an incomplete append result.");
        }
        long sequenceNumber = Long.parseLong(String.valueOf(result.get(1)));
        Instant acceptedAt = Instant.parse(String.valueOf(result.get(2)));
        TripEventEnvelope envelope = new TripEventEnvelope(
                SCHEMA_VERSION,
                command.eventId(),
                command.tenantId(),
                command.tripId(),
                command.cabId(),
                sequenceNumber,
                command.eventType(),
                command.occurredAt(),
                acceptedAt,
                command.priority(),
                command.payload()
        );
        return new AcceptedTripEvent(envelope, "DUPLICATE".equals(status));
    }

    @Override
    public List<TripEventEnvelope> readAfter(String tripId, long afterSequence, int limit) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range("trip:%s:events".formatted(tripId), Range.unbounded());
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(this::toEnvelope)
                .filter(Objects::nonNull)
                .filter(envelope -> envelope.sequenceNumber() > afterSequence)
                .sorted(Comparator.comparingLong(TripEventEnvelope::sequenceNumber))
                .limit(limit)
                .toList();
    }

    @Override
    public long currentSequence(String tripId) {
        String value = redisTemplate.opsForValue().get("trip:%s:sequence".formatted(tripId));
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Long.parseLong(value);
    }

    private String writePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new TripEventValidationException("Payload could not be serialized.");
        }
    }

    private TripEventEnvelope toEnvelope(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        try {
            return new TripEventEnvelope(
                    Integer.parseInt(field(value, "schemaVersion")),
                    field(value, "eventId"),
                    field(value, "tenantId"),
                    field(value, "tripId"),
                    field(value, "cabId"),
                    Long.parseLong(field(value, "sequenceNumber")),
                    TripEventType.valueOf(field(value, "eventType")),
                    Instant.parse(field(value, "occurredAt")),
                    Instant.parse(field(value, "acceptedAt")),
                    EventPriority.valueOf(field(value, "priority")),
                    objectMapper.readTree(field(value, "payload"))
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            return null;
        }
    }

    private String field(Map<Object, Object> value, String fieldName) {
        Object fieldValue = value.get(fieldName);
        if (fieldValue == null) {
            throw new IllegalArgumentException("Missing stream field: " + fieldName);
        }
        return String.valueOf(fieldValue);
    }
}
