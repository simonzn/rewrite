plugins {
    id("org.openrewrite.build.language-library")
    id("jvm-test-suite")
}

val javaTck = configurations.create("javaTck") {
    isTransitive = false
}

dependencies {
    api(project(":rewrite-core"))
    api(project(":rewrite-java"))
    implementation(project(":rewrite-java-lombok"))

    compileOnly("org.slf4j:slf4j-api:1.7.+")

    implementation("io.micrometer:micrometer-core:1.9.+")
    implementation("io.github.classgraph:classgraph:latest.release")
    implementation("org.ow2.asm:asm:latest.release")

    testImplementation(project(":rewrite-test"))
    "javaTck"(project(":rewrite-java-tck"))
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.assertj" && requested.name == "assertj-core") {
                useVersion("3.+") // Pin to latest 3.+ version as AssertJ 4 requires Java 17
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.withType<JavaCompile>().configureEach {
    // allows --add-exports to in spite of the JDK's restrictions on this
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()

    options.release.set(null as? Int?) // remove `--release 8` set in `org.openrewrite.java-base`
    options.compilerArgs.addAll(
        listOf(
            "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        )
    )
}

//Javadoc compiler will complain about the use of the internal types.
tasks.withType<Javadoc>().configureEach {
    exclude(
        "**/ReloadableJava11JavadocVisitor**",
        "**/ReloadableJava11Parser**",
        "**/ReloadableJava11ParserVisitor**",
        "**/ReloadableJava11TypeMapping**",
        "**/ReloadableJava11TypeSignatureBuilder**"
    )
}

testing {
    suites {
        val test by getting(JvmTestSuite::class)

        register("compatibilityTest", JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation(project(":rewrite-test"))
                implementation(project(":rewrite-java-tck"))
                implementation(project(":rewrite-java-test"))
                implementation("org.assertj:assertj-core:latest.release")
            }

            targets {
                all {
                    testTask.configure {
                        useJUnitPlatform()
                        testClassesDirs += files(javaTck.files.map { zipTree(it) })
                        jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("compatibilityTest"))
}
