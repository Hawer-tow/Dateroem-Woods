from typing import Optional, Literal

from pydantic import BaseModel


class ComputerProof(BaseModel):
    # Which computer the desktop app is on (see the backend's
    # ComputerService): its public key, a one-time challenge and that
    # challenge signed by the key that never leaves Windows' key store.
    publicKey: str
    challenge: str
    signature: str
    model: str | None = None
    osVersion: str | None = None
    appVersion: str | None = None


class FaqRequest(BaseModel):
    # One question on the website's Help page (see the backend's FaqService).
    topic: str
    question: str
    answer: str
    linkPath: Optional[str] = None
    sortOrder: Optional[int] = None
    published: Optional[bool] = None


class NoticeRequest(BaseModel):
    # One notice in the website's Updates (see the backend's NoticeService):
    # showUntil is the last day it shows ("2026-11-30").
    title: str
    message: str
    showUntil: Optional[str] = None
    published: Optional[bool] = None


class LoginRequest(BaseModel):
    user_name: str
    password: str
    computer: ComputerProof | None = None


class SetUserStatusRequest(BaseModel):
    active: bool


class UpdateTripRequest(BaseModel):
    endDate: Optional[str] = None
    noOfDeliveries: Optional[int] = None
    totalBodyFeet: Optional[float] = None
    productDescription: Optional[str] = None


class CreateTripRequest(BaseModel):
    startDate: str
    productDescription: Optional[str] = None


class CreateUserRequest(BaseModel):
    userName: str
    name: str
    password: str
    email: Optional[str] = None
    phoneNo: Optional[str] = None
    roleId: int


class UpdateUserRequest(BaseModel):
    name: Optional[str] = None
    email: Optional[str] = None
    phoneNo: Optional[str] = None
    roleId: Optional[int] = None
    # "Company phone (can sync M-Pesa SMS)" in Users > Edit - it was missing
    # here, so the tick was dropped before it reached the backend.
    canSyncSms: Optional[bool] = None
    # Sent only by the SuperAdmin's edit form (the backend refuses anyone
    # else who tries to change it).
    userName: Optional[str] = None


class ProductRequest(BaseModel):
    name: str
    sku: Optional[str] = None
    type: Optional[str] = None
    description: Optional[str] = None
    price: float
    stock: float
    unit: str = "pieces"
    attributes: Optional[str] = None
    marketerNote: Optional[str] = None
    image: Optional[str] = None
    treesId: Optional[int] = None


class CampaignRequest(BaseModel):
    name: str
    channel: Optional[str] = None
    startAt: Optional[str] = None
    endAt: Optional[str] = None
    status: Optional[str] = None
    media: Optional[str] = None
    mediaType: Optional[str] = None


class CampaignProductsRequest(BaseModel):
    # "Products in this campaign": every product the campaign promotes,
    # replacing what was linked before (an empty list removes them all).
    productIds: list[int]


class ShippingAreaRequest(BaseModel):
    city: str
    areaName: str
    description: Optional[str] = None
    coordinates: Optional[str] = None
    isActive: bool = True


class RepaymentRequest(BaseModel):
    amountPaid: float
    description: Optional[str] = None


class PayLoanRequest(BaseModel):
    amount: float
    phoneNumber: Optional[str] = None


class LoanRequest(BaseModel):
    amount: float
    description: Optional[str] = None
    category: Optional[str] = None


class UpdateLoanRequest(BaseModel):
    amount: float
    description: Optional[str] = None
    category: Optional[str] = None
    status: Optional[str] = None


class CapitalRequest(BaseModel):
    amount: float
    description: Optional[str] = None
    category: Optional[str] = None


class CashSplitRequest(BaseModel):
    hardCash: float
    softCash: float


class LightDeliveryStatusRequest(BaseModel):
    status: str


# "the admin assigns a delivery e.g to a light delivery or a bulk if
# it's heading to the same direction and there is space" - links a
# pending order's delivery to a real trip/vehicle with a concrete date.
class AssignLightDeliveryRequest(BaseModel):
    tripId: int | None = None
    vehicleId: int | None = None
    estimatedDeliveryDate: str | None = None
    # "a light delivery can be fixed to a bulk delivery as a side quest" /
    # grouped under another light delivery - at most one of these.
    bulkDeliveryId: int | None = None
    parentLightDeliveryId: int | None = None
    # "vehicle visible only to staff" (migration 038): may the customer see
    # the driver's first name and the number plate? Off unless ticked.
    shareDriver: bool | None = None


class CreateLightDeliveryRequest(BaseModel):
    tripId: int
    vehicleId: int | None = None
    deliveryAreaId: int | None = None
    estimatedDeliveryDate: str | None = None
    # "vehicle visible only to staff" (migration 038): may the customer see
    # the driver's first name and the number plate? Off unless ticked.
    shareDriver: bool | None = None


class AssignLightDeliveriesBatchRequest(BaseModel):
    lightDeliveryIds: list[int]
    tripId: int | None = None
    vehicleId: int | None = None
    estimatedDeliveryDate: str | None = None
    bulkDeliveryId: int | None = None
    parentLightDeliveryId: int | None = None
    # "vehicle visible only to staff" (migration 038): may the customer see
    # the driver's first name and the number plate? Off unless ticked.
    shareDriver: bool | None = None


class ShareDriverRequest(BaseModel):
    share: bool


class FinalizeTripRequest(BaseModel):
    dropUndelivered: bool = False


class SettleLightOrdersRequest(BaseModel):
    deliveredIds: list[int] = []
    undeliveredIds: list[int] = []
    reason: str | None = None


class CreateBulkDeliveryRequest(BaseModel):
    deliveredTo: str
    tripId: int


class AssignTransactionRequest(BaseModel):
    target: str
    targetRowId: int


class ProductionDescriptionRequest(BaseModel):
    productType: Optional[str] = None
    productDescription: Optional[str] = None
    productMeasurement: Optional[str] = None
    totalBodyFeet: Optional[float] = None
    productTotal: Optional[float] = None
    # "add a photo instead of only scan with AI" - a scaled-down JPEG data URL.
    photo: Optional[str] = None


class FinalizeBulkDeliveryRequest(BaseModel):
    # Re-postable: an admin correcting a mistyped amount sends this again
    # with the same fields (see DeliveryService.finalizeBulkDelivery - it
    # overwrites the cash split rather than failing on an already-delivered
    # order). productType left blank means "just the payment, no new line
    # item" - the desktop's "Edit Payment" dialog does exactly that.
    hardCashAmount: Optional[float] = None
    softCashAmount: Optional[float] = None
    productType: Optional[str] = None
    productDescription: Optional[str] = None
    productMeasurement: Optional[str] = None
    totalBodyFeet: Optional[float] = None
    productTotal: Optional[float] = None


class ScanImageRequest(BaseModel):
    imageDataUrl: str


class FarmerRequest(BaseModel):
    name: str
    phoneNo: Optional[str] = None
    bankDetailsMarked: Optional[str] = None
    balance: Optional[float] = 0


# "adding of a new machine or vehicle is only allowed in the desktop" -
# these two are only ever posted from here, never from mobile.
class MachineRequest(BaseModel):
    name: str
    serialNo: Optional[str] = None
    description: Optional[str] = None


class VehicleRequest(BaseModel):
    uniqueIdentifier: str
    driver: Optional[str] = None
    phoneNumber: Optional[str] = None
    description: Optional[str] = None


class RemainderTreeRequest(BaseModel):
    previousTripId: Optional[int] = None
    originTreesId: Optional[int] = None
    noTrees: Optional[int] = None
    typeTrees: Optional[str] = None
    totalBodyFeet: Optional[float] = None


class TreeRequest(BaseModel):
    houseHold: Optional[str] = None
    coordinates: Optional[str] = None
    noTrees: Optional[int] = None
    typeTrees: Optional[str] = None
    active: bool = True
    remainderTreesId: Optional[int] = None
    farmerId: Optional[int] = None
    tripId: int


# One shape for all eight expense categories - fields a category doesn't use
# are ignored by the backend (same as the mobile app's).
class CreateExpenseRequest(BaseModel):
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
    method: Optional[str] = None


class SendMoneyRequest(BaseModel):
    amount: float
    phoneNumber: Optional[str] = None
    target: str
    targetRowId: int


# "i think its also fine time we add stk push in the desktop" - same shape
# as the mobile app's own StkPushRequest/ConfirmStkPushRequest.
class StkPushRequest(BaseModel):
    amount: float
    phoneNumber: Optional[str] = None


class ConfirmStkPushRequest(BaseModel):
    referenceId: str


# "add the drop option ... for a particular order for a customer" -
# mirrors the mobile app's DropOrderRequest.
class DropOrderRequest(BaseModel):
    cancellationCost: float


class ThemeRequest(BaseModel):
    # The colour themes an account can choose (USERS.Theme, migration 037).
    theme: Literal["forest", "dark", "light"]
