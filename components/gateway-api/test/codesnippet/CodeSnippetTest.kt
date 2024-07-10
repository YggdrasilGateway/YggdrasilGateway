package com.kasukusakura.yggdrasilgateway.api.codesnippet

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFails

class CodeSnippetTest {
    @Test
    fun compileNoPackage() = runTest {
        CodeSnippetProvider.compileCodeSnippet(
            "test", """
            public class AnyRun {
                public static void run() {}
            }
        """.trimIndent(), Runnable::class.java
        )
    }

    @Test
    fun compileWithPackage() = runTest {
        CodeSnippetProvider.compileCodeSnippet(
            "test", """
            package test;
            public class AnyRun {
                public static void run() {}
            }
        """.trimIndent(), Runnable::class.java
        )
    }

    interface RunnableHandle {
        fun invoke(act: Runnable)
    }

    @Test
    fun testInvoke() = runTest {

        var counter = 0
        CodeSnippetProvider.compileCodeSnippet(
            "test", """
            public class AnyRun {
                public static void invoker(Runnable act) {act.run();act.run();act.run();}
            }
        """.trimIndent(), RunnableHandle::class.java
        ).also(::println).invoke {
            counter++
        }
        assertEquals(3, counter)
    }

    @Test
    fun failWhenMultiTopLevel() = runTest {
        assertFails {
            CodeSnippetProvider.compileCodeSnippet(
                "test", """
            public class AnyRun {}
            public class AnyRun2 {}
        """.trimIndent(), Runnable::class.java
            )
        }
    }

    @Test
    fun failWhenMultiImplementation() = runTest {
        assertFails {
            CodeSnippetProvider.compileCodeSnippet(
                "test", """
            public class AnyRun {
                public static void run() {}
                public static void run2() {}
            }
        """.trimIndent(), Runnable::class.java
            )
        }
    }

    @Test
    fun compileWhenWithPrivate() = runTest {
        CodeSnippetProvider.compileCodeSnippet(
            "test", """
            public class AnyRun {
                public static void run() {}
                private static void run2() {}
            }
        """.trimIndent(), Runnable::class.java
        )
    }

    @Test
    fun failWhenNoImplementation() = runTest {
        assertFails {
            CodeSnippetProvider.compileCodeSnippet(
                "test", """
            public class AnyRun {
            }
        """.trimIndent(), Runnable::class.java
            )
        }
    }

    @Test
    fun failWhenNoImplementationAllPrivate() = runTest {
        assertFails {
            CodeSnippetProvider.compileCodeSnippet(
                "test", """
            public class AnyRun {
                private static void run() {}
                private static void run2() {}
            }
        """.trimIndent(), Runnable::class.java
            )
        }
    }
}