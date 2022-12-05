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

object AstInspector {
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
    private val packageCache = HashBasedTable.create<List<String>, String, AstInterfaceDescriptor>()

    fun describe(className: String, sourcepath: List<String>): AstInterfaceDescriptor? {
        AstSourcePackageInspector(sourcepath, manager, filesystem)
            .describeInterfaces()
            .forEach { packageCache.put(sourcepath, it.desc.name, it) }
        return packageCache.get(sourcepath, className)
    }
}

class InterfaceDescriptor(
    val name: String,
    val properties: Set<String>
)

class AstInterfaceDescriptor(
    val desc: InterfaceDescriptor,
    val declaredSupertypes: Set<String>
)

private class AstSourcePackageInspector(
    sourcepath: List<String>,
    private val manager: PsiManager,
    private val fileSystem: CoreLocalFileSystem
) {
    private val files =
        sourcepath
            .flatMap { File(it).walkBottomUp().asSequence() }
            .filter { it.extension == "kt" }
            .map { manager.findFile(CoreLocalVirtualFile(fileSystem, it)) }
            .filterIsInstance<KtFile>()

    private val importsByFile =
        files.associate { file ->
            file.name to (file.importList?.imports.orEmpty()).map { it.importedFqName.toString() }
        }

    private fun resolveType(shortName: String, referencingClass: KtClass) =
        importsByFile[referencingClass.containingKtFile.name]
            .orEmpty()
            .firstOrNull { it.endsWith(shortName) }
            ?: shortName

    fun resolveProperties(cls: KtClass) =
        cls.declarations
            .asSequence()
            .filterIsInstance<KtProperty>()
            .mapNotNull { it.name }
            .toSet()

    fun describeInterfaces() =
        files
            .flatMap { it.declarations }
            .filterIsInstance<KtClass>()
            .asSequence()
            .filter { it.isInterface() }
            .map { cls ->
                AstInterfaceDescriptor(
                    InterfaceDescriptor(cls.fqName.toString(), resolveProperties(cls)),
                    cls.getSuperNames().map { resolveType(it, cls) }.toSet()
                )
            }
}
