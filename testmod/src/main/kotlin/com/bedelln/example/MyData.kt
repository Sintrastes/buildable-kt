
package com.bedelln.example

import arrow.optics.Lens
import com.bedelln.buildable.Buildable
import com.bedelln.buildable.GenBuildable

@GenBuildable
data class MyData(
    val arg1: String,
    val arg2: Int
) {
    companion object { }
}