package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.CreateFarmerPaymentRequest;
import com.dateroem.backend.service.FarmerPaymentService;

@RestController
@RequestMapping("/api/farmer-payments")
public class FarmerPaymentController {

    private final FarmerPaymentService farmerPaymentService;

    public FarmerPaymentController(FarmerPaymentService farmerPaymentService) {
        this.farmerPaymentService = farmerPaymentService;
    }

    @PostMapping
    public Map<String, Integer> create(@RequestBody CreateFarmerPaymentRequest body,
                                        @RequestHeader("X-Actor-User-Id") int actorUserId) {
        return Map.of("farmerPaymentId", farmerPaymentService.create(body, actorUserId));
    }

    @GetMapping
    public List<Map<String, Object>> listByFarmer(@RequestParam int farmerId) {
        return farmerPaymentService.listByFarmer(farmerId);
    }
}
