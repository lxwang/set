// AGP 9 compiles Kotlin itself (built-in Kotlin), so only the Android plugin and
// the Compose compiler plugin are applied here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
