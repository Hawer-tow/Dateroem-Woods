package com.dateroem.backend.controller;

import java.util.List;

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

import com.dateroem.backend.dto.TreeDto;
import com.dateroem.backend.service.TreeService;

@RestController
@RequestMapping("/api/trees")
public class TreeController {

    private final TreeService treeService;

    public TreeController(TreeService treeService) {
        this.treeService = treeService;
    }

    // tripId scopes the Products form's picker ("select from trees already
    // registered for a certain trip") and the desktop's per-trip Trees tab;
    // omitted, it's every tree (the raw Trees management list).
    @GetMapping
    public List<TreeDto> list(@RequestParam(required = false) Integer tripId) {
        return treeService.list(tripId);
    }

    // "delete trees should also be created and add a bin just like the
    // product section does it" - same admin-only-in-the-client-UI note as
    // ProductController's own /deleted route.
    @GetMapping("/deleted")
    public List<TreeDto> listDeleted() {
        return treeService.listDeleted();
    }

    @GetMapping("/{treesId}")
    public TreeDto get(@PathVariable int treesId) {
        return treeService.get(treesId);
    }

    @PostMapping
    public TreeDto create(@RequestBody TreeDto tree) {
        int id = treeService.create(tree);
        return treeService.get(id);
    }

    @PatchMapping("/{treesId}")
    public TreeDto update(@PathVariable int treesId, @RequestBody TreeDto tree) {
        treeService.update(treesId, tree);
        return treeService.get(treesId);
    }

    @DeleteMapping("/{treesId}")
    public void delete(@PathVariable int treesId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        treeService.delete(treesId, actorUserId);
    }

    @PostMapping("/{treesId}/restore")
    public TreeDto restore(@PathVariable int treesId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        treeService.restore(treesId, actorUserId);
        return treeService.get(treesId);
    }
}
