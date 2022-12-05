package com.toasttab.protokt.codegen.util

import com.google.common.collect.HashBasedTable
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getSuperNames
import java.io.File
import java.net.URLClassLoader
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmName

class InterfaceDescriptor(
    val name: String,
    val properties: Set<String>
)

private data class AstSourcePackage(
    val pkg: String,
    val sourcepath: List<String>
)

private class AstInterfaceDescriptor(
    val desc: InterfaceDescriptor,
    val declaredSupertypes: Set<String>
)

private class AstSourcePackageInspector(
    private val sourcePackage: AstSourcePackage,
    private val manager: PsiManager,
    private val fileSystem: CoreLocalFileSystem
) {
    // todo: are package paths needed? Can this be a file walk?
    private val filesInPackage = sourcePackage.sourcepath.map { File(it + "/" + sourcePackage.pkg.replace('.', '/')) }
        .filter { it.isDirectory }
        .flatMap { it.listFiles().orEmpty().toList() }
        .filter { it.name.endsWith(".kt") }
        .map { manager.findFile(CoreLocalVirtualFile(fileSystem, it)) }
        .filterIsInstance<KtFile>()

    private val classesInPackage =
        filesInPackage.flatMap { it.declarations }.filterIsInstance<KtClass>()

    private val classNames =
        classesInPackage.asSequence().mapNotNull { it.name }.toSet()

    private val importsByFile =
        filesInPackage.associate { file ->
            file.name to (file.importList?.imports.orEmpty()).map { it.importedFqName.toString() }
        }

    private fun resolveType(shortName: String, referencingClass: KtClass) =
        when (shortName) {
            in classNames -> "${sourcePackage.pkg}.$shortName"
            else ->
                importsByFile[referencingClass.containingKtFile.name]
                    .orEmpty()
                    .firstOrNull { it.endsWith(shortName) }
                    ?: shortName
    }

    fun resolveProperties(cls: KtClass) =
        cls.declarations
            .asSequence()
            .filterIsInstance<KtProperty>()
            .mapNotNull { it.name }
            .toSet()

    fun describeInterfaces() =
        classesInPackage
            .asSequence()
            .filter { it.isInterface() }
            .map { cls ->
                AstInterfaceDescriptor(
                    InterfaceDescriptor(cls.fqName.toString(), resolveProperties(cls)),
                    cls.getSuperNames().map { resolveType(it, cls) }.toSet()
                )
            }
}

private object AstInspector {
    private val manager by lazy {
        PsiManager.getInstance(
            KotlinCoreEnvironment.createForProduction(
                Disposer.newDisposable(),
                CompilerConfiguration().apply {
                    put(
                        CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                        PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
                    )
                },
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            ).project
        )
    }

    private val filesystem by lazy { CoreLocalFileSystem() }
    private val packageCache = HashBasedTable.create<AstSourcePackage, String, AstInterfaceDescriptor>()

    fun describe(className: String, codeContext: CodeContext): AstInterfaceDescriptor? {
        val sourcePackage = AstSourcePackage(className.substringBeforeLast("."), codeContext.sourcePath)
        AstSourcePackageInspector(sourcePackage, manager, filesystem)
            .describeInterfaces()
            .forEach { packageCache.put(sourcePackage, it.desc.name, it) }
        return packageCache.get(sourcePackage, className)
    }
}

private object ReflectionInspector {
    private val classloaderCache = mutableMapOf<List<String>, ClassLoader>()
    private val classCache = mutableMapOf<ReflectionClass, InterfaceDescriptor>()

    private fun getClassLoader(classpath: List<String>): ClassLoader {
        return classloaderCache.getOrPut(classpath) {
            when {
                classpath.isEmpty() -> javaClass.classLoader
                else -> URLClassLoader(classpath.map { p -> File(p).toURI().toURL() }.toTypedArray())
            }
        }
    }

    fun describe(className: String, codeContext: CodeContext) =
        classCache.compute(ReflectionClass(className, codeContext.classPath)) { key, value ->
            value ?: try {
                getClassLoader(key.classPath).loadClass(key.className).kotlin
            } catch (e: ReflectiveOperationException) {
                null
            }?.let { cls ->
                InterfaceDescriptor(cls.jvmName, cls.memberProperties.map { it.name }.toSet())
            }
        }
}

private data class ReflectionClass(val className: String, val classPath: List<String>)

class CodeContext(val sourcePath: List<String> = emptyList(), val classPath: List<String> = emptyList())

object DefaultCodeInspector {
    fun describe(interfaceName: String, ctx: CodeContext) =
        AstInspector.describe(interfaceName, ctx)
            ?.let { convert(it, ctx) }
            ?: ReflectionInspector.describe(interfaceName, ctx)

    private fun convert(astDescriptor: AstInterfaceDescriptor, ctx: CodeContext): InterfaceDescriptor =
        astDescriptor.declaredSupertypes
            .fold(astDescriptor.desc) { current, superInterface ->
                describe(superInterface, ctx)
                    ?.let { current + it }
                    ?: current
            }
}

operator fun InterfaceDescriptor.plus(other: InterfaceDescriptor) = InterfaceDescriptor(name, properties + other.properties)
