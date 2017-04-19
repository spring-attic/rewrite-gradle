package io.spring.rewrite.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RewritePluginTest {

    @JvmField @Rule
    val temp = TemporaryFolder()

    lateinit var projectDir: File
    lateinit var sourceFolder: File
    lateinit var testSourceFolder: File
    val fixGuava = """
            |import com.netflix.rewrite.refactor.Refactor;
            |import com.netflix.rewrite.auto.AutoRewrite;
            |
            |public class FixGuava {
            |    @AutoRewrite(value = "guava-deprecations", description = "fix Guava deprecations")
            |    public static void fixIterators(Refactor refactor) {
            |        refactor.changeMethodTargetToStatic(
            |           refactor.getOriginal().findMethodCalls("com.google.common.collect.Iterators emptyIterator(..)"),
            |           "java.util.Collections"
            |        );
            |    }
            |}
        """.trimMargin()

    @Before
    fun setup() {
        projectDir = temp.root

        File(projectDir, "build.gradle").writeText("""
            plugins {
                id 'java'
                id 'spring.rewrite'
            }

            repositories {
                jcenter()
            }

            dependencies {
                compileOnly 'com.netflix.devinsight.rewrite:rewrite-core:1.1.2'
                compile 'com.google.guava:guava:18.0'
            }
        """)

        sourceFolder = File(projectDir, "src/main/java")
        sourceFolder.mkdirs()

        testSourceFolder = File(projectDir, "src/test/java")
        testSourceFolder.mkdirs()
    }

    @Test
    fun `@AutoRewrite must be on a static method`() {
        File(sourceFolder, "FixGuava.java").writeText(fixGuava.replace("public static", "public"))
        File(testSourceFolder, "A.java").writeText("class A {}")

        val output = runTasks(projectDir, "compileTestJava", "fixSourceLint").output
        assertTrue(output, output.contains("FixGuava.fixIterators will be ignored"))
    }

    @Test
    fun `@AutoRewrite must be on a method that takes a single Refactor parameter`() {
        File(sourceFolder, "FixGuava.java").writeText(fixGuava.replace("fixIterators(Refactor refactor)", "fixIterators()"))
        File(testSourceFolder, "A.java").writeText("class A {}")

        val output = runTasks(projectDir, "compileTestJava", "fixSourceLint").output
        assertTrue(output, output.contains("FixGuava.fixIterators will be ignored"))
    }

    @Test
    fun `fixSourceLint makes necessary changes to the codebase`() {
        File(sourceFolder, "FixGuava.java").writeText(fixGuava)

        val a = File(testSourceFolder, "A.java")
        a.writeText("""
            |import com.google.common.collect.Iterators;
            |import java.util.Iterator;
            |public class A {
            |    Iterator<String> empty = Iterators.emptyIterator();
            |}
        """.trimMargin())

        println(runTasks(projectDir, "compileTestJava", "fixSourceLint").output)

        assertEquals("""
            |import java.util.Collections;
            |import java.util.Iterator;
            |public class A {
            |    Iterator<String> empty = Collections.emptyIterator();
            |}
        """.trimMargin(), a.readText())
    }

    private fun runTasks(projectDir: File?, vararg tasks: String): BuildResult {
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(projectDir)
                .withArguments(tasks.toList())
                .withPluginClasspath()
                .build()
    }
}