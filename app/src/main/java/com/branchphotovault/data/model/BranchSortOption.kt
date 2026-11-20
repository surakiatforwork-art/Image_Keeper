package com.branchphotovault.data.model

enum class BranchSortOption(val label: String) {
    ACCOUNT("Account"),
    BRANCH_CODE("Branch Code"),
    SHOP_NAME("Shop Name"),
    OLDEST_FIRST("เวลา: เก่ากว่า → ล่าสุด"),
    NEWEST_FIRST("เวลา: ล่าสุด → เก่ากว่า")
}

