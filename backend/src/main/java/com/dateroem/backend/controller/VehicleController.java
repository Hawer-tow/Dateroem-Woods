package com.dateroem.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.VehicleDto;
import com.dateroem.backend.service.VehicleService;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleDto> list() {
        return vehicleService.list();
    }

    @GetMapping("/{vehicleId}")
    public VehicleDto get(@PathVariable int vehicleId) {
        return vehicleService.get(vehicleId);
    }

    @PostMapping
    public VehicleDto create(@RequestBody VehicleDto vehicle) {
        int id = vehicleService.create(vehicle);
        return vehicleService.get(id);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{vehicleId}")
    public VehicleDto update(@PathVariable int vehicleId, @RequestBody VehicleDto vehicle) {
        return vehicleService.update(vehicleId, vehicle);
    }
}
