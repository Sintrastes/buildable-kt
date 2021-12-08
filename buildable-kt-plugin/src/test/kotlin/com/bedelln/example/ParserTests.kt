package com.bedelln.example

import com.bedelln.buildable.parseTypeName
import com.squareup.kotlinpoet.ClassName
import org.junit.Test

class ParserTests {
    @Test
    fun `parse basic type`() {
        val x = parseTypeName("Int")
        assert(x == ClassName("", "Int"))
    }

    @Test
    fun `parse nullable basic type`() {
        val x = parseTypeName("Int?")
        assert(x == ClassName("", "Int").copy(nullable = true))
    }

    @Test
    fun `parse compound type`() {
        val x = parseTypeName("List<Int>")
        println(x)
    }

    @Test
    fun `parse compound type 2`() {
        val x = parseTypeName("Map<Int,String>")
        println(x)
    }

    @Test
    fun `parse compound type with space`() {
        val x = parseTypeName("Map<Int, String>")
        println(x)
    }
}