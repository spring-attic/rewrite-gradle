# Rewrite plugin

A Gradle plugin that discovers and applies [Rewrite](https://github.com/Netflix-Skunkworks/rewrite)
refactoring rules to your codebase.

## Requirements

 - Gradle 2.x (2.9 or later) or Gradle 3.x. Gradle 2.8 and earlier are not supported.
 - Java 8 or later

## Using the plugin

The plugin is available in the Gradle Plugin Portal and can be applied like this:

```groovy
plugins {
    id "io.spring.rewrite" version "0.1.0"
}
```

If you prefer, the plugin is also available from Maven Central and JCenter.