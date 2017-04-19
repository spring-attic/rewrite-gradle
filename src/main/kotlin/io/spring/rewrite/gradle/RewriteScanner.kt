package io.spring.rewrite.gradle;

import com.netflix.rewrite.auto.AutoRewrite
import com.netflix.rewrite.refactor.Refactor
import eu.infomas.annotation.AnnotationDetector
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Modifier
import java.net.URLClassLoader

class RewriteScanner(classpath: Iterable<File>) {
    private val logger = LoggerFactory.getLogger(RewriteScanner::class.java)

    val filteredClasspath = classpath.filter {
        val fn = it.name.toString()
        if(it.isDirectory) true
        else if(fn.endsWith(".class")) true
        else fn.endsWith(".jar") && !fn.endsWith("-javadoc.jar") && !fn.endsWith("-sources.jar")
    }

    fun rewriteRunnersOnClasspath(): Collection<AutoRewriteRunner> {
        val scanners = mutableListOf<AutoRewriteRunner>()
        val classLoader = URLClassLoader(filteredClasspath.map { it.toURI().toURL() }.toTypedArray(), this::class.java.classLoader)

        val reporter = object: AnnotationDetector.MethodReporter {
            override fun annotations() = arrayOf(AutoRewrite::class.java)

            override fun reportMethodAnnotation(annotation: Class<out Annotation>?, className: String?, methodName: String?) {
                val clazz = Class.forName(className, true, classLoader)
                val method = clazz.getMethod(methodName, Refactor::class.java)

                if(method == null || !Modifier.isStatic(method.modifiers)) {
                    logger.warn("$className.$methodName will be ignored. To be useable, an @AutoRewrite method must be static and take a single Refactor argument.")
                    return
                }

                scanners.add(AutoRewriteRunner(method.getAnnotation(AutoRewrite::class.java)) { r: Refactor ->
                    method.invoke(clazz, r)
                })
            }
        }

        AnnotationDetector(reporter).detect(*filteredClasspath.map { it }.toTypedArray())
        return scanners
    }
}