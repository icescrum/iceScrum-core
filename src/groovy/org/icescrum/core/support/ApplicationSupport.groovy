/*
 * Copyright (c) 2010 iceScrum Technologies / 2011 Kagilum SAS.
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
import grails.util.Metadata
import org.apache.commons.logging.LogFactory
import groovy.util.slurpersupport.NodeChild
import org.icescrum.core.domain.User


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

  //  See http://jira.codehaus.org/browse/GRAILS-6515
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
        def interval = CheckerTimerTask.computeInterval(config.icescrum.check.interval?:360)
        timer.scheduleAtFixedRate(new CheckerTimerTask(timer,interval), 60000, interval)
    }
  }

  static public createUUID = {
    def config = ApplicationHolder.application.config
    def filePath = config.icescrum.baseDir.toString() + File.separator + "appID.txt"
    def fileID = new File(filePath)

    if(!fileID.exists() || !fileID.readLines()[0]){
        !fileID.exists() ?: fileID.delete()
        if (!fileID.createNewFile()){
            println "Error could not create file : ${filePath} please check directory & user permission"
        }
        config.icescrum.appID = UUID.randomUUID()
        fileID <<  config.icescrum.appID
        if (log.debugEnabled) log.debug('regenerate appID '+config.icescrum.appID)
    }else{
        config.icescrum.appID = fileID.readLines()[0]
        if (log.debugEnabled) log.debug('retrieve appID '+config.icescrum.appID)
    }
  }
    
  public static Date getMidnightTime(Date time){
    def midnightTime = Calendar.getInstance()
    midnightTime.setTime(time)
    midnightTime.set(Calendar.HOUR_OF_DAY, 0)
    midnightTime.set(Calendar.MINUTE, 0)
    midnightTime.set(Calendar.SECOND, 0)
    midnightTime.set(Calendar.MILLISECOND,0)
    return midnightTime.getTime()
  }
    
  static public findUserUIDOldXMl(NodeChild object, name, users){
      def root = object.parent().parent().parent().parent().parent().parent().parent().parent().parent()
      def uXml = root.'**'.find{ it.@id.text() == (name ? object."${name}".@id.text() : object.@id.text() )  && it.username.text()}
      if (uXml){
          def UXmlUID = (uXml.username?.text() + uXml.email?.text()).encodeAsMD5()
          return ((User) users?.find { it.uid == UXmlUID } ) ?: null
      }else{
          return null
      }
  }
  
}

class CheckerTimerTask extends TimerTask {

    private static final log = LogFactory.getLog(this)
    private Timer timer
    private int interval

    CheckerTimerTask(Timer timer, int interval){
        this.timer = timer
        this.interval = interval
    }

    @Override
    void run() {
        def config = ApplicationHolder.application.config
        def configInterval = computeInterval(config.icescrum.check.interval?:1440)
        def http = new RESTClient(config.icescrum.check.url)
        http.client.params.setIntParameter( "http.connection.timeout", config.icescrum.check.timeout?:5000 )
        http.client.params.setIntParameter( "http.socket.timeout", config.icescrum.check.timeout?:5000 )
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
            if (interval != configInterval){
                //Back to normal delay
                this.cancel()
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer,configInterval),configInterval,configInterval)
                if (log.debugEnabled) log.debug('Automatic check update - back to normal delay')
            }
        }catch( ex ){
            if (interval == configInterval){
                //Setup new timer with a long delay
                if (log.debugEnabled) log.debug('Automatic check update error - new timer delay')
                this.cancel()
                def longInterval = configInterval >= 1440 ? configInterval*2 : computeInterval(1440)
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer,longInterval),longInterval,longInterval)
            }
        }
    }

    public static computeInterval(int interval){
        return 1000 * 60 * interval
    }

}
