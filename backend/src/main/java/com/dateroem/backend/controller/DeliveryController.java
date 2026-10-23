package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.AssignLightDeliveriesBatchRequest;
import com.dateroem.backend.dto.AssignLightDeliveryRequest;
import com.dateroem.backend.dto.CreateBulkOrderRequest;
import com.dateroem.backend.dto.CreateLightDeliveryRequest;
import com.dateroem.backend.dto.SettleLightOrdersRequest;
import com.dateroem.backend.dto.FinalizeBulkDeliveryRequest;
import com.dateroem.backend.service.AnthropicClient;
import com.dateroem.backend.service.DeliveryService;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final AnthropicClient anthropicClient;

    public DeliveryController(DeliveryService deliveryService, AnthropicClient anthropicClient) {
        this.deliveryService = deliveryService;
        this.anthropicClient = anthropicClient;
    }

    @GetMapping("/light")
    public List<Map<String, Object>> lightDeliveries(@RequestParam(required = false) Integer tripId) {
        return deliveryService.lightDeliveries(tripId);
    }

    @PatchMapping("/light/{lightDeliveryId}/status")
    public void updateLightDeliveryStatus(@PathVariable int lightDeliveryId, @RequestBody StatusRequest body) {
        deliveryService.updateLightDeliveryStatus(lightDeliveryId, body.status());
    }

    // "add a signature area which is stored in the signature blob for the
    // said products for the person to be added to the database" - a
    // dedicated endpoint rather than overloading the generic status patch
    // above, since this one also carries proof-of-receipt fields.
    @PatchMapping("/light/{lightDeliveryId}/deliver")
    public void deliverLightDelivery(@PathVariable int lightDeliveryId, @RequestBody DeliverRequest body) {
        deliveryService.deliverLightDelivery(lightDeliveryId, body.signature(), body.receivedBy());
    }

    public record DeliverRequest(String signature, String receivedBy) {}

    // "if it's heading to the same direction and there is space" - pending
    // orders an admin can attach to a trip they're sending out. Must stay
    // ahead of GET /light/{lightDeliveryId}-shaped routes (there are none
    // today, but mirrors the /mine-before-/{id} rule used elsewhere).
    @GetMapping("/light/unassigned")
    public List<Map<String, Object>> unassignedLightDeliveries() {
        return deliveryService.unassignedLightDeliveries();
    }

    // "the assigned delivery since it has a delivery end the customer in
    // the ordered goods sees will be delivered on 21st" - overrides the
    // generic SLA estimate with a real trip/vehicle + concrete date.
    @PostMapping("/light")
    public Map<String, Object> createLightDelivery(
        @RequestBody CreateLightDeliveryRequest body, @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        return deliveryService.createLightDelivery(body, actorUserId);
    }

    @GetMapping("/light/pending-groups")
    public List<Map<String, Object>> pendingOrderGroups() {
        return deliveryService.pendingOrderGroups();
    }

    @PostMapping("/light/assign-batch")
    public void assignLightDeliveriesBatch(
        @RequestBody AssignLightDeliveriesBatchRequest body, @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        deliveryService.assignLightDeliveriesBatch(body, actorUserId);
    }

    @PostMapping("/light/settle")
    public void settleLightOrders(
        @RequestBody SettleLightOrdersRequest body, @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        deliveryService.settleLightOrders(body, actorUserId);
    }

    @PatchMapping("/light/{lightDeliveryId}/assign")
    public void assignLightDelivery(
        @PathVariable int lightDeliveryId, @RequestBody AssignLightDeliveryRequest body,
        @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        deliveryService.assignLightDelivery(lightDeliveryId, body, actorUserId);
    }

    // "vehicle visible only to staff ... unless enabled" - Share driver for
    // one delivery (and the orders grouped under it), migration 038.
    @PatchMapping("/light/{lightDeliveryId}/share-driver")
    public void setShareDriver(
        @PathVariable int lightDeliveryId, @RequestBody ShareDriverRequest body,
        @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        deliveryService.setShareDriver(lightDeliveryId, Boolean.TRUE.equals(body.share()), actorUserId);
    }

    public record ShareDriverRequest(Boolean share) {}

    @GetMapping("/bulk")
    public List<Map<String, Object>> bulkDeliveries(@RequestParam(required = false) Integer tripId) {
        return deliveryService.bulkDeliveries(tripId);
    }

    @GetMapping("/bulk/{bulkDeliveryId}")
    public Map<String, Object> getBulkDelivery(@PathVariable int bulkDeliveryId) {
        return deliveryService.getBulkDelivery(bulkDeliveryId);
    }

    // The photo is kept out of the list above (it's large) - fetched on
    // demand when someone actually opens it.
    @GetMapping("/bulk/production/{descriptionId}/photo")
    public Map<String, Object> productionPhoto(@PathVariable int descriptionId) {
        return Map.of("photo", deliveryService.productionPhoto(descriptionId));
    }

    @GetMapping("/bulk/{bulkDeliveryId}/production")
    public List<Map<String, Object>> bulkProductionDescriptions(@PathVariable int bulkDeliveryId) {
        return deliveryService.bulkProductionDescriptions(bulkDeliveryId);
    }

    // "add a BULK_TRIP_PRODUCTION_DESCRIPTION section to add data
    // concerning that BULK_DELIVERY" - a standalone add, independent of
    // finalizeBulkDelivery() below (which only ever writes one row as a
    // side effect of closing out the whole order); a bulk order can have
    // more than one product-description line item.
    @PostMapping("/bulk/{bulkDeliveryId}/production")
    public Map<String, Integer> createBulkProductionDescription(
        @PathVariable int bulkDeliveryId, @RequestBody ProductionDescriptionRequest body
    ) {
        int id = deliveryService.createBulkProductionDescription(
            bulkDeliveryId, body.productType(), body.productDescription(), body.productMeasurement(),
            body.totalBodyFeet(), body.productTotal(), body.photo()
        );
        return Map.of("bulkTripProductionDescriptionId", id);
    }

    public record ProductionDescriptionRequest(
        String productType, String productDescription, String productMeasurement,
        java.math.BigDecimal totalBodyFeet, java.math.BigDecimal productTotal, String photo
    ) {}

    // "provide a place to allow one to edit since one might have missed on
    // some issues" - correcting a mistyped entry, or removing one recorded
    // by mistake, instead of only ever being able to add new ones.
    @PatchMapping("/bulk/production/{descriptionId}")
    public void updateBulkProductionDescription(
        @PathVariable int descriptionId, @RequestBody ProductionDescriptionRequest body
    ) {
        deliveryService.updateBulkProductionDescription(
            descriptionId, body.productType(), body.productDescription(), body.productMeasurement(),
            body.totalBodyFeet(), body.productTotal(), body.photo()
        );
    }

    @DeleteMapping("/bulk/production/{descriptionId}")
    public void deleteBulkProductionDescription(
        @PathVariable int descriptionId, @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        deliveryService.deleteBulkProductionDescription(descriptionId, actorUserId);
    }

    // "the ai will pick a picture ... and it will scan that picture for
    // data and fill them" - extracts values only, never saves them
    // directly; the admin reviews/edits the pre-filled Add Entry form and
    // saves through createBulkProductionDescription() above like any
    // other entry, same as a human would have typed it.
    @PostMapping("/bulk/{bulkDeliveryId}/production/scan")
    public ProductionDescriptionRequest scanProductionDescription(
        @PathVariable int bulkDeliveryId, @RequestBody ScanImageRequest body
    ) {
        if (!anthropicClient.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "AI scanning isn't configured yet - app.anthropic.api-key is blank"
            );
        }
        return anthropicClient.scan(body.imageDataUrl());
    }

    public record ScanImageRequest(String imageDataUrl) {}

    // "i want to allow the admin or staff to create a bulk order and its
    // added into the system" - opens the paired ORDERS + BULK_DELIVERY
    // record for a heavy-order negotiation.
    @PostMapping("/bulk")
    public Map<String, Object> createBulkOrder(@RequestBody CreateBulkOrderRequest body,
                                                @RequestHeader("X-Actor-User-Id") int actorUserId) {
        return deliveryService.createBulkOrder(body.deliveredTo(), body.tripId(), actorUserId);
    }

    // "if it fails its just deleted" - the negotiation didn't proceed.
    @DeleteMapping("/bulk/{bulkDeliveryId}")
    public void deleteBulkOrder(@PathVariable int bulkDeliveryId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        deliveryService.deleteBulkOrder(bulkDeliveryId, actorUserId);
    }

    // "once the bulk delivery is delivered the details are filled on
    // amount cash soft ... as well as the bulk delivery description table"
    @PatchMapping("/bulk/{bulkDeliveryId}/finalize")
    public void finalizeBulkDelivery(@PathVariable int bulkDeliveryId, @RequestBody FinalizeBulkDeliveryRequest body,
                                      @RequestHeader("X-Actor-User-Id") int actorUserId) {
        deliveryService.finalizeBulkDelivery(bulkDeliveryId, body, actorUserId);
    }

    public record StatusRequest(String status) {}
}
