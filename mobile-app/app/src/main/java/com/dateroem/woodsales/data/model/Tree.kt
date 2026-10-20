package com.dateroem.woodsales.data.model

// Who trees are registered against, and who FARMER_PAYMENTS pays.
data class Farmer(
    val farmerId: Int,
    val name: String?,
    val phoneNo: String?,
    val bankDetailsMarked: String?,
    val balance: Double?
)

data class FarmerRequest(
    val name: String,
    val phoneNo: String?,
    val bankDetailsMarked: String?,
    val balance: Double?
)

// A trip that ends with unused registered trees leaves one of these
// behind; registering a Tree for a *different* trip can then point at it
// (remainderTreesId) instead of being a brand-new registration.
// originTreesId is which specific Tree it actually came from - "linking a
// specific tree ... is better since one can use this to detect even the
// exact location of the remainder trees by using the coordinates of the
// originator tree", since leftovers from several households can end up
// physically consolidated at one storage household.
data class RemainderTree(
    val remainderTreesId: Int,
    val previousTripId: Int?,
    val originTreesId: Int?,
    val noTrees: Int?,
    val typeTrees: String?,
    val totalBodyFeet: Double?
)

data class RemainderTreeRequest(
    val previousTripId: Int?,
    val originTreesId: Int?,
    val noTrees: Int?,
    val typeTrees: String?,
    val totalBodyFeet: Double?
)

// "an area in products to select from trees already registered for a
// certain trip" - Product.treesId points at one of these.
data class Tree(
    val treesId: Int,
    val houseHold: String?,
    val coordinates: String?,
    val noTrees: Int?,
    val typeTrees: String?,
    val active: Boolean?,
    val remainderTreesId: Int?,
    val farmerId: Int?,
    val tripId: Int?
)

data class TreeRequest(
    val houseHold: String?,
    val coordinates: String?,
    val noTrees: Int?,
    val typeTrees: String?,
    val active: Boolean,
    val remainderTreesId: Int?,
    val farmerId: Int?,
    val tripId: Int
)
