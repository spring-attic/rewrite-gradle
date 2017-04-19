package io.spring.rewrite.gradle

import com.netflix.rewrite.auto.AutoRewrite
import com.netflix.rewrite.parse.OracleJdkParser
import com.netflix.rewrite.refactor.Refactor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class RewritePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType(JavaPlugin::class.java) {
            project.tasks.create("lintSource", RewriteTask::class.java)
            project.tasks.create("fixSourceLint", RewriteAndFixTask::class.java)
        }
    }
}

typealias RewriteStats = Map<AutoRewrite, Int>

abstract class AbstractRewriteTask : DefaultTask() {
    fun refactor(afterRefactor: (Refactor) -> Any?): RewriteStats {
        return project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.fold(mutableMapOf<AutoRewrite, Int>()) { stats, ss ->
            val asts = OracleJdkParser(ss.compileClasspath.map(File::toPath)).parse(ss.allJava.map(File::toPath))
            val runners = RewriteScanner(ss.compileClasspath).rewriteRunnersOnClasspath()

            asts.forEach { cu ->
                runners.forEach { runner ->
                    val refactor = cu.refactor()
                    runner.op.invoke(refactor)
                    afterRefactor.invoke(refactor)
                    stats.merge(runner.rule, refactor.stats().values.sum(), Int::plus)
                }
            }

            stats
        }
    }
}

open class RewriteTask : AbstractRewriteTask() {

    @TaskAction
    fun refactorSourceStats() {
        val textOutput = StyledTextService(services)

        val stats = refactor {}

        if (stats.isNotEmpty()) {
            textOutput.withStyle(Styling.Red).println("\u2716 Your source code requires refactoring.")
            textOutput.text("Run").withStyle(Styling.Bold).text("./gradlew fixSourceLint")
            textOutput.println(" to automatically fix")

            stats.entries.forEachIndexed { i, (rewrite, count) ->
                textOutput.text("   ${i + 1}. ")
                textOutput.withStyle(Styling.Bold).text(rewrite.value)
                textOutput.println(" requires $count changes to ${rewrite.description}")
            }

            throw GradleException("This project requires refactoring. Run ./gradlew fixSourceLint to automatically fix.")
        }
    }
}

open class RewriteAndFixTask : AbstractRewriteTask() {

    @TaskAction
    fun refactorSource() {
        val textOutput = StyledTextService(services)

        val stats = refactor { refactor ->
            if (refactor.stats().values.sum() > 0) {
                Files.newBufferedWriter(Paths.get(refactor.original.sourcePath)).use {
                    it.write(refactor.fix().print())
                }
            }
        }

        if (stats.isNotEmpty()) {
            textOutput.withStyle(Styling.Red).text("\u2716 Your source code requires refactoring. ")
            textOutput.text("Please refactor ")
            textOutput.withStyle(Styling.Bold).text("git diff")
            textOutput.println(", review, and commit changes.")
            stats.entries.forEachIndexed { i, (rewrite, count) ->
                textOutput.text("   ${i + 1}. ")
                textOutput.withStyle(Styling.Bold).text(rewrite.value)
                textOutput.println(" requires $count changes to ${rewrite.description}")
            }
        }
    }
}