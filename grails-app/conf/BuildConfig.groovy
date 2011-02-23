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
 * Vincent Barrier (vincent.barrier@icescrum.com)
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
        mavenRepo "http://repo.icescrum.org/artifactory/plugins-release/"
        //mavenRepo "http://snapshots.repository.codehaus.org"
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"
    }

    dependencies {
      // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
      runtime 'mysql:mysql-connector-java:5.1.5'
   }

    plugins {
      compile 'org.icescrum:fluxiable:0.3'
      compile ':burning-image:0.5.0'
      compile 'org.icescrum:icescrum-attachmentable:0.2'
      compile 'spring:spring-security-core:1.1'
      compile 'spring:spring-security-acl:1.1'
      compile ':commentable:0.7.5'
      compile ':followable:0.3'
      compile ':autobase:0.11.0'
      compile ':jdbc-pool:0.3'
      compile ':spring-events:1.1'
      compile ':springcache:1.3.1'
      compile ':mail:1.0-SNAPSHOT'
      compile ':jasper:1.2'
      compile ':maven-publisher:0.7.5'
    }
}

grails.project.dependency.distribution = {
    remoteRepository(id: "pluginsSnapshot", url: "http://repo.icescrum.org/artifactory/plugins-snapshot-local/") {
        authentication username: "", password: ""
    }
    remoteRepository(id: "pluginsRelease", url: "http://repo.icescrum.org/artifactory/plugins-release-local/") {
        authentication username: "", password: ""
    }
}