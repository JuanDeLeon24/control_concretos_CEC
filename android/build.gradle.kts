import org.gradle.api.tasks.Delete

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val rootBuildDirectory = rootProject.layout.buildDirectory
    .dir("../build")
    .get()

rootProject.layout.buildDirectory.set(rootBuildDirectory)

subprojects {
    val subprojectBuildDirectory = rootBuildDirectory.dir(project.name)
    project.layout.buildDirectory.set(subprojectBuildDirectory)
}

subprojects {
    if (project.name != "app") {
        project.evaluationDependsOn(":app")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
