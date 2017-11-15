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

import com.netflix.rewrite.auto.AutoRewrite
import com.netflix.rewrite.parse.OracleJdkParser
import com.netflix.rewrite.refactor.Refactor
import org.gradle.api.*
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class RewritePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("rewrite", RewriteExtension::class.java)

        project.plugins.withType(JavaPlugin::class.java) {
            project.tasks.create("lintSource", RewriteTask::class.java)
            project.tasks.create("fixSourceLint", RewriteAndFixTask::class.java)

            project.plugins.withType(JavaBasePlugin::class.java) {
                project.tasks.withType(AbstractCompile::class.java) { task ->
                    // auto-linting does not force compilation
                    project.rootProject.tasks.getByName("lintSource").dependsOn(task)
                    project.rootProject.tasks.getByName("fixSourceLint").dependsOn(task)
                }
            }
        }
    }
}

typealias RewriteStats = Map<AutoRewrite, Int>

abstract class AbstractRewriteTask : DefaultTask() {
    val extension: RewriteExtension = project.extensions.getByType(RewriteExtension::class.java)

    fun refactor(afterRefactor: (Refactor) -> Any?): RewriteStats {
        return project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.fold(mutableMapOf<AutoRewrite, Int>()) { stats, ss ->
            val asts = OracleJdkParser(ss.compileClasspath.map(File::toPath)).parse(ss.allJava.map(File::toPath))
            val runners = RewriteScanner(ss.compileClasspath).rewriteRunnersOnClasspath()

            asts.forEach { cu ->
                val refactor = cu.refactor()
                runners.forEach { (rule, op) ->
                    op.invoke(refactor)
                    stats.merge(rule, refactor.stats().values.sum(), Int::plus)
                }
                afterRefactor.invoke(refactor)
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

            if(extension.failOnLint)
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
            textOutput.println("Please review changes and commit.")
            stats.entries.forEachIndexed { i, (rewrite, count) ->
                textOutput.text("   ${i + 1}. ")
                textOutput.withStyle(Styling.Bold).text(rewrite.value)
                textOutput.println(" requires $count changes to ${rewrite.description}")
            }
        }
    }
}