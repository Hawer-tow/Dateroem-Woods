package com.dateroem.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.TripSummaryDto;
import com.dateroem.backend.service.TripService;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // "desktop one should see all the trips ... not unless i select the
    // 50 only etc" vs mobile's own "send only a maximum of data related to
    // 3-6 trips" load-shedding - limit is optional (null = every trip);
    // mobileServer's own adaptive batching always passes an explicit
    // value, desktop only does when the admin picks something other than
    // "All".
    @GetMapping
    public java.util.List<Map<String, Object>> list(@RequestParam(required = false) Integer limit) {
        return tripService.list(limit);
    }

    // The one trip not yet finalized (null when there is none) - lets the
    // apps say "finalize Trip #N first" before showing a new-trip form.
    @GetMapping("/open")
    public Map<String, Object> openTrip() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("openTrip", tripService.openTrip());
        return result;
    }

    @GetMapping("/{tripId}/finalization")
    public Map<String, Object> finalizationCheck(@PathVariable int tripId) {
        return tripService.finalizationCheck(tripId);
    }

    @PostMapping("/{tripId}/finalize")
    public Map<String, Object> finalizeTrip(
        @PathVariable int tripId, @RequestBody FinalizeTripRequest body,
        @org.springframework.web.bind.annotation.RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        return tripService.finalizeTrip(tripId, body.dropUndelivered(), actorUserId);
    }

    public record FinalizeTripRequest(boolean dropUndelivered) {}

    @GetMapping("/{tripId}")
    public Map<String, Object> get(@PathVariable int tripId) {
        return tripService.get(tripId);
    }

    @PostMapping
    public Map<String, Integer> create(@RequestBody CreateTripRequest body) {
        return Map.of("tripId", tripService.create(body.startDate(), body.productDescription()));
    }

    @PatchMapping("/{tripId}")
    public void update(@PathVariable int tripId, @RequestBody UpdateTripRequest body) {
        tripService.updateDetails(tripId, body.endDate(), body.noOfDeliveries(), body.totalBodyFeet(), body.productDescription());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{tripId}")
    public void delete(
        @PathVariable int tripId,
        @org.springframework.web.bind.annotation.RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        tripService.delete(tripId, actorUserId);
    }

    @GetMapping("/{tripId}/summary")
    public TripSummaryDto summary(@PathVariable int tripId) {
        return tripService.getSummary(tripId);
    }

    @PostMapping("/{tripId}/total-payment/recalculate")
    public Map<String, BigDecimal> recalculateTotalPayment(@PathVariable int tripId) {
        return Map.of("totalPayment", tripService.recalculateTotalPayment(tripId));
    }

    @PutMapping("/{tripId}/cash-split")
    public void setCashSplit(@PathVariable int tripId, @RequestBody CashSplitRequest body) {
        tripService.recordCashSplit(tripId, body.hardCash(), body.softCash());
    }

    public record CashSplitRequest(BigDecimal hardCash, BigDecimal softCash) {}

    public record CreateTripRequest(LocalDate startDate, String productDescription) {}

    public record UpdateTripRequest(LocalDate endDate, Integer noOfDeliveries, BigDecimal totalBodyFeet, String productDescription) {}
}
