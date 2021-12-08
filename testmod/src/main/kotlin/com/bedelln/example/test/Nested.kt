
package com.bedelln.example.test

import com.bedelln.buildable.GenBuildable

object Scope1 {
    @GenBuildable
    data class Nested(
        val arg1: String,
        val arg2: Int
    ) {
        companion object { }
    }
}

class Scope2 {
    @GenBuildable
    data class Nested(
       val arg1: Int,
       val arg2: List<Pair<Double, Int>>
    ) {
        companion object { }
    }
}