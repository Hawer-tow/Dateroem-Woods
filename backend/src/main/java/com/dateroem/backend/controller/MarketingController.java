package com.dateroem.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.CampaignDto;
import com.dateroem.backend.dto.CampaignProductsRequest;
import com.dateroem.backend.model.RoleName;
import com.dateroem.backend.service.MarketingService;
import com.dateroem.backend.service.RoleService;

@RestController
@RequestMapping("/api/marketing/campaigns")
public class MarketingController {

    private final MarketingService marketingService;
    private final RoleService roleService;

    public MarketingController(MarketingService marketingService, RoleService roleService) {
        this.marketingService = marketingService;
        this.roleService = roleService;
    }

    // includeRetired is set only by the apps' campaign lists; the website
    // server leaves it off, so customers never see a retired campaign.
    @GetMapping
    public List<CampaignDto> list(@RequestParam(required = false) Integer limit,
                                  @RequestParam(defaultValue = "false") boolean includeRetired) {
        return marketingService.list(limit, includeRetired);
    }

    // Must come before /{campaignId} - Spring's PathPattern matching
    // prioritizes literal segments over variables regardless of
    // declaration order, but keeping it here mirrors ProductController.
    @GetMapping("/deleted")
    public List<CampaignDto> listDeleted() {
        return marketingService.listDeleted();
    }

    @GetMapping("/{campaignId}")
    public CampaignDto get(@PathVariable int campaignId) {
        return marketingService.get(campaignId);
    }

    @PostMapping
    public CampaignDto create(@RequestBody CampaignDto campaign, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        int id = marketingService.create(campaign, actorUserId);
        return marketingService.get(id);
    }

    @PatchMapping("/{campaignId}")
    public CampaignDto update(
        @PathVariable int campaignId,
        @RequestBody CampaignDto campaign,
        @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        marketingService.update(campaignId, campaign, actorUserId);
        return marketingService.get(campaignId);
    }

    @DeleteMapping("/{campaignId}")
    public void delete(@PathVariable int campaignId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        marketingService.delete(campaignId, actorUserId);
    }

    @PostMapping("/{campaignId}/restore")
    public CampaignDto restore(@PathVariable int campaignId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        marketingService.restore(campaignId, actorUserId);
        return marketingService.get(campaignId);
    }

    // Retiring and bringing back a campaign change what customers see, so
    // only the people who run campaigns may: Marketers, Admins and the
    // SuperAdmin (checked here, not just in the apps).
    @PostMapping("/{campaignId}/retire")
    public CampaignDto retire(@PathVariable int campaignId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        requireCampaignManager(actorUserId);
        marketingService.retire(campaignId, actorUserId);
        return marketingService.get(campaignId);
    }

    @PostMapping("/{campaignId}/unretire")
    public CampaignDto unretire(@PathVariable int campaignId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        requireCampaignManager(actorUserId);
        marketingService.unretire(campaignId, actorUserId);
        return marketingService.get(campaignId);
    }

    private void requireCampaignManager(int actorUserId) {
        if (!(RoleName.Marketer.name().equals(roleService.roleNameOf(actorUserId)) || roleService.isAdmin(actorUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Marketer, Admin or SuperAdmin only");
        }
    }

    @PostMapping("/{campaignId}/products/{productId}")
    public void linkProduct(@PathVariable int campaignId, @PathVariable int productId) {
        marketingService.linkProduct(campaignId, productId);
    }

    @GetMapping("/{campaignId}/products")
    public List<Integer> linkedProducts(@PathVariable int campaignId) {
        return marketingService.linkedProductIds(campaignId);
    }

    // "Products in this campaign" in the phone and desktop apps - the whole
    // chosen set at once. Returns the product ids linked afterwards.
    @PutMapping("/{campaignId}/products")
    public List<Integer> setProducts(
        @PathVariable int campaignId,
        @RequestBody CampaignProductsRequest request,
        @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        return marketingService.setProducts(campaignId, request.productIds(), actorUserId);
    }

    @PostMapping("/{campaignId}/audience/{userId}")
    public void addAudienceMember(@PathVariable int campaignId, @PathVariable int userId) {
        marketingService.addAudienceMember(campaignId, userId);
    }
}
