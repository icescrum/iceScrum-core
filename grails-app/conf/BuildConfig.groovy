/*
 * Copyright (c) 2010 iceScrum Technologies.
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
        compile('org.atmosphere:atmosphere-runtime:0.8.6'){
            excludes 'slf4j-api', 'atmosphere-ping'
        }
    }

    plugins {
        compile ':taggable:1.0.1'
        compile 'org.icescrum:fluxiable:0.3.2'
        compile ':burning-image:0.5.0'
        compile 'org.icescrum:icescrum-attachmentable:0.3'
        compile 'spring:spring-security-core:1.2.7.3'
        compile 'spring:spring-security-acl:1.1.1'
        compile 'org.icescrum:commentable:1.3'
        compile ':followable:0.3'
        compile ':autobase:1.0.0.0'
        compile ':jdbc-pool:1.0.9.3'
        compile ':spring-events:1.2'
        compile ':springcache:1.3.1'
        compile ':mail:1.0'
        compile ':jasper:1.6.0'
        compile ':maven-publisher:0.8.1'
        compile ':rollback-on-exception:0.1'
        compile ':wikitext:0.1.2'
        compile ':hibernate:1.3.9'
    }
}