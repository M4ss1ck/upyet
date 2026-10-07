plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.spotless)
}

/**
 * Release signing material lives outside the repository, the same way the sibling projects do it:
 * `~/.config/upyet/android-signing/{android-release.jks,credentials.env}`, created on first run by
 * `scripts/build-android-release.sh`. Environment variables take precedence so CI can inject secrets
 * without writing the file. When nothing is configured the release build stays unsigned rather than
 * failing, so `assembleRelease` still works for lint/CI smoke builds.
 */
val releaseSigning: ReleaseSigningMaterial? = resolveReleaseSigning(providers)

/** SemVer string from gradle.properties; the only place the version is written down. */
val appVersionName: String = providers.gradleProperty("upyet.version").get()

/**
 * Monotonic versionCode derived from the SemVer string: 0.1.0 -> 100, 1.2.3 -> 10203. Deriving it means a
 * version bump can never ship with a stale code, which the Play Store and sideloaded upgrades both reject.
 */
val appVersionCode: Int = versionCodeOf(appVersionName)

android {
    namespace = "dev.upyet"
    compileSdk = 37

    sourceSets["androidTest"].assets.srcDir(
        "$projectDir/schemas",
    )

    defaultConfig {
        applicationId = "dev.upyet"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { material ->
            create("release") {
                storeFile = material.keystore
                storePassword = material.password
                keyAlias = material.alias
                keyPassword = material.password
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt",
                ),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    androidResources {
        // Generates the LocaleConfig from the values-* folders present, which is what puts UpYet in the
        // system's per-app language screen. res/resources.properties declares values/ as the English default.
        generateLocaleConfig = true
        // AndroidX ships ~70 locales; without this the APK carries partial translations we never wrote.
        localeFilters += listOf("en", "es")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            // Zero-warning policy: a compiler warning fails the build, so new ones are fixed when they appear
            // instead of piling up until a release. Fix the cause; do not add blanket suppressions.
            allWarningsAsErrors = true
            // Opts into the annotation-target default Kotlin is moving to (KT-73255): an annotation on a
            // constructor property, such as a Hilt qualifier or @StringRes, applies to the parameter and the
            // backing field. Without it every such site warns that the default is about to change.
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Zero-warning policy, same as the compiler. lint.xml lists the few checks that are informational
        // instead, because their result depends on today's date or on Maven, not on this code.
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        lintConfig = file("lint.xml")
    }

    packaging {
        resources.excludes +=
            setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Version the artifact names so a downloaded APK is self-identifying in a bug report.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("upyet-$appVersionName-${variant.name}.apk")
        }
    }
}

/*
 * Room's MigrationTestHelper parses the exported schema JSON with kotlinx-serialization, and
 * room-migration 2.8.5 is compiled against serialization 1.8.1. androidx.savedstate 1.4.0, pulled in
 * through lifecycle and navigation, requires serialization 1.7.3 *strictly*, and that strict constraint
 * silently downgraded json to 1.7.3 - so Room's generated serializers met a GeneratedSerializer interface
 * that no longer matched and every MigrationTestHelper call died with AbstractMethodError.
 *
 * A strict constraint cannot be lifted by asking for a newer version or a newer BOM; both fail to resolve.
 * Forcing is the mechanism Gradle provides for exactly this. Scoped to the androidTest classpaths, because
 * room-migration is test-only and the application's own classpaths resolve 1.7.3 consistently with no
 * conflict to fix. Core is forced alongside json: json 1.8.1 against core 1.7.3 is the same mismatch again.
 *
 * Remove this once savedstate ships a release built against serialization 1.8.x.
 */
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        // The -jvm variants carry the same strict constraint and are the artifacts that actually land on
        // the classpath, so forcing only the top-level modules leaves 1.7.3 jars in place.
        force(
            libs.kotlinx.serialization.core,
            libs.kotlinx.serialization.json,
            libs.kotlinx.serialization.core.jvm,
            libs.kotlinx.serialization.json.jvm,
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(
        libs.androidx.lifecycle.viewmodel.compose,
    )
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    // androidTest only: the device-level e2e flows drive the ringing screen from outside the app.
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}

spotless {
    // Kotlin's official code style (ktlint calls it "intellij_idea"); ktlint's own opinionated
    // "ktlint_official" style is deliberately not used.
    val ktlintConfig =
        mapOf(
            "ktlint_code_style" to "intellij_idea",
            "max_line_length" to "140",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        )
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintConfig)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintConfig)
    }
}

/** 0.1.0 -> 100, 1.2.3 -> 10203. Each component is capped at 99 so the ordering can never invert. */
fun versionCodeOf(version: String): Int {
    val parts = version.split(".")
    require(parts.size == 3) { "upyet.version must be MAJOR.MINOR.PATCH, was \"$version\"" }
    val (major, minor, patch) =
        parts.map { part ->
            val number = part.toIntOrNull()
            require(number != null && number in 0..99) { "version component out of range in \"$version\"" }
            number
        }
    return major * 10_000 + minor * 100 + patch
}

class ReleaseSigningMaterial(val keystore: File, val alias: String, val password: String)

/**
 * Reads the signing material from the environment first and from the shared credentials file second.
 * Both are read through [ProviderFactory] so the configuration cache tracks them as build inputs.
 */
fun resolveReleaseSigning(providers: ProviderFactory): ReleaseSigningMaterial? {
    val credentialsFile = File(System.getProperty("user.home"), ".config/upyet/android-signing/credentials.env")
    val fileValues =
        providers
            .fileContents(layout.projectDirectory.file(credentialsFile.absolutePath))
            .asText
            .orNull
            ?.lineSequence()
            ?.mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0 || line.startsWith("#")) {
                    null
                } else {
                    // printf %q may quote values; the shell forms we emit are plain or single-quoted.
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim().trim('\'')
                }
            }?.toMap()
            .orEmpty()

    fun value(name: String): String? = providers.environmentVariable(name).orNull ?: fileValues[name]

    val keystorePath = value("UPYET_ANDROID_KEYSTORE") ?: return null
    val alias = value("UPYET_ANDROID_KEY_ALIAS") ?: return null
    val password = value("UPYET_ANDROID_KEYSTORE_PASSWORD") ?: return null
    val keystore = File(keystorePath)
    if (!keystore.isFile) {
        logger.warn("Release signing skipped: keystore $keystorePath does not exist. Run scripts/build-android-release.sh.")
        return null
    }
    return ReleaseSigningMaterial(keystore, alias, password)
}
