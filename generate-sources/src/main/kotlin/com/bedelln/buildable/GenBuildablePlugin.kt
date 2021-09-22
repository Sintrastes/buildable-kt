
package com.bedelln.buildable

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import arrow.meta.*
import arrow.meta.phases.CompilerContext
import arrow.meta.quotes.Transform
import arrow.meta.quotes.classDeclaration
import arrow.meta.quotes.scope
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.psi.*
import kotlin.contracts.ExperimentalContracts

class GenBuildablePlugin : Meta {
    @ExperimentalContracts
    override fun intercept(ctx: CompilerContext): List<CliPlugin> =
        listOf(genBuildable)
}

/** Kotlin compiler plugin to automatically derive a Buildable instance for any class
 * annotated Buildable. */
val Meta.genBuildable: CliPlugin get() =
    "GenBuildable" {
        val messageCollector = this.messageCollector!!
        meta(
            classDeclaration(
                ctx = this,
                match = {
                    var res = false
                    annotationEntryVisitor {
                        if(it.name == "GenBuildable") {
                            res = true
                        }
                    }
                    res && element.isData()
                },
                map = { (c, d) ->
                    val dataClass = classAsDataClass(c)!!
                    Transform.replace(
                        replacing = c,
                        newDeclaration = c.apply {
                            messageCollector
                                .report(CompilerMessageSeverity.ERROR, "TEST")
                            println("------------------- Building new declaration ----------------------")
                            addDeclaration(
                                generatePartialClass(dataClass)
                                    .toString()
                                    .declaration<KtDeclaration>()
                                    .value!!
                            )
                            addDeclaration(
                                generateCtx(dataClass)
                                    .toString()
                                    .declaration<KtDeclaration>()
                                    .value!!
                            )
                        }
                            .scope()
                            .syntheticScope
                    )
                }
            )
        )
}

/** Helper class to convert an Element into a DataClass if it is in fact a Kotlin data class. */
internal fun classAsDataClass(ktClass: KtClass): DataClass? =
    if (ktClass.isData()) {
        val name = ktClass.name!!
        val fields = ktClass.primaryConstructor!!.valueParameters.map {
            DataClassField(it.isVarArg, it.name!!, it.typeReference!!.text)
        }
        DataClass(name, fields)
    } else {
        null
    }

/** Given a data class, generate it's "Partial" implementation. */
internal fun generatePartialClass(dataClass: DataClass): TypeSpec = run {
    println("------------------------ GENERATING PARTIAL CLASS ----------------")
    TypeSpec.classBuilder("Partial")
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
            ClassName("","Buildable", "Partial")
                .parameterizedBy(ClassName("", dataClass.name))
        )
        .addCode(
            CodeBlock.builder()
                .addStatement("""
                    return Partial(
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
        .returns(ClassName("",dataClass.name)) // TODO: make this nullable
        .addCode(
            CodeBlock.builder()
                .addStatement(
                    """
                        return if(${dataClass.fields.map { field -> "${field.name} != null" }.joinToString(" && ")} {
                            ${dataClass.name}(${dataClass.fields.map { field -> "${field.name}!!"}.joinToString(",")
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
    TypeSpec.objectBuilder("Ctx")
        .addProperty(
            PropertySpec.builder("empty", ClassName("","Partial"))
                .initializer(
                    CodeBlock.builder()
                        .addStatement(
                            "Partial(${dataClass.fields.map { "null" }.joinToString(",")})"
                        )
                        .build()
                )
                .build()
        )
        .build()

/** Abstract representation of a Kotlin data class. */
internal data class DataClass(
    val name: String,
    val fields: List<DataClassField>
)

/** Abstract representation of a Kotlin data class field. */
internal data class DataClassField(
    val isVar: Boolean,
    val name: String,
    val typeName: String
)