package io.spring.rewrite.gradle

import com.netflix.rewrite.auto.AutoRewrite
import com.netflix.rewrite.refactor.Refactor

data class AutoRewriteRunner(val rule: AutoRewrite, val op: (Refactor) -> Any?)