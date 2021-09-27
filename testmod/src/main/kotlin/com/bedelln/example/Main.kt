
package com.bedelln.example

import arrow.optics.Lens
import com.bedelln.buildable.Buildable
import com.bedelln.buildable.GenBuildable

@GenBuildable
data class MyData(
    val arg1: String,
    val arg2: Int
) {
    companion object {}
}

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