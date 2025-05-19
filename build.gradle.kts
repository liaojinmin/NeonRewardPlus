
val taboolibVersion: String by project

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("io.izzel.taboolib") version "1.60"
}

taboolib {
    install(
        "common",
        "common-5",
        "platform-bukkit",
        "module-configuration",
        "module-chat",
        "module-lang",
        "module-ui",
        "module-kether",
        "module-metrics",
    )
    description {
        bukkitApi("1.13")
        contributors {
            name("HSDLao_liao")
        }
        dependencies {
            name("PlayerPoints").optional(true)
            name("PlaceholderAPI").optional(true)
            name("Vault").optional(true)
            name("XConomy").optional(true)
            name("GeekEconomy").optional(true)
            name("CMI").optional(true)
        }
    }

    relocate("com.zaxxer.hikari", "com.zaxxer.hikari_4_0_3_rw")
    classifier = null
    version = "6.0.12-69"
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.tabooproject.org/repository/releases")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    maven("https://jitpack.io")

}


dependencies {

    // Libraries
    compileOnly(fileTree("lib"))
    compileOnly(kotlin("stdlib"))
    // Server Core
    compileOnly("com.github.YiC200333:XConomyAPI:2.19.1")
    compileOnly("com.zaxxer:HikariCP:4.0.3")
    compileOnly("ink.ptms.core:v11701:11701-minimize:mapped")
    compileOnly("ink.ptms.core:v11701:11701-minimize:universal")
    compileOnly("ink.ptms.core:v11604:11604")

    // Hook Plugins
    compileOnly("me.clip:placeholderapi:2.10.9") { isTransitive = false }
    //compileOnly("com.github.MilkBowl:VaultAPI:-SNAPSHOT") { isTransitive = false }
    compileOnly("org.black_ixx:playerpoints:3.1.1") { isTransitive = false }
}

