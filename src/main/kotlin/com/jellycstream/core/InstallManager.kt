package com.jellycstream.core

import com.jellycstream.models.PluginInstallPayload
import com.jellycstream.models.TicketStatusResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object InstallManager {
    private val httpClient = HttpClient(CIO)
    
    // Status can be: "processing", "success", or "failed|<error_message>"
    private val ticketStatuses = ConcurrentHashMap<String, String>()

    fun getTicketStatus(ticketId: String): TicketStatusResponse? {
        val status = ticketStatuses[ticketId] ?: return null
        return if (status.startsWith("failed|")) {
            TicketStatusResponse("failed", status.substringAfter("failed|"))
        } else {
            TicketStatusResponse(status)
        }
    }

    suspend fun startInstallJob(payload: PluginInstallPayload): String {
        val ticketId = UUID.randomUUID().toString()
        ticketStatuses[ticketId] = "processing"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processInstallation(payload)
                ticketStatuses[ticketId] = "success"
            } catch (e: Exception) {
                ticketStatuses[ticketId] = "failed|${e.message}"
            }
        }
        
        return ticketId
    }
    
    suspend fun processInstallation(payload: PluginInstallPayload) {
        val jarName = payload.jarUrl.substringAfterLast("/")
        val destFile = File("extensions", jarName)
        
        // 1. Download JAR
        downloadFile(payload.jarUrl, destFile)
        
        // 2. Hash Verification
        val fileBytes = destFile.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val calculatedHash = "sha256-" + md.digest(fileBytes).joinToString("") { "%02x".format(it) }
        
        if (calculatedHash != payload.jarHash) {
            destFile.delete()
            throw Exception("Hash Mismatch! Expected ${payload.jarHash} but got $calculatedHash")
        }
        
        // 3. Load Extension into JVM
        ExtensionManager.loadExtensionJar(destFile)
        val loadedProviders = ExtensionManager.getInstalledPlugins()[destFile.nameWithoutExtension]?.providers ?: emptyList()
        
        if (loadedProviders.isEmpty()) {
            throw Exception("JAR loaded but no MainAPI providers found inside.")
        }
        
        // 4. Save to Database
        transaction {
            // Delete if exists to update securely
            org.jetbrains.exposed.sql.SqlExpressionBuilder.run {
                InstalledPlugins.deleteWhere { InstalledPlugins.pluginUrl eq payload.url }
            }
            
            InstalledPlugins.insert {
                it[pluginUrl] = payload.url
                it[name] = payload.name
                it[internalName] = payload.internalName
                it[authors] = payload.authors.joinToString(",")
                it[version] = payload.version
                it[status] = payload.status
                it[language] = payload.language ?: ""
                it[tvTypes] = payload.tvTypes.joinToString(",")
                it[iconUrl] = payload.iconUrl
                it[jarUrl] = payload.jarUrl
                it[jarHash] = payload.jarHash
                it[providers] = loadedProviders.joinToString(",")
                it[localFilePath] = destFile.absolutePath
            }
        }
    }

    private suspend fun downloadFile(url: String, destFile: File) {
        val response = httpClient.get(url)
        val bytes = response.body<ByteArray>()
        destFile.writeBytes(bytes)
    }

    /**
     * Uninstalls a plugin by name (JAR filename without extension).
     * Delegates to ExtensionManager to clean up memory + disk, then removes the DB record.
     * Returns true if the plugin was found and uninstalled.
     */
    fun uninstallExtension(pluginName: String): Boolean {
        val removed = ExtensionManager.uninstallExtension(pluginName)
        if (removed) {
            transaction {
                InstalledPlugins.deleteWhere { InstalledPlugins.name eq pluginName }
            }
            println("[InstallManager] Removed DB record for plugin: '$pluginName'")
        }
        return removed
    }
}
