package com.example.fpvupdater

import com.google.gson.annotations.SerializedName

data class ReleaseResponse(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("prerelease") val prerelease: Boolean,
    @SerializedName("published_at") val publishedAt: String? = null
)