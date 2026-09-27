pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

// Suppress benign KSP IntelliJ Core teardown race on AWT-EventQueue-0 in the Gradle daemon JVM
val previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
  val messageAndStack = (throwable.message ?: "") + "\n" + throwable.stackTraceToString()
  if (
    thread.name.startsWith("AWT-EventQueue") &&
      (messageAndStack.contains("ksp.com.intellij.openapi") ||
        messageAndStack.contains("BinaryFileTypeDecompilers") ||
        messageAndStack.contains("ApplicationManager.getApplication"))
  ) {
    return@setDefaultUncaughtExceptionHandler
  }
  if (previousUncaughtHandler != null) {
    previousUncaughtHandler.uncaughtException(thread, throwable)
  } else if (throwable !is ThreadDeath) {
    System.err.print("Exception in thread \"${thread.name}\" ")
    throwable.printStackTrace(System.err)
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Nelitv"

include(":app")
