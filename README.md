# Rewrite plugin

[![Build Status](https://travis-ci.org/spring-gradle-plugins/rewrite-gradle.svg?branch=master)](https://travis-ci.org/spring-gradle-plugins/rewrite-gradle)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/spring-gradle-plugins/rewrite-gradle?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/spring-gradle-plugins/rewrite-gradle.svg)](http://www.apache.org/licenses/LICENSE-2.0)

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
