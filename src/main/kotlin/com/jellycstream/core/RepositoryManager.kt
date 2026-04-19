package com.jellycstream.core

import com.jellycstream.models.PluginInfo
import com.jellycstream.models.RepoResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.File

object RepositoryManager {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    data class RawRepoJson(
        val name: String,
        val description: String? = null,
        val pluginLists: List<String> = emptyList()
    )

    suspend fun fetchRepo(url: String): RepoResponse {
        // Fetch repo json
        val repoResponse: RawRepoJson = httpClient.get(url).body()
        
        val plugins = mutableListOf<PluginInfo>()
        for (pluginListUrl in repoResponse.pluginLists) {
            try {
                val list: List<PluginInfo> = httpClient.get(pluginListUrl).body()
                plugins.addAll(list)
            } catch (e: Exception) {
                println("Failed to fetch plugins from ${pluginListUrl}: ${e.message}")
            }
        }
        
        return RepoResponse(
            name = repoResponse.name,
            description = repoResponse.description,
            plugins = plugins
        )
    }

    suspend fun downloadPluginJar(pluginUrl: String, destFile: File) {
        val response = httpClient.get(pluginUrl)
        val bytes = response.body<ByteArray>()
        destFile.writeBytes(bytes)
    }
}
