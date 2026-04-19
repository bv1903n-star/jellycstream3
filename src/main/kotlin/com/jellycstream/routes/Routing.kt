package com.jellycstream.routes

import com.jellycstream.models.*
import com.jellycstream.core.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Cloudstream 3 Bridge is running.")
        }
        
        route("/repos") {
            post {
                val req = call.receive<SyncRepoRequest>()
                try {
                    val repoInfo = RepositoryManager.fetchRepo(req.url)
                    call.respond(HttpStatusCode.OK, repoInfo)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to fetch repo"))
                }
            }
        }
        
        route("/extensions") {
            get {
                val plugins = ExtensionManager.getInstalledPlugins().values.map {
                    ExtensionStatus(
                        name = it.name,
                        pluginUrl = it.filePath,
                        isInstalled = true,
                        providers = it.providers
                    )
                }
                call.respond(HttpStatusCode.OK, plugins)
            }
            post("/install") {
                val req = call.receive<PluginInstallPayload>()
                val ticketId = InstallManager.startInstallJob(req)
                
                // Wait up to 5 seconds
                val result = kotlinx.coroutines.withTimeoutOrNull(5000) {
                    while (InstallManager.getTicketStatus(ticketId)?.status == "processing") {
                        kotlinx.coroutines.delay(200)
                    }
                    InstallManager.getTicketStatus(ticketId)
                }
                
                if (result == null || result.status == "processing") {
                    call.respond(HttpStatusCode.Accepted, TicketResponse(status = "processing", ticketId = ticketId))
                } else if (result.status == "success") {
                    call.respond(HttpStatusCode.OK, TicketResponse(status = "success", ticketId = ticketId))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(result.error ?: "Installation failed"))
                }
            }
            post("/update") {
                // Update behaves the same as install for now (overwriting db & file)
                val req = call.receive<PluginInstallPayload>()
                val ticketId = InstallManager.startInstallJob(req)
                
                val result = kotlinx.coroutines.withTimeoutOrNull(5000) {
                    while (InstallManager.getTicketStatus(ticketId)?.status == "processing") {
                        kotlinx.coroutines.delay(200)
                    }
                    InstallManager.getTicketStatus(ticketId)
                }
                
                if (result == null || result.status == "processing") {
                    call.respond(HttpStatusCode.Accepted, TicketResponse(status = "processing", ticketId = ticketId))
                } else if (result.status == "success") {
                    call.respond(HttpStatusCode.OK, TicketResponse(status = "success", ticketId = ticketId))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(result.error ?: "Update failed"))
                }
            }
            get("/ticket/{id}") {
                val ticketId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("id missing"))
                val status = InstallManager.getTicketStatus(ticketId)
                if (status != null) {
                    call.respond(HttpStatusCode.OK, status)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Ticket not found"))
                }
            }
        }
        
        route("/api") {
            // Debug endpoint: list all providers currently in memory
            get("/providers") {
                val providers = ExtensionManager.getProviders().keys.toList()
                call.respond(HttpStatusCode.OK, mapOf("count" to providers.size, "providers" to providers))
            }
            get("/search") {
                val query = call.request.queryParameters["query"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("query missing"))
                val providerName = call.request.queryParameters["provider"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("provider missing"))
                
                val provider = ExtensionManager.getProvider(providerName) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                
                try {
                    val result = provider.search(query)
                    val mappedResult = result?.map { 
                        SearchResultSchema(
                            name = it.name,
                            url = it.url,
                            posterUrl = it.posterUrl,
                            type = null
                        )
                    } ?: emptyList()
                    call.respond(HttpStatusCode.OK, mappedResult)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Search execution failed"))
                }
            }
            get("/load") {
                val url = call.request.queryParameters["url"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("url missing"))
                val providerName = call.request.queryParameters["provider"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("provider missing"))
                
                val provider = ExtensionManager.getProvider(providerName) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                
                try {
                    val result = provider.load(url)
                    // Simplify response for now
                    call.respond(HttpStatusCode.OK, mapOf("name" to result?.name, "url" to result?.url))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Load execution failed"))
                }
            }
            get("/links") {
                val url = call.request.queryParameters["url"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("url missing"))
                val providerName = call.request.queryParameters["provider"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("provider missing"))
                
                val provider = ExtensionManager.getProvider(providerName) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                
                try {
                    val links = mutableListOf<Map<String, String>>()
                    val subs = mutableListOf<Map<String, String>>()
                    
                    provider.loadLinks(url, isCasting = false, subtitleCallback = { sub ->
                        subs.add(mapOf("url" to sub.url, "lang" to sub.lang))
                    }, callback = { link ->
                        links.add(mapOf(
                            "url" to link.url, 
                            "name" to link.name,
                            "type" to "quality" // simplified
                        ))
                    })
                    
                    call.respond(HttpStatusCode.OK, mapOf("links" to links, "subtitles" to subs))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Links execution failed"))
                }
            }
        }
    }
}
