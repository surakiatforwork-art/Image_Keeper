package com.branchphotovault.data.remote

import com.branchphotovault.util.AppConfig
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface AppsScriptService {

    @GET
    suspend fun health(
        @Url url: String,
        @Query("action") action: String = AppConfig.ACTION_HEALTH
    ): HealthResponse

    @GET
    suspend fun branches(
        @Url url: String,
        @Query("action") action: String = AppConfig.ACTION_BRANCHES
    ): BranchesResponse

    @FormUrlEncoded
    @POST
    suspend fun addBranch(
        @Url url: String,
        @Field("action") action: String = AppConfig.ACTION_ADD_BRANCH,
        @Field("account") account: String,
        @Field("branchCode") branchCode: String,
        @Field("shopName") shopName: String,
        @Field("route") route: Int?
    ): MutationResponse

    @FormUrlEncoded
    @POST
    suspend fun updateRoute(
        @Url url: String,
        @Field("action") action: String = AppConfig.ACTION_UPDATE_ROUTE,
        @Field("account") account: String,
        @Field("branchCode") branchCode: String,
        @Field("route") route: Int?,
        @Field("rowNumber") rowNumber: Int?
    ): MutationResponse
}

