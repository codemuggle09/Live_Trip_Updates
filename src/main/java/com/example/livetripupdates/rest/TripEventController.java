package com.example.livetripupdates.rest;

import com.example.livetripupdates.event.AcceptedTripEvent;
import com.example.livetripupdates.event.PublishSosRequest;
import com.example.livetripupdates.event.PublishTripEventRequest;
import com.example.livetripupdates.event.TripEventEnvelope;
import com.example.livetripupdates.event.TripEventHistoryPage;
import com.example.livetripupdates.event.TripEventIngestionService;
import com.example.livetripupdates.event.TripEventReplayService;
import com.example.livetripupdates.security.CurrentPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
public class TripEventController {

    private final TripEventIngestionService ingestionService;
    private final TripEventReplayService replayService;

    public TripEventController(TripEventIngestionService ingestionService, TripEventReplayService replayService) {
        this.ingestionService = ingestionService;
        this.replayService = replayService;
    }

    @GetMapping("/events")
    public TripEventHistoryPage readTripEvents(
            Authentication authentication,
            @PathVariable String tripId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return replayService.replayTrip(principalFrom(authentication), tripId, afterSequence, limit);
    }

    @PostMapping("/events")
    public ResponseEntity<TripEventEnvelope> publishTripEvent(
            Authentication authentication,
            @PathVariable String tripId,
            @Valid @RequestBody PublishTripEventRequest request
    ) {
        AcceptedTripEvent accepted = ingestionService.publishTripEvent(
                principalFrom(authentication),
                tripId,
                request
        );
        return ResponseEntity.status(accepted.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(accepted.envelope());
    }

    @PostMapping("/sos")
    public ResponseEntity<TripEventEnvelope> publishSos(
            Authentication authentication,
            @PathVariable String tripId,
            @Valid @RequestBody PublishSosRequest request
    ) {
        AcceptedTripEvent accepted = ingestionService.publishSos(principalFrom(authentication), tripId, request);
        return ResponseEntity.status(accepted.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(accepted.envelope());
    }

    private CurrentPrincipal principalFrom(Authentication authentication) {
        CurrentPrincipal principal = CurrentPrincipal.from(authentication);
        if (!principal.isComplete()) {
            throw new IllegalArgumentException("Required principal claims are missing.");
        }
        return principal;
    }
}
