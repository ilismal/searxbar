// Fuerza versiones parcheadas de dependencias transitivas del classpath de AGP
// (alertas de Dependabot). Quitar cuando AGP las traiga por sí mismo.
buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.85")
            classpath("com.google.guava:guava:33.7.2-jre")
            classpath("org.apache.commons:commons-lang3:3.18.0")
        }
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
}
