plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(21) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.register<JavaExec>("exportCases") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.kirinonakar.calcmax.math.CasesKt")
    args(rootProject.layout.projectDirectory.file("build/math-cases.json").asFile.absolutePath)
}
