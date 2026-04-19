package com.jellycstream.core

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.io.File
import java.sql.Connection

object InstalledPlugins : Table() {
    val pluginUrl = varchar("pluginUrl", 500)
    val name = varchar("name", 255)
    val internalName = varchar("internalName", 255)
    val authors = text("authors") // Stored as comma-separated or JSON
    val version = integer("version")
    val status = integer("status")
    val language = varchar("language", 10)
    val tvTypes = text("tvTypes") // Stored as comma-separated or JSON
    val iconUrl = varchar("iconUrl", 500).nullable()
    val jarUrl = varchar("jarUrl", 500)
    val jarHash = varchar("jarHash", 255)
    val providers = text("providers") // List of class names loaded
    val localFilePath = varchar("localFilePath", 500)

    override val primaryKey = PrimaryKey(pluginUrl)
}

object DatabaseManager {
    fun init() {
        // Ensure db directory exists
        val dbDir = File("db")
        if (!dbDir.exists()) dbDir.mkdirs()

        Database.connect("jdbc:sqlite:db/plugins.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        transaction {
            SchemaUtils.create(InstalledPlugins)
            
            // Auto-Recovery Strategy -> reload all existing plugins
            InstalledPlugins.selectAll().forEach { row ->
                val localPath = row[InstalledPlugins.localFilePath]
                val file = File(localPath)
                if (file.exists()) {
                    try {
                        ExtensionManager.loadExtensionJar(file)
                        println("Auto-Recovered plugin: ${row[InstalledPlugins.name]}")
                    } catch (e: Exception) {
                        println("Failed to auto-recover plugin at ${localPath}: ${e.message}")
                    }
                } else {
                    println("JAR file missing for ${row[InstalledPlugins.name]}, cannot auto-recover.")
                }
            }
        }
    }
}
