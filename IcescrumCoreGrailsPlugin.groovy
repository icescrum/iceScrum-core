/*
* Copyright (c) 2011 Kagilum SAS
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


import com.quirklabs.hdimageutils.HdImageService
import grails.converters.JSON
import grails.converters.XML
import grails.plugins.wikitext.WikiTextTagLib
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.BroadcasterFactory
import org.atmosphere.cpr.HeaderConfig
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.scaffolding.view.ScaffoldingViewResolver
import org.icescrum.atmosphere.IceScrumAtmosphereEventListener
import org.icescrum.core.cors.CorsFilter
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.services.StoryService
import org.icescrum.core.utils.JSONIceScrumDomainClassMarshaller
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.icescrum.plugins.attachmentable.services.AttachmentableService
import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.commons.GrailsApplication
import grails.util.Environment
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean
import grails.plugin.springcache.web.key.WebContentKeyGenerator
import org.icescrum.cache.LocaleKeyGenerator
import org.icescrum.cache.ISKeyGeneratorHelper
import org.icescrum.cache.UserKeyGenerator
import org.icescrum.cache.RoleKeyGenerator
import org.icescrum.cache.ProjectUserKeyGenerator
import org.icescrum.cache.StoryKeyGenerator
import org.icescrum.cache.ActorKeyGenerator
import org.icescrum.cache.FeatureKeyGenerator
import org.icescrum.cache.TaskKeyGenerator
import org.icescrum.cache.ReleasesKeyGenerator
import org.icescrum.cache.ReleasesRoleKeyGenerator
import org.icescrum.cache.FeaturesKeyGenerator
import org.icescrum.cache.SprintKeyGenerator
import org.icescrum.cache.TasksKeyGenerator
import org.icescrum.cache.ActorsKeyGenerator
import org.icescrum.cache.StoriesKeyGenerator
import org.icescrum.cache.ProjectKeyGenerator
import org.icescrum.cache.ReleaseKeyGenerator
import org.icescrum.cache.TeamKeyGenerator
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Task
import org.codehaus.groovy.grails.plugins.jasper.JasperReportDef
import org.icescrum.core.domain.User
import org.codehaus.groovy.grails.plugins.jasper.JasperExportFormat
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.plugins.jasper.JasperService
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.services.UiDefinitionService
import org.icescrum.core.ui.UiDefinitionArtefactHandler
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.icescrum.core.domain.Product
import org.icescrum.core.event.IceScrumApplicationEventMulticaster
import org.icescrum.core.utils.XMLIceScrumDomainClassMarshaller
import org.icescrum.core.support.ApplicationSupport

import org.codehaus.groovy.grails.context.support.PluginAwareResourceBundleMessageSource
import org.icescrum.i18n.IceScrumMessageSource

import javax.servlet.http.HttpServletResponse
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class IcescrumCoreGrailsPlugin {
    def groupId = 'org.icescrum'
    // the plugin version
    def version = "1.6-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def artefacts = [new UiDefinitionArtefactHandler()]

    def watchedResources = [
        "file:./grails-app/conf/*UiDefinition.groovy",
        "file:./plugins/*/grails-app/conf/*UiDefinition.groovy"
    ]

    def observe = ['controllers']
    def loadAfter = ['controllers', 'feeds', 'springcache', 'hibernate']
    def loadBefore = ['autobase']

    // TODO Fill in these fields
    def author = "iceScrum"
    def authorEmail = "contact@icescrum.org"
    def title = "iceScrum core plugin (include domain / services / taglib)"
    def description = '''
    iceScrum core plugin (include domain / services / taglib)
'''

    // URL to the plugin's documentation
    def documentation = "http://www.icescrum.org/plugin/icescrum-core"

    def doWithWebDescriptor = { xml ->
        mergeConfig(application)
        def servlets = xml.'servlet'
        servlets[servlets.size() - 1] + {
            'servlet' {
                'description'('AtmosphereServlet')
                'servlet-name'('AtmosphereServlet')
                'servlet-class'('org.atmosphere.cpr.AtmosphereServlet')
                application.config.icescrum.push.servlet.initParams.each { initParam ->
                    'init-param' {
                        'param-name'(initParam.key)
                        'param-value'(initParam.value)
                    }
                }
                'load-on-startup'('0')
            }
        }

        def mappings = xml.'servlet-mapping'
        mappings[mappings.size() - 1] + {
            'servlet-mapping' {
                'servlet-name'('AtmosphereServlet')
                def urlPattern = application.config.icescrum.push.servlet?.urlPattern ?: '/atmosphere/*'
                'url-pattern'(urlPattern)
            }
        }

        def cors = application.config.icescrum.cors
        if (cors.enable){
            addCorsSupport(xml, cors)
        }
    }

    def controllersWithDownloadAndPreview = ['story', 'actor', 'task', 'feature', 'sprint', 'release', 'project']

    def doWithSpring = {
        mergeConfig(application)

        if (application.config.springcache.configLocation){
            springcacheCacheManager(EhCacheManagerFactoryBean) {
                shared = false
                configLocation = application.config.springcache.configLocation
            }
        }

        iSKeyGeneratorHelper(ISKeyGeneratorHelper){
            springSecurityService = ref('springSecurityService')
            securityService = ref('securityService')
        }

        localeKeyGenerator(LocaleKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        userKeyGenerator(UserKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        roleKeyGenerator(RoleKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        projectKeyGenerator(ProjectKeyGenerator){
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        teamKeyGenerator(TeamKeyGenerator){
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        projectUserKeyGenerator(ProjectUserKeyGenerator){
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        sprintKeyGenerator(SprintKeyGenerator){
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        releaseKeyGenerator(ReleaseKeyGenerator){
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        releasesKeyGenerator(ReleasesKeyGenerator){
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        releasesRoleKeyGenerator(ReleasesRoleKeyGenerator){
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        storyKeyGenerator(StoryKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        storiesKeyGenerator(StoriesKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }


        actorKeyGenerator(ActorKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        actorsKeyGenerator(ActorsKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        featureKeyGenerator(FeatureKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        featuresKeyGenerator(FeaturesKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        taskKeyGenerator(TaskKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        tasksKeyGenerator(TasksKeyGenerator) {
            iSKeyGeneratorHelper = ref('iSKeyGeneratorHelper')
            contentType = true
        }

        springcacheDefaultKeyGenerator(WebContentKeyGenerator){
            contentType = true
        }

        asyncApplicationEventMulticaster(IceScrumApplicationEventMulticaster) {
			persistenceInterceptor = ref("persistenceInterceptor")
            taskExecutor = java.util.concurrent.Executors.newCachedThreadPool()
		}

        def beanconf = springConfig.getBeanConfig('messageSource')
        def beandef = beanconf ? beanconf.beanDefinition : springConfig.getBeanDefinition('messageSource')
        if (beandef?.beanClassName == PluginAwareResourceBundleMessageSource.class.canonicalName) {
            beandef.beanClassName = IceScrumMessageSource.class.canonicalName
        }

        ApplicationSupport.createUUID()
        System.setProperty('lbdsl.home', "${application.config.icescrum.baseDir.toString()}${File.separator}lbdsl")
    }

    private void mergeConfig(GrailsApplication app) {
      ConfigObject currentConfig = app.config.icescrum
      ConfigSlurper slurper = new ConfigSlurper(Environment.getCurrent().getName());
      ConfigObject secondaryConfig = slurper.parse(app.classLoader.loadClass("DefaultIceScrumCoreConfig"))
      ConfigObject config = new ConfigObject();
      config.putAll((ConfigObject)secondaryConfig.getProperty('icescrum').merge(currentConfig))
      app.config.icescrum = config
    }

    def doWithDynamicMethods = { ctx ->
        // Manually match the UIController classes
        SpringSecurityService springSecurityService = ctx.getBean('springSecurityService')
        HdImageService hdImageService = ctx.getBean('hdImageService')
        AttachmentableService attachmentableService = ctx.getBean('attachmentableService')
        JasperService jasperService = ctx.getBean('jasperService')
        UiDefinitionService uiDefinitionService = ctx.getBean('uiDefinitionService')
        uiDefinitionService.loadDefinitions()

        application.controllerClasses.each {
            if(uiDefinitionService.hasDefinition(it.logicalPropertyName)) {
                def plugin = it.hasProperty('pluginName') ? it.getPropertyValue('pluginName') : null
                addUIControllerMethods(it, ctx, plugin)
            }
            addBroadcastMethods(it, application)
            addErrorMethod(it)
            addWithObjectsMethods(it)
            addRenderRESTMethod(it)
            addJasperMethod(it, springSecurityService, jasperService)

            if (it.logicalPropertyName in controllersWithDownloadAndPreview){
                addDownloadAndPreviewMethods(it, attachmentableService, hdImageService)
            }
        }

        application.serviceClasses.each {
            addBroadcastMethods(it, application)
            addListenerSupport(it, ctx)
        }
        // Old school because no GORM Static API at the point where it is called
        def transactionManager = ctx.getBean('transactionManager')
        def migrateTemplates = {
            StoryService storyService = ctx.getBean('storyService')
            storyService.migrateTemplatesInDb()
        }
        new TransactionTemplate(transactionManager).execute(migrateTemplates as TransactionCallback)
    }

    def doWithApplicationContext = { applicationContext ->
        //For iceScrum internal
        Map properties = application.config?.icescrum?.marshaller
        WikiTextTagLib textileRenderer = (WikiTextTagLib)application.mainContext["grails.plugins.wikitext.WikiTextTagLib"]
        JSON.registerObjectMarshaller(new JSONIceScrumDomainClassMarshaller(false, true, properties, textileRenderer), 1)

        XML.registerObjectMarshaller(new XMLIceScrumDomainClassMarshaller(true, properties), 1)

        //TODO should be removed and merged with marshaller
        properties = application.config?.icescrum?.restMarshaller
        //For rest API
        JSON.createNamedConfig('rest'){
            it.registerObjectMarshaller(new JSONIceScrumDomainClassMarshaller(false, false, properties),2)
        }
        XML.createNamedConfig('rest'){
            it.registerObjectMarshaller(new XMLIceScrumDomainClassMarshaller(false, properties), 2)
        }
        applicationContext.bootStrapService.start()
    }

    def onChange = { event ->
        UiDefinitionService uiDefinitionService = event.ctx.getBean('uiDefinitionService')
        def type = UiDefinitionArtefactHandler.TYPE

        if (application.isArtefactOfType(type, event.source))
        {
            def oldClass = application.getArtefact(type, event.source.name)
            application.addArtefact(type, event.source)
            application.getArtefacts(type).each {
                if (it.clazz != event.source && oldClass.clazz.isAssignableFrom(it.clazz)) {
                    def newClass = application.classLoader.reloadClass(it.clazz.name)
                    application.addArtefact(type, newClass)
                }
            }
            uiDefinitionService.reload()
        }
        else if (application.isArtefactOfType(ControllerArtefactHandler.TYPE, event.source))
        {
            def controller = application.getControllerClass(event.source?.name)
            HdImageService hdImageService = event.ctx.getBean('hdImageService')
            AttachmentableService attachmentableService = event.ctx.getBean('attachmentableService')

            if(uiDefinitionService.hasDefinition(controller.logicalPropertyName)) {
                ScaffoldingViewResolver.clearViewCache()
                def plugin = controller.hasProperty('pluginName') ? controller.getPropertyValue('pluginName') : null
                addUIControllerMethods(controller, application.mainContext, plugin)
                if (controller.logicalPropertyName in controllersWithDownloadAndPreview){
                    addDownloadAndPreviewMethods(controller, attachmentableService, hdImageService)
                }
            }
            if (application.isControllerClass(event.source)) {
                addBroadcastMethods(event.source, application)

                addErrorMethod(event.source)
                addWithObjectsMethods(event.source)
                addRenderRESTMethod(event.source)

                SpringSecurityService springSecurityService = event.ctx.getBean('springSecurityService')
                JasperService jasperService = event.ctx.getBean('jasperService')
                addJasperMethod(event.source, springSecurityService, jasperService)
            }
        }
    }

    def onConfigChange = { event ->
        this.mergeConfig(event.application)
        event.application.mainContext.uiDefinitionService.reload()
    }

    private addUIControllerMethods(clazz, ApplicationContext ctx, pluginName) {
        def mc = clazz.metaClass
        def dynamicActions = [
                toolbarWidget: {->
                    try {
                        render(plugin: pluginName, template: "widget/toolbar", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        log.debug(e.getMessage())
                    }
                },
                toolbar: {->
                    try {
                        render(plugin: pluginName, template: "window/toolbar", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        log.debug(e.getMessage())
                    }
                },
                titleBarContent: {
                    try {
                        render(plugin: pluginName, template: "window/titleBarContent", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        log.debug(e.getMessage())
                    }
                },
                right: {
                    try {
                        render(plugin: pluginName, template: "window/right", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        log.debug(e.getMessage())
                    }
                },
                titleBarContentWidget: {
                    try {
                        render(plugin: pluginName, template: "widget/titleBarContent", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        log.debug(e.getMessage())
                    }
                }
        ]

        dynamicActions.each { actionName, actionClosure ->
            if (!clazz.getPropertyValue(actionName)) {
                mc."${GrailsClassUtils.getGetterName(actionName)}" = {->
                    actionClosure.delegate = delegate
                    actionClosure.resolveStrategy = Closure.DELEGATE_FIRST
                    actionClosure
                }
                clazz.registerMapping(actionName)
            }
        }
    }

    private addDownloadAndPreviewMethods(clazz, attachmentableService, hdImageService){
        def mc = clazz.metaClass
        def dynamicActions = [
            download : { ->
                Attachment attachment = Attachment.get(params.id as Long)
                if (attachment) {
                    if (attachment.url){
                        redirect(url: "${attachment.url}")
                        return
                    }else{
                        File file = attachmentableService.getFile(attachment)

                        if (file.exists()) {
                            if (!attachment.previewable){
                                String filename = attachment.filename
                                ['Content-disposition': "attachment;filename=\"$filename\"",'Cache-Control': 'private','Pragma': ''].each {k, v ->
                                    response.setHeader(k, v)
                                }
                            }
                            response.contentType = attachment.contentType
                            response.outputStream << file.newInputStream()
                            return
                        }
                    }
                }
                response.status = HttpServletResponse.SC_NOT_FOUND
            },
            preview: {
                Attachment attachment = Attachment.get(params.id as Long)
                File file = attachmentableService.getFile(attachment)
                def thumbnail = new File(file.parentFile.absolutePath+File.separator+attachment.id+'-thumbnail.'+(attachment.ext?.toLowerCase() != 'gif'? attachment.ext :'jpg'))
                if (!thumbnail.exists()){
                    thumbnail.setBytes(hdImageService.scale(file.absolutePath, 40, 40))
                }
                if (thumbnail.exists()){
                    response.contentType = attachment.contentType
                    response.outputStream << thumbnail.newInputStream()
                } else {
                    render (status: 404)
                }
            }
        ]

        dynamicActions.each{ actionName, actionClosure ->
            mc."${GrailsClassUtils.getGetterName(actionName)}" = {->
                actionClosure.delegate = delegate
                actionClosure.resolveStrategy = Closure.DELEGATE_FIRST
                actionClosure
            }
            clazz.registerMapping(actionName)
        }
    }

    def bufferBroadcast = new ConcurrentHashMap<String, ArrayList<String>>()

    private addBroadcastMethods(source, application) {

        source.metaClass.bufferBroadcast = { attrs ->

            if (!application.config.icescrum.push?.enable)
                return

            attrs.channel = attrs.channel ? (attrs.channel instanceof String ? [attrs.channel] : attrs.channel) : [application.config.icescrum.push.mainChannel]
            def threadId = Thread.currentThread().id
            attrs.channel.each{ String it ->
                if (!bufferBroadcast.containsKey(it)) {
                    bufferBroadcast.put(threadId+'#'+it,[])
                }
            }
        }

        source.metaClass.resumeBufferedBroadcast = { attrs ->
            if (!application.config.icescrum.push?.enable)
                return

            attrs.channel = attrs.channel ? (attrs.channel instanceof String ? [attrs.channel] : attrs.channel) : [application.config.icescrum.push.mainChannel]
            attrs.excludeCaller = attrs.excludeCaller ?: true
            def size = attrs.batchSize ?: 10
            def threadId = Thread.currentThread().id

            attrs.channel.each { String it ->
                if (bufferBroadcast && bufferBroadcast.containsKey(threadId+'#'+it)) {
                    def broadcaster = BroadcasterFactory?.default?.lookup(it)?:null
                    if(broadcaster){
                        def batch = []
                        def messages = bufferBroadcast.get(threadId+'#'+it)
                        def uuid = null
                        try{
                            uuid = attrs.excludeCaller ? RequestContextHolder.currentRequestAttributes()?.request?.getHeader(HeaderConfig.X_ATMOSPHERE_TRACKING_ID) : null
                        }catch(IllegalStateException e){
                            //something we are not in a webrequest (like in batch threads)
                        }
                        int partitionCount = messages.size() / size
                        partitionCount.times { partitionNumber ->
                            def start = partitionNumber * size
                            def end = start + size - 1
                            batch << messages[start..end]
                        }
                        if (messages.size() % size) batch << messages[partitionCount * size..-1]
                        try {
                            if (log.debugEnabled){
                                log.debug("broadcast to channel ${it} and exclude uuid : ${uuid}")
                            }
                            batch.each {
                                Set<AtmosphereResource> resources = uuid ? broadcaster.atmosphereResources?.findAll{ AtmosphereResource r -> r.uuid() !=  uuid} : null
                                if(resources){
                                    broadcaster.broadcast((it as JSON).toString(), resources)
                                } else if (!uuid) {
                                    broadcaster.broadcast((it as JSON).toString())
                                }
                            }
                        }catch(Exception e){
                            log.error("Error when broadcasting, message: ${e.getMessage()}", e)
                        }
                    }
                }
                bufferBroadcast.remove(threadId+'#'+it)
            }

            //clean old buffered broadcast if something happened...
            def threadIds = Thread.getAllStackTraces().keySet()*.id
            bufferBroadcast.keys().findAll{ String it ->
                !((it.substring(0, it.indexOf('#'))).toLong() in threadIds)
            }?.each{
                if (log.errorEnabled){
                    log.error("Clean old buffered broadcast")
                }
                bufferBroadcast.remove(it)
            }
        }

        source.metaClass.broadcast = { attrs ->
            if (!application.config.icescrum.push?.enable)
                return

            assert attrs.function, attrs.message

            attrs.channel = attrs.channel ? (attrs.channel instanceof String ? [attrs.channel] : attrs.channel) : [application.config.icescrum.push.mainChannel]
            attrs.excludeCaller = attrs.excludeCaller ?: true
            def threadId = Thread.currentThread().id

            def message = [call: attrs.function, object: attrs.message]
            attrs.channel.each { String it ->
                def broadcaster = BroadcasterFactory?.default?.lookup(it)?:null
                if(broadcaster){
                    def uuid = null
                    try{
                        uuid = attrs.excludeCaller ? RequestContextHolder.currentRequestAttributes()?.request?.getHeader(HeaderConfig.X_ATMOSPHERE_TRACKING_ID) : null
                    }catch(IllegalStateException e){
                        //something we are not in a webrequest (like in batch threads)
                    }
                    if (bufferBroadcast.containsKey(threadId+'#'+it)) {
                        bufferBroadcast.get(threadId+'#'+it) << message
                    } else {
                        try {
                            if (log.debugEnabled){
                                log.debug("broadcast to channel ${it} and exclude uuid : ${uuid}")
                            }
                            Set<AtmosphereResource> resources = uuid ? broadcaster.atmosphereResources?.findAll{ AtmosphereResource r -> r.uuid() !=  uuid} : null
                            if(resources){
                                broadcaster.broadcast((message as JSON).toString(), resources)
                            } else if (!uuid) {
                                broadcaster.broadcast((message as JSON).toString())
                            }
                        }catch(Exception e){
                            log.error("Error when broadcasting, message: ${e.getMessage()}", e)
                        }
                    }
                }
            }
        }

        source.metaClass.broadcastToSingleUser = {attrs ->
            if (!application.config.icescrum.push?.enable)
               return
            assert attrs.function
            assert attrs.message
            assert attrs.user
            if (!attrs.user)
                return
            if (attrs.user instanceof String) {
                attrs.user = [attrs.user]
            }
            def message = [call: attrs.function, object: attrs.message]
            def broadcaster = BroadcasterFactory?.default?.lookup(application.config.icescrum.push.mainChannel)?:null
            Set<AtmosphereResource> resources = broadcaster?.atmosphereResources?.findAll{ it.request?.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)?.username in attrs.user }?:null
            if(resources){
                try {
                    broadcaster.broadcast((message as JSON).toString(), resources)
                }catch(Exception e){
                    log.error("Error when broadcasting, message: ${e.getMessage()}", e)
                }
            }
        }
    }

    private addErrorMethod(source) {
        source.metaClass.returnError = { attrs ->
            def error = attrs.object?.hasErrors() ? attrs.object.errors.allErrors.collect { [code: "${controllerName}.${it.field}",text:message(error:it)] } :
                    attrs.text ? [error:attrs.text] : attrs.exception?.getMessage() ? [error:attrs.exception.getMessage()] : [error: 'An error has occured']

            if (delegate.log.debugEnabled && !attrs.object?.hasErrors() && attrs.exception) {
                delegate.log.debug(attrs.exception)
                delegate.log.debug(attrs.exception.cause)
                attrs.exception.stackTrace.each {
                    delegate.log.debug(it)
                }
            }else if (!delegate.log.debugEnabled && delegate.log.errorEnabled && !attrs.object?.hasErrors() && attrs.exception){
                delegate.log.error(attrs.exception)
                delegate.log.error(attrs.exception.cause)
                attrs.exception.stackTrace.each {
                    delegate.log.error(it)
                }
            }
            withFormat {
                html { render(status: 400, contentType: 'application/json', text:error as JSON) }
                json { renderRESTJSON(text:error, status:400) }
                xml  { renderRESTXML(text:error, status:400) }
            }
        }
    }

    private addRenderRESTMethod(source) {
        source.metaClass.renderRESTJSON = { attrs ->
            JSON.use('rest'){
                render (status: attrs.status?:200, contentType: 'application/json', text: attrs.text as JSON)
            }
        }
        source.metaClass.renderRESTXML = { attrs ->
            XML.use('rest'){
                render(status: attrs.status?:200, contentType: 'application/xml', text: attrs.text as XML)
            }
        }
    }

    private void addJasperMethod(source, springSecurityService, jasperService){
        try {
            source.metaClass.outputJasperReport = { String reportName, String format, def data, String outputName = null, def parameters = null ->
                outputName = (outputName ? outputName.replaceAll("[^a-zA-Z\\s]", "").replaceAll(" ", "") + '-' + reportName : reportName) + '-' + (g.formatDate(formatName: 'is.date.file'))
                if (!session.progress){
                     session.progress = new ProgressSupport()
                }
                session.progress.updateProgress(50, message(code: 'is.report.processing'))
                if (parameters){
                    parameters.SUBREPORT_DIR = "${servletContext.getRealPath('reports/subreports')}/"
                }else{
                    parameters = [SUBREPORT_DIR: "${servletContext.getRealPath('reports/subreports')}/"]
                }
                def reportDef = new JasperReportDef(name: reportName,
                                                    reportData: data,
                                                    locale: springSecurityService.isLoggedIn() ? springSecurityService.currentUser.locale : RCU.getLocale(request),
                                                    parameters: parameters,
                                                    fileFormat: JasperExportFormat.determineFileFormat(format))

                response.setHeader("Content-disposition", "attachment; filename=" + outputName + "." + reportDef.fileFormat.extension)
                response.contentType = reportDef.fileFormat.mimeTyp
                response.characterEncoding = "UTF-8"
                response.outputStream << jasperService.generateReport(reportDef).toByteArray()
                session.progress?.completeProgress(message(code: 'is.report.complete'))
            }
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            session.progress.progressError(message(code: 'is.report.error'))
        }
    }

    private addWithObjectsMethods (source) {
        source.metaClass.withFeature = { def id = 'id', Closure c ->
            def feature = (Feature)Feature.getInProduct(params.long('product'), (id instanceof String ? params."$id".toLong() : id) ).list()
            if (feature) {
                try {
                    c.call feature
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(object: feature, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.feature.error.not.exist'))
            }
        }

        source.metaClass.withFeatures = { String id = 'id', Closure c ->
            def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
            List<Feature> features = ids ? Feature.getAll(ids) : null
            if (features) {
                try {
                    c.call features
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (features.size() == 1){
                        returnError(exception: e, object:features[0])
                    } else {
                        returnError(exception: e)
                    }
                }
            } else {
                returnError(text: message(code: 'is.feature.error.not.exist'))
            }
        }

        source.metaClass.withActor = { def id = 'id', Closure c ->
            def actor = (Actor)Actor.getInProduct(params.long('product'), (id instanceof String ? params."$id".toLong() : id) ).list()
            if (actor) {
                try {
                    c.call actor
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(object: actor, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.actor.error.not.exist'))
            }
        }

        source.metaClass.withActors = { String id = 'id', Closure c ->
            def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
            List<Actor> actors = ids ? Actor.getAll(ids) : null
            if (actors) {
                try {
                    c.call actors
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (actors.size() == 1){
                        returnError(exception: e, object:actors[0])
                    } else {
                        returnError(exception: e)
                    }
                }
            } else {
                returnError(text: message(code: 'is.actor.error.not.exist'))
            }
        }

        source.metaClass.withStory = { def id = 'id', def uid = false, Closure c ->
            def story
            if (uid)
                story = (Story)Story.getInProductByUid(params.long('product'), (id instanceof String ? params."$id".toInteger() : id) ).list()
            else
                story = (Story)Story.getInProduct(params.long('product'), (id instanceof String ? params."$id".toLong() : id) ).list()

            if (story) {
                try {
                    c.call story
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(object: story, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.story.error.not.exist'))
            }
        }

        source.metaClass.withStories = { String id = 'id', Closure c ->
            def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
            List<Story> stories = ids ? Story.getAll(ids) : null
            if (stories) {
                try {
                    c.call stories
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (stories.size() == 1){
                        returnError(exception: e, object:stories[0])
                    } else {
                        returnError(exception: e)
                    }
                }
            } else {
                returnError(text: message(code: 'is.story.error.not.exist'))
            }
        }

        source.metaClass.withAcceptanceTest = { def id = 'id', Closure c ->
            def acceptanceTest = (AcceptanceTest) AcceptanceTest.getInProduct(params.long('product'), (id instanceof String ? params."$id".toLong() : id) )
            if (acceptanceTest) {
                try {
                    c.call acceptanceTest
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(object: acceptanceTest, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.acceptanceTest.error.not.exist'))
            }
        }

        source.metaClass.withTask = { def id = 'id', def uid = false, Closure c ->
            def task
            if (uid)
                task = (Task)Task.getInProductByUid(params.long('product'), (id instanceof String ? params."$id".toInteger() : id) )
            else
                task = (Task)Task.getInProduct(params.long('product'), (id instanceof String ? params."$id".toLong() : id) )
            if (task) {
                try {
                    c.call task
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(object: task, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.task.error.not.exist'))
            }
        }

        source.metaClass.withTasks = { String id = 'id', Closure c ->
            def ids = params.list(id).collect { it.toLong() }
            List<Task> tasks = Task.getAllInProduct(params.long('product'), ids)
            if (tasks) {
                try {
                    c.call tasks
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (tasks.size() == 1){
                        returnError(exception: e, object:tasks[0])
                    } else {
                        returnError(exception: e)
                    }
                }
            } else {
                returnError(text: message(code: 'is.tasks.error.not.exist'))
            }
        }

        source.metaClass.withSprint = { def id = 'id', Closure c ->
            def sprint = (Sprint)Sprint.getInProduct(params.long('product'), (id instanceof String ? params."$id"?.toLong() : id) ).list()
            if (sprint) {
                try {
                    c.call sprint
                } catch (IllegalStateException ise) {
                    returnError(text: message(code: ise.getMessage()))
                } catch (RuntimeException e) {
                    returnError(object: sprint, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.sprint.error.not.exist'))
            }
        }

        source.metaClass.withRelease = { def id = 'id', Closure c ->
            def release = (Release)Release.getInProduct(params.long('product'), (id instanceof String ? params."$id".toLong() : id) ).list()
            if (release) {
                try {
                    c.call release
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(object: release, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.release.error.not.exist'))
            }
        }

        source.metaClass.withUser = { String id = 'id', Closure c ->
            User user = User.get(params."$id"?.toLong())
            if (user) {
                try {
                    c.call user
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(object: user, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.user.error.not.exist'))
            }
        }

        source.metaClass.withProduct = { String id = 'product', Closure c ->
            Product product = Product.get(params."$id"?.toLong())
            if (product) {
                try {
                    c.call product
                }catch (RuntimeException e) {
                    returnError(object: product, exception: e)
                }
            } else {
                returnError(text: message(code: 'is.product.error.not.exist'))
            }
        }
    }

    private addCorsSupport(def xml, def config){
        def contextParam = xml.'context-param'
        contextParam[contextParam.size() - 1] + {
            'filter' {
                'filter-name'('cors-headers')
                'filter-class'(CorsFilter.name)
                if (config.allow.origin.regex) {
                    'init-param' {
                        'param-name'('allow.origin.regex')
                        'param-value'(config.allow.origin.regex.toString())
                    }
                }
                if (config.headers instanceof Map) {
                    config.headers.each { k,v ->
                        'init-param' {
                            'param-name'('header:' + k)
                            'param-value'(v)
                        }
                    }
                }
                if (config.expose.headers) {
                    'init-param' {
                        'param-name'('expose.headers')
                        'param-value'(cors.expose.headers.toString())
                    }
                }
            }
        }

        def urlPattern = config.url.pattern ?: '/*'
        List list = urlPattern instanceof List ? urlPattern : [urlPattern]

        def filter = xml.'filter'
        list.each { pattern ->
            filter[0] + {
                'filter-mapping'{
                    'filter-name'('cors-headers')
                    'url-pattern'(pattern)
                }
            }
        }
    }

    private void addListenerSupport(serviceGrailsClass, ctx) {
        serviceGrailsClass.clazz.declaredMethods.each { Method method ->
            IceScrumListener listener = method.getAnnotation(IceScrumListener)
            if (listener) {
                def listenerService = ctx.getBean(serviceGrailsClass.propertyName)
                def domains =  listener.domain() ? [listener.domain()] : listener.domains()
                domains.each { domain ->
                    def publisherService = ctx.getBean(domain + 'Service')
                    if (publisherService && publisherService instanceof IceScrumEventPublisher) {
                        if (listener.eventType() == IceScrumEventType.UGLY_HACK_BECAUSE_ANNOTATION_CANT_BE_NULL) {
                            println 'Add listener on all ' + domain + ' events: ' + serviceGrailsClass.propertyName + '.' + method.name
                            publisherService.registerListener { eventType, object, dirtyProperties ->
                                listenerService."$method.name"(eventType, object, dirtyProperties)
                            }
                        } else {
                            println 'Add listener on ' + domain + ' ' + listener.eventType().toString() + ' events: ' + serviceGrailsClass.propertyName + '.' + method.name
                            publisherService.registerListener(listener.eventType()) { eventType, object, dirtyProperties ->
                                listenerService."$method.name"(object, dirtyProperties)
                            }
                        }
                    }
                }

            }
        }
    }
}
