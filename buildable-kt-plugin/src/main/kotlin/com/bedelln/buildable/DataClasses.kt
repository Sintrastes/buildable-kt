package com.bedelln.buildable

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.AnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.fileClasses.isInsideJvmMultifileClassFile
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.synthetics.findClassDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

/** Abstract representation of a Kotlin data class. */
internal data class DataClass(
    /** The package the class was declared in. */
    val pkg: String,
    /** The name of the class. */
    val name: String,
    /** List of fields of the data class. */
    val fields: List<DataClassField>
)

/** Abstract representation of a Kotlin data class field. */
internal data class DataClassField(
    val isVar: Boolean,
    val name: String,
    val typeName: ClassName
)

/** Helper class to convert an Element into a DataClass if it is in fact a Kotlin data class. */
@OptIn(ExperimentalAnvilApi::class)
internal fun classAsDataClass(module: ModuleDescriptor, ktClass: KtClass): DataClass? =
    if (ktClass.isData()) {
        val ktFile = ktClass.containingKtFile

        val name = ktClass.name!!
        val fields = ktClass.primaryConstructor!!.valueParameters.map {
            DataClassField(
                isVar = it.isVarArg,
                name = it.name!!,
                typeName = it.typeReference!!
                    .findClassDescriptor(BindingContext.EMPTY)
                    .asClassName()
            )
        }
        DataClass(ktFile.name, name, fields)
    } else {
        null
    }


@OptIn(ExperimentalAnvilApi::class)
class RealAnvilModuleDescriptor(
    delegate: ModuleDescriptor
) : AnvilModuleDescriptor, ModuleDescriptor by delegate {

    internal val allFiles = mutableListOf<KtFile>()

    private val classesMap = mutableMapOf<String, List<KtClassOrObject>>()
    private val allClasses: Sequence<KtClassOrObject>
        get() = classesMap.values.asSequence().flatMap { it }

    fun addFiles(files: Collection<KtFile>) {
        allFiles += files

        files.forEach { ktFile ->
            classesMap[ktFile.identifier] = ktFile.classesAndInnerClasses()
        }
    }

    @OptIn(ExperimentalAnvilApi::class)
    override fun getClassesAndInnerClasses(ktFile: KtFile): List<KtClassOrObject> {
        return classesMap.getOrPut(ktFile.identifier) {
            ktFile.classesAndInnerClasses()
        }
    }

    @OptIn(ExperimentalAnvilApi::class)
    override fun resolveClassIdOrNull(classId: ClassId): FqName? {
        val fqName = classId.asSingleFqName()

        resolveClassByFqName(fqName, NoLookupLocation.FROM_BACKEND)
            ?.let { return it.fqNameSafe }

        findTypeAliasAcrossModuleDependencies(classId)
            ?.let { return it.fqNameSafe }

        return allClasses
            .firstOrNull { it.fqName == fqName }
            ?.fqName
    }

    override fun getKtClassOrObjectOrNull(fqName: FqName): KtClassOrObject? {
        return allClasses
            .firstOrNull { it.fqName == fqName }
    }

    private val KtFile.identifier: String
        get() = packageFqName.asString() + name
}

private fun KtFile.classesAndInnerClasses(): List<KtClassOrObject> {
    val children = findChildrenByClass(KtClassOrObject::class.java)

    return generateSequence(children.toList()) { list ->
        list
            .flatMap {
                it.declarations.filterIsInstance<KtClassOrObject>()
            }
            .ifEmpty { null }
    }.flatten().toList()
}