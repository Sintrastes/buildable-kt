
import arrow.optics.Lens
import com.bedelln.buildable.Buildable
import com.bedelln.buildable.GenBuildable

@GenBuildable
data class MyData(
    val arg1: String,
    val arg2: Int
) {
    companion object {
        /*
        val arg1: Buildable.Field<MyData, String, PartialMyData> {
            override fun put(source: MyData, value: String): MyData {

            }

            override fun get(source: MyData): String {
                return source.arg2
            }

            override val partial = object: Lens<PartialMyData, String?> {
                override fun put(source: MyData, value: String): MyData {

                }

                override fun get(source: MyData): String? {
                    return source.arg2
                }
            }
        }

        val arg2: Buildable.Field<MyData, Int, PartialMyData> {
            override fun put(source: MyData, value: Int): MyData {
                return MyData(source.arg1, value)
            }

            override fun get(source: MyData): Int {
                return source.arg2
            }

            override val partial = object: Lens<PartialMyData, Int?> {
                override fun put(source: MyData, value: Int?): MyData {
                    return PartialMyData(source.arg1, value)
                }

                override fun get(source: MyData): Int? {
                    return source.arg2
                }
            }
        }
         */
    }
}

fun main() {
    // Example using the generated idiomatic generic builder
    // for MyData.
    // val test = MyData.builder
    //     .set(MyData.arg1, "test")
    //     .set(MyData.arg2, 42)
    //    .build()!!
    // println("Test complete: $test")

    // Example using the buildable interface directly:
    // val test2 = with(MyData.buildable) {
    //     empty
    //         .combine(PartialMyData("test", null))
    //         .combine(PartialMyData(null, 42))
    //         .build()
    // }
    // println("Test complete: $test2")
}