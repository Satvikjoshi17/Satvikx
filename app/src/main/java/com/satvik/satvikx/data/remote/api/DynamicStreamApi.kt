package com.satvik.satvikx.data.remote.api

import com.satvik.satvikx.data.remote.model.InvidiousSearchResultItem
import com.satvik.satvikx.data.remote.model.InvidiousVideoResponse
import com.satvik.satvikx.data.remote.model.PipedSearchWrapper
import com.satvik.satvikx.data.remote.model.PipedStreamResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

/**
 * Universal dynamic Retrofit API interface enabling runtime failover across decentralized endpoints.
 */
interface DynamicStreamApi {

    @Headers("User-Agent: Mozilla/5.0 (Android; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
    @GET
    suspend fun getPipedSearch(@Url url: String): Response<PipedSearchWrapper>

    @Headers("User-Agent: Mozilla/5.0 (Android; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
    @GET
    suspend fun getPipedStreams(@Url url: String): Response<PipedStreamResponse>

    @Headers("User-Agent: Mozilla/5.0 (Android; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
    @GET
    suspend fun getInvidiousSearch(@Url url: String): Response<List<InvidiousSearchResultItem>>

    @Headers("User-Agent: Mozilla/5.0 (Android; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
    @GET
    suspend fun getInvidiousVideo(@Url url: String): Response<InvidiousVideoResponse>
}
