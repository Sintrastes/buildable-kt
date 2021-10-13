# buildable-kt
`buildable-kt` is a small library for deriving a generic "buildable" implementation for Kotlin data classes.

# Introduction

## Why?

You may be wondering: Isn't the builder pattern essentially useless in Kotlin, 
 as builders were always essentially just a way to get around lack of a language
 support for named parameters in the first place? Why do we need a compiler plugin
 to generate builders?

Well, there are two considerations to be made here:

  1. Whereas most builders can and should be replaced with Kotlin 
    named parameters, there are some examples (in particular, fluent
    APIs) that are not covered by named parameters alone. `buildable-kt`
    does not cover such cases -- but this still should be noted in general.
  2. Consider a Kotlin DSL using the [typesafe builder](https://kotlinlang.org/docs/type-safe-builders.html)
   paradigm, or even something like [compose](https://developer.android.com/jetpack/compose)
   where rather than describing raw data (such as XML), we want to build up
   a description of some mechanism for either consuming, modifying, or producing
   some piece of data of type `A`, where _parts_ of the description are used
   to manipulate _parts_ of the structure `A` -- a good example is a user
   interface for a _form_ for inputting an `A`. In this case, named parameters
   don't really make sense, as (for instance), the _parts_ of `A` may be at an
   arbitrary place in the UI.

The second consideration is exactly why I decided to make this library, and thus the
 use-case it is optimized to handle. However, there are certainly other uses for this library as
 well (see the discussion below on higher-kinded data).

If you have other use-cases for this -- let us know, or submit a PR, and I'd
 be happy to update the documentation or code to meet your needs!

## The buildable class

To generate a generic builder that can be used in the manner described
 in consideration 2 above, the idea is to have a notion of a _partial data type_,
 `P` associated with a "complete" data type `A`. The `Buildable<A,P>` class 
 can be viewed as providing witness to such a relationship.

There may be more general uses for these classes, but as far as this
 library is concerned -- for a `Buildable<A,P>`, `A` is always a 
 Kotlin data class, and for such a class `P` is an identical version
 of that data class with all fields nullable.

## Fields

TODO

## Usages

TODO

## Example

```kotlin
// Data definition:

@GenBuildable
data class Person(
    val name: String,
    val age: Int,
    val height: Double
) {
    // The plugin generates extensions on
    // the companion object of your data class,
    // so this is needed.
    companion object { }
}
```

```kotlin
// Usage:

Person.builder()
    .set(Person.age, 42)
    .set(Person.name, "Haskell B. Curry")
    .set(Person.height, 42.0)
    .build()!!
```

## Tradeoffs

As with anything, `buildable-kt` comes with some trade-offs. 

In particular
 in languages with better support for functional programming, there
 are many possible solutions for solving the problem that `buildable-kt`
 solves. For instance, `Applicatives` or `Monad`s can be used to solve
 similar problems of building declarative user interfaces manipulating
 a particular piece of data.

Whereas these approaches are definitely
 [_possible_](https://github.com/KindedJ/KindedJ) to emulate in Kotlin, and would certainly be more type-safe, we decided to go a different
 route due to the lack of support for implicit [currying](https://wiki.haskell.org/Currying) and language support for
 higher-kinded types does make those approaches a bit of a non-starter.

Another approach with some promise -- which is actually the approach that inspired this 
 library is the concept of [higher-kinded-data](https://reasonablypolymorphic.com/blog/higher-kinded-data/) -- but alas, as Kotlin's support for
 higher-kinded types is poor, we resort to codegen to reproduce a facsimile of HKD, specialized
 to the two cases of `f = Identity` and `f = Maybe`. 

Another possibility which would be both more idiomatic and more typesafe would be to instead
 generate a typesafe fluent API for building up the data type `A` -- built in such a way
 that it would be possible to integrate such a fluent API with another fluent API for building
 up user-interfaces. The disadvantage of this would be simply that typesafe-builders (when possible
 to use!) are much cleaner than fluent APIs. For instance, compare:

```kotlin
beginUI(VerticalLayout)
    .then(HorizontalSeperator())
    .then(beginUI(HorizontalLayout)
        .then(Label("Name: "))
        .then(StringEntry.bind(Person.name))
        .build())
    .then(beginUI(HorizontalLayout)
        .then(Label("Age: "))
        .then(IntEntry.bind(Person.age))
        .build())
    )
    .build()
```

with:

```kotlin
VerticalLayout {
    -HorizontalSeperator()
    -HorizontalLayout {
        -Label("Name: ")
        -StringEntry
            .bind(Person.name)
    }
    -HorizontalLayout {
        -Label("Age: ")
        -StringEntry
            .bind(Person.age)
    }
}
```

I know which one I'd rather write -- or even more importantly, _read_.

The latter approach being less type-safe means that mistakes like the following:

```kotlin
VerticalLayout {
    -HorizontalSeperator()
    -HorizontalLayout {
        -Label("Name: ")
        -StringEntry
            .bind(Person.name)
    }
    -HorizontalLayout {
        -Label("Age: ")
        -StringEntry
            // Forgot to bind person age
    }
}
```

or even:

```kotlin
VerticalLayout {
    -HorizontalSeperator()
    -HorizontalLayout {
        -Label("Name: ")
        -StringEntry
            .bind(Person.name)
    }
    -HorizontalLayout {
        -Label("Age: ")
        // Forgot to prefix with the DSL operator `-`.
        // This won't show up in the UI.
        StringEntry
            .bind(Person.age)
    }
}
```

will lead to a `NullPointerException` at runtime when trying to build up a `Person(name: String, age: Int)`
 from a `PartialPerson(name: String?, age: String?)` whilst using this form.

However, given that this is the sort of bug (in a well-designed framework making use of `buildable-kt`) that _any testing at all_ should run into immediately
 upon use of the faulty form, rather than some subtle bug which might take hours of debugging
 to root out -- that this is an acceptable trade-off for the improved readability.

In the future, since `buildable-kt` is a _compiler plugin_, it may even be possible to root out
 such errors at compile-time as well -- but this is work that remains to be done. Another possibility
 would be to write an appropriate rule for a static analyzer like [detekt](https://github.com/detekt/detekt) to report such mistakes
 as warnings.
 
## Aside: Higher-kinded data

TODO

# Getting started

`buildable-kt` is currently distributed as a Kotlin compiler plugin, built uisng [arrow-meta]().
 In the future, to give the user of this library some options in how it is used, we may consider
 also distributing it as a kapt plugin (PRs welcome!).

To get started, add the following to your build.gradle:

```groovy
// Let the compiler know where to find the compiler plugin.
compileKotlin {
    kotlinOptions {
        freeCompilerArgs += [
                "-Xplugin=/plugins/generate-buildable-sources-all.jar",
                "-P", "plugin:arrow.meta.plugin.compiler:generatedSrcOutputDir=${buildDir}"
        ]
    }
}

// Make sure generated sources are seen by the IDE
java.sourceSets["main"].java {
    srcDir("build/generated/main/java")
}

// Add the relevant runtime dependencies.
dependencies {
    ...
    
    // Arrow
    implementation "io.arrow-kt:arrow-core:$ARROW_VERSION"
    implementation "io.arrow-kt:arrow-optics:$ARROW_VERSION"

    ...
    
    implementation "com.bedelln.buildable-kt:0.1"
    
    ...
}

```

