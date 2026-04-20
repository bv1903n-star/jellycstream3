package com.jellycstream.core

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import kotlin.reflect.KClass

object ExtensionManager {
    private val loadedProviders = mutableMapOf<String, MainAPI>()
    private val installedPlugins = mutableMapOf<String, PluginEntry>()
    
    data class PluginEntry(
        val name: String,
        val filePath: String,
        val classLoader: URLClassLoader,
        val providers: List<String>
    )

    fun loadAllExtensions(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles { file -> file.extension == "jar" || file.extension == "cs3" }?.forEach { jar ->
            try {
                loadExtensionJar(jar)
            } catch (e: Exception) {
                println("Failed to load plugin ${jar.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun loadExtensionJar(jarFile: File) {
        val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)
        
        val loadedProviderNames = mutableListOf<String>()
        val foundPluginName = jarFile.nameWithoutExtension

        println("[ExtensionManager] Scanning JAR: ${jarFile.name}")

        val foundProviders = scanJarForProviders(jarFile, classLoader)
        println("[ExtensionManager] scanJarForProviders found ${foundProviders.size} provider class(es) in ${jarFile.name}")

        foundProviders.forEach { providerClass ->
            try {
                val providerInstance = providerClass.getDeclaredConstructor().newInstance() as MainAPI
                println("[ExtensionManager] Registered provider: '${providerInstance.name}' from class ${providerClass.name}")
                loadedProviders[providerInstance.name] = providerInstance
                loadedProviderNames.add(providerInstance.name)
            } catch (e: Exception) {
                println("[ExtensionManager] Failed to instantiate provider class ${providerClass.name}: ${e.message}")
                e.printStackTrace()
            }
        }

        if (loadedProviderNames.isEmpty()) {
            println("[ExtensionManager] WARNING: No providers were successfully registered from ${jarFile.name}!")
        }

        installedPlugins[foundPluginName] = PluginEntry(
            name = foundPluginName,
            filePath = jarFile.absolutePath,
            classLoader = classLoader,
            providers = loadedProviderNames
        )
    }

    private fun scanJarForProviders(jarFile: File, classLoader: URLClassLoader): List<Class<*>> {
        val providers = mutableListOf<Class<*>>()
        var scannedCount = 0
        var failedCount = 0
        java.util.zip.ZipFile(jarFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class")) {
                    scannedCount++
                    val className = entry.name.replace('/', '.').removeSuffix(".class")
                    try {
                        val cls = classLoader.loadClass(className)
                        if (MainAPI::class.java.isAssignableFrom(cls) && !cls.isInterface && !java.lang.reflect.Modifier.isAbstract(cls.modifiers)) {
                            println("[ExtensionManager] Found MainAPI subclass: $className")
                            providers.add(cls)
                        }
                    } catch (e: NoClassDefFoundError) {
                        failedCount++
                        if (className.contains("Provider", ignoreCase = true)) {
                            println("[ExtensionManager] NoClassDefFoundError for $className: ${e.message}")
                        }
                    } catch (e: Exception) {
                        failedCount++
                        if (className.contains("Provider", ignoreCase = true)) {
                            println("[ExtensionManager] Error loading $className: ${e.message}")
                        }
                    }
                }
            }
        }
        println("[ExtensionManager] Scan complete: $scannedCount classes scanned, $failedCount failed to load, ${providers.size} MainAPI found.")
        return providers
    }

    fun getProviders(): Map<String, MainAPI> = loadedProviders

    fun getProvider(name: String): MainAPI? {
        // Return exactly matched or first match containing the string
        return loadedProviders[name] ?: loadedProviders.values.firstOrNull { it.name.contains(name, ignoreCase = true) }
    }

    fun getInstalledPlugins(): Map<String, PluginEntry> = installedPlugins

    /**
     * Uninstalls a plugin by its name (JAR filename without extension).
     * Removes all associated providers from memory and deletes the JAR file from disk.
     * Returns true if the plugin was found and removed, false if not found.
     *
     * NOTE: The `pluginName` parameter must match the JAR filename without extension,
     * e.g. for "IdlixProvider.jar" use "IdlixProvider".
     */
    fun uninstallExtension(pluginName: String): Boolean {
        val entry = installedPlugins[pluginName] ?: return false

        // 1. Remove all associated providers from memory
        entry.providers.forEach { providerName ->
            loadedProviders.remove(providerName)
            println("[ExtensionManager] Unloaded provider: '$providerName'")
        }

        // 2. Delete the physical JAR file from disk
        val jarFile = File(entry.filePath)
        if (jarFile.exists()) {
            jarFile.delete()
            println("[ExtensionManager] Deleted JAR: ${jarFile.absolutePath}")
        }

        // 3. Remove from memory map
        installedPlugins.remove(pluginName)
        println("[ExtensionManager] Uninstalled plugin: '$pluginName'")
        return true
    }

    /**
     * Safely extracts a List<SearchResponse> from whatever the search() method returns.
     * This handles the case where SearchResponseList is a custom class (not a java.util.List)
     * in different versions of the Cloudstream library compiled by different providers.
     */
    private fun extractSearchItems(rawResult: Any?): List<SearchResponse> {
        if (rawResult == null) return emptyList()

        // Case 1: Already a standard List
        if (rawResult is List<*>) return rawResult.filterIsInstance<SearchResponse>()

        // Case 2: Implements Iterable (but not List)
        if (rawResult is Iterable<*>) return rawResult.filterIsInstance<SearchResponse>()

        // Case 3: Custom class wrapping a list - use reflection to find the internal collection
        val fieldsToCheck = rawResult.javaClass.declaredFields.toList() +
                (rawResult.javaClass.superclass?.declaredFields?.toList() ?: emptyList())

        for (field in fieldsToCheck) {
            try {
                field.isAccessible = true
                val value = field.get(rawResult)
                if (value is Iterable<*>) {
                    val items = value.filterIsInstance<SearchResponse>()
                    if (items.isNotEmpty()) return items
                }
            } catch (_: Exception) {}
        }

        println("[ExtensionManager] WARNING: Could not extract items from ${rawResult.javaClass.name}")
        return emptyList()
    }

    suspend fun safeSearch(provider: MainAPI, query: String): List<SearchResponse>? {
        // Strategy 1: Simple search(query) - returns List<SearchResponse>?
        try {
            val result = provider.search(query)
            println("[ExtensionManager] search(query) succeeded for '${provider.name}'")
            return result
        } catch (e: NotImplementedError) {
            println("[ExtensionManager] search(query) not implemented for '${provider.name}', trying search(query, page)...")
        }

        // Strategy 2: Paginated search(query, page) - returns SearchResponseList (may not be java.util.List)
        try {
            val rawResult: Any? = provider.search(query, 1)
            val result = extractSearchItems(rawResult)
            println("[ExtensionManager] search(query, page) succeeded for '${provider.name}' (${result.size} items)")
            return result
        } catch (e: NotImplementedError) {
            println("[ExtensionManager] search(query, page) not implemented for '${provider.name}', trying quickSearch...")
        }

        // Strategy 3: quickSearch(query)
        try {
            val result = provider.quickSearch(query)
            println("[ExtensionManager] quickSearch(query) succeeded for '${provider.name}'")
            return result
        } catch (e: NotImplementedError) {
            println("[ExtensionManager] quickSearch(query) also not implemented for '${provider.name}'")
        }

        return null
    }
}
