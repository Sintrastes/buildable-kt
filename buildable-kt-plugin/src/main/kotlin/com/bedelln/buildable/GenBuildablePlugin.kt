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
import org.jetbrains.kotlin.resolve.descriptorUtil.module
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
                                if (it.shortName?.identifier == "GenBuildable") {
                                    res = true
                                }
                            }
                                .visitAnnotationEntry(annotation)
                        }
                        res
                    },
                    map = { (c, d) ->
                        if (!c.isData()) {
                            c.createLookupLocation()?.location?.let { loc ->
                                messageCollector.report(
                                    CompilerMessageSeverity.STRONG_WARNING,
                                    "@GenBuildable can only be applied to data classes. " +
                                            "No sources will be generated for ${c.name}.",
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
                                    "@GenBuildable can only be applied to classes with companion objects. " +
                                            "No sources will be generated for ${c.name}.",
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
                            |package ${dataClass.pkg}
                            |
                            |import com.bedelln.buildable.*
                            |import arrow.optics.Lens
                            |
                            |${generatePartialClass(dataClass)}                           
                            |${generateCtx(dataClass)}
                            |${generateBuilderExtension(dataClass)}
                            |${generateBuildableExtension(dataClass)}
                            |${
                                dataClass.fields.mapIndexed { i, _ ->
                                    generateField(dataClass, i)
                                        .toString()
                                }
                                    .joinToString("\n")
                            }
                            """
                                .trimMargin()
                        Transform.newSources(
                            packageText
                                .file(
                                    "${dataClass.name}Buildable",
                                    "/generated/main/kotlin/${dataClass.pkg.replace('.','/')}"
                                )
                        )
                    }
                )
            )
        }

internal fun generateField(dataClass: DataClass, fieldNo: Int): PropertySpec = run {
    val field = dataClass.fields[fieldNo]
    PropertySpec.builder(
        dataClass.fields[fieldNo].name,
        ClassName("", "Buildable", "Field")
            .parameterizedBy(
                dataClass.className,
                field.typeName,
                dataClass.partialClassName
            )
    )
        .receiver(dataClass.companionClassName)
        .getter(
            FunSpec.getterBuilder()
                .addCode(
                    CodeBlock.builder()
                        .addStatement(
                            """
                            |return object: Buildable.Field<${dataClass.classQualifiedName}, ${field.typeName}, ${dataClass.partialClassName}> {
                            |${generatePut(dataClass, field)}
                            |${generateGet(dataClass, field)}
                            |${generatePartial(dataClass, field)}
                            |}
                            """.trimMargin()
                        )
                        .build()
                )
                .build()
        )
        .build()
}

internal fun generatePut(dataClass: DataClass, field: DataClassField): FunSpec =
    FunSpec.builder("set")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("source", dataClass.className)
        .addParameter("focus", field.typeName)
        .returns(dataClass.className)
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        |return ${dataClass.classQualifiedName}(
                        |    ${
                        dataClass.fields.map {
                            if (it.name == field.name) {
                                "focus"
                            } else {
                                "source.${it.name}"
                            }
                        }
                            .joinToString(",")}
                        |)
                    """.trimMargin()
                )
                .build()
        )
        .build()

internal fun generateGet(dataClass: DataClass, field: DataClassField): FunSpec =
    FunSpec.builder("get")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("source", dataClass.className)
        .returns(field.typeName)
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        |return source.${field.name}
                    """.trimMargin()
                )
                .build()
        )
        .build()

internal fun generatePartial(dataClass: DataClass, field: DataClassField): PropertySpec =
    PropertySpec.builder(
        "partial", ClassName("", "Lens")
            .parameterizedBy(
                dataClass.partialClassName,
                field.typeName.copy(true)
            )
    )
        .addModifiers(KModifier.OVERRIDE)
        .getter(
            FunSpec.getterBuilder()
                .addCode(
                    CodeBlock.builder()
                        .addStatement(
                            """
                        |return object: Lens<Partial${dataClass.name}, ${field.typeName}?> {
                        |    ${generatePartialGet(dataClass, field)}
                        |    ${generatePartialPut(dataClass, field)}
                        |}
                    """.trimMargin()
                        )
                        .build()
                )
                .build()
        )
        .build()

internal fun generatePartialGet(dataClass: DataClass, field: DataClassField): FunSpec =
    FunSpec.builder("get")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("source", dataClass.partialClassName)
        .returns(field.typeName.copy(true))
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
        .addParameter("source", dataClass.partialClassName)
        .addParameter("focus", field.typeName.copy(true))
        .returns(dataClass.partialClassName)
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        |return ${dataClass.partialClassName}(
                        |    ${
                        dataClass.fields.map {
                            if (it.name == field.name) {
                                "focus"
                            } else {
                                "source.${it.name}"
                            }
                        }
                            .joinToString(",")}
                        |)
                    """.trimMargin()
                )
                .build()
        )
        .build()

internal fun generateBuilderExtension(dataClass: DataClass): FunSpec =
    FunSpec.builder("builder")
        .receiver(dataClass.companionClassName)
        .returns(
            ClassName("", "BuildableBuilder")
                .parameterizedBy(
                    dataClass.className,
                    ClassName(dataClass.packageQualifier, "Partial${dataClass.name}")
                )
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
        .receiver(dataClass.companionClassName)
        .returns(
            ClassName("", "Buildable", "Ctx")
                .parameterizedBy(
                    dataClass.className,
                    dataClass.partialClassName
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
    TypeSpec.classBuilder(dataClass.unqualifiedPartialClassName)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .apply {
                    for (field in dataClass.fields) {
                        addParameter(
                            ParameterSpec.builder(
                                field.name,
                                field.typeName.copy(true)
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
                    PropertySpec.builder(field.name, field.typeName.copy(true))
                        .initializer(field.name)
                        .build()
                )
            }
        }
        .addModifiers(KModifier.DATA)
        .addSuperinterface(
            ClassName("", "Buildable.Partial")
                .parameterizedBy(
                    dataClass.className,
                    dataClass.partialClassName
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
            dataClass.partialClassName
        )
        .returns(dataClass.partialClassName)
        .addCode(
            CodeBlock.builder()
                .addStatement("""
                    |return ${dataClass.partialClassName}(
                    |    ${
                            dataClass.fields.map { field ->
                                "${field.name} ?: other.${field.name}"
                            }
                                .joinToString(",\n")
                        }
                    |)
                """.trimMargin())
                .build()
        )
        .build()

/** Given a data class, generate it's build operation. */
internal fun generateBuildOperation(dataClass: DataClass): FunSpec =
    FunSpec.builder("build")
        .addModifiers(KModifier.OVERRIDE)
        .returns(dataClass.className.copy(true))
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        |return if(${dataClass.fields.map { field -> "${field.name} != null" }.joinToString(" && ")}) {
                        |    ${dataClass.classQualifiedName}(${dataClass.fields.map { field -> "${field.name}!!" }.joinToString(",")})
                        |} else {
                        |    null
                        |}
                    """.trimMargin()
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
                    dataClass.className,
                    dataClass.partialClassName
                )
        )
        .addProperty(
            PropertySpec.builder("empty", dataClass.partialClassName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(
                    CodeBlock.builder()
                        .addStatement(
                            "${dataClass.unqualifiedPartialClassName}(${dataClass.fields.map { "null" }.joinToString(",")})"
                        )
                        .build()
                )
                .build()
        )
        .addFunction(
            FunSpec.builder("buildable")
                .addModifiers(KModifier.OVERRIDE)
                .receiver(dataClass.className)
                .returns(
                    ClassName("", "Buildable")
                        .parameterizedBy(
                            dataClass.className,
                            dataClass.partialClassName
                        )
                )
                .addCode(
                    CodeBlock.builder()
                        .addStatement(
                            """
                                |return object: Buildable<${dataClass.classQualifiedName}, ${dataClass.partialClassName}> {
                                |    ${generateAsPartial(dataClass)}
                                |}
                            """.trimMargin()
                        )
                        .build()
                )
                .build()
        )
        .build()

internal fun generateAsPartial(dataClass: DataClass): FunSpec =
    FunSpec.builder("asPartial")
        .addModifiers(KModifier.OVERRIDE)
        .returns(dataClass.partialClassName)
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        |return Partial${dataClass.name}(
                        |    ${dataClass.fields.map { it.name }.joinToString(",")}
                        |)
                    """.trimMargin()
                )
                .build()
        )
        .build()