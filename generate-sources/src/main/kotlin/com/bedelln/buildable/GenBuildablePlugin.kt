package com.bedelln.buildable

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import arrow.meta.*
import arrow.meta.phases.CompilerContext
import arrow.meta.quotes.Transform
import arrow.meta.quotes.classDeclaration
import arrow.meta.quotes.scope
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.createLookupLocation
import org.jetbrains.kotlin.resolve.source.toSourceElement
import kotlin.contracts.ExperimentalContracts

class GenBuildablePlugin : Meta {
    @ExperimentalContracts
    override fun intercept(ctx: CompilerContext): List<CliPlugin> =
        listOf(genBuildable)
}

/** Kotlin compiler plugin to automatically derive a Buildable instance for any class
 * annotated Buildable. */
val Meta.genBuildable: CliPlugin
    get() =
        "GenBuildable" {
            val messageCollector = this.messageCollector!!

            meta(
                classDeclaration(
                    ctx = this,
                    match = {
                        var res = false
                        element.annotationEntries.forEach { annotation ->
                            annotationEntryVisitor {
                                messageCollector.report(
                                    CompilerMessageSeverity.WARNING,
                                    "in match: ${it.shortName}."
                                )
                                if (it.shortName?.identifier == "GenBuildable") {
                                    res = true
                                }
                            }
                                .visitAnnotationEntry(annotation)
                        }
                        res
                    },
                    map = { (c, _) ->
                        if (!c.isData()) {
                            c.createLookupLocation()?.location?.let { loc ->
                                messageCollector.report(
                                    CompilerMessageSeverity.STRONG_WARNING,
                                    "@GenBuildable can only be applied to data classes. No sources will be generated for ${c.name}.",
                                    object : CompilerMessageSourceLocation {
                                        override val path = loc.filePath
                                        override val line = loc.position.line
                                        override val column = loc.position.column
                                        override val lineContent = null
                                    }
                                )
                            }
                            return@classDeclaration Transform.empty
                        }

                        if (c.body?.allCompanionObjects?.isNotEmpty() != true) {
                            c.createLookupLocation()?.location?.let { loc ->
                                messageCollector.report(
                                    CompilerMessageSeverity.STRONG_WARNING,
                                    "@GenBuildable can only be applied to classes with companion objects. No sources will be generated for ${c.name}.",
                                    object : CompilerMessageSourceLocation {
                                        override val path = loc.filePath
                                        override val line = loc.position.line
                                        override val column = loc.position.column
                                        override val lineContent = null
                                    }
                                )
                            }
                            return@classDeclaration Transform.empty
                        }

                        val dataClass = classAsDataClass(c)!!

                        val packageText =
                            """
                            package ${dataClass.pkg}
                            
                            import com.bedelln.buildable.*
                            import arrow.optics.Lens
                            
                            ${generatePartialClass(dataClass)}                           
                            ${generateCtx(dataClass)}
                            ${generateBuilderExtension(dataClass)}
                            ${generateBuildableExtension(dataClass)}
                            ${
                                dataClass.fields.mapIndexed { i, _ ->
                                    generateField(dataClass, i)
                                        .toString()
                                }
                                    .joinToString("\n")
                            }
                        """
                                .trimIndent()

                        Transform.newSources(
                            packageText
                                .file("BuildableGenerated")
                        )
                    }
                )
            )
        }

/** Helper class to convert an Element into a DataClass if it is in fact a Kotlin data class. */
internal fun classAsDataClass(ktClass: KtClass): DataClass? =
    if (ktClass.isData()) {
        val pkgName = ktClass.containingKtFile.packageFqName.toString()
        val name = ktClass.name!!
        val fields = ktClass.primaryConstructor!!.valueParameters.map {
            DataClassField(it.isVarArg, it.name!!, it.typeReference!!.text)
        }
        DataClass(pkgName, name, fields)
    } else {
        null
    }

internal fun generateField(dataClass: DataClass, fieldNo: Int): PropertySpec = run {
    val field = dataClass.fields[fieldNo]
    PropertySpec.builder(
        dataClass.fields[fieldNo].name,
        ClassName("", "Buildable", "Field")
            .parameterizedBy(
                ClassName("", dataClass.name),
                ClassName("", field.typeName),
                ClassName("", "Partial${dataClass.name}")
            )
    )
        .receiver(ClassName("", dataClass.name, "Companion"))
        .initializer(
            CodeBlock.builder()
                .addStatement(
                    """
                        object: Buildable.Field<${dataClass.name}, ${field.typeName}, Partial${dataClass.name}> {
                            ${generatePut(dataClass, field)}
                            ${generateGet(dataClass, field)}
                            ${generatePartial(dataClass, field)}
                        }
                    """.trimIndent()
                )
                .build()
        )
        .build()
}

internal fun generatePut(dataClass: DataClass, field: DataClassField): FunSpec =
    FunSpec.builder("set")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("source", ClassName("", dataClass.name))
        .addParameter("focus", ClassName("", field.typeName))
        .returns(ClassName("", dataClass.name))
        .build()

internal fun generateGet(dataClass: DataClass, field: DataClassField): FunSpec =
    FunSpec.builder("get")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("source", ClassName("", dataClass.name))
        .returns(ClassName("", field.typeName))
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        return source.${field.name}
                    """.trimIndent()
                )
                .build()
        )
        .build()

internal fun generatePartial(dataClass: DataClass, field: DataClassField): PropertySpec =
    PropertySpec.builder(
        "partial", ClassName("", "Lens")
            .parameterizedBy(
                ClassName("", "Partial${dataClass.name}"),
                ClassName("", field.typeName).copy(true)
            )
    )
        .addModifiers(KModifier.OVERRIDE)
        .getter(
            FunSpec.getterBuilder()
                .addCode(
                    CodeBlock.builder()
                        .addStatement(
                            """
                        object: Lens<Partial${dataClass.name}, ${field.typeName}?> {
                            ${generatePartialGet(dataClass, field)}
                            ${generatePartialPut(dataClass, field)}
                        }
                    """.trimIndent()
                        )
                        .build()
                )
                .build()
        )
        .build()

internal fun generatePartialGet(dataClass: DataClass, field: DataClassField): FunSpec =
    FunSpec.builder("get")
        .addModifiers(KModifier.OVERRIDE)
        .returns(ClassName("", field.typeName).copy(true))
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        return source.${field.name}
                    """.trimIndent()
                )
                .build()
        )
        .build()

internal fun generatePartialPut(dataClass: DataClass, field: DataClassField): FunSpec =
    FunSpec.builder("set")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("source", ClassName("", "Partial${dataClass.name}"))
        .addParameter("focus", ClassName("", field.typeName).copy(true))
        .returns(ClassName("", "Partial${dataClass.name}"))
        .build()

internal fun generateBuilderExtension(dataClass: DataClass): FunSpec =
    FunSpec.builder("builder")
        .receiver(ClassName("", dataClass.name, "Companion"))
        .returns(
            ClassName("", "Builder")
                .parameterizedBy(ClassName("", dataClass.name))
        )
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        return BuildableBuilder(${dataClass.name}Ctx)
                    """.trimIndent()
                )
                .build()
        )
        .build()

internal fun generateBuildableExtension(dataClass: DataClass): FunSpec =
    FunSpec.builder("buildable")
        .receiver(ClassName("", dataClass.name, "Companion"))
        .returns(
            ClassName("", "Buildable", "Ctx")
                .parameterizedBy(
                    ClassName("", dataClass.name),
                    ClassName("", "Partial${dataClass.name}")
                )
        )
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        return ${dataClass.name}Ctx
                    """.trimIndent()
                )
                .build()
        )
        .build()


/** Given a data class, generate it's "Partial" implementation. */
internal fun generatePartialClass(dataClass: DataClass): TypeSpec = run {
    TypeSpec.classBuilder("Partial${dataClass.name}")
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .apply {
                    for (field in dataClass.fields) {
                        addParameter(
                            ParameterSpec.builder(
                                field.name,
                                ClassName("", field.typeName)
                                    .copy(true)
                            )
                                .build()
                        )
                    }
                }
                .build()
        )
        .apply {
            for (field in dataClass.fields) {
                addProperty(
                    PropertySpec.builder(field.name, ClassName("", field.typeName).copy(true))
                        .initializer(field.name)
                        .build()
                )
            }
        }
        .addModifiers(KModifier.DATA)
        .addSuperinterface(
            ClassName("", "Buildable.Partial")
                .parameterizedBy(
                    ClassName("", dataClass.name),
                    ClassName("", "Partial${dataClass.name}")
                )
        )
        .addFunction(
            generateCombineOperation(dataClass)
        )
        .addFunction(
            generateBuildOperation(dataClass)
        )
        .build()
}

/** Given a data class, generate it's partial class's combine operation. */
internal fun generateCombineOperation(dataClass: DataClass): FunSpec =
    FunSpec.builder("combine")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter(
            "other",
            ClassName("", "Partial${dataClass.name}")
        )
        .returns(ClassName("", "Partial${dataClass.name}"))
        .addCode(
            CodeBlock.builder()
                .addStatement("""
                    return Partial${dataClass.name}(
                        ${
                    dataClass.fields.map { field ->
                        "${field.name} ?: other.${field.name}"
                    }
                        .joinToString(",\n")
                }
                    )
                """.trimIndent())
                .build()
        )
        .build()

/** Given a data class, generate it's build operation. */
internal fun generateBuildOperation(dataClass: DataClass): FunSpec =
    FunSpec.builder("build")
        .addModifiers(KModifier.OVERRIDE)
        .returns(ClassName("", dataClass.name).copy(true))
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        return if(${dataClass.fields.map { field -> "${field.name} != null" }.joinToString(" && ")}) {
                            ${dataClass.name}(${dataClass.fields.map { field -> "${field.name}!!" }.joinToString(",")})
                        } else {
                            null
                        }
                    """.trimIndent()
                )
                .build()
        )
        .build()

/** Given a data class, generate it's ctx class. */
internal fun generateCtx(dataClass: DataClass): TypeSpec =
    TypeSpec.objectBuilder("${dataClass.name}Ctx")
        .addSuperinterface(
            ClassName("", "Buildable.Ctx")
                .parameterizedBy(
                    ClassName("", dataClass.name),
                    ClassName("", "Partial${dataClass.name}")
                )
        )
        .addProperty(
            PropertySpec.builder("empty", ClassName("", "Partial${dataClass.name}"))
                .addModifiers(KModifier.OVERRIDE)
                .initializer(
                    CodeBlock.builder()
                        .addStatement(
                            "Partial${dataClass.name}(${dataClass.fields.map { "null" }.joinToString(",")})"
                        )
                        .build()
                )
                .build()
        )
        .addFunction(
            FunSpec.builder("buildable")
                .addModifiers(KModifier.OVERRIDE)
                .receiver(ClassName("", dataClass.name))
                .returns(
                    ClassName("", "Buildable")
                        .parameterizedBy(
                            ClassName("", dataClass.name),
                            ClassName("", "Partial${dataClass.name}")
                        )
                )
                .addCode(
                    CodeBlock.builder()
                        .addStatement(
                            """
                                object: Buildable<${dataClass.name}, Partial${dataClass.name}> {
                                    ${generateAsPartial(dataClass)}
                                }
                            """.trimIndent()
                        )
                        .build()
                )
                .build()
        )
        .build()

internal fun generateAsPartial(dataClass: DataClass): FunSpec =
    FunSpec.builder("asPartial")
        .addModifiers(KModifier.OVERRIDE)
        .returns(ClassName("", "Partial${dataClass.name}"))
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        return Partial${dataClass.name}(
                            ${dataClass.fields.map { it.name }.joinToString(",")}
                        )
                    """.trimIndent()
                )
                .build()
        )
        .build()

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
    val typeName: String
)