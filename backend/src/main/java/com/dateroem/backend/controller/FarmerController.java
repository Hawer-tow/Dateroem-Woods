package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.FarmerDto;
import com.dateroem.backend.service.FarmerService;

@RestController
@RequestMapping("/api/farmers")
public class FarmerController {

    private final FarmerService farmerService;

    public FarmerController(FarmerService farmerService) {
        this.farmerService = farmerService;
    }

    @GetMapping
    public List<FarmerDto> list() {
        return farmerService.list();
    }

    @GetMapping("/{farmerId}")
    public FarmerDto get(@PathVariable int farmerId) {
        return farmerService.get(farmerId);
    }

    // see FarmerService.topSuppliers() - part of the Insights feature.
    @GetMapping("/insights/top-suppliers")
    public List<Map<String, Object>> topSuppliers() {
        return farmerService.topSuppliers();
    }

    @PostMapping
    public FarmerDto create(@RequestBody FarmerDto farmer) {
        int id = farmerService.create(farmer);
        return farmerService.get(id);
    }

    @PatchMapping("/{farmerId}")
    public FarmerDto update(@PathVariable int farmerId, @RequestBody FarmerDto farmer) {
        farmerService.update(farmerId, farmer);
        return farmerService.get(farmerId);
    }
}
