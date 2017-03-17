package ru.vyarus.gradle.plugin.animalsniffer

import org.gradle.testkit.runner.BuildResult

/**
 * Attempt to reproduce https://github.com/xvik/gradle-animalsniffer-plugin/issues/3
 * (mixed reports from different modules).
 *
 * @author Vyacheslav Rusakov
 * @since 28.10.2016
 */
class MultiModuleUseKitTest extends AbstractKitTest {

    def "Check java checks"() {
        setup:
        build("""
            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath 'net.sf.proguard:proguard-gradle:5.3.1'
                }
            }

            plugins {
                id 'ru.vyarus.animalsniffer'
            }

            subprojects {
                apply plugin: 'java'
                apply plugin: 'ru.vyarus.animalsniffer'

                animalsniffer {
                    ignoreFailures = true
                }

                repositories { mavenCentral()}
                dependencies {
                    // separate ant task (sequentially) called for each signature (increasing entropy)
                    signature 'org.codehaus.mojo.signature:java16:1.0@signature'
                    signature 'org.codehaus.mojo.signature:java15:1.0@signature'
                    signature 'org.codehaus.mojo.signature:java14:1.0@signature'
                    signature 'org.codehaus.mojo.signature:java12:1.0@signature'
                }


                jar {
                  manifest {
                    attributes 'Main-Class': "valid.Sample"
                  }
                }

                task pg(type: proguard.gradle.ProGuardTask, dependsOn: build) {
                    configuration rootProject.file('proguard.txt').absolutePath

                    ignorewarnings

                    injars "build/libs/\${project.name}.jar"
                    outjars 'build/libs/out.jar'
                }
            }
        """)

        // amount of modules in test project
        int cnt = 15

        file('settings.gradle') << ' include ' + (1..cnt).collect { "'mod$it'" }.join(',')
        file('proguard.txt') << """
# Include java runtime classes
#force warnongs -libraryjars  <java.home>/lib/rt.jar

# Output a source map file
-printmapping proguard.map

# Keep filenames and line numbers
-keepattributes SourceFile, LineNumberTable

# Disable certain proguard optimizations which remove stackframes (same as Android defaults)
-optimizations !method/inlining/*

-keep public class * {
    public protected *;
}

-keepclassmembernames class * {
    java.lang.Class class\$(java.lang.String);
    java.lang.Class class\$(java.lang.String, boolean);
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
"""
        // three types of modules (to produce different error reporting and easily detect mixes)
        (1..cnt).each {
            if (it % 2 == 0) {
                fileFromClasspath("mod$it/src/main/java/invalid/Sample.java", '/ru/vyarus/gradle/plugin/animalsniffer/java/invalid/Sample.java')
                fileFromClasspath("mod$it/src/main/java/invalid/Sample2.java", '/ru/vyarus/gradle/plugin/animalsniffer/java/invalid/Sample2.java')
                fileFromClasspath("mod$it/src/main/java/valid/Sample.java", '/ru/vyarus/gradle/plugin/animalsniffer/java/valid/Sample.java')
            } else if (it%3 ==0 ) {
                fileFromClasspath("mod$it/src/main/java/invalid/Sample2.java", '/ru/vyarus/gradle/plugin/animalsniffer/java/invalid/Sample2.java')
                fileFromClasspath("mod$it/src/main/java/valid/Sample.java", '/ru/vyarus/gradle/plugin/animalsniffer/java/valid/Sample.java')
            } else {
                fileFromClasspath("mod$it/src/main/java/valid/Sample.java", '/ru/vyarus/gradle/plugin/animalsniffer/java/valid/Sample.java')
            }
        }

        when: "run check for both modules"
        BuildResult result = run('pg', '--parallel', '--max-workers=15')

        then: "task successful"
        (1..cnt).each {
            File report = file("mod$it/build/reports/animalsniffer/main.text")
            if (it % 2 == 0) {
                assert report.exists()
                assert report.readLines() == [
                        "invalid.Sample:9  Undefined reference: int Boolean.compare(boolean, boolean)",
                        "invalid.Sample:14  Undefined reference: java.nio.file.Path java.nio.file.Paths.get(String, String[])",
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()",
                        "invalid.Sample:9  Undefined reference: int Boolean.compare(boolean, boolean)",
                        "invalid.Sample:14  Undefined reference: java.nio.file.Path java.nio.file.Paths.get(String, String[])",
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()",
                        "invalid.Sample:9  Undefined reference: int Boolean.compare(boolean, boolean)",
                        "invalid.Sample:14  Undefined reference: java.nio.file.Path java.nio.file.Paths.get(String, String[])",
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()",
                        "invalid.Sample:9  Undefined reference: int Boolean.compare(boolean, boolean)",
                        "invalid.Sample:14  Undefined reference: java.nio.file.Path java.nio.file.Paths.get(String, String[])",
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()"
                ]
                println "case 1 ok for $it"
            } else if (it % 3 == 0) {
                assert report.exists()
                assert report.readLines() == [
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()",
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()",
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()",
                        "invalid.Sample2:9  Undefined reference: java.nio.file.FileSystem java.nio.file.FileSystems.getDefault()",
                        "invalid.Sample2:9  Undefined reference: Iterable java.nio.file.FileSystem.getFileStores()"
                ]
                println "case 2 ok for $it"
            } else {
                assert !report.exists()
                println "case 3 ok for $it"
            }
        }


    }
}