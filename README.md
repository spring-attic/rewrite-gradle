# Rewrite plugin

[![Build Status](https://travis-ci.org/spring-gradle-plugins/rewrite-gradle.svg?branch=master)](https://travis-ci.org/spring-gradle-plugins/rewrite-gradle)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/spring-gradle-plugins/rewrite-gradle?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/spring-gradle-plugins/rewrite-gradle.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A Gradle plugin that discovers and applies [Rewrite](https://github.com/Netflix-Skunkworks/rewrite)
refactoring rules to your codebase.

## Requirements

 - Gradle 2.x (2.9 or later) or Gradle 3.x. Gradle 2.8 and earlier are not supported.
 - Java 8 or later

## Using the plugin

To apply the plugin, see the instructions on the [Gradle plugin portal](https://plugins.gradle.org/plugin/io.spring.rewrite).

The Rewrite plugin scans each source set's classpath for methods marked with `@AutoRewrite` and applies their contents to the source set.

To generate a report of what should be refactored in your project based on the `@AutoRewrite` methods found, run:

`./gradlew lintSource`

To automatically fix your code (preserving all of your beautiful code style), run:

`./gradlew fixSourceLint && git diff`

It is up to you to check the diff, run tests, and commit the resultant changes!

## Creating an `@AutoRewrite` rule

`@AutoRewrite` must be placed on a `public static` method that takes a single `Refactor` argument. The method may return anything you wish or nothing at all.

Below is an example of a rule:

```java
@AutoRewrite(value = "reactor-mono-flatmap", description = "change flatMap to flatMapMany")
public static void migrateMonoFlatMap(Refactor refactor) {
  // a compilation unit representing the source file we are refactoring
  Tr.CompilationUnit cu = refactor.getOriginal();

  refactor.changeMethodName(cu.findMethodCalls("reactor.core.publisher.Mono flatMap(..)"),
    "flatMapMany");
}
```
