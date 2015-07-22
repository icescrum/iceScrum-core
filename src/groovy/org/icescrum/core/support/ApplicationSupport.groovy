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

import grails.converters.JSON
import grails.util.Environment
import org.apache.http.Consts
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.codehaus.groovy.grails.commons.ApplicationHolder
import grails.util.Metadata
import org.apache.commons.logging.LogFactory
import org.icescrum.core.domain.User

import java.security.MessageDigest
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class ApplicationSupport {

    private static final log = LogFactory.getLog(this)
    public  static final CONFIG_ENV_NAME = 'icescrum_config_location'

    public static String getNormalisedVersion(){
        def version = Metadata.current['app.version']
        return version.substring(0, version.indexOf(" ") > 0 ? version.indexOf(" ")  : version.length()).toLowerCase().replaceAll('#','')
    }

    static public checkInitialConfig = { def config ->
        //check if Tomcat version is compatible
        try{
            ApplicationSupport.forName("javax.servlet.http.Part")
        }catch(ClassNotFoundException e){
            config.icescrum.errors << [error:true, title:'is.warning.httpPart.title',
                                       message:'is.warning.httpPart.message']
            config.icescrum.push.enable = false;
        }
        //check if serverURL is valid
        if (config.grails.serverURL.contains('localhost') && Environment.current != Environment.DEVELOPMENT){
            config.icescrum.errors << [error:true, title:'is.warning.serverUrl.title',
                                       message:'is.warning.serverUrl.message',
                                       args:[config.grails.serverURL]]
        }
    }

    static public generateFolders = { def config ->
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

    static public stringToMap = { String st, String separatorK = "=", String separatorV = "," ->
        def map = [:]
        st?.split(separatorV)?.each { param ->
            def nameAndValue = param.split(separatorK)
            if (nameAndValue.size() == 2)
                map[nameAndValue[0]] = nameAndValue[1]
        }
        map
    }

    static public mapToString = { Map map, String separatorK = "=", String separatorV = "," ->
        String st = ""
        map?.eachWithIndex{ it, i ->
            st += "${it.key}${separatorK}${it.value}"
            if (i != map.size() - 1){
                st += "${separatorV}"
            }
        }
        st
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

    static public checkNewVersion = { def config ->
        if (booleanValue(config.icescrum.check.enable)){
            def timer = new Timer()
            def interval = CheckerTimerTask.computeInterval(config.icescrum.check.interval?:360)
            timer.scheduleAtFixedRate(new CheckerTimerTask(timer,interval), 60000, interval)
        }
    }

    static public createUUID = {
        if (log.debugEnabled) log.debug "Retrieving appID..."
        def config = ApplicationHolder.application.config
        def filePath = config.icescrum.baseDir.toString() + File.separator + "appID.txt"
        def fileID = new File(filePath)
        def line = fileID.exists() ?  fileID.readLines()[0] : null

        if (!line || line == 'd41d8cd9-8f00-b204-e980-0998ecf8427e') {
            def uid
            try {
                uid = NetworkInterface.networkInterfaces?.nextElement()?.hardwareAddress
                if (uid) {
                    MessageDigest md = MessageDigest.getInstance("MD5")
                    md.update(uid)
                    uid = new BigInteger(1, md.digest() ).toString(16).padLeft(32, '0')
                    uid = uid.substring(0,8) +'-'+ uid.substring(8,12) +'-'+ uid.substring(12,16) +'-'+ uid.substring(16,20) +'-'+ uid.substring(20,32)
                }
            } catch (IOException ioe) {
                if (log.debugEnabled) log.debug "Warning could not access network interfaces, message: $ioe.message"
            }
            config.icescrum.appID = uid ?: UUID.randomUUID()
            if (log.debugEnabled) log.debug "Generated (${uid?'m':'r'}) appID: $config.icescrum.appID"
            try {
                if (!fileID.exists()) fileID.parentFile.mkdirs()
                if (fileID.exists()) fileID.delete()
                if (fileID.createNewFile()){
                    fileID << config.icescrum.appID
                } else {
                    log.error "Error could not create file: ${filePath} please check directory & user permission"
                }
            } catch (IOException ioe) {
                log.error "Error (exception) could not create file: ${filePath} please check directory & user permission"
                throw ioe
            }
        } else {
            config.icescrum.appID = line
            if (log.debugEnabled) log.debug "Retrieved appID: $config.icescrum.appID"
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

    static public findUserUIDOldXMl(def object, name, users){
        //be sure we are at root node
        def root = object.parent().parent().parent().parent().parent().parent().parent().parent().parent()
        //be compatible with xml without export tag
        if (root.find{ it.name == 'export' }){ root = root.product }
        def uXml = root.'**'.find{ it.@id.text() == (name ? object."${name}".@id.text() : object.@id.text() )  && it.username.text()}
        if (uXml){
            def UXmlUID = (uXml.username?.text() + uXml.email?.text()).encodeAsMD5()
            return users ? ((User) users?.find { it.uid == UXmlUID } ) : User.findByUid(UXmlUID) ?: null
        }else{
            return null
        }
    }


    static public findIceScrumVersionFromXml(def object) {
        def root = object.parent().parent().parent().parent().parent().parent().parent().parent().parent()
        return root.find{ it.name == 'export' }?.@version?.text()
    }

    static public zipExportFile(OutputStream zipStream, List<File> files, File xml, String subdir) throws IOException {
        ZipOutputStream zout = new ZipOutputStream(zipStream)
        try {
            if (xml){
                if (log.debugEnabled){ log.debug "Zipping : ${xml.name}" }
                zout.putNextEntry(new ZipEntry(xml.name))
                zout << new FileInputStream(xml)
            }
            zout.closeEntry()
            files?.each{
                if (log.debugEnabled){ log.debug "Zipping : ${it.name}" }
                if (it.exists()){
                    def entryName = (subdir ? File.separator + subdir + File.separator : '') + it.name
                    zout.putNextEntry(new ZipEntry(entryName))
                    zout << new FileInputStream(it)
                    zout.closeEntry()
                }else{
                    if (log.debugEnabled){ log.debug "Zipping : Warning file not found : ${it.name}" }
                }

            }
        } finally {
            zout.close()
        }
    }

    static public unzip(File zip, File destination){
        def result = new ZipInputStream(new FileInputStream(zip))

        if (log.debugEnabled){ log.debug "Unzip file : ${zip.name} to ${destination.absolutePath}" }

        if(!destination.exists()){
            destination.mkdir();
        }
        result.withStream{
            def entry
            while(entry = result.nextEntry){
                if (log.debugEnabled){ log.debug "Unzipping : ${entry.name}" }
                if (!entry.isDirectory()){
                    new File(destination.absolutePath + File.separator + entry.name).parentFile?.mkdirs()
                    def output = new FileOutputStream(destination.absolutePath + File.separator + entry.name)
                    output.withStream{
                        int len = 0;
                        byte[] buffer = new byte[4096]
                        while ((len = result.read(buffer)) > 0){
                            output.write(buffer, 0, len);
                        }
                    }
                }
                else {
                    new File(destination.absolutePath + File.separator + entry.name).mkdir()
                }
            }
        }
    }

    static public createTempDir(String name){
        File dir = File.createTempFile( name, '.dir' )
        dir.delete()  // delete the file that was created
        dir.mkdir()   // create a directory in its place.
        if (log.debugEnabled){ log.debug "Created tmp dir ${dir.absolutePath}" }
        return dir
    }

    public static getCurrentSpace(def params, def id = null){
        def space = ApplicationHolder.application.config.icescrum.spaces.find{ id ? it.key == id : params."$it.key" }
        if (space){
            def object = space.value.spaceClass.get(params.long("$space.key"))
            return object ? [name:space.key,
                    object:object,
                    config:space.value.config(object),
                    params:space.value.params(object),
                    indexScrumOS:space.value.indexScrumOS] : false
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
        def configInterval = computeInterval(config.icescrum.check.interval ?: 1440)
        try {
            def vers = Metadata.current['app.version'].replace('#', '.').replaceFirst('R', '').replace('.','-')
            def headers = ['User-Agent': 'iceScrum-Agent/1.0', 'Referer': config.grails.serverURL]
            def params = ['http.connection.timeout': config.icescrum.check.timeout ?: 5000, 'http.socket.timeout': config.icescrum.check.timeout ?: 5000]
            def resp = getJSON(config.icescrum.check.url, config.icescrum.check.path + "/" + config.icescrum.appID + "/" + vers, [:], headers, params)
            if (resp.status == 200) {
                if (resp.data) {
                    config.icescrum.errors << [error: false, title: 'is.warning.version', version: resp.data.version, url: resp.data.url, message: resp.data.message]
                    if (log.debugEnabled) log.debug('Automatic check update - A new version is available : ' + resp.data.version)
                    return
                } else {
                        if (log.debugEnabled) log.debug('Automatic check update - iceScrum is up to date')
                }
            }
            if (interval != configInterval) {
                //Back to normal delay
                this.cancel()
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer, configInterval), configInterval, configInterval)
                if (log.debugEnabled) log.debug('Automatic check update - back to normal delay')
            }
        } catch (ex) {
            if (interval == configInterval) {
                //Setup new timer with a long delay
                if (log.debugEnabled) log.debug('Automatic check update error - new timer delay')
                this.cancel()
                def longInterval = configInterval >= 1440 ? configInterval * 2 : computeInterval(1440)
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer, longInterval), longInterval, longInterval)
            }
        }
    }

    public static computeInterval(int interval){
        return 1000 * 60 * interval
    }

    private static Map getJSON(String domain, String path, queryParams = [:], headers = [:], params = [:]) {
        HttpClient httpclient = new DefaultHttpClient();
        Map resp = [data:'']
        try {
            String url = domain + '/' + path
            if (queryParams) {
                // Don't use URIBuilder because of bug : https://issues.apache.org/jira/browse/HTTPCLIENT-1195
                url += ('?' + URLEncodedUtils.format(queryParams.collect { k, v -> new BasicNameValuePair(k, v) }, Consts.UTF_8))
            }
            HttpGet httpget = new HttpGet(url);
            headers.each { k, v -> httpget.setHeader(k, v) }
            params.each { k, v -> httpget.params.setParameter(k, v) }
            HttpResponse response = httpclient.execute(httpget);
            resp.status = response.statusLine.statusCode
            def data = EntityUtils.toString(response.entity)
            if (resp.status == HttpStatus.SC_OK && data) {
                resp.data = JSON.parse(data)
            }
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
        return resp
    }
}
