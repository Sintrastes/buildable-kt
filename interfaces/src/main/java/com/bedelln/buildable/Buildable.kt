
package com.bedelln.buildable

import arrow.optics.*

/**
 * Interface for a type that implements a
 * typesafe builder for some other type A.
 */
interface Builder<A> {
    /** Attempt to "build" this partial data structure into a
     * complete structure `A`. Returns null if this data structure
     * is not complete. */
    fun build(): A?
}

/**
 * Interface for a type with an associative binary
 * "combine" operation.
 */
interface Semigroup<A> {
    /** The combine operation of a semigroup. */
    fun combine(other: A): A
}

/**
 * Interface for a type A that is buildable from another
 * type Partial<A> via a typesafe builder that can be combined
 * via a monoid.
 *
 * Valid implementations satisfy:
 *
 * ```
 * x.asPartial().build() == x
 * ```
 *
 * As well as the monoid laws for P.
 *
 * @param A recursive "self type" parameter
 * @param P "partial" version of A, allowing for fields to be null.
 */
interface Buildable<A, P: Buildable.Partial<A, P>> {
    /** Converts a buildable object into it's partial type. */
    fun asPartial(): P

    /**
     * Interface for a "partial" version of A, where all of its
     * fields may be potentially null.
     *
     * Semigroup implementation should have the semantics that
     *  `x.combine(y)` "overwrites" the fields of x with the
     *  corresponding field of y, so long as the corresponding
     *  y field is not null.
     */
    interface Partial<A, P>: Builder<A>, Semigroup<P>

    /**
     * Typeclass "context" providing a retroactive implementation of
     * the Buildable interface, as well as the static "empty" value
     * needed in order to make Partial<A,P> a monoid.
     *
     * Used, for instance, when the Buildable interface is created
     * via codegen with [@GenBuildable], where it can be accessed
     * via `MyClass.buildable`.
     */
    interface Ctx<A, P: Partial<A, P>> {
        /** Partial data type with no fields set. */
        val empty: P
        /** Function providing access to a (potentially retroactive) buildable
         * implementation for an `A`. */
        fun A.buildable(): Buildable<A, P>
    }

    /** A field of a buildable data class. Can act either as a
     * regular lens, or as a lens on the "partial" data type for
     * the buildable implementation. */
    interface Field<A,B,P: Partial<A,P>>: Lens<A, B> {
        val partial: Lens<P, B?>
    }
}

/** A generic traditional OOP "Builder pattern" implementation for
 * implementors of the buildable interface. */
class BuildableBuilder<A,P>(ctx: Buildable.Ctx<A,P>): Builder<A>
  where P: Buildable.Partial<A,P> {
    private var partialData: P = ctx.empty
    fun <B> set(field: Buildable.Field<A,B,P>, value: B) {
        partialData = field.partial.set(partialData, value)
    }

    override fun build(): A? {
        return partialData.build()
    }
}

/** Helper function to derive a full lens from a partial lens. */
fun <A,B,P: Buildable.Partial<A,P>> Buildable.Ctx<A,P>.getFull(lens: Lens<P,B?>): Lens<A, B> {
    return object: Lens<A, B> {
        override fun get(source: A): B {
            val buildableSource = source.buildable()
            return lens.get(buildableSource.asPartial())!!
        }

        override fun set(source: A, focus: B): A {
            val buildableSource = source.buildable()
            return lens.set(buildableSource.asPartial(), focus).build()!!
        }
    }
}