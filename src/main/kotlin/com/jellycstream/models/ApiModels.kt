package com.jellycstream.models

import kotlinx.serialization.Serializable

@Serializable
data class SyncRepoRequest(val url: String)

@Serializable
data class PluginInfo(
    val name: String,
    val internalName: String,
    val version: Int,
    val url: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val authors: List<String> = emptyList()
)

@Serializable
data class RepoResponse(
    val name: String,
    val description: String? = null,
    val plugins: List<PluginInfo>
)

@Serializable
data class ExtensionStatus(
    val name: String,
    val pluginUrl: String,
    val isInstalled: Boolean,
    val version: Int? = null,
    val providers: List<String> = emptyList() // The class names of MainAPI in this jar
)

@Serializable
data class PluginInstallPayload(
    val url: String, // Repo specific url string
    val status: Int,
    val version: Int,
    val name: String,
    val internalName: String,
    val authors: List<String> = emptyList(),
    val fileSize: Long? = null,
    val repositoryUrl: String? = null,
    val language: String? = null,
    val tvTypes: List<String> = emptyList(),
    val iconUrl: String? = null,
    val apiVersion: Int? = null,
    val fileHash: String? = null,
    val jarFileSize: Long? = null,
    val jarUrl: String,
    val jarHash: String
)

@Serializable
data class TicketResponse(
    val status: String,
    val ticketId: String
)

@Serializable
data class TicketStatusResponse(
    val status: String, // processing, success, failed
    val error: String? = null
)

@Serializable
data class SearchRequest(val query: String, val provider: String? = null)

@Serializable
data class LoadRequest(val url: String, val provider: String? = null)

@Serializable
data class LinksRequest(val url: String, val provider: String? = null)

// Standard Response Schemas for Microservice
@Serializable
data class SearchResultSchema(
    val name: String,
    val url: String,
    val posterUrl: String? = null,
    val type: String? = null // e.g. TvSeries, Movie, Anime
)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class ProvidersResponse(val count: Int, val providers: List<String>)

@Serializable
data class LoadResultSchema(val name: String?, val url: String?)

@Serializable
data class LinkItem(val url: String, val name: String, val type: String)

@Serializable
data class SubtitleItem(val url: String, val lang: String)

@Serializable
data class LinksResultSchema(val links: List<LinkItem>, val subtitles: List<SubtitleItem>)
