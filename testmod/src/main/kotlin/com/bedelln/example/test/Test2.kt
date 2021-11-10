
package com.bedelln.example.test

import com.bedelln.buildable.GenBuildable

@GenBuildable
data class Test2A(
    val arg1: String,
    val arg2: Int,
    val arg3: Double?
) {
    companion object { }
}

@GenBuildable
data class Test2B(
    val arg1: String,
    val arg2: Int,
    val arg3: Double?,
    val arg4: List<String>
) {
    companion object { }
}