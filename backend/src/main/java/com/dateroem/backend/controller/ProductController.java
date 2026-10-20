package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.ProductDto;
import com.dateroem.backend.service.ProductService;
import com.dateroem.backend.service.RoleService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final RoleService roleService;

    public ProductController(ProductService productService, RoleService roleService) {
        this.productService = productService;
        this.roleService = roleService;
    }

    // includeRetired is set only by the admin servers (desktop/mobile); the
    // website server leaves it off, so the customer shop never lists a
    // retired product.
    @GetMapping
    public List<ProductDto> list(@RequestParam(required = false) Integer limit,
                                 @RequestParam(defaultValue = "false") boolean includeRetired) {
        return productService.list(limit, includeRetired);
    }

    @GetMapping("/popular")
    public List<Map<String, Object>> popular(@RequestParam(defaultValue = "10") int limit) {
        return productService.popular(limit);
    }

    // Admin "needs attention" list: products that have run out but are still
    // live and not retired, so an admin can retire one from there.
    @GetMapping("/depleted")
    public List<ProductDto> depleted() {
        return productService.listDepleted();
    }

    // Admin-only in the client UI (role check happens there, same as the
    // rest of this app's TODO on server-side role enforcement); the route
    // itself is reachable by anyone holding a valid token for now.
    @GetMapping("/deleted")
    public List<ProductDto> listDeleted() {
        return productService.listDeleted();
    }

    @GetMapping("/{productId}")
    public ProductDto get(@PathVariable int productId,
                          @RequestParam(defaultValue = "false") boolean includeRetired) {
        // Admin path sees a retired product; the customer shop does not.
        return includeRetired ? productService.get(productId) : productService.getForCustomer(productId);
    }

    @PostMapping
    public ProductDto create(@RequestBody ProductDto product, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        int id = productService.create(product, actorUserId);
        return productService.get(id);
    }

    @PatchMapping("/{productId}")
    public ProductDto update(
        @PathVariable int productId,
        @RequestBody ProductDto product,
        @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        productService.update(productId, product, actorUserId);
        return productService.get(productId);
    }

    @DeleteMapping("/{productId}")
    public void delete(@PathVariable int productId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        productService.delete(productId, actorUserId);
    }

    @PostMapping("/{productId}/restore")
    public ProductDto restore(@PathVariable int productId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        productService.restore(productId, actorUserId);
        return productService.get(productId);
    }

    // Retiring and bringing back a product both change what customers can
    // buy, so they are admin-only (checked on the server, not just in the
    // apps). The returned product carries its new Retired state.
    @PostMapping("/{productId}/retire")
    public ProductDto retire(@PathVariable int productId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        requireAdmin(actorUserId);
        productService.retire(productId, actorUserId);
        return productService.get(productId);
    }

    @PostMapping("/{productId}/unretire")
    public ProductDto unretire(@PathVariable int productId, @RequestHeader("X-Actor-User-Id") int actorUserId) {
        requireAdmin(actorUserId);
        productService.unretire(productId, actorUserId);
        return productService.get(productId);
    }

    private void requireAdmin(int actorUserId) {
        if (!roleService.isAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
    }
}
