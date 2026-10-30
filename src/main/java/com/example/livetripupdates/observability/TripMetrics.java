package com.example.livetripupdates.observability;

import com.example.livetripupdates.event.EventPriority;
import com.example.livetripupdates.event.TripEventType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TripMetrics {

    private final MeterRegistry meterRegistry;

    public TripMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void eventAccepted(TripEventType eventType, EventPriority priority, boolean duplicate) {
        meterRegistry.counter(
                "trip_events_accepted_total",
                "eventType", eventType.name(),
                "priority", priority.name(),
                "duplicate", String.valueOf(duplicate)
        ).increment();
        if (duplicate) {
            meterRegistry.counter("trip_duplicate_events_total", "eventType", eventType.name()).increment();
        }
    }

    public void eventRejected(String reason) {
        meterRegistry.counter("trip_events_rejected_total", "reason", reason).increment();
    }

    public void replayRequested() {
        meterRegistry.counter("trip_event_replay_requests_total").increment();
    }

    public void eventsReplayed(int count) {
        meterRegistry.counter("trip_event_replayed_total").increment(count);
    }

    public void eventsDelivered(EventPriority priority, int count) {
        meterRegistry.counter("trip_events_delivered_total", "priority", priority.name()).increment(count);
    }
}
