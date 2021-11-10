
package com.bedelln.example.test

import com.bedelln.buildable.GenBuildable

@GenBuildable
data class Test1(
    val arg1: String,
    val arg2: Int,
    val arg3: Double?
) {
    companion object { }
}