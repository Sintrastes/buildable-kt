
import com.bedelln.buildable.Buildable
import com.bedelln.buildable.GenBuildable

@GenBuildable
data class MyData(
    val arg1: String,
    val arg2: Int
): Buildable<MyData>

fun main() {
    val test = MyData("test", 32)
    println("Test complete: $test")
}