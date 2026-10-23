package com.dateroem.woodsales.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import com.dateroem.woodsales.ui.ai.ChatPanel
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.dateroem.woodsales.ui.profile.ProfileScreen
import com.dateroem.woodsales.ui.profile.ProfileViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dateroem.woodsales.AppContainer
import com.dateroem.woodsales.data.session.Session
import com.dateroem.woodsales.ui.ai.ChatFloatingButton
import com.dateroem.woodsales.ui.orders.AssignGroupScreen
import com.dateroem.woodsales.ui.orders.AssignGroupViewModel
import com.dateroem.woodsales.ui.orders.OrdersScreen
import com.dateroem.woodsales.ui.orders.OrdersViewModel
import com.dateroem.woodsales.ui.transactions.RefundsScreen
import com.dateroem.woodsales.ui.transactions.RefundsViewModel
import com.dateroem.woodsales.ui.ai.ChatScreen
import com.dateroem.woodsales.ui.ai.ChatViewModel
import com.dateroem.woodsales.ui.ai.ExpenseBreakdownScreen
import com.dateroem.woodsales.ui.ai.ExpenseBreakdownViewModel
import com.dateroem.woodsales.ui.ai.TopFarmersScreen
import com.dateroem.woodsales.ui.ai.TopFarmersViewModel
import com.dateroem.woodsales.ui.ai.UnassignedTransactionsScreen
import com.dateroem.woodsales.ui.ai.UnassignedTransactionsViewModel
import com.dateroem.woodsales.ui.bulkorders.BulkOrderDetailScreen
import com.dateroem.woodsales.ui.bulkorders.BulkOrderDetailViewModel
import com.dateroem.woodsales.ui.bulkorders.BulkOrderFormScreen
import com.dateroem.woodsales.ui.bulkorders.BulkOrderFormViewModel
import com.dateroem.woodsales.ui.bulkorders.BulkOrdersScreen
import com.dateroem.woodsales.ui.bulkorders.BulkOrdersViewModel
import com.dateroem.woodsales.ui.dashboard.DashboardScreen
import com.dateroem.woodsales.ui.dashboard.DashboardViewModel
import com.dateroem.woodsales.ui.deliveries.LightDeliveriesScreen
import com.dateroem.woodsales.ui.deliveries.LightDeliveriesViewModel
import com.dateroem.woodsales.ui.finance.CapitalScreen
import com.dateroem.woodsales.ui.finance.CapitalViewModel
import com.dateroem.woodsales.ui.finance.FinanceOverviewScreen
import com.dateroem.woodsales.ui.finance.FinanceOverviewViewModel
import com.dateroem.woodsales.ui.finance.LoanDetailScreen
import com.dateroem.woodsales.ui.finance.LoanDetailViewModel
import com.dateroem.woodsales.ui.finance.LoanFormScreen
import com.dateroem.woodsales.ui.finance.LoanFormViewModel
import com.dateroem.woodsales.ui.finance.LoansListScreen
import com.dateroem.woodsales.ui.finance.LoansListViewModel
import com.dateroem.woodsales.ui.finance.PayLoanScreen
import com.dateroem.woodsales.ui.finance.PayLoanViewModel
import com.dateroem.woodsales.ui.marketing.CampaignFormScreen
import com.dateroem.woodsales.ui.marketing.CampaignFormViewModel
import com.dateroem.woodsales.ui.marketing.CampaignsScreen
import com.dateroem.woodsales.ui.marketing.CampaignsViewModel
import com.dateroem.woodsales.ui.marketing.DeletedCampaignsScreen
import com.dateroem.woodsales.ui.marketing.DeletedCampaignsViewModel
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.marketing.ShowcaseScreen
import com.dateroem.woodsales.ui.marketing.ShowcaseViewModel
import com.dateroem.woodsales.ui.expenses.ExpenseCategoriesScreen
import com.dateroem.woodsales.ui.expenses.ExpenseCategoriesViewModel
import com.dateroem.woodsales.ui.expenses.ExpenseCategory
import com.dateroem.woodsales.ui.expenses.ExpenseFormScreen
import com.dateroem.woodsales.ui.expenses.ExpenseFormViewModel
import com.dateroem.woodsales.ui.expenses.ExpenseListScreen
import com.dateroem.woodsales.ui.expenses.ExpenseListViewModel
import com.dateroem.woodsales.ui.expenses.TripPickerScreen
import com.dateroem.woodsales.ui.expenses.TripPickerViewModel
import com.dateroem.woodsales.ui.products.DeletedProductsScreen
import com.dateroem.woodsales.ui.products.DeletedProductsViewModel
import com.dateroem.woodsales.ui.products.ProductFormScreen
import com.dateroem.woodsales.ui.products.ProductFormViewModel
import com.dateroem.woodsales.ui.products.ProductsScreen
import com.dateroem.woodsales.ui.products.ProductsViewModel
import com.dateroem.woodsales.ui.auth.SignOutConfirmDialog
import com.dateroem.woodsales.ui.settings.SettingsScreen
import com.dateroem.woodsales.ui.shipping.ShippingAreaFormScreen
import com.dateroem.woodsales.ui.shipping.ShippingAreaFormViewModel
import com.dateroem.woodsales.ui.shipping.ShippingAreasScreen
import com.dateroem.woodsales.ui.shipping.ShippingAreasViewModel
import com.dateroem.woodsales.ui.transactions.AssignDetailScreen
import com.dateroem.woodsales.ui.transactions.AssignDetailViewModel
import com.dateroem.woodsales.ui.transactions.AssignQueueScreen
import com.dateroem.woodsales.ui.transactions.AssignQueueViewModel
import com.dateroem.woodsales.ui.transactions.SendMoneyScreen
import com.dateroem.woodsales.ui.transactions.SendMoneyViewModel
import com.dateroem.woodsales.ui.transactions.TransactionSummary
import com.dateroem.woodsales.ui.transactions.SmsSyncScreen
import com.dateroem.woodsales.ui.transactions.SmsSyncViewModel
import com.dateroem.woodsales.ui.transactions.StkPushScreen
import com.dateroem.woodsales.ui.transactions.StkPushViewModel
import com.dateroem.woodsales.ui.transactions.TransactionsHomeScreen
import com.dateroem.woodsales.ui.trips.TripDetailScreen
import com.dateroem.woodsales.ui.trips.TripDetailViewModel
import com.dateroem.woodsales.ui.trips.TripDialPicker
import com.dateroem.woodsales.ui.trips.TripDialTriggerButton
import com.dateroem.woodsales.ui.trips.TripDialViewModel
import com.dateroem.woodsales.ui.trips.TripFormScreen
import com.dateroem.woodsales.ui.trips.TripFormViewModel
import com.dateroem.woodsales.ui.trips.TripsScreen
import com.dateroem.woodsales.ui.trips.TripsViewModel
import com.dateroem.woodsales.ui.trees.DeletedTreesScreen
import com.dateroem.woodsales.ui.trees.DeletedTreesViewModel
import com.dateroem.woodsales.ui.trees.RemainderTreeFormScreen
import com.dateroem.woodsales.ui.trees.RemainderTreeFormViewModel
import com.dateroem.woodsales.ui.trees.TreeFormScreen
import com.dateroem.woodsales.ui.trees.TreeFormViewModel
import com.dateroem.woodsales.ui.trees.TreesScreen
import com.dateroem.woodsales.ui.trees.TreesViewModel
import kotlinx.coroutines.launch

private val ADMIN_ROLES = setOf("Admin", "SuperAdmin")
// Who may change the website's Bulk Orders pictures (the server checks too).
private val SHOWCASE_EDITORS = setOf("Marketer", "Admin", "SuperAdmin")

// Carries amount/date/reference/paymentMode from whichever unassigned-
// transactions list was tapped straight into assign_detail's URL, so that
// screen can show them without a second network call - see "place the
// date of that transaction ... for quicker location ... when allocating".
private fun assignDetailRoute(row: com.dateroem.woodsales.data.network.JsonMap): String {
    val id = (row["financialTransactionId"] as? Number)?.toInt() ?: return "assign_detail/0"
    fun enc(v: Any?) = android.net.Uri.encode(v?.toString() ?: "")
    return "assign_detail/$id?amount=${enc(row["amount"])}&createdAt=${enc(row["createdAt"])}" +
        "&referenceId=${enc(row["referenceId"])}&paymentMode=${enc(row["paymentMode"])}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(container: AppContainer, session: Session, onLoggedOut: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val visibleSections = Section.all.filter { it.requiredRoles.isEmpty() || session.roleName in it.requiredRoles }

    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        SignOutConfirmDialog(
            onConfirm = {
                confirmSignOut = false
                scope.launch {
                    container.sessionManager.clear()
                    onLoggedOut()
                }
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    // "in an event where the stock is depleted the admins are notified" -
    // how many products have run out, shown as a badge on Products in the
    // menu. Checked each time an admin opens the menu.
    val isAdmin = session.roleName in ADMIN_ROLES
    var depletedCount by remember { mutableStateOf(0) }
    LaunchedEffect(drawerState.targetValue) {
        if (isAdmin && drawerState.targetValue == DrawerValue.Open) {
            depletedCount = try {
                container.api.listDepletedProducts().size
            } catch (_: Exception) {
                depletedCount
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                session = session,
                visibleSections = visibleSections,
                currentRoute = currentRoute,
                badges = mapOf(Section.Products to depletedCount),
                onSelectSection = { section ->
                    scope.launch { drawerState.close() }
                    navController.navigate(section.route) {
                        launchSingleTop = true
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        restoreState = true
                    }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate("settings") { launchSingleTop = true }
                },
                onOpenProfile = {
                    scope.launch { drawerState.close() }
                    navController.navigate("profile") { launchSingleTop = true }
                },
                // Asks first (SignOutConfirmDialog, below) - a mistaken tap
                // only closes the menu.
                onSignOut = {
                    scope.launch { drawerState.close() }
                    confirmSignOut = true
                }
            )
        }
    ) {
        // currentRoute is the route *pattern* (e.g.
        // "product_form_edit/{productId}"), not the resolved path - covers
        // every screen the top bar can land on, not just the 7 drawer
        // sections (that gap was why the title fell back to "Dateroem" on
        // Deleted Products and the product form).
        val topBarTitle = when (currentRoute) {
            Section.Dashboard.route -> "Home"
            "settings" -> "Settings"
            "profile" -> "My profile"
            "deleted_products" -> "Deleted Products"
            "product_form_create" -> "New Product"
            "product_form_edit/{productId}" -> "Edit Product"
            "deleted_campaigns" -> "Deleted Campaigns"
            "deleted_trees" -> "Deleted Trees"
            "campaign_form_create" -> "New Campaign"
            "campaign_form_edit/{campaignId}" -> "Edit Campaign"
            "showcase" -> "Bulk Order Pictures"
            "expense_categories/{tripId}" -> "Expense Categories"
            "expense_list/{tripId}/{category}" -> "Expenses"
            "expense_form_create/{tripId}/{category}" -> "New Expense"
            "stk_push" -> "STK Push"
            "assign_queue" -> "Assign Transactions"
            "assign_detail/{financialTransactionId}" -> "Assign Transaction"
            "send_money/{category}/{rowId}/{amount}" -> "Send Money"
            "finance_overview/{tripId}" -> "Loans & Finance"
            "loan_detail/{tripId}/{loanId}/{loanAmount}" -> "Loan Detail"
            "pay_loan/{loanId}/{remaining}" -> "Pay Loan"
            "loan_form_create/{tripId}" -> "New Loan"
            "loan_form_edit/{tripId}/{loanId}" -> "Edit Loan"
            "loans_list/{tripId}" -> "Loans"
            "capital/{tripId}" -> "Capital"
            "sms_sync" -> "M-Pesa SMS Sync"
            "ai_unassigned_transactions" -> "Unassigned Transactions"
            "ai_top_farmers" -> "Top Farmers"
            "ai_expense_breakdown" -> "Expense Breakdown"
            "ai_chat" -> "Chat Assistant"
            "remainder_form_create/{tripId}" -> "Finalize Trees"
            "trip_form_create" -> "New Trip"
            "trip_detail/{tripId}" -> "Trip Detail"
            "bulk_orders/{tripId}" -> "Heavy Orders"
            "bulk_order_form_create/{tripId}" -> "New Heavy Order"
            "bulk_order_detail/{bulkDeliveryId}" -> "Heavy Order"
            "light_deliveries/{tripId}" -> "Light Deliveries"
            "stk_push_amount/{amount}" -> "STK Push"
            "shipping_areas" -> "Delivery Areas"
            "shipping_area_form_create" -> "New Delivery Area"
            "shipping_area_form_edit/{areaId}" -> "Edit Delivery Area"
            "trees/{tripId}" -> "Trees"
            "tree_form_create/{tripId}" -> "Register Trees"
            "tree_form_edit/{tripId}/{treesId}" -> "Edit Tree Registration"
            else -> visibleSections.firstOrNull { it.route == currentRoute }?.label ?: "Dateroem"
        }

        val tripDialViewModel: TripDialViewModel = viewModel(factory = TripDialViewModel.Factory(container.api, container.activeTripStore))
        val activeTripId by tripDialViewModel.activeTripStore.activeTripId.collectAsState(initial = null)
        val trips by tripDialViewModel.trips.collectAsState()
        var showTripDial by remember { mutableStateOf(false) }
        // The assistant: a small orb on the right edge that opens a compact
        // floating card (see ChatPanel). The conversation lives here so it
        // survives closing and reopening the card.
        var showChat by remember { mutableStateOf(false) }
        // "improve the ai in the mobile ... providing assistant based on role
        // ... through the various privileges and functions they have" - every
        // signed-in role gets the assistant now (it was Admin/SuperAdmin
        // only). What it offers each role - which sections, which actions -
        // is decided on the server from the user's real role.
        val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(container.api, session.userId))

        LaunchedEffect(Unit) { tripDialViewModel.loadTrips() }

        val activeSlotLabel = remember(activeTripId, trips) {
            if (activeTripId == null) "—" else {
                val idx = trips.indexOfFirst { (it["tripId"] as? Number)?.toInt() == activeTripId }
                if (idx < 0) "—" else "${idx + 1}"
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // Same top-bar-icon pattern on both list screens
                        // that have a "bin" - admin/SuperAdmin only, one
                        // tap instead of a menu to discover.
                        if (currentRoute == Section.Products.route && session.roleName in ADMIN_ROLES) {
                            IconButton(onClick = { navController.navigate("deleted_products") }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Deleted products")
                            }
                        }
                        // The website's Bulk Orders slideshow pictures -
                        // kept with the other things customers see.
                        if (currentRoute == Section.Marketing.route && session.roleName in SHOWCASE_EDITORS) {
                            IconButton(onClick = { navController.navigate("showcase") }) {
                                Icon(AppIcons.Image, contentDescription = "Bulk order pictures")
                            }
                        }
                        if (currentRoute == Section.Marketing.route && session.roleName in ADMIN_ROLES) {
                            IconButton(onClick = { navController.navigate("deleted_campaigns") }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Deleted campaigns")
                            }
                        }
                        if (currentRoute == "trees/{tripId}" && session.roleName in ADMIN_ROLES) {
                            IconButton(onClick = { navController.navigate("deleted_trees") }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Deleted trees")
                            }
                        }
                        // "create a place to edit and add or modify the
                        // existing picking areas so that this is added
                        // also in the heavy order area" - one tap from the
                        // Heavy Orders list to the same management screen.
                        if (currentRoute == "bulk_orders/{tripId}") {
                            IconButton(onClick = { navController.navigate("shipping_areas") }) {
                                Icon(Icons.Filled.Place, contentDescription = "Delivery areas")
                            }
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                // One dock in the bottom middle: main-menu shortcut on the left,
                // the trip selector (opens the wood dial that sets the app-wide
                // active trip) in the centre, the AI assistant on the right.
                BottomDock(
                    activeSlotLabel = activeSlotLabel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onTrip = {
                        tripDialViewModel.loadTrips()
                        showTripDial = true
                    },
                    onChat = if (!showChat) ({ showChat = true }) else null
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = visibleSections.firstOrNull()?.route ?: Section.Products.route,
                modifier = Modifier.padding(padding).fillMaxWidth()
            ) {
                composable(Section.Dashboard.route) {
                    val vm: DashboardViewModel = viewModel(
                        factory = DashboardViewModel.Factory(
                            container.api, container.activeTripStore, container.companyInfoStore, isAdmin = session.roleName in ADMIN_ROLES
                        )
                    )
                    DashboardScreen(vm, isAdmin = session.roleName in ADMIN_ROLES, isSuperAdmin = session.roleName == "SuperAdmin")
                }
                // From the menu's bottom row; every role has it.
                composable("settings") {
                    SettingsScreen(container)
                }
                // From the photo and name at the top of the menu (migration 039).
                composable("profile") {
                    val context = LocalContext.current
                    val vm: ProfileViewModel = viewModel(
                        factory = ProfileViewModel.Factory(container.api, container.sessionManager, context.applicationContext)
                    )
                    ProfileScreen(vm)
                }
                composable(Section.Products.route) {
                    // viewModel(factory=...) ties this to the back stack
                    // entry's own ViewModelStore, so it survives the camera
                    // app briefly taking over the foreground - a plain
                    // constructor call here was getting silently recreated
                    // on that transition, discarding whatever was typed or
                    // just-picked (the "photo shows then reverts" symptom).
                    val vm: ProductsViewModel = viewModel(
                        factory = ProductsViewModel.Factory(container.api, session.userId, isAdmin = session.roleName in ADMIN_ROLES)
                    )
                    // "i change the product image ... i have to shut the
                    // app open it again in order to see the images" -
                    // this ViewModel is scoped to the back stack entry
                    // (see the comment above), so it survives navigating
                    // to the edit form and back untouched - init{}'s own
                    // refresh() only ever ran once, the very first time
                    // this screen was created, so returning from a save
                    // kept showing whatever was loaded before the edit
                    // even happened. Re-entering this destination is a
                    // fresh composition of this call site even though the
                    // ViewModel instance is the same, so this fires again
                    // every time you land back here - including right
                    // after a save.
                    LaunchedEffect(Unit) { vm.refresh() }
                    ProductsScreen(
                        vm,
                        onAddProduct = { navController.navigate("product_form_create") },
                        onEditProduct = { id -> navController.navigate("product_form_edit/$id") }
                    )
                }
                composable("product_form_create") {
                    val vm: ProductFormViewModel = viewModel(
                        factory = ProductFormViewModel.Factory(container.api, session.userId, editingProductId = null, activeTripId = activeTripId)
                    )
                    ProductFormScreen(
                        viewModel = vm,
                        isEditing = false,
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(
                    "product_form_edit/{productId}",
                    arguments = listOf(navArgument("productId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val productId = backStackEntry.arguments?.getInt("productId") ?: return@composable
                    val vm: ProductFormViewModel = viewModel(
                        factory = ProductFormViewModel.Factory(
                            container.api, session.userId, editingProductId = productId, activeTripId = activeTripId,
                            isAdmin = session.roleName in ADMIN_ROLES
                        )
                    )
                    ProductFormScreen(
                        viewModel = vm,
                        isEditing = true,
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() },
                        onRetired = { navController.popBackStack() }
                    )
                }
                composable("deleted_products") {
                    val vm: DeletedProductsViewModel = viewModel(
                        factory = DeletedProductsViewModel.Factory(container.api, session.userId)
                    )
                    DeletedProductsScreen(vm)
                }
                composable(Section.Marketing.route) {
                    val vm: CampaignsViewModel = viewModel(factory = CampaignsViewModel.Factory(container.api))
                    // Same stale-until-restart issue as Products (see the
                    // comment there) - this is a direct popBackStack()
                    // target of campaign_form_create/edit.
                    LaunchedEffect(Unit) { vm.refresh() }
                    CampaignsScreen(
                        vm,
                        onAddCampaign = { navController.navigate("campaign_form_create") },
                        onEditCampaign = { id -> navController.navigate("campaign_form_edit/$id") }
                    )
                }
                composable("campaign_form_create") {
                    val vm: CampaignFormViewModel = viewModel(
                        factory = CampaignFormViewModel.Factory(container.api, session.userId, editingCampaignId = null)
                    )
                    CampaignFormScreen(
                        viewModel = vm,
                        isEditing = false,
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(
                    "campaign_form_edit/{campaignId}",
                    arguments = listOf(navArgument("campaignId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val campaignId = backStackEntry.arguments?.getInt("campaignId") ?: return@composable
                    val vm: CampaignFormViewModel = viewModel(
                        factory = CampaignFormViewModel.Factory(container.api, session.userId, editingCampaignId = campaignId)
                    )
                    CampaignFormScreen(
                        viewModel = vm,
                        isEditing = true,
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable("deleted_campaigns") {
                    val vm: DeletedCampaignsViewModel = viewModel(
                        factory = DeletedCampaignsViewModel.Factory(container.api, session.userId)
                    )
                    DeletedCampaignsScreen(vm)
                }
                composable("showcase") {
                    val vm: ShowcaseViewModel = viewModel(factory = ShowcaseViewModel.Factory(container.api))
                    ShowcaseScreen(vm)
                }
                composable(Section.Trips.route) {
                    // "i have selected a trip in the trip selector yet when
                    // you click the trip it still opens the [list] and i
                    // have to click the trip ... why not select the trip
                    // there" - same rule as Expenses/Bulk Orders/Trees/
                    // Loans & Finance already follow: a trip is already
                    // picked, so jump straight to it instead of making
                    // that a second click. Only falls back to the full
                    // list (Add Trip, pick any trip) when nothing is
                    // active yet - same as those other sections' pickers.
                    if (activeTripId != null) {
                        LaunchedEffect(activeTripId) {
                            navController.navigate("trip_detail/$activeTripId") {
                                popUpTo(Section.Trips.route) { inclusive = true }
                            }
                        }
                    } else {
                        val vm: TripsViewModel = viewModel(factory = TripsViewModel.Factory(container.api))
                        // Same stale-until-restart issue as Products - direct
                        // popBackStack() target of trip_form_create.
                        LaunchedEffect(Unit) { vm.refresh() }
                        TripsScreen(
                            vm,
                            onAddTrip = { navController.navigate("trip_form_create") },
                            onSelectTrip = { tripId -> navController.navigate("trip_detail/$tripId") }
                        )
                    }
                }
                composable("trip_form_create") {
                    val vm: TripFormViewModel = viewModel(factory = TripFormViewModel.Factory(container.api))
                    TripFormScreen(
                        vm,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() },
                        // Another trip is still open - go finalize it.
                        onOpenTrip = { openId ->
                            navController.popBackStack()
                            navController.navigate("trip_detail/$openId")
                        }
                    )
                }
                composable(
                    "trip_detail/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: TripDetailViewModel = viewModel(factory = TripDetailViewModel.Factory(container.api, tripId, session.userId))
                    TripDetailScreen(
                        vm,
                        onOpenHeavyDeliveries = { navController.navigate("bulk_orders/$tripId") },
                        onOpenLightDeliveries = { navController.navigate("light_deliveries/$tripId") },
                        onOpenPermits = { navController.navigate("expense_list/$tripId/permits") },
                        onOpenFinance = { navController.navigate("finance_overview/$tripId") }
                    )
                }
                composable(
                    "light_deliveries/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: LightDeliveriesViewModel = viewModel(factory = LightDeliveriesViewModel.Factory(container.api, tripId, session.userId))
                    LightDeliveriesScreen(vm, canShareDriver = session.roleName in setOf("Manager", "Admin", "SuperAdmin"))
                }
                composable(Section.BulkOrders.route) {
                    if (activeTripId != null) {
                        LaunchedEffect(activeTripId) {
                            navController.navigate("bulk_orders/$activeTripId") {
                                popUpTo(Section.BulkOrders.route) { inclusive = true }
                            }
                        }
                    } else {
                        val vm: TripPickerViewModel = viewModel(factory = TripPickerViewModel.Factory(container.api))
                        TripPickerScreen(vm, onSelectTrip = { tripId -> navController.navigate("bulk_orders/$tripId") })
                    }
                }
                composable(
                    "bulk_orders/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: BulkOrdersViewModel = viewModel(factory = BulkOrdersViewModel.Factory(container.api, tripId))
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of bulk_order_form_create.
                    LaunchedEffect(Unit) { vm.refresh() }
                    BulkOrdersScreen(
                        vm,
                        onAdd = { navController.navigate("bulk_order_form_create/$tripId") },
                        onSelect = { id -> navController.navigate("bulk_order_detail/$id") }
                    )
                }
                composable(
                    "bulk_order_form_create/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: BulkOrderFormViewModel = viewModel(
                        factory = BulkOrderFormViewModel.Factory(container.api, tripId, session.userId)
                    )
                    BulkOrderFormScreen(vm, onSaved = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
                }
                composable(Section.Trees.route) {
                    if (activeTripId != null) {
                        LaunchedEffect(activeTripId) {
                            navController.navigate("trees/$activeTripId") {
                                popUpTo(Section.Trees.route) { inclusive = true }
                            }
                        }
                    } else {
                        val vm: TripPickerViewModel = viewModel(factory = TripPickerViewModel.Factory(container.api))
                        TripPickerScreen(vm, onSelectTrip = { tripId -> navController.navigate("trees/$tripId") })
                    }
                }
                composable(
                    "trees/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: TreesViewModel = viewModel(factory = TreesViewModel.Factory(container.api, tripId))
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of tree_form_create/edit and
                    // remainder_form_create.
                    LaunchedEffect(Unit) { vm.refresh() }
                    TreesScreen(
                        vm,
                        isAdmin = session.roleName in ADMIN_ROLES,
                        onAdd = { navController.navigate("tree_form_create/$tripId") },
                        onSelect = { treesId -> navController.navigate("tree_form_edit/$tripId/$treesId") },
                        onFinalizeRemainders = { navController.navigate("remainder_form_create/$tripId") }
                    )
                }
                composable(
                    "tree_form_create/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: TreeFormViewModel = viewModel(
                        factory = TreeFormViewModel.Factory(container.api, tripId, session.userId, editingTreesId = null)
                    )
                    TreeFormScreen(
                        vm, isEditing = false,
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(
                    "remainder_form_create/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: RemainderTreeFormViewModel = viewModel(
                        factory = RemainderTreeFormViewModel.Factory(container.api, tripId)
                    )
                    RemainderTreeFormScreen(vm, onDone = { navController.popBackStack() })
                }
                composable(
                    "tree_form_edit/{tripId}/{treesId}",
                    arguments = listOf(
                        navArgument("tripId") { type = NavType.IntType },
                        navArgument("treesId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val treesId = backStackEntry.arguments?.getInt("treesId") ?: return@composable
                    val vm: TreeFormViewModel = viewModel(
                        factory = TreeFormViewModel.Factory(container.api, tripId, session.userId, editingTreesId = treesId)
                    )
                    TreeFormScreen(
                        vm, isEditing = true,
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable("deleted_trees") {
                    val vm: DeletedTreesViewModel = viewModel(
                        factory = DeletedTreesViewModel.Factory(container.api, session.userId)
                    )
                    DeletedTreesScreen(vm)
                }
                composable("shipping_areas") {
                    val vm: ShippingAreasViewModel = viewModel(factory = ShippingAreasViewModel.Factory(container.api))
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of shipping_area_form_create/edit.
                    LaunchedEffect(Unit) { vm.refresh() }
                    ShippingAreasScreen(
                        vm,
                        onAdd = { navController.navigate("shipping_area_form_create") },
                        onSelect = { areaId -> navController.navigate("shipping_area_form_edit/$areaId") }
                    )
                }
                composable("shipping_area_form_create") {
                    val vm: ShippingAreaFormViewModel = viewModel(
                        factory = ShippingAreaFormViewModel.Factory(container.api, editingAreaId = null)
                    )
                    ShippingAreaFormScreen(
                        vm,
                        isEditing = false,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(
                    "shipping_area_form_edit/{areaId}",
                    arguments = listOf(navArgument("areaId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val areaId = backStackEntry.arguments?.getInt("areaId") ?: return@composable
                    val vm: ShippingAreaFormViewModel = viewModel(
                        factory = ShippingAreaFormViewModel.Factory(container.api, editingAreaId = areaId)
                    )
                    ShippingAreaFormScreen(
                        vm,
                        isEditing = true,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(
                    "bulk_order_detail/{bulkDeliveryId}",
                    arguments = listOf(navArgument("bulkDeliveryId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getInt("bulkDeliveryId") ?: return@composable
                    val vm: BulkOrderDetailViewModel = viewModel(
                        factory = BulkOrderDetailViewModel.Factory(container.api, id, session.userId)
                    )
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of stk_push_amount.
                    LaunchedEffect(Unit) { vm.refresh() }
                    BulkOrderDetailScreen(
                        vm,
                        onDeleted = { navController.popBackStack() },
                        onSendStkPush = { amount ->
                            if (amount != null) navController.navigate("stk_push_amount/$amount") else navController.navigate("stk_push")
                        }
                    )
                }
                composable(
                    "stk_push_amount/{amount}",
                    arguments = listOf(navArgument("amount") { type = NavType.StringType })
                ) { backStackEntry ->
                    val amount = backStackEntry.arguments?.getString("amount")?.toDoubleOrNull()
                    val vm: StkPushViewModel = viewModel(
                        factory = StkPushViewModel.Factory(container.api, session.userId, amount)
                    )
                    StkPushScreen(vm, onDone = { navController.popBackStack() })
                }
                composable(Section.Expenses.route) {
                    if (activeTripId != null) {
                        LaunchedEffect(activeTripId) {
                            navController.navigate("expense_categories/$activeTripId") {
                                popUpTo(Section.Expenses.route) { inclusive = true }
                            }
                        }
                    } else {
                        val vm: TripPickerViewModel = viewModel(factory = TripPickerViewModel.Factory(container.api))
                        TripPickerScreen(vm, onSelectTrip = { tripId -> navController.navigate("expense_categories/$tripId") })
                    }
                }
                composable(
                    "expense_categories/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: ExpenseCategoriesViewModel = viewModel(
                        factory = ExpenseCategoriesViewModel.Factory(container.api, tripId)
                    )
                    ExpenseCategoriesScreen(
                        vm,
                        onSelectCategory = { category -> navController.navigate("expense_list/$tripId/${category.key}") }
                    )
                }
                composable(
                    "expense_list/{tripId}/{category}",
                    arguments = listOf(
                        navArgument("tripId") { type = NavType.IntType },
                        navArgument("category") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val categoryKey = backStackEntry.arguments?.getString("category") ?: return@composable
                    val category = ExpenseCategory.fromKey(categoryKey)
                    val vm: ExpenseListViewModel = viewModel(
                        factory = ExpenseListViewModel.Factory(container.api, tripId, category, session.userId)
                    )
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of expense_form_create and
                    // send_money.
                    LaunchedEffect(Unit) { vm.refresh() }
                    ExpenseListScreen(
                        vm,
                        category = category,
                        isAdmin = session.roleName in ADMIN_ROLES,
                        onAdd = { navController.navigate("expense_form_create/$tripId/$categoryKey") },
                        onPay = { rowId, amount -> navController.navigate("send_money/$categoryKey/$rowId/$amount") }
                    )
                }
                composable(
                    "expense_form_create/{tripId}/{category}",
                    arguments = listOf(
                        navArgument("tripId") { type = NavType.IntType },
                        navArgument("category") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val categoryKey = backStackEntry.arguments?.getString("category") ?: return@composable
                    val category = ExpenseCategory.fromKey(categoryKey)
                    val vm: ExpenseFormViewModel = viewModel(
                        factory = ExpenseFormViewModel.Factory(container.api, tripId, category, session.userId)
                    )
                    ExpenseFormScreen(
                        vm,
                        category = category,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(Section.Transactions.route) {
                    TransactionsHomeScreen(
                        isAdmin = session.roleName in ADMIN_ROLES,
                        canSyncSms = session.canSyncSms,
                        onStkPush = { navController.navigate("stk_push") },
                        // Reuses Expenses' own active-trip-or-picker entry
                        // point (Section.Expenses.route) rather than a
                        // separate flow - "send money for the expenses" is
                        // that same category-list-then-Pay screen, just
                        // reachable directly from Transactions too now.
                        onSendMoney = { navController.navigate(Section.Expenses.route) },
                        onAssign = { navController.navigate("assign_queue") },
                        onSmsSync = { navController.navigate("sms_sync") },
                        onViewAllUnassigned = { navController.navigate("ai_unassigned_transactions") },
                        onRefunds = { navController.navigate("refunds") }
                    )
                }
                composable("refunds") {
                    val vm: RefundsViewModel = viewModel(factory = RefundsViewModel.Factory(container.api))
                    LaunchedEffect(Unit) { vm.refresh() }
                    RefundsScreen(
                        vm,
                        onPay = { orderId, amount -> navController.navigate("send_money/order-refund/$orderId/$amount") }
                    )
                }
                composable("sms_sync") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val vm: SmsSyncViewModel = viewModel(
                        factory = SmsSyncViewModel.Factory(context, container.companySimStore)
                    )
                    SmsSyncScreen(vm)
                }
                composable("stk_push") {
                    val vm: StkPushViewModel = viewModel(factory = StkPushViewModel.Factory(container.api, session.userId))
                    StkPushScreen(vm, onDone = { navController.popBackStack() })
                }
                composable(
                    "send_money/{category}/{rowId}/{amount}",
                    arguments = listOf(
                        navArgument("category") { type = NavType.StringType },
                        navArgument("rowId") { type = NavType.IntType },
                        navArgument("amount") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val categoryKey = backStackEntry.arguments?.getString("category") ?: return@composable
                    val rowId = backStackEntry.arguments?.getInt("rowId") ?: return@composable
                    val amount = backStackEntry.arguments?.getString("amount")?.toDoubleOrNull()
                    val category = ExpenseCategory.fromKey(categoryKey)
                    val vm: SendMoneyViewModel = viewModel(
                        factory = SendMoneyViewModel.Factory(container.api, category.name, rowId, category.label, amount, session.userId)
                    )
                    SendMoneyScreen(vm, onDone = { navController.popBackStack() })
                }
                composable("assign_queue") {
                    val vm: AssignQueueViewModel = viewModel(factory = AssignQueueViewModel.Factory(container.api, session.userId))
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of assign_detail's onAssigned.
                    // Without this, a transaction you just assigned kept
                    // showing here as still unassigned.
                    LaunchedEffect(Unit) { vm.refresh() }
                    AssignQueueScreen(vm, onOpen = { row -> navController.navigate(assignDetailRoute(row)) })
                }
                composable(
                    "assign_detail/{financialTransactionId}?amount={amount}&createdAt={createdAt}&referenceId={referenceId}&paymentMode={paymentMode}",
                    arguments = listOf(
                        navArgument("financialTransactionId") { type = NavType.IntType },
                        navArgument("amount") { type = NavType.StringType; defaultValue = "" },
                        navArgument("createdAt") { type = NavType.StringType; defaultValue = "" },
                        navArgument("referenceId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("paymentMode") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getInt("financialTransactionId") ?: return@composable
                    val vm: AssignDetailViewModel = viewModel(
                        factory = AssignDetailViewModel.Factory(container.api, id, session.userId)
                    )
                    // "place the date of that transaction as it is in the
                    // database for quicker location ... when allocating" -
                    // carried straight from the list row that was tapped,
                    // no extra fetch needed just to show it here.
                    AssignDetailScreen(
                        vm,
                        transactionSummary = TransactionSummary(
                            financialTransactionId = id,
                            amount = backStackEntry.arguments?.getString("amount")?.takeIf { it.isNotBlank() },
                            createdAt = backStackEntry.arguments?.getString("createdAt")?.takeIf { it.isNotBlank() },
                            referenceId = backStackEntry.arguments?.getString("referenceId")?.takeIf { it.isNotBlank() },
                            paymentMode = backStackEntry.arguments?.getString("paymentMode")?.takeIf { it.isNotBlank() }
                        ),
                        onAssigned = { navController.popBackStack() }
                    )
                }
                composable(Section.Finance.route) {
                    if (activeTripId != null) {
                        LaunchedEffect(activeTripId) {
                            navController.navigate("finance_overview/$activeTripId") {
                                popUpTo(Section.Finance.route) { inclusive = true }
                            }
                        }
                    } else {
                        val vm: TripPickerViewModel = viewModel(factory = TripPickerViewModel.Factory(container.api))
                        TripPickerScreen(vm, onSelectTrip = { tripId -> navController.navigate("finance_overview/$tripId") })
                    }
                }
                composable(
                    "finance_overview/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: FinanceOverviewViewModel = viewModel(
                        factory = FinanceOverviewViewModel.Factory(container.api, tripId)
                    )
                    FinanceOverviewScreen(
                        vm,
                        onOpenLoans = { navController.navigate("loans_list/$tripId") },
                        onOpenCapital = { navController.navigate("capital/$tripId") }
                    )
                }
                composable(
                    "loans_list/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: LoansListViewModel = viewModel(factory = LoansListViewModel.Factory(container.api, tripId))
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of loan_form_create.
                    LaunchedEffect(Unit) { vm.refresh() }
                    LoansListScreen(
                        vm,
                        onAddLoan = { navController.navigate("loan_form_create/$tripId") },
                        onSelectLoan = { loanId, amount -> navController.navigate("loan_detail/$tripId/$loanId/$amount") }
                    )
                }
                composable(
                    "capital/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: CapitalViewModel = viewModel(factory = CapitalViewModel.Factory(container.api, tripId))
                    CapitalScreen(vm)
                }
                // "adding a section specifically for the ai feature" - the
                // insights layer (unassigned transactions, top farmer
                // suppliers, expense breakdown), same admin-only gate as
                // Loans & Finance.
                composable(Section.Ai.route) {
                    // The assistant is a floating button now; anything that
                    // still points at the old AI section lands on the chat.
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                        showChat = true
                    }
                }
                // Insights > Orders: pending orders per customer, assigned to a
                // light or bulk delivery - same job the desktop does.
                composable(Section.Orders.route) {
                    val vm: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory(container.api, session.userId))
                    // Direct popBackStack() target of orders_assign - reload on return.
                    LaunchedEffect(Unit) { vm.refresh() }
                    OrdersScreen(
                        vm,
                        onAssign = { customerId, areaId, kind -> navController.navigate("orders_assign/$customerId/$areaId/$kind") }
                    )
                }
                composable(
                    "orders_assign/{customerId}/{areaId}/{kind}",
                    arguments = listOf(
                        navArgument("customerId") { type = NavType.IntType },
                        navArgument("areaId") { type = NavType.IntType },
                        navArgument("kind") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getInt("customerId") ?: return@composable
                    val areaId = backStackEntry.arguments?.getInt("areaId") ?: return@composable
                    val kind = backStackEntry.arguments?.getString("kind") ?: return@composable
                    val vm: AssignGroupViewModel = viewModel(
                        factory = AssignGroupViewModel.Factory(container.api, session.userId, customerId, areaId, kind)
                    )
                    AssignGroupScreen(vm, onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
                }
                // Anything that still points at the old chat screen opens the card.
                composable("ai_chat") {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                        showChat = true
                    }
                }
                composable("ai_unassigned_transactions") {
                    val vm: UnassignedTransactionsViewModel = viewModel(factory = UnassignedTransactionsViewModel.Factory(container.api))
                    // Reuses the existing assign_detail screen (built for
                    // Transactions' own claim-queue flow) rather than a
                    // second copy of the same candidates/assign UI. Same
                    // stale-until-restart issue as Products - direct
                    // popBackStack() target of assign_detail's onAssigned.
                    LaunchedEffect(Unit) { vm.refresh() }
                    UnassignedTransactionsScreen(vm, onOpen = { row -> navController.navigate(assignDetailRoute(row)) })
                }
                composable("ai_top_farmers") {
                    val vm: TopFarmersViewModel = viewModel(factory = TopFarmersViewModel.Factory(container.api))
                    TopFarmersScreen(vm)
                }
                composable("ai_expense_breakdown") {
                    val vm: ExpenseBreakdownViewModel = viewModel(factory = ExpenseBreakdownViewModel.Factory(container.api))
                    ExpenseBreakdownScreen(vm)
                }
                composable(
                    "loan_detail/{tripId}/{loanId}/{loanAmount}",
                    arguments = listOf(
                        navArgument("tripId") { type = NavType.IntType },
                        navArgument("loanId") { type = NavType.IntType },
                        navArgument("loanAmount") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val loanId = backStackEntry.arguments?.getInt("loanId") ?: return@composable
                    val loanAmount = backStackEntry.arguments?.getString("loanAmount")?.toDoubleOrNull() ?: 0.0
                    val vm: LoanDetailViewModel = viewModel(
                        factory = LoanDetailViewModel.Factory(container.api, loanId, loanAmount)
                    )
                    // Same stale-until-restart issue as Products - direct
                    // popBackStack() target of pay_loan and loan_form_edit.
                    LaunchedEffect(Unit) { vm.refresh() }
                    LoanDetailScreen(
                        vm,
                        isAdmin = session.roleName in ADMIN_ROLES,
                        onPayLoan = { remaining -> navController.navigate("pay_loan/$loanId/$remaining") },
                        onEditLoan = { navController.navigate("loan_form_edit/$tripId/$loanId") }
                    )
                }
                composable(
                    "loan_form_create/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val vm: LoanFormViewModel = viewModel(
                        factory = LoanFormViewModel.Factory(container.api, tripId, session.userId, editingLoanId = null)
                    )
                    LoanFormScreen(vm, isEditing = false, onSaved = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
                }
                composable(
                    "loan_form_edit/{tripId}/{loanId}",
                    arguments = listOf(
                        navArgument("tripId") { type = NavType.IntType },
                        navArgument("loanId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val tripId = backStackEntry.arguments?.getInt("tripId") ?: return@composable
                    val loanId = backStackEntry.arguments?.getInt("loanId") ?: return@composable
                    val vm: LoanFormViewModel = viewModel(
                        factory = LoanFormViewModel.Factory(container.api, tripId, session.userId, editingLoanId = loanId)
                    )
                    LoanFormScreen(
                        vm,
                        isEditing = true,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(
                    "pay_loan/{loanId}/{remaining}",
                    arguments = listOf(
                        navArgument("loanId") { type = NavType.IntType },
                        navArgument("remaining") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val loanId = backStackEntry.arguments?.getInt("loanId") ?: return@composable
                    val remaining = backStackEntry.arguments?.getString("remaining")?.toDoubleOrNull() ?: 0.0
                    val vm: PayLoanViewModel = viewModel(
                        factory = PayLoanViewModel.Factory(container.api, loanId, remaining, session.userId)
                    )
                    PayLoanScreen(vm, onDone = { navController.popBackStack() })
                }
            }

            if (showChat) {
                ChatPanel(
                    viewModel = chatViewModel,
                    onClose = { showChat = false },
                    onNavigate = { route ->
                        showChat = false
                        navController.navigate(route)
                    }
                )
            }

            if (showTripDial) {
                TripDialPicker(
                    trips = trips,
                    currentTripId = activeTripId,
                    onSelect = { tripId -> tripDialViewModel.select(tripId) },
                    onDismiss = { showTripDial = false }
                )
            }
        }
    }
}
