package com.jellycstream.core

import com.lagradost.cloudstream3.MainAPI
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
        
        // Cloudstream 3 plugins often expose their entry point via ServiceLoader
        // or they have a manifest entry. Let's try both or just scan classes.
        // Actually, plugins extend CloudstreamPlugin. 
        // We will read META-INF/services/com.lagradost.cloudstream3.plugins.CloudstreamPlugin if it exists.
        val services = ServiceLoader.load(CloudstreamPlugin::class.java, classLoader).iterator()
        
        val loadedProviderNames = mutableListOf<String>()
        var foundPluginName = jarFile.nameWithoutExtension

        while (services.hasNext()) {
            val plugin = services.next()
            println("Loaded CloudstreamPlugin: ${plugin.javaClass.name}")
            // A plugin usually calls registerMainAPI(provider)
            // But CloudstreamPlugin context inside JVM might need to be mocked. Let's skip calling plugin.load() for now
            // and instead directly instantiate MainAPI if we can find it.
        }

        // Simpler way: we just load any MainAPI exposed via ServiceLoader or scan.
        // Usually, many scrapers just extend MainAPI directly and some might not be in ServiceLoader.
        // To be safe, wait, does Cloudstream use ServiceLoader? No, Cloudstream uses android DexFile to scan all classes!
        // JVM can't run DexFile. We need to scan the JAR file entries using ZipFile.
        scanJarForProviders(jarFile, classLoader).forEach { providerClass ->
            val providerInstance = providerClass.getDeclaredConstructor().newInstance() as MainAPI
            loadedProviders[providerInstance.name] = providerInstance
            loadedProviderNames.add(providerInstance.name)
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
        java.util.zip.ZipFile(jarFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class")) {
                    val className = entry.name.replace('/', '.').removeSuffix(".class")
                    try {
                        val cls = classLoader.loadClass(className)
                        if (MainAPI::class.java.isAssignableFrom(cls) && !cls.isInterface && !java.lang.reflect.Modifier.isAbstract(cls.modifiers)) {
                            providers.add(cls)
                        }
                    } catch (e: NoClassDefFoundError) {
                        // ignore classes that fail to load
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
        return providers
    }

    fun getProviders(): Map<String, MainAPI> = loadedProviders

    fun getProvider(name: String): MainAPI? {
        // Return exactly matched or first match containing the string
        return loadedProviders[name] ?: loadedProviders.values.firstOrNull { it.name.contains(name, ignoreCase = true) }
    }
    
    fun getInstalledPlugins(): Map<String, PluginEntry> = installedPlugins
}
