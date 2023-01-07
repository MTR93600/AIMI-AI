package info.nightscout.sdk.networking

import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.sdk.remotemodel.NSResponse
import info.nightscout.sdk.remotemodel.RemoteCreateUpdateResponse
import info.nightscout.sdk.remotemodel.RemoteDeviceStatus
import info.nightscout.sdk.remotemodel.RemoteEntry
import info.nightscout.sdk.remotemodel.RemoteFood
import info.nightscout.sdk.remotemodel.RemoteStatusResponse
import info.nightscout.sdk.remotemodel.RemoteTreatment
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Created by adrian on 2019-12-23.
 *
 * https://github.com/nightscout/cgm-remote-monitor/blob/master/lib/api3/doc/tutorial.md
 *
 */

internal interface NightscoutRemoteService {

    @GET("v3/status")
    // used to get the raw response for more error checking. E.g. to give the user better feedback after new settings.
    suspend fun statusVerbose(): Response<NSResponse<RemoteStatusResponse>>

    @GET("v3/status")
    suspend fun statusSimple(): NSResponse<RemoteStatusResponse>

    @GET("v3/lastModified")
    suspend fun lastModified(): Response<NSResponse<LastModified>>

    @GET("v3/entries?sort\$desc=date&type=sgv")
    suspend fun getSgvs(): Response<NSResponse<List<RemoteEntry>>>

    @GET("v3/entries")
    suspend fun getSgvsNewerThan(@Query(value = "date\$gt", encoded = true) date: Long, @Query("limit") limit: Long): Response<NSResponse<List<RemoteEntry>>>

    @GET("v3/entries/history/{from}")
    suspend fun getSgvsModifiedSince(@Path("from") from: Long, @Query("limit") limit: Long): Response<NSResponse<List<RemoteEntry>>>

    @POST("v3/entries")
    suspend fun createEntry(@Body remoteEntry: RemoteEntry): Response<NSResponse<RemoteCreateUpdateResponse>>

    @PATCH("v3/entries/{identifier}")
    suspend fun updateEntry(@Body remoteEntry: RemoteEntry, @Path("identifier") identifier: String): Response<NSResponse<RemoteCreateUpdateResponse>>

    @GET("v3/treatments")
    suspend fun getTreatmentsNewerThan(@Query(value = "created_at\$gt", encoded = true) createdAt: String, @Query("limit") limit: Long): Response<NSResponse<List<RemoteTreatment>>>

    @GET("v3/treatments/history/{from}")
    suspend fun getTreatmentsModifiedSince(@Path("from") from: Long, @Query("limit") limit: Long): Response<NSResponse<List<RemoteTreatment>>>

    @POST("v3/treatments")
    suspend fun createTreatment(@Body remoteTreatment: RemoteTreatment): Response<NSResponse<RemoteCreateUpdateResponse>>

    @PATCH("v3/treatments/{identifier}")
    suspend fun updateTreatment(@Body remoteTreatment: RemoteTreatment, @Path("identifier") identifier: String): Response<NSResponse<RemoteCreateUpdateResponse>>

    @POST("v3/devicestatus")
    suspend fun createDeviceStatus(@Body remoteDeviceStatus: RemoteDeviceStatus): Response<NSResponse<RemoteCreateUpdateResponse>>

    @GET("v3/devicestatus/history/{from}")
    suspend fun getDeviceStatusModifiedSince(@Path("from") from: Long): Response<NSResponse<List<RemoteDeviceStatus>>>

    @GET("v3/food")
    suspend fun getFoods(@Query("limit") limit: Long): Response<NSResponse<List<RemoteFood>>>
/*
    @GET("v3/food/history/{from}")
    suspend fun getFoodsModifiedSince(@Path("from") from: Long, @Query("limit") limit: Long): Response<NSResponse<List<RemoteFood>>>
*/
    @POST("v3/food")
    suspend fun createFood(@Body remoteFood: RemoteFood): Response<NSResponse<RemoteCreateUpdateResponse>>

    @PATCH("v3/food")
    suspend fun updateFood(@Body remoteFood: RemoteFood): Response<NSResponse<RemoteCreateUpdateResponse>>

}
