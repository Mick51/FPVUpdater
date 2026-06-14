package com.example.fpvupdater

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): ReleaseResponse

    @GET("repos/{owner}/{repo}/releases?per_page=10")
    suspend fun getAllReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<ReleaseResponse>

    @GET("repos/{owner}/{repo}/tags?per_page=5")
    suspend fun getTags(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<TagResponse>
}

data class TagResponse(
    val name: String,
    @SerializedName("zipball_url") val zipballUrl: String
)