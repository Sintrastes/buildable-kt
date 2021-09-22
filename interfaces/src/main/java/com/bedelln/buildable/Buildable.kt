
package com.bedelln.buildable


/**
 * Interface for a type that implements a
 * typesafe builder for some other type A.
 */
interface Builder<A> {
    fun build(): A?
}

/**
 * Interface for a type with an associative binary
 * "combine" operation.
 */
interface Semigroup<A> {
    fun combine(other: A): A
}

/**
 * Interface for a type A that is buildable from another
 * type Partial<A> via a typesafe builder that can be combined
 * via a monoid.
 */
interface Buildable<A> {
    interface Partial<A>: Builder<A>, Semigroup<Partial<A>>
    interface Ctx<A> {
        val empty: Partial<A>
    }
}


/** An example of a function that can be implemented
 * by making use of the Buildable interface.
 */
fun <A: Buildable<A>> Buildable.Ctx<A>.combineTwo(
    x: Buildable.Partial<A>,
    y: Buildable.Partial<A>
): A? {
    return empty
        .combine(x)
        .combine(y)
        .build()
}