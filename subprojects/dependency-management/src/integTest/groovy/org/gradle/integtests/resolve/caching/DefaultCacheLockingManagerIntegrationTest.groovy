/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.resolve.caching

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree
import org.gradle.cache.internal.IndexedCacheBackedFileAccessJournal
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule
import spock.lang.Unroll

import static java.util.concurrent.TimeUnit.DAYS

class DefaultCacheLockingManagerIntegrationTest extends AbstractHttpDependencyResolutionTest {
    private final static long MAX_CACHE_AGE_IN_DAYS = LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_EXTERNAL_CACHE_ENTRIES

    def snapshotModule = mavenHttpRepo.module('org.example', 'example', '1.0-SNAPSHOT').publish().allowAll()

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    def "does not clean up resources and files that were recently used from caches"() {
        given:
        buildscriptWithDependency(snapshotModule)

        when:
        succeeds 'resolve'

        then:
        def resources = findFiles(cacheDir, 'resources-*/**/maven-metadata.xml')
        resources.size() == 1
        def files = findFiles(cacheDir, "files-*/**/*")
        files.size() == 2
        def metadata = findFiles(cacheDir, "metadata-*/descriptors/**/*")
        metadata.size() == 1

        when:
        markForCleanup(gcFile)

        and:
        succeeds 'tasks'

        then:
        resources[0].assertExists()
        files[0].assertExists()
        files[1].assertExists()
        metadata[0].assertExists()
    }

    def "cleans up resources, files and metadata that were not recently used from caches"() {
        given:
        buildscriptWithDependency(snapshotModule)

        when:
        succeeds 'resolve'

        then:
        def resources = findFiles(cacheDir, 'resources-*/**/maven-metadata.xml')
        resources.size() == 1
        def files = findFiles(cacheDir, "files-*/**/*")
        files.size() == 2
        def metadata = findFiles(cacheDir, "metadata-*/descriptors/**/*")
        metadata.size() == 1
        journal().assertExists()

        when:
        journal().delete() // delete journal to destroy last access information
        markForCleanup(gcFile) // force cleanup

        and: // last modified timestamp is used when journal does not exist
        markForCleanup(resources[0].parentFile)
        markForCleanup(files[0].parentFile)
        markForCleanup(files[1].parentFile)
        markForCleanup(metadata[0].parentFile)

        and:
        succeeds 'tasks'

        then:
        resources[0].assertDoesNotExist()
        files[0].assertDoesNotExist()
        files[1].assertDoesNotExist()
        metadata[0].assertDoesNotExist()
    }

    @Unroll
    def "downloads deleted artifacts and metadata again when deleting #filesToDelete"() {
        given:
        buildscriptWithDependency(snapshotModule)

        when:
        succeeds 'resolve'

        then:
        def resources = findFiles(cacheDir, 'resources-*/**/maven-metadata.xml')
        resources.size() == 1
        def files = findFiles(cacheDir, "files-*/**/*")
        files.size() == 2
        def metadata = findFiles(cacheDir, "metadata-*/descriptors/**/*")
        metadata.size() == 1

        when:
        findFiles(cacheDir, filesToDelete).each { it.delete() }

        and:
        succeeds 'resolve'

        then:
        metadata[0].parentFile.assertExists()
        files.findAll { it.name.endsWith(".jar") }.each { it.assertExists() }

        where:
        filesToDelete << ['files-*/**/*', 'metadata-*/descriptors/**/*']
    }

    def "marks artifacts as recently used when accessed"() {
        given:
        buildscriptWithDependency(snapshotModule)

        when:
        succeeds 'resolve'

        and:
        journal().delete()

        then:
        succeeds 'resolve'

        and:
        journal().assertExists()
    }

    private TestFile journal() {
        def journal = findFiles(cacheDir, "metadata-*/" + IndexedCacheBackedFileAccessJournal.CACHE_NAME + ".bin")
        journal.size() == 1
        journal[0]
    }

    private static List<TestFile> findFiles(File baseDir, String includePattern) {
        List<TestFile> files = []
        new SingleIncludePatternFileTree(baseDir, includePattern).visit(new FileVisitor() {
            @Override
            void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            void visitFile(FileVisitDetails fileDetails) {
                files.add(new TestFile(fileDetails.file))
            }
        })
        return files
    }

    private void buildscriptWithDependency(MavenModule module) {
        buildFile.text = """
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
            configurations {
                custom
            }
            dependencies {
                custom group: '${module.groupId}', name: '${module.artifactId}', version: '${module.version}'
            }
            task resolve {
                doLast {
                    configurations.custom.incoming.files.each { println it }
                }
            }
        """
    }

    TestFile getGcFile() {
        return cacheDir.file("gc.properties")
    }

    TestFile getCacheDir() {
        return executer.gradleUserHomeDir.file("caches", "modules-2")
    }

    void markForCleanup(File file) {
        file.lastModified = System.currentTimeMillis() - DAYS.toMillis(MAX_CACHE_AGE_IN_DAYS + 1)
    }
}
