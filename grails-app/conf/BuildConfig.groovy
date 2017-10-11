/*
 * Copyright (c) 2015 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.dependency.resolver = "maven"

grails.project.dependency.resolution = {
    inherits("global") {}
    log "warn"
    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()
        mavenCentral()
        mavenRepo "http://repo.spring.io/milestone"
        mavenRepo "http://repo.spring.io/libs-release" // for http-builder 0.7.2
        mavenRepo "https://oss.sonatype.org/content/repositories/snapshots/"
        mavenRepo "http://repo.icescrum.org/artifactory/plugins-release/"
        mavenRepo "http://repo.icescrum.org/artifactory/plugins-snapshot/"
        mavenRepo "http://jaspersoft.artifactoryonline.com/jaspersoft/third-party-ce-artifacts/" // Because Jasper depends on olap4j which is not available anymore the repositories
        mavenRepo "https://repo.eclipse.org/content/repositories/mylyn/" // For wikitext
    }
    dependencies {
        compile 'org.atmosphere:atmosphere-runtime:2.4.9', {
            excludes 'slf4j-api'
        }
        compile('org.codehaus.groovy.modules.http-builder:http-builder:0.7.2') {
            excludes 'commons-logging', 'xml-apis', 'groovy'
        }
        compile 'org.eclipse.mylyn.docs:org.eclipse.mylyn.wikitext.core:2.8.0-SNAPSHOT' // Cannot use 2.9 or above because it is compiled with Java 8 so it won't work with Java 7
        compile 'org.eclipse.mylyn.docs:org.eclipse.mylyn.wikitext.textile.core:2.8.0-SNAPSHOT'
        compile 'com.google.guava:guava:15.0' // For wikitext (was previously brought by asset-pipeline but they left the dependency: https://github.com/bertramdev/asset-pipeline/pull/117)
    }
    plugins {
        compile ':atmosphere-meteor:1.0.5'
        compile ':spring-security-core:2.0.0'
        compile ':spring-security-acl:2.0.1'
        compile ':hd-image-utils:1.1'
        compile 'org.icescrum:taggable:1.1.3'
        compile ':jdbc-pool:7.0.47'
        compile 'org.icescrum:mail:1.0.9' // Forked because of https://github.com/gpc/grails-mail/issues/32 which prevents config change
        compile ':jasper:1.11.0'
        compile ':feeds:1.6'
        compile ':cache:1.1.8'
        compile ':cache-ehcache:1.0.5'

        runtime ':hibernate4:4.3.10'
        build(':release:3.1.2') {
            export = false
        }
        compile 'org.icescrum:icescrum-attachmentable:1.0.3'
        compile 'org.icescrum:commentable:1.3'
    }
}