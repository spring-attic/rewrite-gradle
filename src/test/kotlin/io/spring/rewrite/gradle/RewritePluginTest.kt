/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spring.rewrite.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RewritePluginTest {
    @JvmField @Rule val temp = TemporaryFolder()

    lateinit var projectDir: File
    lateinit var sourceFolder: File
    lateinit var testSourceFolder: File

    fun generateRule(className: String, signature: String): String {
        val source = """
                |import com.netflix.rewrite.refactor.Refactor;
                |import com.netflix.rewrite.auto.AutoRewrite;
                |
                |public class $className {
                |    @AutoRewrite(value = "guava-deprecations", description = "fix Guava deprecations")
                |    $signature {
                |        refactor.changeMethodTargetToStatic(
                |           refactor.getOriginal().findMethodCalls("com.google.common.collect.Iterators emptyIterator(..)"),
                |           "java.util.Collections"
                |        );
                |    }
                |}
            """.trimMargin()

        File(sourceFolder, "$className.java").writeText(source)
        return source
    }

    fun runTasks(vararg tasks: String, fail: Boolean = false): BuildResult {
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(projectDir)
                .withArguments(tasks.toList())
                .withPluginClasspath()
                .run { if (fail) buildAndFail() else build() }
    }

    @Before
    fun setup() {
        projectDir = temp.root
        sourceFolder = File(projectDir, "src/main/java").apply { mkdirs() }
        testSourceFolder = File(projectDir, "src/test/java").apply { mkdirs() }

        File(projectDir, "build.gradle").writeText("""
            plugins {
                id 'java'
                id 'spring.rewrite'
            }

            repositories {
                jcenter()
            }

            dependencies {
                compileOnly 'com.netflix.devinsight.rewrite:rewrite-core:1.2.0'
                compile 'com.google.guava:guava:18.0'
            }
        """)

        File(testSourceFolder, "A.java").writeText("""
                |import com.google.common.collect.Iterators;
                |import java.util.Iterator;
                |public class A {
                |    Iterator<String> empty = Iterators.emptyIterator();
                |}
            """.trimMargin())
    }

    @Test
    fun `does not apply @AutoRewrite if the annotation is not on a static method`() {
        generateRule("NonStatic", "public void fix(Refactor refactor)")

        val output = runTasks("compileTestJava", "fixSourceLint").output
        assertTrue(output, output.contains("NonStatic.fix will be ignored"))
    }

    @Test
    fun `does not apply @AutoRewrite if the annotation is on method that anything other than a single Refactor parameter`() {
        generateRule("WrongNumberOfParams", "public static void fix(Refactor refactor, String extra)")

        runTasks("compileTestJava", "fixSourceLint").output.let {
            assertTrue(it, it.contains("WrongNumberOfParams.fix will be ignored"))
        }
    }

    @Test
    fun `makes necessary changes to the codebase when fixSourceLint is ran`() {
        generateRule("GoodRule", "public static void fix(Refactor refactor)")

        println(runTasks("compileTestJava", "fixSourceLint").output)
        assertEquals("""
            |import java.util.Collections;
            |import java.util.Iterator;
            |public class A {
            |    Iterator<String> empty = Collections.emptyIterator();
            |}
        """.trimMargin(), File(testSourceFolder, "A.java").readText())
    }
}