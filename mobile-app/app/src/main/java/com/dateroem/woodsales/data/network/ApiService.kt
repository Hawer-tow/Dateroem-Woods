package com.dateroem.woodsales.data.network

import com.dateroem.woodsales.data.model.AiChatMessageRequest
import com.dateroem.woodsales.data.model.AiChatReplyResponse
import com.dateroem.woodsales.data.model.AssignTransactionRequest
import com.dateroem.woodsales.data.model.AuthResponse
import com.dateroem.woodsales.data.model.Campaign
import com.dateroem.woodsales.data.model.CampaignFormRequest
import com.dateroem.woodsales.data.model.CampaignMediaUploadResponse
import com.dateroem.woodsales.data.model.CampaignProductsRequest
import com.dateroem.woodsales.data.model.CashSplitRequest
import com.dateroem.woodsales.data.model.ConfirmSendMoneyRequest
import com.dateroem.woodsales.data.model.ConfirmStkPushRequest
import com.dateroem.woodsales.data.model.CreateBulkOrderRequest
import com.dateroem.woodsales.data.model.CreateExpenseRequest
import com.dateroem.woodsales.data.model.CreateTripRequest
import com.dateroem.woodsales.data.model.DeviceChallenge
import com.dateroem.woodsales.data.model.DropOrderRequest
import com.dateroem.woodsales.data.model.FinalizeBulkDeliveryRequest
import com.dateroem.woodsales.data.model.IngestSmsRequest
import com.dateroem.woodsales.data.model.AssignBatchRequest
import com.dateroem.woodsales.data.model.CreateLightDeliveryRequest
import com.dateroem.woodsales.data.model.FinalizeTripRequest
import com.dateroem.woodsales.data.model.DeliverLightDeliveryRequest
import com.dateroem.woodsales.data.model.LightDeliveryStatusRequest
import com.dateroem.woodsales.data.model.SettleRequest
import com.dateroem.woodsales.data.model.PasswordChangeRequest
import com.dateroem.woodsales.data.model.ProfileUpdateRequest
import com.dateroem.woodsales.data.model.ShareDriverRequest
import com.dateroem.woodsales.data.model.LoanRequest
import com.dateroem.woodsales.data.model.LoginRequest
import com.dateroem.woodsales.data.model.PayLoanRequest
import com.dateroem.woodsales.data.model.Product
import com.dateroem.woodsales.data.model.ProductFormRequest
import com.dateroem.woodsales.data.model.SendMoneyRequest
import com.dateroem.woodsales.data.model.ShippingArea
import com.dateroem.woodsales.data.model.ShippingAreaRequest
import com.dateroem.woodsales.data.model.ShowcaseOrderRequest
import com.dateroem.woodsales.data.model.ShowcasePicture
import com.dateroem.woodsales.data.model.ShowcasePictureRequest
import com.dateroem.woodsales.data.model.StkPushRequest
import com.dateroem.woodsales.data.model.UpdateLoanRequest
import com.dateroem.woodsales.data.model.Farmer
import com.dateroem.woodsales.data.model.FarmerRequest
import com.dateroem.woodsales.data.model.Machine
import com.dateroem.woodsales.data.model.UserOption
import com.dateroem.woodsales.data.model.Vehicle
import com.dateroem.woodsales.data.model.RemainderTree
import com.dateroem.woodsales.data.model.RemainderTreeRequest
import com.dateroem.woodsales.data.model.Tree
import com.dateroem.woodsales.data.model.TreeRequest
import com.dateroem.woodsales.data.model.UpdateTripRequest
import com.dateroem.woodsales.data.model.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// Response shapes for the operational sections (trips/expenses/finance/
// transactions/bulk-orders) are still evolving on the server side, so
// they're read as loose maps for now rather than locked-in data classes -
// enough to prove each screen's communication works, without churn every
// time a field is added.
typealias JsonMap = Map<String, Any?>

interface ApiService {

    // One-time value the phone signs before signing in (DeviceIdentity).
    @GET("auth/device/challenge")
    suspend fun deviceChallenge(): DeviceChallenge

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun me(): JsonMap

    // The signed-in account's colour theme ({"theme": "forest" | "dark" | "light"}).
    @PUT("auth/me/theme")
    suspend fun saveTheme(@Body body: Map<String, String>): JsonMap

    // The person's own profile (migration 039): name and email (the password
    // only when the email changes), password - answers a new {token}, every
    // other sign-in of the account ends - and the photo.
    // Home (24 October 2026): the business at a glance for Admins and the
    // SuperAdmin, and the company card - one copy on the server, which only
    // the SuperAdmin changes.
    // Worker details and documents (migration 040): the staff member's own,
    // from My profile. Only the SuperAdmin opens them afterwards.
    @GET("workers/me")
    suspend fun myWorkerDetails(): JsonMap

    @PUT("workers/me")
    suspend fun saveMyWorkerDetails(@Body body: Map<String, Map<String, String?>>): JsonMap

    @Multipart
    @POST("workers/me/documents/{typeKey}")
    suspend fun uploadWorkerDocument(@Path("typeKey") typeKey: String, @Part file: MultipartBody.Part): JsonMap

    @GET("insights/overview")
    suspend fun overview(): JsonMap

    @GET("settings/company")
    suspend fun companyCard(): JsonMap

    @PUT("settings/company")
    suspend fun saveCompanyCard(@Body body: Map<String, String>): JsonMap

    @PUT("auth/me/profile")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): JsonMap

    @POST("auth/me/password")
    suspend fun changePassword(@Body body: PasswordChangeRequest): JsonMap

    @Multipart
    @POST("auth/me/photo")
    suspend fun uploadProfilePhoto(@Part file: MultipartBody.Part): JsonMap

    @DELETE("auth/me/photo")
    suspend fun removeProfilePhoto(): JsonMap

    @GET("products")
    suspend fun listProducts(): List<Product>

    @GET("products/{productId}")
    suspend fun getProduct(@Path("productId") productId: Int): Product

    // x_actor_user_id is separate from the Authorization bearer token:
    // mobileServer's create/update still key audit logs off this header
    // (see backend's AuditLogs TODO on migrating that to the JWT subject).
    @POST("products")
    suspend fun createProduct(
        @Body request: ProductFormRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Product

    @PATCH("products/{productId}")
    suspend fun updateProduct(
        @Path("productId") productId: Int,
        @Body request: ProductFormRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Product

    // name becomes the stored file's basename server-side (slugified) -
    // "the product namepart of the product be used to name the image".
    @Multipart
    @POST("uploads/products")
    suspend fun uploadProductImage(
        @Part file: MultipartBody.Part,
        @Query("name") name: String
    ): UploadResponse

    @DELETE("products/{productId}")
    suspend fun deleteProduct(
        @Path("productId") productId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    // Admin-only in the UI - lets a soft-deleted product be brought back
    // instead of it just being gone.
    @GET("products/deleted")
    suspend fun listDeletedProducts(): List<Product>

    @POST("products/{productId}/restore")
    suspend fun restoreProduct(
        @Path("productId") productId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Product

    // Products that have run out but are still on sale - the admins'
    // "needs attention" list, where one can be retired.
    @GET("products/depleted")
    suspend fun listDepletedProducts(): List<Product>

    // Retire: hidden from the shop and no new orders; its stock and existing
    // orders are kept. Admin/SuperAdmin only (checked on the server).
    @POST("products/{productId}/retire")
    suspend fun retireProduct(
        @Path("productId") productId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Product

    @POST("products/{productId}/unretire")
    suspend fun unretireProduct(
        @Path("productId") productId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Product

    @GET("trips")
    suspend fun listTrips(): List<JsonMap>

    @GET("trips/{tripId}")
    suspend fun getTrip(@Path("tripId") tripId: Int): JsonMap

    // "this is the section where i want one to create a trip so that then
    // the rest of the sections are active since almost all are connected
    // to the trip factor".
    @POST("trips")
    suspend fun createTrip(@Body request: CreateTripRequest): JsonMap

    @PATCH("trips/{tripId}")
    suspend fun updateTrip(@Path("tripId") tripId: Int, @Body request: UpdateTripRequest)

    // "how total amount paid in bulk delivery + product payment amount ...
    // the total amount in trip is calculated".
    @POST("trips/{tripId}/total-payment/recalculate")
    suspend fun recalculateTotalPayment(@Path("tripId") tripId: Int): JsonMap

    // "the hardcash part is written manually".
    @PUT("trips/{tripId}/cash-split")
    suspend fun setCashSplit(@Path("tripId") tripId: Int, @Body request: CashSplitRequest)

    // "you can never create a new trip before you finalize every detail in
    // the other trip" - the one trip not yet finalized (openTrip is null
    // when there is none), what's still open on a trip, and closing it.
    @GET("trips/open")
    suspend fun openTrip(): JsonMap

    @GET("trips/{tripId}/finalization")
    suspend fun tripFinalization(@Path("tripId") tripId: Int): JsonMap

    @POST("trips/{tripId}/finalize")
    suspend fun finalizeTrip(
        @Path("tripId") tripId: Int,
        @Body request: FinalizeTripRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    @GET("trips/{tripId}/summary")
    suspend fun tripSummary(@Path("tripId") tripId: Int): JsonMap

    @GET("trips/{tripId}/deliveries")
    suspend fun tripDeliveries(@Path("tripId") tripId: Int): JsonMap

    @GET("trips/{tripId}/expenses")
    suspend fun expenseCategoryTotals(@Path("tripId") tripId: Int): JsonMap

    // assigned: "all" | "assigned" | "pending" - business rules: "2
    // sections ... toggle to the all or assigned ones".
    @GET("trips/{tripId}/expenses/{category}")
    suspend fun expenseLineItems(
        @Path("tripId") tripId: Int,
        @Path("category") category: String,
        @Query("assigned") assigned: String = "all"
    ): List<JsonMap>

    // Creates an unvalidated holder - business rules: "creates a holder
    // since this is stored in the system but not yet validated and then
    // later it will be validated by admins".
    @POST("trips/{tripId}/expenses/{category}")
    suspend fun createExpense(
        @Path("tripId") tripId: Int,
        @Path("category") category: String,
        @Body request: CreateExpenseRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    // Only your own not-yet-assigned holder can be deleted - the server
    // blocks it once a real payment has already been matched to it.
    @DELETE("trips/{tripId}/expenses/{category}/{rowId}")
    suspend fun deleteExpense(
        @Path("tripId") tripId: Int,
        @Path("category") category: String,
        @Path("rowId") rowId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    @GET("trips/{tripId}/finance/loans")
    suspend fun tripLoans(@Path("tripId") tripId: Int): List<JsonMap>

    // "allow one to access loans ... or even edit and add a new loan" -
    // admin-only, enforced server-side.
    @POST("trips/{tripId}/finance/loans")
    suspend fun createLoan(
        @Path("tripId") tripId: Int,
        @Body request: LoanRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    @PATCH("loans/{loanId}")
    suspend fun updateLoan(
        @Path("loanId") loanId: Int,
        @Body request: UpdateLoanRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    @GET("trips/{tripId}/finance/capital")
    suspend fun tripCapital(@Path("tripId") tripId: Int): List<JsonMap>

    @GET("bulk-orders")
    suspend fun listBulkOrders(@Query("trip_id") tripId: Int? = null): List<JsonMap>

    @GET("bulk-orders/{bulkDeliveryId}")
    suspend fun getBulkOrder(@Path("bulkDeliveryId") bulkDeliveryId: Int): JsonMap

    @GET("bulk-orders/{bulkDeliveryId}/production")
    suspend fun bulkOrderProduction(@Path("bulkDeliveryId") bulkDeliveryId: Int): List<JsonMap>

    // "i want to allow the admin or staff to create a bulk order and its
    // added into the system and if it fails its just deleted".
    @POST("bulk-orders")
    suspend fun createBulkOrder(
        @Body request: CreateBulkOrderRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    @DELETE("bulk-orders/{bulkDeliveryId}")
    suspend fun deleteBulkOrder(
        @Path("bulkDeliveryId") bulkDeliveryId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    // "once the bulk delivery is delivered the details are filled on
    // amount cash soft ... as well as the bulk delivery description table".
    @PATCH("bulk-orders/{bulkDeliveryId}/finalize")
    suspend fun finalizeBulkOrder(
        @Path("bulkDeliveryId") bulkDeliveryId: Int,
        @Body request: FinalizeBulkDeliveryRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    @GET("deliveries/light")
    suspend fun listLightDeliveries(@Query("trip_id") tripId: Int? = null): List<JsonMap>

    // Orders section: pending orders gathered per customer and delivery
    // area (with the LIGHT / BULK / COMBINED verdict and any batch the
    // customer already has), assigned to a delivery in one go.
    @GET("deliveries/light/pending-groups")
    suspend fun pendingOrderGroups(): List<JsonMap>

    // "allow a user to drop the product if it has not yet been assigned a
    // trip which will allow one to get refunded the money paid for that
    // product but a cost is reduced from the initially sent by the
    // admins who can only do this" - no actor header: the backend checks
    // the real signed-in JWT (RetrofitClient's AuthInterceptor already
    // attaches it to every call), same as login itself.
    @POST("orders/{orderId}/drop")
    suspend fun dropOrder(@Path("orderId") orderId: Int, @Body request: DropOrderRequest): JsonMap

    // "where exactly do i go ... still cannot see it" - dropped orders
    // still owing their customer a refund, feeding the Refunds list ->
    // Send Money (ORDER_REFUND) flow.
    @GET("orders/refunds/pending")
    suspend fun pendingRefunds(): List<JsonMap>

    @POST("deliveries/light/assign-batch")
    suspend fun assignOrdersBatch(
        @Body request: AssignBatchRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    @POST("deliveries/light/settle")
    suspend fun settleLightOrders(
        @Body request: SettleRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    @POST("deliveries/light")
    suspend fun createLightDelivery(
        @Body request: CreateLightDeliveryRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    // "vehicle visible only to staff ... unless enabled" (migration 038).
    @PATCH("deliveries/light/{lightDeliveryId}/share-driver")
    suspend fun setShareDriver(
        @Path("lightDeliveryId") lightDeliveryId: Int,
        @Body request: ShareDriverRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    @PATCH("deliveries/light/{lightDeliveryId}/status")
    suspend fun updateLightDeliveryStatus(
        @Path("lightDeliveryId") lightDeliveryId: Int,
        @Body request: LightDeliveryStatusRequest
    )

    // "add a signature area which is stored in the signature blob for
    // the said products for the person to be added to the database" -
    // both fields optional, so a delivery can still be closed out without
    // one (matches updateLightDeliveryStatus's old behaviour).
    @PATCH("deliveries/light/{lightDeliveryId}/deliver")
    suspend fun deliverLightDelivery(
        @Path("lightDeliveryId") lightDeliveryId: Int,
        @Body request: DeliverLightDeliveryRequest
    )

    // includeRetired: the Marketing list shows retired campaigns too
    // (marked, with Bring back); Home leaves it off.
    @GET("marketing/campaigns")
    suspend fun listCampaigns(@Query("includeRetired") includeRetired: Boolean? = null): List<Campaign>

    // Retire: off the website, whatever its status and dates; its media,
    // products and dates are kept. Marketer/Admin/SuperAdmin (checked on
    // the server).
    @POST("marketing/campaigns/{campaignId}/retire")
    suspend fun retireCampaign(
        @Path("campaignId") campaignId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Campaign

    @POST("marketing/campaigns/{campaignId}/unretire")
    suspend fun unretireCampaign(
        @Path("campaignId") campaignId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Campaign

    // Admin-only in the UI, same "bin" pattern as deleted products.
    @GET("marketing/campaigns/deleted")
    suspend fun listDeletedCampaigns(): List<Campaign>

    @GET("marketing/campaigns/{campaignId}")
    suspend fun getCampaign(@Path("campaignId") campaignId: Int): Campaign

    @POST("marketing/campaigns")
    suspend fun createCampaign(
        @Body request: CampaignFormRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Campaign

    @PATCH("marketing/campaigns/{campaignId}")
    suspend fun updateCampaign(
        @Path("campaignId") campaignId: Int,
        @Body request: CampaignFormRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Campaign

    // The ids of the products a campaign promotes (ticked in its form).
    @GET("marketing/campaigns/{campaignId}/products")
    suspend fun campaignProductIds(@Path("campaignId") campaignId: Int): List<Int>

    // "Products in this campaign" - the whole chosen set at once.
    @PUT("marketing/campaigns/{campaignId}/products")
    suspend fun setCampaignProducts(
        @Path("campaignId") campaignId: Int,
        @Body request: CampaignProductsRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): List<Int>

    @DELETE("marketing/campaigns/{campaignId}")
    suspend fun deleteCampaign(
        @Path("campaignId") campaignId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    @POST("marketing/campaigns/{campaignId}/restore")
    suspend fun restoreCampaign(
        @Path("campaignId") campaignId: Int,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): Campaign

    // One upload slot per campaign, either a video or an image - the
    // server detects which from what was actually sent and reports it
    // back in mediaType, rather than the client having to declare it
    // upfront. Named after the campaign, same scheme as product photos.
    @Multipart
    @POST("uploads/campaigns")
    suspend fun uploadCampaignMedia(
        @Part file: MultipartBody.Part,
        @Query("name") name: String
    ): CampaignMediaUploadResponse

    // The website's Bulk Orders slideshow - Marketer, Admin and SuperAdmin
    // only (the server checks). The photo is uploaded first, then added
    // with the key that comes back.
    @GET("showcase")
    suspend fun listShowcase(): List<ShowcasePicture>

    @Multipart
    @POST("uploads/showcase")
    suspend fun uploadShowcasePicture(
        @Part file: MultipartBody.Part,
        @Query("name") name: String
    ): UploadResponse

    @POST("showcase")
    suspend fun addShowcasePicture(@Body request: ShowcasePictureRequest): ShowcasePicture

    @PATCH("showcase/{showcaseId}")
    suspend fun updateShowcaseCaption(
        @Path("showcaseId") showcaseId: Int,
        @Body request: ShowcasePictureRequest
    ): ShowcasePicture

    @PUT("showcase/order")
    suspend fun reorderShowcase(@Body request: ShowcaseOrderRequest): List<ShowcasePicture>

    @DELETE("showcase/{showcaseId}")
    suspend fun removeShowcasePicture(@Path("showcaseId") showcaseId: Int)

    // includeInactive=true so the management screen can find and
    // re-enable an area that's currently switched off - the website's
    // checkout dropdown never sees those (fetched separately, server-side).
    @GET("shipping/areas")
    suspend fun listShippingAreas(@Query("include_inactive") includeInactive: Boolean = true): List<ShippingArea>

    @GET("shipping/areas/{areaId}")
    suspend fun getShippingArea(@Path("areaId") areaId: Int): ShippingArea

    @POST("shipping/areas")
    suspend fun createShippingArea(@Body request: ShippingAreaRequest): ShippingArea

    @PATCH("shipping/areas/{areaId}")
    suspend fun updateShippingArea(
        @Path("areaId") areaId: Int,
        @Body request: ShippingAreaRequest
    ): ShippingArea

    // Admin-only screen: claims a batch of unassigned FINANCIAL_TRANSACTION
    // rows so concurrent admins split the queue instead of colliding
    // (business rules: "reduce one's load to 7 for each").
    @POST("transactions/claim")
    suspend fun claimTransactions(
        @Query("batch_size") batchSize: Int = 7,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): List<Int>

    @GET("transactions/claimed")
    suspend fun claimedTransactions(@Header("X-Actor-User-Id") actorUserId: Int): List<JsonMap>

    @POST("transactions/{financialTransactionId}/release")
    suspend fun releaseTransaction(@Path("financialTransactionId") financialTransactionId: Int)

    // "you ask any unassigned transactions? it will provide them" - the
    // AI/Insights section's plain read-only browse, distinct from
    // claimTransactions()/claimedTransactions() above (which mutate a
    // per-admin working queue). Tapping a row reuses the existing
    // assign_detail screen (transactionCandidates/assignTransaction below).
    @GET("transactions/unassigned")
    suspend fun unassignedTransactions(): List<JsonMap>

    // Ranked suggestion list for the assign screen - business rules: "the
    // system checks for a record of 20 ksh in the holders ... the one that
    // almost matches" (closest recorded time, same amount).
    @GET("transactions/{financialTransactionId}/candidates")
    suspend fun transactionCandidates(@Path("financialTransactionId") financialTransactionId: Int): List<JsonMap>

    @POST("transactions/{financialTransactionId}/assign")
    suspend fun assignTransaction(
        @Path("financialTransactionId") financialTransactionId: Int,
        @Body request: AssignTransactionRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    )

    // "i want the system to check the messages compare at times" - one
    // M-Pesa SMS off the company-SIM phone, posted as it arrives (or on a
    // catch-up resync). No actor header - a background listener, not a
    // signed-in action.
    @POST("sms-queue")
    suspend fun ingestSms(@Body request: IngestSmsRequest): JsonMap

    // STK pulls money FROM a customer - open to any signed-in user
    // (business rules: "this should also work for any user since it is
    // mostly for bulk goods"). No pre-targeting - see SendMoneyRequest for
    // paying an expense out.
    @POST("transactions/stk-push")
    suspend fun stkPush(
        @Body request: StkPushRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    // Stand-in for the real Safaricom Daraja callback until those
    // credentials exist - whoever sent the prompt confirms it went through.
    @POST("transactions/{financialTransactionId}/stk-push/confirm")
    suspend fun confirmStkPush(
        @Path("financialTransactionId") financialTransactionId: Int,
        @Body request: ConfirmStkPushRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    // "Send Money" pays a specific already-recorded expense holder out to
    // a partner - admin-only, enforced server-side (business owner
    // correction: expenses aren't STK, they're a payout).
    @POST("transactions/send-money")
    suspend fun sendMoney(
        @Body request: SendMoneyRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    // "one can only send money the rest is filled" - who an entry pays out to
    // (name, phone, amount), worked out from the entry itself.
    @GET("transactions/send-money/recipient")
    suspend fun sendMoneyRecipient(
        @Query("target") target: String,
        @Query("row_id") rowId: Int
    ): JsonMap

    // "updating the number in the payment ... should also show the
    // change [in] the name" - resolves a typed phone number to whoever's
    // saved under it, if anyone. A miss is a real 404, not an empty body.
    @GET("users/lookup-by-phone")
    suspend fun lookupUserByPhone(@Query("phone") phone: String): JsonMap

    // "connect major expense to a user not just who created it ... who
    // its meant for" - source list for the "For User" picker.
    @GET("users/picker")
    suspend fun listUsersForPicker(): List<UserOption>

    // PENDING / COMPLETED (with the M-Pesa receipt) / FAILED (with the reason):
    // polled after a payout so the receipt never has to be typed in.
    @GET("transactions/{financialTransactionId}/status")
    suspend fun paymentStatus(@Path("financialTransactionId") financialTransactionId: Int): JsonMap

    @POST("transactions/{financialTransactionId}/send-money/confirm")
    suspend fun confirmSendMoney(
        @Path("financialTransactionId") financialTransactionId: Int,
        @Body request: ConfirmSendMoneyRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    // "for each loan there will be an option to click it and an option pay
    // loan in it" - creates a fresh repayment for this loan and sends it,
    // admin-only.
    @POST("loans/{loanId}/repayments/pay")
    suspend fun payLoan(
        @Path("loanId") loanId: Int,
        @Body request: PayLoanRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): JsonMap

    @GET("loans/{loanId}/repayments")
    suspend fun loanRepayments(@Path("loanId") loanId: Int): List<JsonMap>

    // "an area in products to select from trees already registered for a
    // certain trip" - tripId is always the app's current active trip.
    @GET("trees")
    suspend fun listTrees(@Query("tripId") tripId: Int): List<Tree>

    @POST("trees")
    suspend fun createTree(@Body request: TreeRequest): Tree

    // Full-object update - including reassigning remainderTreesId, which
    // is how "pick other remainder tree to a tree item in another trip"
    // happens: point this Tree's remainderTreesId at a different
    // RemainderTree (or null to detach it).
    @PATCH("trees/{treesId}")
    suspend fun updateTree(@Path("treesId") treesId: Int, @Body request: TreeRequest): Tree

    // "delete trees should also be created and add a bin just like the
    // product section does it".
    @GET("trees/deleted")
    suspend fun listDeletedTrees(): List<Tree>

    @DELETE("trees/{treesId}")
    suspend fun deleteTree(@Path("treesId") treesId: Int, @Header("X-Actor-User-Id") actorUserId: Int)

    @POST("trees/{treesId}/restore")
    suspend fun restoreTree(@Path("treesId") treesId: Int, @Header("X-Actor-User-Id") actorUserId: Int): Tree

    @GET("farmers")
    suspend fun listFarmers(): List<Farmer>

    // "machine should be selected from the machine table" / "select the
    // vehicle one used" - list-only, "adding of a new machine or vehicle
    // is only allowed in the desktop".
    @GET("machines")
    suspend fun listMachines(): List<Machine>

    @GET("vehicles")
    suspend fun listVehicles(): List<Vehicle>

    // "check a certain farmer and provide info that this farmer tops on
    // the list as one of the farmers whom you bought most trees from" -
    // AI/Insights section, ranked by total trees supplied across every
    // trip.
    @GET("farmers/insights/top-suppliers")
    suspend fun topFarmerSuppliers(): List<JsonMap>

    // "for the expense it can scan for the expense provide information
    // on the most expensive expense and observe even from other trips" -
    // AI/Insights section, one row per (trip, category), ranked highest
    // first.
    @GET("insights/expenses/breakdown")
    suspend fun expenseBreakdown(): List<JsonMap>

    @POST("farmers")
    suspend fun createFarmer(@Body request: FarmerRequest): Farmer

    // "finalizing trees can be continuous" - previousTripId scopes the
    // list to what's already been recorded while finalizing *this* trip.
    @GET("remainder-trees")
    suspend fun listRemainderTrees(@Query("previousTripId") previousTripId: Int? = null): List<RemainderTree>

    @POST("remainder-trees")
    suspend fun createRemainderTree(@Body request: RemainderTreeRequest): RemainderTree

    // "implement the chat bot right below that in the same section" -
    // role-based Q&A with navigation shortcuts, admin/SuperAdmin only in
    // the UI (same gate as the rest of the AI section).
    @GET("ai/chat/history")
    suspend fun aiChatHistory(@Header("X-Actor-User-Id") actorUserId: Int): List<JsonMap>

    @POST("ai/chat")
    suspend fun sendAiChatMessage(
        @Body request: AiChatMessageRequest,
        @Header("X-Actor-User-Id") actorUserId: Int
    ): AiChatReplyResponse
}
