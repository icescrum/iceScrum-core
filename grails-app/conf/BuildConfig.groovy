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
    inherits("global") {
        excludes 'grails-plugin-log4j', 'log4j'
    }
    log "warn"
    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()
        mavenCentral()
        mavenRepo "https://jcenter.bintray.com" // For http-builder 7.2 (unavailable in maven central)
        mavenRepo "https://oss.sonatype.org/content/repositories/snapshots/"
        mavenRepo "https://repo.icescrum.org/artifactory/plugins-release/"
        mavenRepo "https://repo.icescrum.org/artifactory/plugins-snapshot/"
        mavenRepo "http://jaspersoft.artifactoryonline.com/jaspersoft/third-party-ce-artifacts/" // Because Jasper depends on olap4j which is not available anymore the repositories
        mavenRepo "https://repo.eclipse.org/content/repositories/mylyn/" // For wikitext
    }
    dependencies {
        compile('org.atmosphere:atmosphere-runtime:2.5.3') {
            excludes 'slf4j-api'
        }
        compile('org.codehaus.groovy.modules.http-builder:http-builder:0.7.2') {
            excludes 'commons-logging', 'xml-apis', 'groovy'
        }
        compile 'org.eclipse.mylyn.docs:org.eclipse.mylyn.wikitext.core:2.8.0-SNAPSHOT' // Cannot use 2.9 or above because it is compiled with Java 8 so it won't work with Java 7
        compile 'org.eclipse.mylyn.docs:org.eclipse.mylyn.wikitext.textile.core:2.8.0-SNAPSHOT'
        compile 'com.google.guava:guava:19.0' // For wikitext (was previously brought by asset-pipeline but they left the dependency: https://github.com/bertramdev/asset-pipeline/pull/117)
        compile 'org.liquibase:liquibase-core:2.0.5'
        compile "org.apache.poi:poi:3.17" // 4.0 works only with Java 8
        compile "org.apache.poi:poi-ooxml:3.17"
        compile "org.apache.poi:ooxml-schemas:1.3" // Recommended by http://poi.apache.org/help/faq.html instead of poi-ooxml-schemas
        compile 'org.apache.logging.log4j:log4j-api:2.17.2'
        compile 'org.apache.logging.log4j:log4j-core:2.17.2'
        compile 'org.apache.logging.log4j:log4j-1.2-api:2.17.2'
        compile 'org.apache.logging.log4j:log4j-slf4j-impl:2.17.2'
    }
    plugins {
        compile ':atmosphere-meteor:1.0.5'
        compile ':spring-security-core:2.0.0'
        compile ':spring-security-acl:2.0.1'
        compile ':spring-security-oauth2-provider:2.0-RC5'
        compile ':hd-image-utils:1.1'
        compile 'org.icescrum:taggable:1.1.9'
        compile ':jdbc-pool:7.0.47'
        compile 'org.icescrum:mail:1.0.9' // Forked because of https://github.com/gpc/grails-mail/issues/32 which prevents config change
        compile(':jasper:1.11.0') {
            excludes 'poi', 'poi-ooxml', 'poi-ooxml-schemas' // 3.10 brought by jasper is wayyy outdated
        }
        compile ':feeds:1.6'
        compile ':cache:1.1.8'
        compile ':cache-ehcache:1.0.5'

        runtime ':hibernate4:4.3.10'
        build(':release:3.1.2') {
            export = false
        }
        compile 'org.icescrum:icescrum-attachmentable:1.1.0'
        compile 'org.icescrum:commentable:1.3'
    }
}
