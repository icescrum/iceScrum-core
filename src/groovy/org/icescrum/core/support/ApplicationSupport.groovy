/*
 * Copyright (c) 2016 Kagilum SAS
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
import grails.plugin.springsecurity.userdetails.GrailsUser
import grails.plugin.springsecurity.web.SecurityRequestHolder as SRH
import grails.util.Environment
import grails.util.Holders
import grails.util.Metadata
import groovy.xml.MarkupBuilder
import org.apache.commons.logging.LogFactory
import org.apache.http.Consts
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.ClientContext
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.util.EntityUtils
import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.User
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.security.WebScrumExpressionHandler
import org.icescrum.core.ui.WindowDefinition
import org.springframework.expression.Expression
import org.springframework.security.access.expression.ExpressionUtils
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator

import javax.servlet.FilterChain
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ApplicationSupport {

    private static final log = LogFactory.getLog(this)
    public static final CONFIG_ENV_NAME = 'icescrum_config_location'
    protected static final FilterChain DUMMY_CHAIN = [
            doFilter: { req, res -> throw new UnsupportedOperationException() }
    ] as FilterChain

    public static def controllerExist(def controllerName, def actionName = '') {
        def controllerClass = Holders.grailsApplication.controllerClasses.find {
            it.logicalPropertyName == controllerName
        }
        return actionName ? controllerClass?.metaClass?.respondsTo(actionName) : controllerClass
    }

    public static boolean isAllowed(def viewDefinition, def params, def widget = false) {
        def grailsApplication = Holders.grailsApplication
        WebScrumExpressionHandler webExpressionHandler = (WebScrumExpressionHandler) grailsApplication.mainContext.getBean(WebScrumExpressionHandler.class)
        def contexts = viewDefinition?.context ? (viewDefinition.context instanceof String) ? [viewDefinition.context] : viewDefinition.context : [null]
        if (!viewDefinition || !((getCurrentContext(params)?.name ?: null) in contexts)) {
            return false
        }
        //secured on uiDefinition
        if (viewDefinition.secured) {
            Expression expression = webExpressionHandler.expressionParser.parseExpression(viewDefinition.secured)
            FilterInvocation fi = new FilterInvocation(SRH.request, SRH.response, DUMMY_CHAIN)
            def ctx = webExpressionHandler.createEvaluationContext(SCH.context.getAuthentication(), fi)
            return ExpressionUtils.evaluateAsBoolean(expression, ctx)
        } else {
            //secured on controller
            if (controllerExist(viewDefinition.id, widget ? 'widget' : 'window')) {
                ApplicationTagLib g = (ApplicationTagLib) grailsApplication.mainContext.getBean(ApplicationTagLib.class)
                WebInvocationPrivilegeEvaluator webInvocationPrivilegeEvaluator = (WebInvocationPrivilegeEvaluator) grailsApplication.mainContext.getBean(WebInvocationPrivilegeEvaluator.class)
                def url = g.createLink(controller: viewDefinition.id, action: widget ? 'widget' : 'window')
                url = url.toString() - SRH.request.contextPath
                return webInvocationPrivilegeEvaluator.isAllowed(SRH.request.contextPath, url, 'GET', SCH.context?.authentication)
            }
        }
        return false
    }

    public static Map menuPositionFromUserPreferences(def windowDefinition) {
        UserPreferences userPreferences = null
        if (GrailsUser.isAssignableFrom(SCH.context.authentication?.principal?.getClass())) {
            userPreferences = User.get(SCH.context.authentication.principal?.id)?.preferences
        }
        def visiblePosition = userPreferences?.menu?.getAt(windowDefinition.id)
        def hiddenPosition = userPreferences?.menuHidden?.getAt(windowDefinition.id)
        def menuEntry = [:]
        if (visiblePosition) {
            menuEntry.pos = visiblePosition
            menuEntry.visible = true
        } else if (hiddenPosition) {
            menuEntry.pos = hiddenPosition
            menuEntry.visible = false
        } else {
            menuEntry = null
        }
        return menuEntry
    }

    public static String getNormalisedVersion() {
        def version = Metadata.current['app.version']
        return version.substring(0, version.indexOf(" ") > 0 ? version.indexOf(" ") : version.length()).toLowerCase().replaceAll('#', '')
    }

    static public checkInitialConfig = { def config ->
        //check if Tomcat version is compatible
        try {
            ApplicationSupport.forName("javax.servlet.http.Part")
        } catch (ClassNotFoundException e) {
            addWarningMessage('http-error', 'warning', [code: 'is.warning.httpPart.title'], [code: 'is.warning.httpPart.message'])
            config.icescrum.push.enable = false;
        }
        //check if serverURL is valid
        if (config.grails.serverURL.contains('localhost') && Environment.current != Environment.DEVELOPMENT) {
            addWarningMessage('serverUrl', 'warning', [code: 'is.warning.serverUrl.title'], [code: 'is.warning.serverUrl.message', args: [config.grails.serverURL]])
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

    static public initEnvironment = { def config ->
        config.icescrum.environment = (System.getProperty('icescrum.environment') ?: 'production')
        if (config.icescrum.environment == 'production' && new File(File.separator + 'dev' + File.separator + 'turnkey').exists()) {
            config.icescrum.environment = 'turnkey'
        }
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
        map?.eachWithIndex { it, i ->
            st += "${it.key}${separatorK}${it.value}"
            if (i != map.size() - 1) {
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
        } else if (value.class == ConfigObject) {
            return value.asBoolean()
        } else if (value.class == Closure) {
            return value()
        } else {
            return value.toBoolean()
        }
    }

    static public checkNewVersion = { def config ->
        if (booleanValue(config.icescrum.check.enable)) {
            def timer = new Timer()
            def interval = CheckerTimerTask.computeInterval(config.icescrum.check.interval ?: 360)
            timer.scheduleAtFixedRate(new CheckerTimerTask(timer, interval), 60000, interval)
        }
    }

    static public createUUID = {
        log.debug "Retrieving appID..."
        def config = Holders.grailsApplication.config
        def filePath = config.icescrum.baseDir.toString() + File.separator + "appID.txt"
        def fileID = new File(filePath)
        def line = fileID.exists() ? fileID.readLines()[0] : null

        if (!line || line == 'd41d8cd9-8f00-b204-e980-0998ecf8427e') {
            def uid
            try {
                uid = NetworkInterface.networkInterfaces?.nextElement()?.hardwareAddress
                if (uid) {
                    MessageDigest md = MessageDigest.getInstance("MD5")
                    md.update(uid)
                    uid = new BigInteger(1, md.digest()).toString(16).padLeft(32, '0')
                    uid = uid.substring(0, 8) + '-' + uid.substring(8, 12) + '-' + uid.substring(12, 16) + '-' + uid.substring(16, 20) + '-' + uid.substring(20, 32)
                }
            } catch (IOException ioe) {
                if (log.debugEnabled) {
                    log.debug "Warning could not access network interfaces, message: $ioe.message"
                }
            }
            config.icescrum.appID = uid ?: UUID.randomUUID()
            if (log.debugEnabled) {
                log.debug "Generated (${uid ? 'm' : 'r'}) appID: $config.icescrum.appID"
            }
            try {
                if (!fileID.exists()) fileID.parentFile.mkdirs()
                if (fileID.exists()) fileID.delete()
                if (fileID.createNewFile()) {
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
            if (log.debugEnabled) {
                log.debug "Retrieved appID: $config.icescrum.appID"
            }
        }
    }

    public static Date getMidnightTime(Date time) {
        def midnightTime = Calendar.getInstance()
        midnightTime.setTime(time)
        midnightTime.set(Calendar.HOUR_OF_DAY, 0)
        midnightTime.set(Calendar.MINUTE, 0)
        midnightTime.set(Calendar.SECOND, 0)
        midnightTime.set(Calendar.MILLISECOND, 0)
        return midnightTime.getTime()
    }

    static public findUserUIDOldXMl(def object, name, users) {
        //be sure we are at root node
        def root = object.parent().parent().parent().parent().parent().parent().parent().parent().parent()
        //be compatible with xml without export tag
        if (root.find { it.name == 'export' }) {
            root = root.product
        }
        def uXml = root.'**'.find {
            it.@id.text() == (name ? object."${name}".@id.text() : object.@id.text()) && it.username.text()
        }
        if (uXml) {
            def UXmlUID = (uXml.username?.text() + uXml.email?.text()).encodeAsMD5()
            return users ? ((User) users?.find { it.uid == UXmlUID }) : User.findByUid(UXmlUID) ?: null
        } else {
            return null
        }
    }


    static public findIceScrumVersionFromXml(def object) {
        def root = object.parent().parent().parent().parent().parent().parent().parent().parent().parent()
        return root.find { it.name == 'export' }?.@version?.text()
    }

    static public zipExportFile(OutputStream zipStream, List<File> files, File xml, String subdir) throws IOException {
        ZipOutputStream zout = new ZipOutputStream(zipStream)
        try {
            if (xml) {
                if (log.debugEnabled) {
                    log.debug "Zipping : ${xml.name}"
                }
                zout.putNextEntry(new ZipEntry(xml.name))
                zout << new FileInputStream(xml)
            }
            zout.closeEntry()
            files?.each {
                if (log.debugEnabled) {
                    log.debug "Zipping : ${it.name}"
                }
                if (it.exists()) {
                    def entryName = (subdir ? File.separator + subdir + File.separator : '') + it.name
                    zout.putNextEntry(new ZipEntry(entryName))
                    zout << new FileInputStream(it)
                    zout.closeEntry()
                } else {
                    if (log.debugEnabled) {
                        log.debug "Zipping : Warning file not found : ${it.name}"
                    }
                }

            }
        } finally {
            zout.close()
        }
    }

    static public unzip(File zip, File destination) {
        def result = new ZipInputStream(new FileInputStream(zip))

        if (log.debugEnabled) {
            log.debug "Unzip file : ${zip.name} to ${destination.absolutePath}"
        }

        if (!destination.exists()) {
            destination.mkdir();
        }
        result.withStream {
            def entry
            while (entry = result.nextEntry) {
                if (log.debugEnabled) {
                    log.debug "Unzipping : ${entry.name}"
                }
                if (!entry.isDirectory()) {
                    new File(destination.absolutePath + File.separator + entry.name).parentFile?.mkdirs()
                    def output = new FileOutputStream(destination.absolutePath + File.separator + entry.name)
                    output.withStream {
                        int len = 0;
                        byte[] buffer = new byte[4096]
                        while ((len = result.read(buffer)) > 0) {
                            output.write(buffer, 0, len);
                        }
                    }
                } else {
                    new File(destination.absolutePath + File.separator + entry.name).mkdir()
                }
            }
        }
    }

    static public createTempDir(String name) {
        File dir = File.createTempFile(name, '.dir')
        dir.delete()  // delete the file that was created
        dir.mkdir()   // create a directory in its place.
        if (log.debugEnabled) {
            log.debug "Created tmp dir ${dir.absolutePath}"
        }
        return dir
    }

    public static getCurrentContext(def params, def id = null) {
        def context = Holders.grailsApplication.config.icescrum.contexts.find { id ? it.key == id : params."$it.key" }
        if (context) {
            def object = params.long("$context.key") ? context.value.contextClass.get(params.long("$context.key")) : null
            return object ? [name        : context.key,
                             object      : object,
                             contextScope: context.value.contextScope,
                             config      : context.value.config(object),
                             params      : context.value.params(object),
                             indexScrumOS: context.value.indexScrumOS] : false
        }
    }

    public static Map getJSON(String url, String username, String password, headers = [:]) {
        DefaultHttpClient httpClient = new DefaultHttpClient()
        Map resp = [:]
        try {
            // Build host
            URI uri = new URI(url)
            String host = uri.host
            Integer port = uri.port
            String scheme = uri.scheme
            if (port == -1 && scheme == 'https') {
                port = 443
            }
            HttpHost targetHost = new HttpHost(host, port, scheme)
            // Configure preemptive basic auth
            httpClient.credentialsProvider.setCredentials(new AuthScope(targetHost.hostName, targetHost.port), new UsernamePasswordCredentials(username, password))
            AuthCache authCache = new BasicAuthCache()
            authCache.put(targetHost, new BasicScheme())
            BasicHttpContext localcontext = new BasicHttpContext()
            localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache)
            // Build request
            HttpGet httpGet = new HttpGet(uri.path)
            headers.each { k, v ->
                httpGet.setHeader(k, v)
            }
            // Execute request
            HttpResponse response = httpClient.execute(targetHost, httpGet, localcontext)
            resp.status = response.statusLine.statusCode
            if (resp.status == HttpStatus.SC_OK) {
                resp.data = JSON.parse(EntityUtils.toString(response.entity))
            } else {
                log.debug('Error ' + resp.status + ' get ' + uri.toString() + ' ' + EntityUtils.toString(response.entity))
            }
        } catch (Exception e) {
            log.error(e.message)
            e.printStackTrace()
        } finally {
            httpClient.connectionManager.shutdown()
        }
        return resp
    }

    public static Map postJSON(String url, String username, String password, JSON json, headers = [:]) {
        DefaultHttpClient httpClient = new DefaultHttpClient()
        Map resp = [:]
        try {
            // Build host
            URI uri = new URI(url)
            String host = uri.host
            Integer port = uri.port
            String scheme = uri.scheme
            if (port == -1 && scheme == 'https') {
                port = 443
            }
            HttpHost targetHost = new HttpHost(host, port, scheme)
            // Configure preemptive basic auth
            httpClient.credentialsProvider.setCredentials(new AuthScope(targetHost.hostName, targetHost.port), new UsernamePasswordCredentials(username, password))
            AuthCache authCache = new BasicAuthCache()
            authCache.put(targetHost, new BasicScheme())
            BasicHttpContext localcontext = new BasicHttpContext()
            localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache)
            // Build request
            HttpPost httpPost = new HttpPost(uri.path)
            headers.each { k, v ->
                httpPost.setHeader(k, v)
            }
            httpPost.setEntity(new StringEntity(json.toString()));
            // Execute request
            HttpResponse response = httpClient.execute(targetHost, httpPost, localcontext)
            resp.status = response.statusLine.statusCode
            if (resp.status == HttpStatus.SC_OK) {
                resp.data = JSON.parse(EntityUtils.toString(response.entity))
            } else {
                log.debug('Error ' + resp.status + ' post ' + uri.toString() + ' ' + json.toString(true) + ' ' + EntityUtils.toString(response.entity))
            }
        } catch (Exception e) {
            log.error(e.message)
            e.printStackTrace()
        } finally {
            httpClient.connectionManager.shutdown()
        }
        return resp
    }

    public static def getUserMenusContext(Map windowDefinitions, Map params) {
        def menus = []
        windowDefinitions.each { String windowDefinitionId, WindowDefinition windowDefinition ->
            def menu = windowDefinition.menu
            menu?.show = menu ? isAllowed(windowDefinition, params) ? menuPositionFromUserPreferences(windowDefinition) ?: [visible: menu.defaultVisibility, pos: menu.defaultPosition] : false : false
            def show = menu?.show
            if (show in Closure) {
                show.delegate = delegate
                show = show()
            }
            if (show) {
                menus << [title   : menu?.title,
                          id      : windowDefinitionId,
                          shortcut: "ctrl+" + (menus.size() + 1),
                          icon    : windowDefinition.icon,
                          position: show instanceof Map ? show.pos.toInteger() ?: 1 : 1,
                          visible : show.visible]
            }
        }
        return menus
    }

    public static void exportProductZIP(Product product, def outputStream) {
        def attachmentableService = Holders.applicationContext.getBean("attachmentableService")
        def projectName = "${product.name.replaceAll("[^a-zA-Z\\s]", "").replaceAll(" ", "")}-${new Date().format('yyyy-MM-dd')}"
        def tempdir = System.getProperty("java.io.tmpdir");
        tempdir = (tempdir.endsWith("/") || tempdir.endsWith("\\")) ? tempdir : tempdir + System.getProperty("file.separator")
        def xml = new File(tempdir + projectName + '.xml')
        try {
            xml.withWriter('UTF-8') { writer ->
                def builder = new MarkupBuilder(writer)
                product.xml(builder)
            }
            def files = []
            product.stories*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            product.features*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            product.releases*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            product.sprints*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            product.attachments.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            def tasks = []
            product.releases*.each { it.sprints*.each { s -> tasks.addAll(s.tasks) } }
            tasks*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            zipExportFile(outputStream, files, xml, 'attachments')
        } catch (Exception e) {
            if (log.debugEnabled) {
                e.printStackTrace()
            }
        } finally {
            xml.delete()
        }
    }

    static ConfigObject mergeConfig(final ConfigObject currentConfig, final ConfigObject secondary) {
        ConfigObject config = new ConfigObject();
        if (secondary == null) {
            if (currentConfig != null) {
                config.putAll(currentConfig);
            }
        } else {
            if (currentConfig == null) {
                config.putAll(secondary);
            } else {
                config.putAll(secondary.merge(currentConfig));
            }
        }
        return config;
    }

    static void addWarningMessage(String id, String icon, Map title, Map message, boolean hideable = false) {
        def errors = Holders.grailsApplication.config.icescrum.errors
        def exist = errors.find { it.id == id } ?: false
        if (!exist) {
            errors << [id: id, title: title, message: message, icon: icon, silent: false, hideable: hideable]
        }
    }

    static def toggleSilentWarningMessage(String id) {
        def warning = Holders.grailsApplication.config.icescrum.errors.find { it.id == id && it.hideable }
        warning?.silent = !warning.silent
        return warning
    }

    static def getLastWarning() {
        def g = Holders.grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        def warning = Holders.grailsApplication.config.icescrum.errors?.reverse()?.find { it ->
            !it.silent
        }
        return warning ? [id: warning.id, icon: warning.icon, title: g.message(warning.title), message: g.message(warning.message), hideable: warning.hideable, silent: warning.silent] : null
    }
}

class CheckerTimerTask extends TimerTask {

    private static final log = LogFactory.getLog(this)
    private Timer timer
    private int interval

    CheckerTimerTask(Timer timer, int interval) {
        this.timer = timer
        this.interval = interval
    }

    @Override
    void run() {
        def config = Holders.grailsApplication.config
        def configInterval = computeInterval(config.icescrum.check.interval ?: 1440)
        try {
            def headers = ['User-Agent': 'iceScrum-Agent/1.0', 'Referer': config.grails.serverURL]
            def vers = Metadata.current['app.version'].replace('#', '.').replaceFirst('R', '').replace('.', '-').replace(' ', '%20')
            def params = ['http.connection.timeout': config.icescrum.check.timeout ?: 5000, 'http.socket.timeout': config.icescrum.check.timeout ?: 5000]
            def queryParams = [environment: config.icescrum.environment]
            def resp = getJSON(config.icescrum.check.url, config.icescrum.check.path + "/" + config.icescrum.appID + "/" + vers, queryParams, headers, params)
            if (resp.status == 200) {
                if (!resp.data.up_to_date) {
                    ApplicationSupport.addWarningMessage('version',
                            'cloud-download',
                            [code: 'is.warning.version', args: [resp.data.version.replaceFirst('\\.', '#')]],
                            [code: 'is.warning.version.download', args: [resp.data.message, resp.data.url]])
                    if (log.debugEnabled) {
                        log.debug('Automatic check update - A new version is available : ' + resp.data.version)
                    }
                    return
                } else {
                    log.debug('Automatic check update - iceScrum is up to date')
                }
            }
            if (interval != configInterval) {
                //Back to normal delay
                this.cancel()
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer, configInterval), configInterval, configInterval)
                log.debug('Automatic check update - back to normal delay')
            }
        } catch (ex) {
            if (interval == configInterval) {
                //Setup new timer with a long delay
                if (log.debugEnabled) {
                    log.debug('Automatic check update error - new timer delay')
                    log.debug(ex.message)
                }
                this.cancel()
                def longInterval = configInterval >= 1440 ? configInterval * 2 : computeInterval(1440)
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer, longInterval), longInterval, longInterval)
            }
        }
    }

    public static computeInterval(int interval) {
        return 1000 * 60 * interval
    }

    private static Map getJSON(String domain, String path, queryParams = [:], headers = [:], params = [:]) {
        HttpClient httpclient = new DefaultHttpClient();
        Map resp = [data: '']
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
