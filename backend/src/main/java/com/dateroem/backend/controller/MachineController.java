package com.dateroem.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.MachineDto;
import com.dateroem.backend.service.MachineService;

@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public List<MachineDto> list() {
        return machineService.list();
    }

    @GetMapping("/{machineId}")
    public MachineDto get(@PathVariable int machineId) {
        return machineService.get(machineId);
    }

    @PostMapping
    public MachineDto create(@RequestBody MachineDto machine) {
        int id = machineService.create(machine);
        return machineService.get(id);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{machineId}")
    public MachineDto update(@PathVariable int machineId, @RequestBody MachineDto machine) {
        return machineService.update(machineId, machine);
    }
}
