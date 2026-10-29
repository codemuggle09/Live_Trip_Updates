package com.example.livetripupdates.event;

import com.example.livetripupdates.observability.TripMetrics;
import com.example.livetripupdates.persistence.TripRepository;
import com.example.livetripupdates.security.CurrentPrincipal;
import com.example.livetripupdates.security.Topic;
import com.example.livetripupdates.security.TopicType;
import com.example.livetripupdates.security.TripAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TripEventReplayService {

    private final TripRepository tripRepository;
    private final TripAuthorizationService authorizationService;
    private final TripEventStore eventStore;
    private final int maxBatchSize;
    private final TripMetrics metrics;

    public TripEventReplayService(
            TripRepository tripRepository,
            TripAuthorizationService authorizationService,
            TripEventStore eventStore,
            @Value("${app.events.replay-max-batch-size:500}") int maxBatchSize,
            TripMetrics metrics
    ) {
        this.tripRepository = tripRepository;
        this.authorizationService = authorizationService;
        this.eventStore = eventStore;
        this.maxBatchSize = maxBatchSize;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public TripEventHistoryPage replayTrip(
            CurrentPrincipal principal,
            String tripId,
            long afterSequence,
            int requestedLimit
    ) {
        if (afterSequence < 0) {
            throw new TripEventValidationException("afterSequence must be non-negative.");
        }
        tripRepository.findByIdAndTenantId(tripId, principal.tenantId())
                .orElseThrow(() -> new TripEventNotFoundException("Trip was not found."));
        if (!authorizationService.canReplayTrip(principal, tripId)) {
            throw new TripEventForbiddenException("Replay is not permitted.");
        }
        metrics.replayRequested();
        int limit = boundedLimit(requestedLimit);
        List<TripEventEnvelope> events = eventStore.readAfter(tripId, afterSequence, limit);
        metrics.eventsReplayed(events.size());
        long nextAfterSequence = events.isEmpty()
                ? afterSequence
                : events.getLast().sequenceNumber();
        return new TripEventHistoryPage(events, nextAfterSequence, eventStore.currentSequence(tripId));
    }

    public TripEventHistoryPage replayTopic(
            CurrentPrincipal principal,
            String rawTopic,
            long afterSequence,
            int requestedLimit
    ) {
        Topic topic = Topic.parse(rawTopic)
                .orElseThrow(() -> new TripEventValidationException("Topic is invalid."));
        if (topic.type() != TopicType.TRIP) {
            throw new TripEventValidationException("Replay currently supports trip topics.");
        }
        return replayTrip(principal, topic.id(), afterSequence, requestedLimit);
    }

    private int boundedLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return Math.min(100, maxBatchSize);
        }
        return Math.min(requestedLimit, maxBatchSize);
    }
}
