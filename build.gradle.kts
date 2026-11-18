// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "imagetopdf")
        property("sonar.projectName", "Image to PDF")
        property("sonar.sourceEncoding", "UTF-8")
    }
}

project(":app") {
    sonar {
        properties {
            property(
                "sonar.java.binaries",
                "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
            )
            property(
                "sonar.junit.reportPaths",
                "build/test-results/testDebugUnitTest"
            )
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                "build/reports/coverage/test/debug/report.xml"
            )
            property(
                "sonar.androidLint.reportPaths",
                "build/reports/lint-results-debug.xml"
            )
        }
    }
}