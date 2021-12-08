package com.bedelln.buildable

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

/** Helper function to parse the raw text of a Kotlin type
 * into a kotlinpoet ClassName. */
fun parseTypeName(text: String): TypeName {
    return classNameGrammar.parseToEnd(text)
}

private val classNameGrammar = object : Grammar<TypeName>() {
    val id by regexToken("\\w+")
    val nullable by literalToken("?")
    val comma by regexToken("\\s*,\\s*")
    val lbrack by literalToken("<")
    val rbrack by literalToken(">")

    val baseClassName by
    (id use { ClassName("",text) })

    val typeArgs: Parser<List<TypeName>> by
    ((skip(lbrack) and (separated(parser { rootParser }, comma)) and skip(rbrack)) map { it.terms })

    val compoundType by
    ((baseClassName and typeArgs) map {
        it.t1.parameterizedBy(it.t2)
    })
    
    val type by
    (((compoundType or baseClassName) and optional(nullable))
            map { it.t1.copy(it.t2 != null)})

    override val rootParser by type
}