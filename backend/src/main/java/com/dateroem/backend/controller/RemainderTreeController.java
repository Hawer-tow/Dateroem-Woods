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

import com.dateroem.backend.dto.RemainderTreeDto;
import com.dateroem.backend.service.RemainderTreeService;

@RestController
@RequestMapping("/api/remainder-trees")
public class RemainderTreeController {

    private final RemainderTreeService remainderTreeService;

    public RemainderTreeController(RemainderTreeService remainderTreeService) {
        this.remainderTreeService = remainderTreeService;
    }

    @GetMapping
    public List<RemainderTreeDto> list(@RequestParam(required = false) Integer previousTripId) {
        return remainderTreeService.list(previousTripId);
    }

    @GetMapping("/{remainderTreesId}")
    public RemainderTreeDto get(@PathVariable int remainderTreesId) {
        return remainderTreeService.get(remainderTreesId);
    }

    @PostMapping
    public RemainderTreeDto create(@RequestBody RemainderTreeDto remainder) {
        int id = remainderTreeService.create(remainder);
        return remainderTreeService.get(id);
    }

    @PatchMapping("/{remainderTreesId}")
    public RemainderTreeDto update(@PathVariable int remainderTreesId, @RequestBody RemainderTreeDto remainder) {
        remainderTreeService.update(remainderTreesId, remainder);
        return remainderTreeService.get(remainderTreesId);
    }
}
