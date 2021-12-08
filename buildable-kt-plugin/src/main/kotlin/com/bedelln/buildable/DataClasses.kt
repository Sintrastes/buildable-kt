package com.bedelln.buildable

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.fileClasses.isInsideJvmMultifileClassFile
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.js.descriptorUtils.getJetTypeFqName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.psi.synthetics.findClassDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.typeBinding.createTypeBindingForReturnType

/** Abstract representation of a Kotlin data class. */
internal data class DataClass(
    /** The package the class was declared in. */
    val pkg: String,
    val parentClasses: List<KtClassOrObject>,
    val packageQualifier: String,
    val classQualifier: String,
    /** The name of the class. */
    val name: String,
    /** List of fields of the data class. */
    val fields: List<DataClassField>
)

/** Abstract representation of a Kotlin data class field. */
internal data class DataClassField(
    val isVar: Boolean,
    val name: String,
    val typeName: TypeName
)

/** Helper class to convert an Element into a DataClass if it is in fact a Kotlin data class. */
@OptIn(ExperimentalAnvilApi::class)
internal fun classAsDataClass(ktClass: KtClass): DataClass? =
    if (ktClass.isData()) {
        val ktFile = ktClass.containingKtFile

        val fqName = ktClass.fqName.toString()
        val sections = fqName.split(".")
        val name = sections.last() // ktClass.getParentClasses() //
        val packageQualifier = sections.subList(0, sections.size - ktClass.getParentClasses().size - 1)
            .joinToString(".")

        val classQualifier = ktClass.getParentClasses().map { it.name }.joinToString(".")

        val fields = ktClass.primaryConstructor!!.valueParameters.map {
            DataClassField(
                isVar = it.isVarArg,
                name = it.name!!,
                typeName = parseTypeName(it.typeReference!!.text)
            )
        }
        DataClass(ktFile.packageFqName.toString(), ktClass.getParentClasses(), packageQualifier, classQualifier, name, fields)
    } else {
        null
    }

// Helper function to recursively get the list of KtClasses
// or objects that a class is defined in, in order as they appear
// when printed on the screen (i.e. outermost class first).
fun KtClass.getParentClasses(): List<KtClassOrObject> {
    val classes = mutableListOf<KtClassOrObject>()
    var clazz: KtClassOrObject? = this.containingClassOrObject
    while(clazz != null) {
        classes.add(0, clazz)
        clazz = clazz.containingClassOrObject
    }
    return classes
}

internal val DataClass.classQualifiedName: String
    get() = if (classQualifier.isNotBlank()) "$classQualifier.$name" else name

internal val DataClass.companionClassName: ClassName
    get() = ClassName("$packageQualifier.$classQualifier", name, "Companion")

internal val DataClass.className: ClassName
    get() = ClassName("$packageQualifier.$classQualifier", name)

internal val DataClass.partialClassName: ClassName
    get() = ClassName(packageQualifier, "Partial$name")

internal val DataClass.unqualifiedPartialClassName: ClassName
    get() = ClassName("", "Partial$name")