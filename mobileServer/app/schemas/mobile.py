from typing import Optional

from pydantic import BaseModel


class CreateProductRequest(BaseModel):
    # name is now required - business decision: the uploaded product photo
    # is named after it, so a product without a name would have nothing
    # meaningful to name its file after. (Previously optional, letting the
    # tree Type stand in alone - that's no longer allowed as of this change.)
    name: str
    sku: Optional[str] = None
    type: Optional[str] = None
    description: Optional[str] = None
    price: float
    stock: float
    unit: str = "pieces"  # "pieces" (count) or "board_feet" (volume)
    attributes: Optional[str] = None
    # Short marketer/admin-filled badge (e.g. "Most Ordered - Only 2
    # left") shown on the product card itself - separate from Campaigns.
    marketerNote: Optional[str] = None
    image: Optional[str] = None
    treesId: Optional[int] = None


class ShippingAreaRequest(BaseModel):
    # Delivery/pickup area management (mobile app, linked from Heavy
    # Orders) - isActive toggles an area out of the website's checkout
    # dropdown without deleting it.
    city: str
    areaName: str
    description: Optional[str] = None
    isActive: bool = True


class CreateCampaignRequest(BaseModel):
    name: str
    channel: Optional[str] = None
    startAt: Optional[str] = None
    endAt: Optional[str] = None
    status: Optional[str] = None
    # Relative media key from /uploads/campaigns (same scheme as
    # products.image) - a campaign carries at most one media item, either
    # a video or an image, never both; mediaType says which ("VIDEO" /
    # "IMAGE"). A future step could connect this to social platforms like
    # TikTok instead of self-hosted storage.
    media: Optional[str] = None
    mediaType: Optional[str] = None


class CampaignProductsRequest(BaseModel):
    # "Products in this campaign": every product the campaign promotes,
    # replacing what was linked before (an empty list removes them all).
    productIds: list[int]


class AssignTransactionRequest(BaseModel):
    target: str
    targetRowId: int


class AiChatMessageRequest(BaseModel):
    message: str


class CreateExpenseRequest(BaseModel):
    # Generic across the 8 mobile "create an expense holder" categories
    # (transport, communications, machine-expenses, accomodation,
    # other-expenses, permits, salaries, farmer-payments) - fields only one
    # or two categories use are simply ignored server-side by the rest.
    amount: float
    description: Optional[str] = None
    type: Optional[str] = None
    vehicleUniqueId: Optional[str] = None
    additionalId: Optional[str] = None
    machineId: Optional[int] = None
    serialNo: Optional[str] = None
    startDate: Optional[str] = None
    endDate: Optional[str] = None
    farmerId: Optional[int] = None
    # FARMER_PAYMENTS only, same as farmerId - which tree registration
    # this payment is for.
    treesId: Optional[int] = None
    # All categories except FARMER_PAYMENTS/PERMITS - who the expense was
    # actually for, distinct from who recorded it.
    forUserId: Optional[int] = None
    method: Optional[str] = None


class StkPushRequest(BaseModel):
    # STK pulls money FROM a customer - business owner correction: only for
    # a product payment (website) or a bulk-goods payment (any user can
    # send one here). Never pre-targets an expense category - that's
    # SendMoneyRequest's job, since expenses are paid OUT to a partner.
    amount: float
    phoneNumber: Optional[str] = None


class ConfirmStkPushRequest(BaseModel):
    # Stand-in for the real Safaricom Daraja STK callback until those
    # credentials exist.
    referenceId: str


class SendMoneyRequest(BaseModel):
    # "Send Money" (Daraja B2C) pays a specific already-recorded expense
    # holder out to a partner - business owner correction: "expenses are
    # payments done to the other partners ... rather a send money option".
    # target/targetRowId are required, unlike StkPushRequest.
    amount: float
    phoneNumber: Optional[str] = None
    target: str
    targetRowId: int


class ConfirmSendMoneyRequest(BaseModel):
    referenceId: str


class IngestSmsRequest(BaseModel):
    # One M-Pesa SMS off the company-SIM phone, parsed there - see
    # SmsQueueService on the backend for how it's reconciled.
    mpesaReferenceId: str
    amount: Optional[float] = None
    rawSmsText: Optional[str] = None
    transactionType: Optional[str] = None
    mpesaBalance: Optional[float] = None
    simSlot: Optional[int] = None
    receivedAt: Optional[str] = None


class PayLoanRequest(BaseModel):
    # "an option pay loan ... led to the transactions area and the option
    # loan repayment for the selected loan" - creates a fresh repayment for
    # this loan and sends it, admin-only.
    amount: float
    phoneNumber: Optional[str] = None


class CreateFarmerPaymentRequest(BaseModel):
    farmerId: int
    # "any tree created in this a certain trip will be linked to the
    # farmer then to the payment" - which tree registration this payment
    # is for, if a specific one.
    treesId: Optional[int] = None
    amount: float
    method: Optional[str] = None
    description: Optional[str] = None


class RepaymentRequest(BaseModel):
    amountPaid: float
    description: Optional[str] = None


class LoanRequest(BaseModel):
    amount: float
    description: Optional[str] = None
    category: Optional[str] = None


class UpdateLoanRequest(BaseModel):
    amount: float
    description: Optional[str] = None
    category: Optional[str] = None
    status: Optional[str] = None


class CreateBulkOrderRequest(BaseModel):
    # "the heavy delivery orders search and finalization ... i want to
    # allow the admin or staff to create a bulk order and its added into
    # the system and if it fails its just deleted" - opens a paired
    # ORDERS + BULK_DELIVERY record for a heavy-order negotiation.
    deliveredTo: Optional[str] = None
    tripId: int


class FinalizeBulkDeliveryRequest(BaseModel):
    # "once the bulk delivery is delivered the details are filled on
    # amount cash soft which is filled into the bulk delivery as well as
    # the bulk delivery description table".
    hardCashAmount: float = 0
    softCashAmount: float = 0
    productType: Optional[str] = None
    productDescription: Optional[str] = None
    productMeasurement: Optional[str] = None
    totalBodyFeet: Optional[float] = None
    productTotal: Optional[float] = None


class CreateTripRequest(BaseModel):
    # "this is the section where i want one to create a trip so that then
    # the rest of the sections are active since almost all are connected
    # to the trip factor".
    startDate: str
    productDescription: Optional[str] = None


class UpdateTripRequest(BaseModel):
    endDate: Optional[str] = None
    noOfDeliveries: Optional[int] = None
    totalBodyFeet: Optional[float] = None
    productDescription: Optional[str] = None


class CashSplitRequest(BaseModel):
    # "the hardcash part is written manually" - the trip-level split
    # (distinct from a single bulk delivery's own hard/soft breakdown).
    hardCash: float
    softCash: float


class FarmerRequest(BaseModel):
    name: str
    phoneNo: Optional[str] = None
    bankDetailsMarked: Optional[str] = None
    balance: Optional[float] = 0


class RemainderTreeRequest(BaseModel):
    # A trip that ends with unused registered trees leaves one of these
    # behind; registering a TREES item for a *different* trip can then
    # point at it instead of being a brand-new registration.
    previousTripId: Optional[int] = None
    # Which specific TREES row this remainder actually came from - "linking
    # a specific tree ... is better since one can use this to detect even
    # the exact location of the remainder trees by using the coordinates
    # of the originator tree", since leftovers from several households can
    # end up physically consolidated at one storage household.
    originTreesId: Optional[int] = None
    noTrees: Optional[int] = None
    typeTrees: Optional[str] = None
    totalBodyFeet: Optional[float] = None


class TreeRequest(BaseModel):
    # "an area in products to select from trees already registered for a
    # certain trip" - this is what that picker creates/edits.
    houseHold: Optional[str] = None
    coordinates: Optional[str] = None
    noTrees: Optional[int] = None
    typeTrees: Optional[str] = None
    active: bool = True
    remainderTreesId: Optional[int] = None
    farmerId: Optional[int] = None
    tripId: int
