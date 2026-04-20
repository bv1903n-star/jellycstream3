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

        // ─── Extension Management (/ext) ───────────────────────────────────────────────────
        route("/ext") {
            // GET /ext/list — list all installed extensions
            get("/list") {
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

            // POST /ext/install — install a new extension from URL
            post("/install") {
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
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(result.error ?: "Installation failed"))
                }
            }

            // POST /ext/update — update an existing extension (re-installs over the existing one)
            post("/update") {
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

            // DELETE /ext/uninstall?name=<pluginName>
            // NOTE: `name` must match the JAR filename without extension.
            // Example: for "IdlixProvider.jar", use name=IdlixProvider
            delete("/uninstall") {
                val pluginName = call.request.queryParameters["name"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'name' query parameter is required"))

                val removed = InstallManager.uninstallExtension(pluginName)
                if (removed) {
                    call.respond(HttpStatusCode.OK, MessageResponse("Plugin '$pluginName' uninstalled successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Plugin '$pluginName' not found"))
                }
            }

            // GET /ext/ticket/{id} — check installation job status
            get("/ticket/{id}") {
                val ticketId = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("id missing"))
                val status = InstallManager.getTicketStatus(ticketId)
                if (status != null) {
                    call.respond(HttpStatusCode.OK, status)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Ticket not found"))
                }
            }
        }

        // ─── Functional Bridge API (/api) ───────────────────────────────────────────────
        route("/api") {
            // GET /api/providers — list all providers currently loaded in memory
            get("/providers") {
                val providers = ExtensionManager.getProviders().keys.toList()
                call.respond(HttpStatusCode.OK, ProvidersResponse(count = providers.size, providers = providers))
            }

            // GET /api/search?query=<q>&provider=<name>
            get("/search") {
                val query = call.request.queryParameters["query"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("query missing"))
                val providerName = call.request.queryParameters["provider"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("provider missing"))

                val provider = ExtensionManager.getProvider(providerName)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))

                try {
                    val result = ExtensionManager.safeSearch(provider, query)
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

            // GET /api/load?url=<url>&provider=<name>
            get("/load") {
                val url = call.request.queryParameters["url"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("url missing"))
                val providerName = call.request.queryParameters["provider"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("provider missing"))

                val provider = ExtensionManager.getProvider(providerName)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))

                try {
                    val result = provider.load(url)
                    call.respond(HttpStatusCode.OK, LoadResultSchema(name = result?.name, url = result?.url))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Load execution failed"))
                }
            }

            // GET /api/links?url=<url>&provider=<name>
            get("/links") {
                val url = call.request.queryParameters["url"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("url missing"))
                val providerName = call.request.queryParameters["provider"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("provider missing"))

                val provider = ExtensionManager.getProvider(providerName)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))

                try {
                    val links = mutableListOf<LinkItem>()
                    val subs = mutableListOf<SubtitleItem>()

                    provider.loadLinks(url, isCasting = false, subtitleCallback = { sub ->
                        subs.add(SubtitleItem(url = sub.url, lang = sub.lang))
                    }, callback = { link ->
                        links.add(LinkItem(
                            url = link.url,
                            name = link.name,
                            type = "quality"
                        ))
                    })

                    call.respond(HttpStatusCode.OK, LinksResultSchema(links = links, subtitles = subs))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Links execution failed"))
                }
            }
        }
    }
}
