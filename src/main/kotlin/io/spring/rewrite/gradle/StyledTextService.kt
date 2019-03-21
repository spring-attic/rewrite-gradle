/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spring.rewrite.gradle

import org.gradle.internal.service.ServiceRegistry

enum class Styling { Bold, Green, Yellow, Red }

class VersionNeutralTextOutput(val gradleTextOutput: Any) {
    fun text(v: Any) {
        val text = gradleTextOutput.javaClass.getMethod("text", Any::class.java)
        text.isAccessible = true
        text.invoke(gradleTextOutput, v)
    }

    fun println(v: Any) {
        val println = gradleTextOutput.javaClass.getMethod("println", Any::class.java)
        println.isAccessible = true
        println.invoke(gradleTextOutput, v)
    }
}

/**
 * Bridges the internal Gradle 2 and 3 APIs for styled text output to provide
 * a single backwards-compatible interface.
 */
class StyledTextService(registry: ServiceRegistry) {
    val textOutput: Any

    init {
        var factoryClass: Class<*>
        try {
            factoryClass = Class.forName("org.gradle.internal.logging.text.StyledTextOutputFactory")
        } catch(ignore: ClassNotFoundException) {
            factoryClass = Class.forName("org.gradle.logging.StyledTextOutputFactory")
        }

        val textOutputFactory = registry.get(factoryClass)
        val create = textOutputFactory.javaClass.getMethod("create", String::class.java)
        textOutput = create.invoke(textOutputFactory, "rewrite")
    }

    fun withStyle(styling: Styling): VersionNeutralTextOutput {
        var styleClass: Class<*>
        try {
            styleClass = Class.forName("org.gradle.internal.logging.text.StyledTextOutput\$Style")
        } catch(ignore: ClassNotFoundException) {
            styleClass = Class.forName("org.gradle.logging.StyledTextOutput\$Style")
        }

        fun styleByName(name: String): VersionNeutralTextOutput {
            val style = styleClass.getDeclaredMethod("valueOf", String::class.java).invoke(null, name)
            return VersionNeutralTextOutput(textOutput.javaClass.methods
                    .first { it.name == "withStyle" }
                    .invoke(textOutput, style))
        }

        return when(styling) {
            Styling.Bold -> styleByName("UserInput")
            Styling.Green -> styleByName("Identifier")
            Styling.Yellow -> styleByName("Description")
            Styling.Red -> styleByName("Failure")
        }
    }

    fun text(text: String): StyledTextService {
        textOutput.javaClass.getMethod("text", Any::class.java).invoke(textOutput, text)
        return this
    }

    fun println(text: String): StyledTextService {
        textOutput.javaClass.getMethod("println", Any::class.java).invoke(textOutput, text)
        return this
    }
}