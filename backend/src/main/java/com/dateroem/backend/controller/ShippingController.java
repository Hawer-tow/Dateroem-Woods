package com.dateroem.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.ShippingAreaDto;
import com.dateroem.backend.service.ShippingService;

@RestController
@RequestMapping("/api/shipping/areas")
public class ShippingController {

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    // includeInactive=true is the mobile app's management view (staff need
    // to find and re-enable a switched-off area); the website's checkout
    // dropdown leaves it false and only ever sees active areas.
    @GetMapping
    public List<ShippingAreaDto> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return includeInactive ? shippingService.allAreas() : shippingService.activeAreas();
    }

    @GetMapping("/{areaId}")
    public ShippingAreaDto get(@PathVariable int areaId) {
        return shippingService.get(areaId);
    }

    @PostMapping
    public ShippingAreaDto create(@RequestBody ShippingAreaDto area) {
        int id = shippingService.createArea(area);
        return shippingService.get(id);
    }

    @PatchMapping("/{areaId}")
    public ShippingAreaDto update(@PathVariable int areaId, @RequestBody ShippingAreaDto area) {
        shippingService.updateArea(areaId, area);
        return shippingService.get(areaId);
    }
}
