package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import com.dateroem.backend.dto.CreateOrderRequest;
import com.dateroem.backend.dto.CreateOrderResult;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.service.OrderService;
import com.dateroem.backend.service.RoleService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final JwtService jwtService;
    private final RoleService roleService;

    public OrderController(OrderService orderService, JwtService jwtService, RoleService roleService) {
        this.orderService = orderService;
        this.jwtService = jwtService;
        this.roleService = roleService;
    }

    private int requireUser(String authHeader) {
        return jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
    }

    // "a cost is reduced from the initially sent by the admins who can
    // only do this" - the real JWT is checked (requireUser), then that
    // real, verified user is checked against the ROLE table, unlike the
    // X-Actor-User-Id-only endpoints elsewhere in this codebase (see the
    // pre-production security review) - this one has no such gap.
    private int requireAdmin(String authHeader) {
        int userId = requireUser(authHeader);
        if (!roleService.isAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        return userId;
    }

    // Checkout requires a signed-in customer: the order's User_ID comes only
    // from the validated JWT, never from the request body, so placing an
    // order always requires having registered/logged in first.
    @PostMapping
    public CreateOrderResult create(
        @RequestBody CreateOrderRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return orderService.createOrder(request, requireUser(authHeader));
    }

    // Most-recent-first, used by the help chat to answer "where's my
    // order" - must be declared before /{orderId} or Spring would try to
    // parse "mine" as an int path variable.
    @GetMapping("/mine")
    public List<Map<String, Object>> mine(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestParam(defaultValue = "5") int limit
    ) {
        return orderService.listMine(requireUser(authHeader), limit);
    }

    // Track my order - no sign-in: the ticket plus the last 3 digits of the
    // account's phone number (limits on wrong tries in the service). Also
    // declared before /{orderId}.
    @GetMapping("/track")
    public Map<String, Object> track(@RequestParam String ticket, @RequestParam String phone) {
        return orderService.track(ticket, phone);
    }

    // Backs the order confirmation/status page - ownership-checked in the
    // service so one customer can't read another's order by guessing an id.
    @GetMapping("/{orderId}")
    public Map<String, Object> get(
        @PathVariable int orderId,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return orderService.get(orderId, requireUser(authHeader));
    }

    // "allow a user to drop the product if it has not yet been assigned a
    // trip ... refunded the money paid ... a cost is reduced from the
    // initially sent by the admins who can only do this" - admin-only,
    // and OrderService itself blocks it once the order is on a trip.
    @PostMapping("/{orderId}/drop")
    public Map<String, Object> drop(
        @PathVariable int orderId,
        @RequestBody DropOrderRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        int actorUserId = requireAdmin(authHeader);
        return orderService.dropOrder(orderId, request.cancellationCost(), actorUserId);
    }

    // "where exactly do i go ... still cannot see it" - lists every dropped
    // order still owing its customer a refund, for the Send Money target
    // picker (ExpenseTarget.ORDER_REFUND) to pay out.
    @GetMapping("/refunds/pending")
    public List<Map<String, Object>> pendingRefunds(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return orderService.listPendingRefunds();
    }

    // "why does the database not show the reference ... in the desktop" -
    // every website payment with its M-Pesa receipt, newest first (admins).
    @GetMapping("/payments")
    public List<Map<String, Object>> payments(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestParam(defaultValue = "200") int limit
    ) {
        requireAdmin(authHeader);
        return orderService.listPayments(limit);
    }

    public record DropOrderRequest(BigDecimal cancellationCost) {}
}
