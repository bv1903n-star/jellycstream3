package com.jellycstream

import com.jellycstream.routes.configureRouting
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    // Ensure extensions directory exists
    val extensionsDir = File("extensions")
    if (!extensionsDir.exists()) {
        extensionsDir.mkdirs()
    }
    
    // Initialize SQLite Database
    com.jellycstream.core.DatabaseManager.init()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { 
            ignoreUnknownKeys = true
            prettyPrint = true
        })
    }
    configureRouting()
}
