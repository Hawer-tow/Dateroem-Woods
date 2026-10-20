package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.service.ReviewService;
import com.dateroem.backend.service.RoleService;

/**
 * Product ratings (see ReviewService). Reading the public figures needs no
 * account; rating, editing and removing always use the customer id from the
 * validated JWT, never from the request, so nobody can rate as someone else.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final JwtService jwtService;
    private final RoleService roleService;

    public ReviewController(ReviewService reviewService, JwtService jwtService, RoleService roleService) {
        this.reviewService = reviewService;
        this.jwtService = jwtService;
        this.roleService = roleService;
    }

    private int requireUser(String authHeader) {
        return jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
    }

    private int requireAdmin(String authHeader) {
        int userId = requireUser(authHeader);
        if (!roleService.isAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        return userId;
    }

    // Average + count per product for the product cards, keyed by product id.
    @GetMapping("/summary")
    public Map<Integer, Map<String, Object>> summaries() {
        return reviewService.summaries();
    }

    @GetMapping("/product/{productId}")
    public Map<String, Object> product(@PathVariable int productId) {
        return reviewService.productSummary(productId);
    }

    @GetMapping("/mine")
    public Map<String, Object> mine(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        return reviewService.mine(requireUser(authHeader));
    }

    @PutMapping("/product/{productId}")
    public Map<String, Object> save(
        @PathVariable int productId,
        @RequestBody ReviewService.SaveReviewRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return reviewService.save(requireUser(authHeader), productId, request);
    }

    @DeleteMapping("/product/{productId}")
    public Map<String, Object> delete(
        @PathVariable int productId,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return reviewService.delete(requireUser(authHeader), productId);
    }

    // Moderation for abusive or false reviews - admins only, checked
    // against the real signed-in user.
    @GetMapping("/admin")
    public List<Map<String, Object>> adminList(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return reviewService.listForAdmin();
    }

    @PostMapping("/admin/{reviewId}/hidden")
    public Map<String, Object> setHidden(
        @PathVariable int reviewId,
        @RequestBody HiddenRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        int adminUserId = requireAdmin(authHeader);
        reviewService.setHidden(reviewId, Boolean.TRUE.equals(request.hidden()), adminUserId);
        return Map.of("reviewId", reviewId, "hidden", Boolean.TRUE.equals(request.hidden()));
    }

    public record HiddenRequest(Boolean hidden) {}
}
