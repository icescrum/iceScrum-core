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


package org.icescrum.core.support

import org.codehaus.groovy.grails.commons.ApplicationHolder
import groovyx.net.http.RESTClient
import groovyx.net.http.Method
import grails.util.Metadata
import java.util.concurrent.TimeUnit
import org.apache.commons.logging.LogFactory


class ApplicationSupport {

  private static final log = LogFactory.getLog(this)

  static public generateFolders = {
    def config = ApplicationHolder.application.config
    def dirPath = config.icescrum.baseDir.toString() + File.separator + "images" + File.separator + "users" + File.separator
    def dir = new File(dirPath)
    if (!dir.exists())
      dir.mkdirs()
    println dirPath
    config.icescrum.images.users.dir = dirPath

    dirPath = config.icescrum.baseDir.toString() + File.separator + "images" + File.separator + "products" + File.separator
    dir = new File(dirPath)
    if (!dir.exists())
      dir.mkdirs()
    config.icescrum.products.users.dir = dirPath

    dirPath = config.icescrum.baseDir.toString() + File.separator + "images" + File.separator + "teams" + File.separator
    dir = new File(dirPath)
    if (!dir.exists())
      dir.mkdirs()
    config.icescrum.products.teams.dir = dirPath
  }

  // See http://jira.codehaus.org/browse/GRAILS-6515
  static public booleanValue(def value) {
      if (value.class == java.lang.Boolean) {
          // because 'true.toBoolean() == false' !!!
          return value
      } else if(value.class == ConfigObject){
        return value.asBoolean()
      } else if(value.class == Closure){
        return value()
      }
      else {
          return value.toBoolean()
      }
  }

  static public checkNewVersion = {
    def config = ApplicationHolder.application.config
    if (config.icescrum.check.enable){
        def timer = new Timer()
        def checker = {
            def http = new RESTClient(config.icescrum.check.url)
            http.client.params.setIntParameter( "http.connection.timeout", 5000 )
            http.client.params.setIntParameter( "http.socket.timeout", 5000 )
            try {
                def vers = Metadata.current['app.version'].replace('#','.').replaceFirst('R','')
                def resp = http.get(path:config.icescrum.check.path,
                                    query:[id:config.icescrum.appID,version:vers],
                                    headers:['User-Agent' : 'iceScrum-Agent/1.0','Referer' : config.grails.serverURL])
                if(resp.success && resp.status == 200){
                    if (resp.data.version?.text()){
                        config.icescrum.check.available = [version:resp.data.version.text(), url:resp.data.url.text(), message:resp.data.message?.text()]
                        if (log.debugEnabled) log.debug('Automatic check update - A new version is available : '+resp.data.version.text())
                    }else{
                        config.icescrum.check.available = false
                        if (log.debugEnabled) log.debug('Automatic check update - iceScrum is up to date')
                    }
                }
                println config.icescrum.check.available
            }catch( ex ){
                if (log.debugEnabled) log.debug('Automatic check update - error cancel timer')
                timer.cancel()
            }

        } as TimerTask
        def interval = 1000 * 60 * (config.icescrum.check.interval?:1440)
        timer.scheduleAtFixedRate(checker, 60000, interval)
    }
  }

  static public createUUID = {
    def config = ApplicationHolder.application.config
    def filePath = config.icescrum.baseDir.toString() + File.separator + "appID.txt"
    def fileID = new File(filePath)

    if(!fileID.exists() || !fileID.readLines()[0]){
        !fileID.exists() ?: fileID.delete()
        fileID.createNewFile()
        config.icescrum.appID = UUID.randomUUID()
        fileID <<  config.icescrum.appID
        if (log.debugEnabled) log.debug('regenerate appID '+config.icescrum.appID)
    }else{
        config.icescrum.appID = fileID.readLines()[0]
        if (log.debugEnabled) log.debug('retrieve appID '+config.icescrum.appID)
    }
  }

}
