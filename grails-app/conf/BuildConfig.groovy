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
        mavenRepo "https://oss.sonatype.org/content/repositories/snapshots/"
        mavenRepo "http://repo.icescrum.org/artifactory/plugins-release/"
        mavenRepo "http://repo.icescrum.org/artifactory/plugins-snapshot/"
    }
    dependencies {
        compile 'org.atmosphere:atmosphere-runtime:2.2.6', {
            excludes 'slf4j-api'
        }
        compile('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1') {
            excludes 'commons-logging', 'xml-apis', 'groovy'
        }
    }
    plugins {
        compile ':atmosphere-meteor:1.0.4'
        compile ':spring-security-core:2.0-RC4'
        compile ':spring-security-acl:2.0-RC2'
        compile ':hd-image-utils:1.1'
        compile ':taggable:1.1.0'
        compile ':jdbc-pool:7.0.47'
        compile ':mail:1.0.7'
        compile ':spring-events:1.2'// TODO Remove
        compile ':jasper:1.11.0'
        compile ':rollback-on-exception:0.1'
        compile ':wikitext:0.1.2'
        compile ':feeds:1.6'
        runtime(':hibernate4:4.3.8.1') {
            export = false
        }
        build  (':release:3.1.1') {
            export = false
        }
        compile 'org.icescrum:icescrum-attachmentable:1.0.1'
        compile 'org.icescrum:commentable:1.3'
    }
}