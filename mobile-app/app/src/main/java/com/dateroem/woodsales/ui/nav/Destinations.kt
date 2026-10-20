package com.dateroem.woodsales.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import com.dateroem.woodsales.ui.common.AppIcons

// Mirrors business rules.txt's 7 mobile-app sections. requiredRoles empty
// means everyone signed in can see it; otherwise gated to those role
// names (e.g. Loans & Finance: "only of the admin/Superadmins"). icon gives
// each drawer entry its own picture so it reads as its own category at a
// glance - product vs expense vs finance - instead of everything being the
// same generic row. "lets do away with emojis" - a line icon (AppIcons),
// the same set as the website and desktop app.
sealed class Section(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val requiredRoles: Set<String> = emptySet()
) {
    // The new default landing screen - "i want to add a layout the
    // default where everything comes to here" - visible to everyone, no
    // role gate, and now the app's actual startDestination (see HomeShell).
    // (Whether the *profit* figure itself shows on this screen is a
    // separate, finer-grained check the business owner said is still
    // "later" work - "so far its only the role based seein that is not
    // there" - this Section-level gate only controls navigation.)
    data object Dashboard : Section("dashboard", "Home", AppIcons.Home, emptySet())

    // The generic "Employee" role is retired - "in the roles you will
    // never see a role called employee but this categories are there" -
    // replaced by four specific roles, each section gated to exactly who
    // the business owner named for it:
    //   marketer      - "incharge of marketing only"
    //   product_staff - "incharge of creation of products only"
    //   worker        - "cannot access the marketing but can access the
    //                    product creation ... incharge of adding expenses
    //                    except the salaries expense ... no access to
    //                    loans and finances and transactions and trips"
    //   manager       - "can access all this that other employees can
    //                    but cannot access the loans and finances and in
    //                    transactions cannot send stk push or send money
    //                    but can assign transactions"
    // Sections the business rules didn't call out per-role (Bulk Orders,
    // Trees) stay open to all four, same breadth as the old Employee gate
    // - restricting an unaddressed section is a worse default than
    // leaving it as it was.
    data object Products : Section("products", "Products", AppIcons.TreeRings, setOf("ProductStaff", "Worker", "Manager", "Admin", "SuperAdmin"))
    data object Marketing : Section("marketing", "Marketing", AppIcons.Megaphone, setOf("Marketer", "Manager", "Admin", "SuperAdmin"))
    data object Expenses : Section("expenses", "Expenses", AppIcons.Receipt, setOf("Worker", "Manager", "Admin", "SuperAdmin"))
    // Manager keeps this (can "assign transactions"); the finer
    // restriction - no STK push / send money button for Manager - is
    // still "later" in-screen work, not a navigation-level gate.
    data object Transactions : Section("transactions", "Transactions", AppIcons.Exchange, setOf("Manager", "Admin", "SuperAdmin"))
    data object Finance : Section("finance", "Loans & Finance", AppIcons.Bank, setOf("Admin", "SuperAdmin"))
    // "implement it into the mobile ... even adding a section
    // specifically for the ai feature" - the insights layer (unassigned
    // transactions, top farmer suppliers, expense breakdown), same admin
    // gate as Loans & Finance since it surfaces the same kind of
    // financial/operational data.
    data object Ai : Section("ai", "AI", AppIcons.Sparkles, setOf("Admin", "SuperAdmin"))
    data object BulkOrders : Section("bulk_orders", "Bulk Orders", AppIcons.Truck, setOf("Marketer", "ProductStaff", "Worker", "Manager", "Admin", "SuperAdmin"))
    // Worker explicitly excluded ("no access to ... trips section") - the
    // bottom trip-dial selector is unaffected (worker "can move from one
    // trip to another" there), since that's a separate always-visible
    // control, not this Section.
    data object Trips : Section("trips", "Trips", AppIcons.Map, setOf("Manager", "Admin", "SuperAdmin"))
    // "the orders things and the assigning the orders ... should be
    // implemented in the mobile" - pending orders gathered per customer, sent
    // to a light or bulk delivery. Same gate as Trips (it works against
    // trips). Also reachable from the AI section's first tile.
    data object Orders : Section("orders", "Orders", AppIcons.Package, setOf("Manager", "Admin", "SuperAdmin"))
    // "an area in products to select from trees already registered for a
    // certain trip" - same access as Products, since it's what Products
    // links against.
    data object Trees : Section("trees", "Trees", AppIcons.Tree, setOf("ProductStaff", "Worker", "Manager", "Admin", "SuperAdmin"))

    companion object {
        // Ai stays defined (chat shortcuts still point at its route) but is no
        // longer listed: the assistant is the floating button now.
        val all = listOf(Dashboard, Orders, Products, BulkOrders, Marketing, Trips, Trees, Expenses, Transactions, Finance)
    }
}
