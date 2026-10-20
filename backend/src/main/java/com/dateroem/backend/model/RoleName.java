package com.dateroem.backend.model;

/**
 * Canonical role names, matching business rules.txt's naming convention
 * request: PascalCase / capitalized-word style (e.g. SuperAdmin), never
 * Superadmin or Super_admin. Seed ROLE.Name with these exact strings.
 *
 * "in the roles you will never see a role called employee but this
 * categories are there" - the old generic Employee role is retired
 * (soft-deleted in ROLE, not reused) in favor of four specific roles
 * covering what "Employee" used to mean: Marketer, ProductStaff, Worker,
 * Manager - each with its own access, not a single shared bucket.
 */
public enum RoleName {
    Customer,
    Marketer,
    Admin,
    SuperAdmin,
    ProductStaff,
    Worker,
    Manager
}
