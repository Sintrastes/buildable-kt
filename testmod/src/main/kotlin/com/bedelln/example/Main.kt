package com.bedelln.example

import com.bedelln.example.test.PartialScope1Nested
import com.bedelln.example.test.Scope1
import com.bedelln.example.test.buildable

fun main() {
    // Example using the generated idiomatic generic builder
    // for MyData.
    val test = MyData.builder()
        .set(MyData.arg1, "test")
        .set(MyData.arg2, 42)
        .build()!!
    println("Test complete: $test")

    // Example using the buildable interface directly:
    val test2 = with(MyData.buildable()) {
        empty
            .combine(PartialMyData("test", null))
            .combine(PartialMyData(null, 42))
            .build()
    }

    println("Test complete: $test2")
}