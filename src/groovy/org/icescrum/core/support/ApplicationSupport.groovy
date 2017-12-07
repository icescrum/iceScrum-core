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
import grails.util.GrailsNameUtils
import grails.util.Holders
import grails.util.Metadata
import groovy.sql.Sql
import groovy.transform.CompileStatic
import org.apache.commons.logging.LogFactory
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.ClientContext
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.util.EntityUtils
import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.grails.comments.Comment
import org.grails.comments.CommentLink
import org.icescrum.core.app.AppDefinition
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.SimpleProjectApp
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.domain.security.UserAuthority
import org.icescrum.core.security.WebScrumExpressionHandler
import org.icescrum.core.services.ProjectService
import org.springframework.expression.Expression
import org.springframework.security.access.expression.ExpressionUtils
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator

import javax.imageio.ImageIO
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ApplicationSupport {

    public static final CONFIG_ENV_NAME = 'icescrum_config_location'
    private static def mySQLUTF8mb4 = null //only one check per app start
    private static final log = LogFactory.getLog(this)
    protected static final FilterChain DUMMY_CHAIN = [
            doFilter: { req, res -> throw new UnsupportedOperationException() }
    ] as FilterChain

    public static String serverURL() {
        // Assets pipeline replaces the default grails link generator by its own LinkGenerator
        // If there is no grails.serverURL, it uses a custom way to generate URLs through AssetProcessorService.makeServerURL that calls HttpServletRequests.getBaseUrlWithScheme
        // This methods uses HTTP headers to retrieve port and proto, as opposed to GrailsWebRequest.getBaseUrl that relies only on request URL
        // Unfortunately, iceScrum core doesn't depend on assets pipeline so we have to mimic its behaviour manually
        // NB: the original request URL can be faked thanks to the Tomcat RemoteIpValve valve, but it requires modyfing server.xml and that the proxy IP is provided
        GrailsWebRequest grailsRequest = WebUtils.retrieveGrailsWebRequest()
        String serverUrl
        if (grailsRequest) {
            HttpServletRequest req = grailsRequest.currentRequest
            String scheme = req.getHeader('x-forwarded-proto') ?: req.scheme
            serverUrl = scheme + '://' + req.serverName
            int port = req.getHeader('x-forwarded-port') ? Integer.parseInt(req.getHeader('x-forwarded-port')) : req.serverPort
            int defaultPort = scheme == 'https' ? 443 : 80
            if (port >= 0 && port != defaultPort) {
                serverUrl += (':' + port)
            }
            serverUrl += req.contextPath
            if (grailsRequest.params.debugServerURL) {
                log.debug('URL: \n' + '\tx-forwarded-proto: ' + req.getHeader('x-forwarded-proto') + '\n'
                        + '\tselected proto: ' + scheme + '\n'
                        + '\tx-forwarded-port: ' + req.getHeader('x-forwarded-port') + '\n'
                        + '\tselected port: ' + port + '\n'
                        + '\tresulting URL: ' + serverUrl)
            }
        }
        return serverUrl
    }

    public static String getDatabaseName() {
        def url = Holders.grailsApplication.config.dataSource.url
        return url.split('/').toList().last().tokenize('?')[0]
    }

    public static boolean isMySQLUTF8mb4() {
        if (mySQLUTF8mb4 == null) {
            def dataSource = Holders.grailsApplication.config.dataSource
            if (dataSource.driverClassName == 'com.mysql.jdbc.Driver' && Environment.current != Environment.DEVELOPMENT) {
                try {
                    Sql.withInstance(dataSource.url, dataSource.username, dataSource.password, dataSource.driverClassName) { Sql sql ->
                        def values = sql.firstRow("SHOW VARIABLES LIKE 'character_set_server'").values()
                        println values
                        mySQLUTF8mb4 = values.contains("utf8mb4")
                    }
                } catch (Exception e) {
                    if (log.debugEnabled) {
                        log.debug(e)
                    }
                    mySQLUTF8mb4 = false
                }

            } else {
                mySQLUTF8mb4 = false
            }
        }
        return mySQLUTF8mb4
    }

    public static User getFirstAdministrator() {
        return UserAuthority.findByAuthority(Authority.findByAuthority(Authority.ROLE_ADMIN)).user
    }

    public static List<User> getAllAdministrators() {
        return UserAuthority.findAllByAuthority(Authority.findByAuthority(Authority.ROLE_ADMIN)).collect { it.user }
    }

    public static def controllerExist(def controllerName, def actionName = '') {
        def controllerClass = Holders.grailsApplication.controllerClasses.find {
            it.logicalPropertyName == controllerName
        }
        return actionName ? controllerClass?.metaClass?.respondsTo(actionName) : controllerClass
    }

    public static boolean isAllowed(def viewDefinition, def params, def widget = false) {
        def grailsApplication = Holders.grailsApplication
        WebScrumExpressionHandler webExpressionHandler = (WebScrumExpressionHandler) grailsApplication.mainContext.getBean(WebScrumExpressionHandler.class)
        if (!viewDefinition || viewDefinition.workspace != getCurrentWorkspace(params)?.name) {
            return false
        }
        // Authentication should never be null, however it happens sometimes. See S194 and S230
        if (!SCH.context.getAuthentication()) {
            return false
        }
        // Secured on uiDefinition
        if (viewDefinition.secured) {
            Expression expression = webExpressionHandler.expressionParser.parseExpression(viewDefinition.secured)
            FilterInvocation fi = new FilterInvocation(SRH.request, SRH.response, DUMMY_CHAIN)
            def ctx = webExpressionHandler.createEvaluationContext(SCH.context.getAuthentication(), fi)
            return ExpressionUtils.evaluateAsBoolean(expression, ctx)
        } else {
            // Secured on controller
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
        return version.substring(0, version.indexOf(" ") > 0 ? version.indexOf(" ") : version.length()).toLowerCase()
    }

    static public checkInitialConfig = { def config ->
        try {
            ApplicationSupport.forName("javax.servlet.http.Part") // Check if Tomcat version is compatible
        } catch (ClassNotFoundException e) {
            addWarning('http-error', 'warning', [code: 'is.warning.httpPart.title'], [code: 'is.warning.httpPart.message'])
            config.icescrum.push.enable = false;
        }
        checkCommonErrors(config)
    }

    static public checkCommonErrors(def config) {
        if (config.dataSource.driverClassName == "org.h2.Driver" && Environment.current != Environment.DEVELOPMENT) {
            addWarning('database', 'warning', [code: 'is.warning.database.title'], [code: 'is.warning.database.message'])
        } else {
            removeWarning('database')
        }
    }

    static public generateFolders = { def config ->
        def dirPath = config.icescrum.baseDir.toString() + File.separator + "images" + File.separator + "users" + File.separator
        def dir = new File(dirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        config.icescrum.images.users.dir = dirPath

        dirPath = config.icescrum.baseDir.toString() + File.separator + "images" + File.separator + "projects" + File.separator
        dir = new File(dirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        config.icescrum.projects.users.dir = dirPath

        dirPath = config.icescrum.baseDir.toString() + File.separator + "images" + File.separator + "teams" + File.separator
        dir = new File(dirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        config.icescrum.projects.teams.dir = dirPath
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
            if (nameAndValue.size() == 2) {
                map[nameAndValue[0]] = nameAndValue[1]
            }
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

    static public checkForUpdateAndReportUsage = { def config ->
        def timer = new Timer()
        def oneHour = CheckerTimerTask.minutesToMilliseconds(60)
        // CheckForUpdate
        def intervalCheckVersion = CheckerTimerTask.minutesToMilliseconds(config.icescrum.check.interval)
        timer.scheduleAtFixedRate(new CheckerTimerTask(timer, intervalCheckVersion), oneHour, intervalCheckVersion)
        // ReportUsage
        def intervalReport = CheckerTimerTask.minutesToMilliseconds(config.icescrum.reportUsage.interval)
        timer.scheduleAtFixedRate(new ReportUsageTimerTask(timer, intervalReport), 3 * 24 * oneHour, intervalReport)
    }

    static public createUUID = {
        log.debug "Retrieving appID..."
        def config = Holders.grailsApplication.config
        def filePath = config.icescrum.baseDir.toString() + File.separator + "appID.txt"
        def fileID = new File(filePath)
        def existingID = fileID.exists() ? fileID.readLines()[0] : null
        boolean docker = config.icescrum.environment == 'docker'
        if (!existingID || existingID == 'd41d8cd9-8f00-b204-e980-0998ecf8427e' || docker) {
            def newID
            try {
                newID = NetworkInterface.networkInterfaces?.nextElement()?.hardwareAddress
                if (newID) {
                    MessageDigest md = MessageDigest.getInstance("MD5")
                    md.update(newID)
                    newID = new BigInteger(1, md.digest()).toString(16).padLeft(32, '0')
                    newID = newID.substring(0, 8) + '-' + newID.substring(8, 12) + '-' + newID.substring(12, 16) + '-' + newID.substring(16, 20) + '-' + newID.substring(20, 32)
                }
            } catch (IOException ioe) {
                if (log.debugEnabled) {
                    log.debug "Warning could not access network interfaces, message: $ioe.message"
                }
            }
            if (docker) {
                if (newID == existingID || (existingID in ['dde5840d-2193-ead2-f4f3-5c131453d19d', '48e1b46b-68ba-8fad-1e7f-9807d121a81d'])) {
                    newID = null
                } else {
                    newID = existingID
                }
            }
            config.icescrum.appID = newID ?: UUID.randomUUID().toString()
            if (log.debugEnabled) {
                log.debug "Generated (${newID ? 'm' : 'r'}) appID: $config.icescrum.appID"
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
            config.icescrum.appID = existingID
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

    // Parse date from an XML export
    public static Date parseDate(String date) {
        if (!date) {
            return null
        }
        try {
            return new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(date)
        } catch (Exception e) { // Ugly hack because export is toString and if java.util.Date has been exported instead of a java.sql.Date the format is different
            String utcDate = date.take(20) + 'UTC' + date.drop(23) // Fix date that has been exported with server not UTC
            return new SimpleDateFormat('EEE MMM d HH:mm:ss zzz yyyy').parse(utcDate)
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
                    def entryName = (subdir ? '/' + subdir + '/' : '') + it.name // ZIP spec says forward slash only
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

    static public boolean isWritablePath(String dirPath) {
        def dir = new File(dirPath.toString())
        return dir.canRead() && dir.canWrite()
    }

    static public String getConfigFilePath() {
        def configLocations = Holders.grailsApplication.config.grails.config.locations.collect { it.contains('file:') ? it.split('file:')[1] : it }
        return configLocations ? configLocations.first() : System.getProperty("user.home") + File.separator + ".icescrum" + File.separator + "config.groovy"
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

    public static Map getCurrentWorkspace(def params, def id = null) {
        def workspace = Holders.grailsApplication.config.icescrum.workspaces.find { id ? it.key == id : params."$it.key" }
        if (workspace) {
            def object = params.long("$workspace.key") ? workspace.value.objectClass.get(params.long("$workspace.key")) : null
            return object ? [name        : workspace.key,
                             object      : object,
                             config      : workspace.value.config(object),
                             params      : workspace.value.params(object),
                             indexScrumOS: workspace.value.indexScrumOS] : null
        }
    }

    public static Map getJSON(String url, String authenticationBearer, headers = [:], params = [:]) {
        headers.Authorization = "Bearer $authenticationBearer"
        return getJSON(url, null, null, headers, params);
    }

    public static Map getJSON(String url, String username, String password, headers = [:], params = [:]) {
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
            BasicHttpContext localcontext = null
            if (!headers.Authorization && username && password) {
                httpClient.credentialsProvider.setCredentials(new AuthScope(targetHost.hostName, targetHost.port), new UsernamePasswordCredentials(username, password))
                AuthCache authCache = new BasicAuthCache()
                authCache.put(targetHost, new BasicScheme())
                localcontext = new BasicHttpContext()
                localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache)
            }
            // Build request
            HttpGet httpGet = new HttpGet(uri.path)
            headers.each { k, v ->
                httpGet.setHeader(k, v)
            }
            params.each { k, v ->
                httpGet.params.setParameter(k, v)
            }
            // Execute request
            HttpResponse response = localcontext ? httpClient.execute(targetHost, httpGet, localcontext) : httpClient.execute(targetHost, httpGet)
            // Gather results
            resp.status = response.statusLine.statusCode
            def responseText = EntityUtils.toString(response.entity)
            resp.data = JSON.parse(responseText)
            if (resp.status != HttpStatus.SC_OK && log.debugEnabled) {
                log.debug('Error ' + resp.status + ' get ' + uri.toString() + ' ' + responseText)
            }
        } catch (Exception e) {
            log.error(e.message)
            e.printStackTrace()
        } finally {
            httpClient.connectionManager.shutdown()
        }
        return resp
    }

    public static Map postJSON(String url, String authenticationBearer, JSON json, headers = [:], params = [:]) {
        headers.Authorization = "Bearer $authenticationBearer"
        return postJSON(url, null, null, json, headers, params);
    }

    public static Map postJSON(String url, String username, String password, JSON json, headers = [:], params = [:]) {
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
            // Configure basic auth
            BasicHttpContext localcontext = null
            if (!headers.Authorization && username && password) {
                httpClient.credentialsProvider.setCredentials(new AuthScope(targetHost.hostName, targetHost.port), new UsernamePasswordCredentials(username, password))
                AuthCache authCache = new BasicAuthCache()
                authCache.put(targetHost, new BasicScheme())
                localcontext = new BasicHttpContext()
                localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache)
            }
            // Build request
            HttpPost httpPost = new HttpPost(uri.path)
            headers.each { k, v ->
                httpPost.setHeader(k, v)
            }
            params.each { k, v ->
                httpPost.params.setParameter(k, v)
            }
            httpPost.setEntity(new StringEntity(json.toString()));
            // Execute request
            HttpResponse response = localcontext ? httpClient.execute(targetHost, httpPost, localcontext) : httpClient.execute(targetHost, httpPost)
            // Gather results
            resp.status = response.statusLine.statusCode
            def responseText = EntityUtils.toString(response.entity)
            resp.data = JSON.parse(responseText)
            if (resp.status != HttpStatus.SC_OK && log.debugEnabled) {
                log.debug('Error ' + resp.status + ' post ' + uri.toString() + ' ' + json.toString(true) + ' ' + responseText)
            }
        } catch (Exception e) {
            log.error(e.message)
            e.printStackTrace()
        } finally {
            httpClient.connectionManager.shutdown()
        }
        return resp
    }

    public static void exportProjectZIP(Project project, def outputStream) {
        def attachmentableService = Holders.applicationContext.getBean("attachmentableService")
        def projectName = "${project.name.replaceAll("[^a-zA-Z\\s]", "").replaceAll(" ", "")}-${new Date().format('yyyy-MM-dd')}"
        def tempdir = System.getProperty("java.io.tmpdir");
        tempdir = (tempdir.endsWith("/") || tempdir.endsWith("\\")) ? tempdir : tempdir + System.getProperty("file.separator")
        def xml = new File(tempdir + projectName + '.xml')
        try {
            xml.withWriter('UTF-8') { writer ->
                ProjectService projectService = Holders.applicationContext.getBean('projectService')
                projectService.export(writer, project)
            }
            def files = []
            project.stories*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            project.features*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            project.releases*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            project.sprints*.attachments.findAll { it.size() > 0 }?.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            project.attachments.each { it?.each { att -> files << attachmentableService.getFile(att) } }
            def tasks = []
            project.releases*.each { it.sprints*.each { s -> tasks.addAll(s.tasks) } }
            tasks*.attachments.findAll { it.size() > 0 }?.each {
                it?.each { att -> files << attachmentableService.getFile(att) }
            }
            zipExportFile(outputStream, files, xml, 'attachments')
        } catch (Exception e) {
            if (log.debugEnabled) {
                e.printStackTrace()
            }
        } finally {
            xml.delete()
        }
    }

    public static List getSprintXDomain(Sprint sprint, List values) {
        def xDomain = []
        if (values) {
            xDomain = [values.label.min()]
            if (sprint.state == Sprint.STATE_INPROGRESS) {
                xDomain << [sprint.endDate.clone().clearTime().time, values.label.max()].max()
            } else {
                xDomain << values.label.max()
            }
        }
        return xDomain
    }

    public static List getChartTickValues(List xDomain) {
        def tickValues = []
        if (xDomain) {
            def firstDay = new Date(xDomain[0]).clearTime()
            def nbDays = new Date(xDomain[1]).clearTime() - firstDay + 1
            def interval = nbDays <= 10 ? 1 : nbDays <= 20 ? 2 : nbDays <= 30 ? 3 : 7
            nbDays.times { i ->
                if (i % interval == 0) {
                    tickValues << (firstDay + i).time
                }
            }
        }
        return tickValues
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

    static void addWarning(String id, String icon, Map title, Map message, boolean hideable = false) {
        def warnings = Holders.grailsApplication.config.icescrum.warnings
        def warningExist = warnings.find { it.id == id }
        if (!warningExist) {
            def newWarning = [id: id, title: title, message: message, icon: icon, silent: false, hideable: hideable]
            if (log.debugEnabled) {
                log.debug('Adding warning ' + newWarning.inspect())
            }
            warnings << newWarning
        } else {
            warningExist.title = title
            warningExist.message = message
            warningExist.icon = icon
            warningExist.hideable = hideable
        }
    }

    static def toggleSilentWarning(String id) {
        def warning = Holders.grailsApplication.config.icescrum.warnings.find { it.id == id && it.hideable }
        warning?.silent = !warning.silent
        return warning
    }

    static def removeWarning(String id) {
        Holders.grailsApplication.config.icescrum.warnings = Holders.grailsApplication.config.icescrum.warnings.findAll { it.id != id }
    }

    static def getLastWarning() {
        def g = Holders.grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        def lastWarning = Holders.grailsApplication.config.icescrum.warnings?.reverse()?.find { it ->
            !it.silent
        }
        return lastWarning ? [id: lastWarning.id, icon: lastWarning.icon, title: g.message(lastWarning.title), message: g.message(lastWarning.message), hideable: lastWarning.hideable, silent: lastWarning.silent] : null
    }

    static void importComment(def object, User poster, String body, Date dateCreated) {
        def posterClass = poster.class.name
        def i = posterClass.indexOf('_$$_javassist')
        if (i > -1) {
            posterClass = posterClass[0..i - 1]
        }
        def c = new Comment(body: body, posterId: poster.id, posterClass: posterClass)
        c.save()
        def link = new CommentLink(comment: c, commentRef: object.id, type: GrailsNameUtils.getPropertyName(object.class))
        link.save()
        c.dateCreated = dateCreated
    }

    static void importAttachment(def object, def user, def importPath, def attachmentXml) {
        def originalName = attachmentXml.inputName.text()
        if (attachmentXml.url.text()) {
            object.addAttachment(user, [name    : originalName,
                                        url     : attachmentXml.url.text(),
                                        provider: attachmentXml.provider.text(),
                                        length  : attachmentXml.length.toInteger()])
        } else {
            def path = "${importPath}${File.separator}attachments${File.separator}${attachmentXml.@id.text()}.${attachmentXml.ext.text()}"
            def fileAttch = new File(path)
            if (fileAttch.exists()) {
                object.addAttachment(user, fileAttch, originalName)
            }
        }
    }

    static def extractError(Map attrs) {
        def g = Holders.grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        def error = attrs.errors ? attrs.errors.allErrors.collect { [text: g.message(error: it) + ' - ' + it.field] } :
                    attrs.code ? [text: g.message(code: attrs.code)] :
                    attrs.text ? [text: attrs.text] :
                    attrs.exception?.message ? [text: attrs.exception.message] :
                    [text: 'An unexpected error has occurred']
        return error
    }

    static boolean generateInitialsAvatar(String firstName, String lastName, OutputStream outputStream) {
        def initials = "${firstName?.charAt(0)?.toUpperCase()}${lastName?.charAt(0)?.toUpperCase()}"
        BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB)

        Graphics2D graphics = img.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setPaint(colorFromName(initials))
        graphics.fillRect(0, 0, img.getWidth(), img.getHeight())
        graphics.setPaint(Color.white)
        graphics.setFont(new Font("SansSerif", Font.BOLD, (img.width / 1.7).toInteger()))

        def fm = graphics.getFontMetrics();
        def stringBounds = fm.getStringBounds(initials, graphics)
        int x = (img.width - stringBounds.width) / 2
        int y = (img.height - stringBounds.height) / 2 + fm.ascent
        graphics.drawString(initials, x, y)
        ImageIO.write(img, "png", outputStream)

        return true
    }


    private static colorFromName(String name) {
        def i, lon = name.size(), charIndex = 0, colorIndex
        def colors = ["#bdc3c7", "#6f7b87", "#2c3e50", "#2f3193", "#662d91", "#922790", "#ec2176", "#ed1c24", "#f36622", "#f8941e", "#fab70f", "#fdde00", "#d1d219", "#8ec73f", "#00a650", "#00aa9c", "#00adef", "#0081cd", "#005bab"]
        for (i = 0; i < lon; i++) charIndex = Character.codePointAt(name, i);
        colorIndex = charIndex % colors.size();
        def _bgcolor = colors[colorIndex];

        return new Color(
                Integer.valueOf(_bgcolor.substring(1, 3), 16),
                Integer.valueOf(_bgcolor.substring(3, 5), 16),
                Integer.valueOf(_bgcolor.substring(5, 7), 16))
    }
}

abstract class IsTimerTask extends TimerTask {

    protected static final log = LogFactory.getLog(this)
    protected Timer timer
    protected int interval

    public static minutesToMilliseconds(int minutes) {
        return minutes * 60000
    }
}

class CheckerTimerTask extends IsTimerTask {

    CheckerTimerTask(Timer timer, int interval) {
        this.timer = timer
        this.interval = interval
    }

    @Override
    void run() {
        def config = Holders.grailsApplication.config.icescrum.check
        if (!config.enable || !Holders.grailsApplication.config.icescrum.setupCompleted) {
            return
        }
        def configInterval = minutesToMilliseconds(config.interval)
        def serverID = Holders.grailsApplication.config.icescrum.appID
        def referer = Holders.grailsApplication.config.icescrum.serverURL
        def environment = Holders.grailsApplication.config.icescrum.environment
        try {
            def headers = ['User-Agent': 'iceScrum-Agent/1.0', 'Referer': referer, 'Content-Type': 'application/json', 'Accept': 'application/json']
            def params = ['http.connection.timeout': config.timeout, 'http.socket.timeout': config.timeout]
            def url = config.url + "/" + config.path
            def data = [
                    server_id  : serverID,
                    environment: environment,
                    version    : Metadata.current['app.version'].split("\\s+")[0],
                    pro        : (Metadata.current['app.version']).contains('Pro'),
            ] as JSON
            def resp = ApplicationSupport.postJSON(url, null, null, data, headers, params)
            if (resp.status == 200) {
                if (!resp.data.up_to_date) {
                    ApplicationSupport.addWarning('version',
                            'cloud-download',
                            [code: 'is.warning.version', args: [resp.data.version]],
                            [code: 'is.warning.version.message', args: [resp.data.message, resp.data.url]])
                    if (log.debugEnabled) {
                        log.debug('Automatic check for update - A new version is available : ' + resp.data.version)
                    }
                } else {
                    if (log.debugEnabled) {
                        log.debug('Automatic check for update - iceScrum is up to date')
                    }
                }
            }
            if (interval != configInterval) {
                // Back to normal delay
                this.cancel()
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer, configInterval), configInterval, configInterval)
                if (log.debugEnabled) {
                    log.debug('Automatic check for update - back to normal delay')
                }
            }
        } catch (ex) {
            if (interval == configInterval) {
                // Setup new timer with a long delay
                if (log.debugEnabled) {
                    log.debug('Automatic check for update error - new timer delay')
                    log.debug(ex.message)
                }
                this.cancel()
                def longInterval = configInterval >= 1440 ? configInterval * 2 : minutesToMilliseconds(1440)
                timer.scheduleAtFixedRate(new CheckerTimerTask(timer, longInterval), longInterval, longInterval)
            }
        }
    }
}

class ReportUsageTimerTask extends IsTimerTask {

    ReportUsageTimerTask(Timer timer, int interval) {
        this.timer = timer
        this.interval = interval
    }

    @Override
    void run() {
        def config = Holders.grailsApplication.config.icescrum.reportUsage
        if (!config.enable) {
            return
        }
        def configInterval = minutesToMilliseconds(config.interval)
        def serverID = Holders.grailsApplication.config.icescrum.appID
        def referer = Holders.grailsApplication.config.icescrum.serverURL
        def environment = Holders.grailsApplication.config.icescrum.environment
        try {
            def headers = ['User-Agent': 'iceScrum-Agent/1.0', 'Referer': referer, 'Content-Type': 'application/json', 'Accept': 'application/json']
            def params = ['http.connection.timeout': config.timeout, 'http.socket.timeout': config.timeout]
            def url = config.url + "/" + config.path
            Map data
            User.withNewSession {
                data = [
                        users       : User.count(),
                        teams       : Team.getAll().collect({ team ->
                            [members     : team.members.size() ?: 0,
                             projects    : [
                                     all     : team.projects.size(),
                                     archived: team.projects.countBy { project -> project.preferences.archived }
                             ],
                             scrumMasters: team.scrumMasters.size() ?: 0]
                        }),
                        projects    : Project.getAll().collect { project ->
                            [users        : project.allUsers.size() ?: 0,
                             productOwners: project.productOwners.size() ?: 0,
                             tasks        : project.tasks.size(),
                             stories      : [
                                     type  : project.stories.countBy { story -> story.type },
                                     states: project.stories.countBy { story -> story.state }
                             ],
                             features     : [
                                     type  : project.features.countBy { feature -> feature.type },
                                     states: project.features.countBy { feature -> feature.state }
                             ],
                             releases     : project.releases.collect { release ->
                                 [
                                         sprints : release.sprints.collect { sprint ->
                                             [state         : sprint.state,
                                              capacity      : sprint.capacity,
                                              velocity      : sprint.velocity,
                                              retrospective : !sprint.retrospective?.isEmpty(),
                                              duration      : sprint.duration,
                                              tasks         : sprint.tasks.size(),
                                              stories       : sprint.stories.size(),
                                              urgentTasks   : sprint.urgentTasks.size(),
                                              recurrentTasks: sprint.recurrentTasks.size()]
                                         },
                                         state   : release.state,
                                         vision  : !release.vision?.isEmpty(),
                                         duration: release.duration]
                             }]
                        },
                        apps        : [:],
                        server_id   : serverID,
                        environment : environment,
                        java_version: System.getProperty("java.version"),
                        OS          : "${System.getProperty('os.name')} / ${System.getProperty('os.arch')} / ${System.getProperty('os.version')}"
                ]

                def appDefinitionService = Holders.grailsApplication.mainContext.appDefinitionService
                appDefinitionService.getAppDefinitions().each { AppDefinition definition ->
                    // Generic data
                    data.apps."$definition.id" = [
                            enabled: definition.enabledForServer
                    ]
                    if (definition.isProject) {
                        data.apps."$definition.id".projects = SimpleProjectApp.countByAppDefinitionIdAndEnabled(definition.id, true)
                    }
                    // App specific data
                    if (definition.reportUsageData) {
                        definition.reportUsageData(data.apps."$definition.id", Holders.grailsApplication)
                    }
                }
            }
            def resp = ApplicationSupport.postJSON(url, null, null, data as JSON, headers, params)
            if (resp.status == 200) {
                if (log.debugEnabled) {
                    log.debug('Automatic report usage - report sent')
                }
            }
            if (interval != configInterval) {
                // Back to normal delay
                this.cancel()
                timer.scheduleAtFixedRate(new ReportUsageTimerTask(timer, configInterval), configInterval, configInterval)
                if (log.debugEnabled) {
                    log.debug('Automatic report usage - back to normal delay')
                }
            }
        } catch (ex) {
            if (interval == configInterval) {
                // Setup new timer with a long delay
                if (log.debugEnabled) {
                    log.debug('Automatic report usage error - new timer delay')
                    log.debug(ex.message)
                    ex.printStackTrace()
                }
                this.cancel()
                def longInterval = configInterval >= 1440 ? configInterval * 2 : minutesToMilliseconds(1440)
                timer.scheduleAtFixedRate(new ReportUsageTimerTask(timer, longInterval), longInterval, longInterval)
            }
        }
    }
}
