/*
 * Copyright (c) 2014 Kagilum SAS.
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
//grails.project.war.file = "target/${appName}-${appVersion}.war"

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()

        // uncomment the below to enable remote dependency resolution
        // from public Maven repositories
        //mavenLocal()
        mavenCentral()
        mavenRepo "http://repo.spring.io/milestone"
        mavenRepo "https://oss.sonatype.org/content/repositories/snapshots/"
        mavenRepo "http://repo.icescrum.org/artifactory/plugins-release/"
        mavenRepo "http://repo.icescrum.org/artifactory/plugins-snapshot/"
        //mavenRepo "http://snapshots.repository.codehaus.org"
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"
    }

    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        compile('org.codehaus.groovy.modules.http-builder:http-builder:0.5.0') {
            excludes "commons-logging", "xml-apis", "groovy"
        }
        compile('org.atmosphere:atmosphere-runtime:1.0.15'){
            excludes 'slf4j-api', 'atmosphere-ping'
        }
    }

    plugins {
        compile ':spring-security-core:2.0-RC4'

        //TODO remove org.icescrum when grails team will have update plugin
        compile 'org.icescrum:spring-security-acl:2.0-RC1'

        compile ":hd-image-utils:1.1"
        compile 'org.icescrum:fluxiable:0.3.2'
        compile 'org.icescrum:icescrum-attachmentable:1.0.1'
        compile 'org.icescrum:commentable:1.3'
        compile ':taggable:1.1.0'
        compile ':jdbc-pool:7.0.47'
        compile ':mail:1.0.7'
        compile ':spring-events:1.2'// TODO Remove
        compile ':jasper:1.10.0'
        compile ':rollback-on-exception:0.1'
        compile ':wikitext:0.1.2'
        compile ":feeds:1.6"
        runtime(":hibernate4:4.3.5.5") {
            export = false
        }
    }
}