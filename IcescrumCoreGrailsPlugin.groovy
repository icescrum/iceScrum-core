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

import grails.converters.JSON
import grails.converters.XML
import grails.util.GrailsNameUtils
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.BroadcasterFactory
import org.atmosphere.cpr.HeaderConfig
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.scaffolding.view.ScaffoldingViewResolver
import org.icescrum.atmosphere.IceScrumAtmosphereEventListener
import org.icescrum.core.cors.CorsFilter
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.event.IceScrumSynchronousEvent
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
import org.icescrum.plugins.attachmentable.interfaces.AttachmentException
import org.codehaus.groovy.grails.plugins.jasper.JasperReportDef
import org.icescrum.core.domain.User
import org.codehaus.groovy.grails.plugins.jasper.JasperExportFormat
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import grails.plugins.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.plugins.jasper.JasperService
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.services.UiDefinitionService
import org.icescrum.core.ui.UiDefinitionArtefactHandler
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.icescrum.core.domain.Product
import org.icescrum.core.event.IceScrumApplicationEventMulticaster
import org.icescrum.core.utils.XMLIceScrumDomainClassMarshaller
import org.icescrum.core.support.ApplicationSupport
import pl.burningice.plugins.image.BurningImageService

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
        BurningImageService burningImageService = ctx.getBean('burningImageService')
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
                addDownloadAndPreviewMethods(it, attachmentableService, burningImageService)
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
        def properties = application.config?.icescrum?.marshaller
        JSON.registerObjectMarshaller(new JSONIceScrumDomainClassMarshaller(true, true, properties), 1)
        XML.registerObjectMarshaller(new XMLIceScrumDomainClassMarshaller(true, properties), 1)

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
            BurningImageService burningImageService = event.ctx.getBean('burningImageService')
            AttachmentableService attachmentableService = event.ctx.getBean('attachmentableService')

            if(uiDefinitionService.hasDefinition(controller.logicalPropertyName)) {
                ScaffoldingViewResolver.clearViewCache()
                def plugin = controller.hasProperty('pluginName') ? controller.getPropertyValue('pluginName') : null
                addUIControllerMethods(controller, application.mainContext, plugin)
                if (controller.logicalPropertyName in controllersWithDownloadAndPreview){
                    addDownloadAndPreviewMethods(controller, attachmentableService, burningImageService)
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
                getExportFormats: { ->
                    def exportFormats = ctx.getBean('uiDefinitionService').getDefinitionById(controllerName).exportFormats
                    if (exportFormats instanceof Closure){
                        exportFormats.delegate = delegate
                        exportFormats.resolveStrategy = Closure.DELEGATE_FIRST
                        exportFormats = exportFormats()
                    }
                    entry.hook(id:"${controllerName}-getExportFormats", model:[exportFormats:exportFormats])
                    return exportFormats
                },

                toolbar: {->
                    try {
                        render(plugin: pluginName, template: "window/toolbar", model: [id: controllerName, exportFormats:getExportFormats()])
                    } catch (Exception e) {
                        render('')
                        log.debug(e.getMessage())
                    }
                },
                toolbarWidget: {->
                    try {
                        render(plugin: pluginName, template: "widget/toolbar", model: [id: controllerName])
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

    private addDownloadAndPreviewMethods(clazz, attachmentableService, burningImageService){
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
                    burningImageService.doWith(file.absolutePath, file.parentFile.absolutePath)
                    .execute (attachment.id+'-thumbnail', {
                       it.scaleApproximate(100, 100)
                    })
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

        mc.manageAttachments = { attachmentable, keptAttachments, addedAttachments ->
            def needPush = false
            if (!keptAttachments && attachmentable.attachments.size() > 0) {
                attachmentable.removeAllAttachments()
                needPush = true
            } else {
                attachmentable.attachments.each { attachment ->
                    if (!keptAttachments.contains(attachment.id.toString())) {
                        attachmentable.removeAttachment(attachment)
                    }
                    needPush = true
                }
            }
            def uploadedFiles = []
            addedAttachments.each { attachment ->
                def parts = attachment.split(":")
                if (parts[0].contains('http')) {
                    uploadedFiles << [url: parts[0] +':'+ parts[1], filename: parts[2], length: parts[3], provider:parts[4]]
                } else {
                    if (session.uploadedFiles && session.uploadedFiles[parts[0]]) {
                        uploadedFiles << [file: new File((String) session.uploadedFiles[parts[0]]), filename: parts[1]]
                    }
                }
            }
            session.uploadedFiles = null
            def currentUser = (User) springSecurityService.currentUser
            if (uploadedFiles){
                attachmentable.addAttachments(currentUser, uploadedFiles)
                needPush = true
            }
            def attachmentableClass = GrailsNameUtils.getShortName(attachmentable.class).toLowerCase()
            def newAttachments = [class:'attachments', attachmentable: [class:attachmentableClass, id: attachmentable.id], controllerName:controllerName, attachments:attachmentable.attachments]
            if (needPush){
                attachmentable.lastUpdated = new Date()
                broadcast(function: 'replaceAll', message: newAttachments, channel:'product-'+params.product)
            }
            return newAttachments
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
            if (attrs.exception){

                if (delegate.log.debugEnabled && !attrs.object?.hasErrors()){
                    delegate.log.debug(attrs.exception)
                    delegate.log.debug(attrs.exception.cause)
                    attrs.exception.stackTrace.each {
                        delegate.log.debug(it)
                    }
                }

                if (attrs.object && attrs.exception instanceof RuntimeException){
                    if (!delegate.log.debugEnabled && delegate.log.errorEnabled && !attrs.object?.hasErrors()){
                        delegate.log.error(attrs.exception)
                        delegate.log.error(attrs.exception.cause)
                        attrs.exception.stackTrace.each {
                            delegate.log.error(it)
                        }
                    }

                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text: renderErrors(bean: attrs.object)]] as JSON) }
                        json {
                            JSON.use('rest'){
                                render(status: 400, contentType: 'application/json', text: is.renderErrors(bean: attrs.object, as:'json'))
                            }
                        }
                        xml  {
                            XML.use('rest'){
                                render(status: 400, contentType: 'application/xml', text: is.renderErrors(bean: attrs.object, as:'xml'))
                            }
                        }
                    }
                }else if(attrs.text){
                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text:attrs.text?:'error']] as JSON) }
                        json { renderRESTJSON(text:[error: attrs.text?:'error'], status:400) }
                        xml  { renderRESTXML(text:[error: attrs.text?:'error'], status:400) }
                    }
                }else{
                    if (!delegate.log.debugEnabled && delegate.log.errorEnabled && attrs.exception){
                        delegate.log.error(attrs.exception)
                        delegate.log.error(attrs.exception.cause)
                        attrs.exception.stackTrace.each {
                            delegate.log.error(it)
                        }
                    }
                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: attrs.exception.getMessage())]] as JSON) }
                        json { renderRESTJSON(text:[error: message(code: attrs.exception.getMessage())], status:400) }
                        xml  { renderRESTXML(text:[error: message(code: attrs.exception.getMessage())], status:400) }
                    }
                }
            }else{
                withFormat {
                    html { render(status: 400, contentType: 'application/json', text: [notice: [text:attrs.text?:'error']] as JSON) }
                    json { renderRESTJSON(text:[error: attrs.text?:'error'], status:400) }
                    xml  { renderRESTXML(text:[error: attrs.text?:'error'], status:400) }
                }
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
                } catch (AttachmentException e) {
                    returnError(exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (feature.errors.errorCount)
                        returnError(object: feature, exception: e)
                    else
                        returnError(exception: e)
                }
            } else {
                returnError(text: message(code: 'is.feature.error.not.exist'))
            }
        }

        source.metaClass.withFeatures = { String id = 'id', Closure c ->
            List<Feature> features = Feature.getAll(params.list(id))
            if (features) {
                try {
                    c.call features
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(exception: e)
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
                } catch (AttachmentException e) {
                    returnError(exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (actor.errors.errorCount)
                        returnError(object: actor, exception: e)
                    else
                        returnError(exception: e)
                }
            } else {
                returnError(text: message(code: 'is.actor.error.not.exist'))
            }
        }

        source.metaClass.withActors = { String id = 'id', Closure c ->
            List<Actor> actors = Actor.getAll(params.list(id))
            if (actors) {
                try {
                    c.call actors
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(exception: e)
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
                } catch (AttachmentException e) {
                    returnError(exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (story.errors.errorCount)
                        returnError(object: story, exception: e)
                    else
                        returnError(exception: e)
                }
            } else {
                returnError(text: message(code: 'is.story.error.not.exist'))
            }
        }

        source.metaClass.withStories = { String id = 'id', Closure c ->
            List<Story> stories = params.list(id) ? Story.getAll(params.list(id)) : null
            if (stories) {
                try {
                    c.call stories
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    returnError(exception: e)
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
                } catch (AttachmentException e) {
                    returnError(exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (acceptanceTest.errors.errorCount)
                        returnError(object: acceptanceTest, exception: e)
                    else
                        returnError(exception: e)
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
                } catch (AttachmentException e) {
                    returnError(object: task, exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (task.errors)
                        returnError(object: task, exception: e)
                    else
                        returnError(exception: e)
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
                    returnError(exception: e)
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
                } catch (AttachmentException e) {
                    returnError(object: sprint, exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (sprint.errors.errorCount)
                        returnError(object: sprint, exception: e)
                    else
                        returnError(exception: e)
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
                } catch (AttachmentException e) {
                    returnError(object: release, exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (release.errors.errorCount)
                        returnError(object: release, exception: e)
                    else
                        returnError(exception: e)
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
                    user.discard()
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    user.discard()
                    if (user.errors.errorCount)
                        returnError(object: user, exception: e)
                    else
                        returnError(exception: e)
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
                    if (product.errors.errorCount)
                        returnError(object: product, exception: e)
                    else
                        returnError(exception: e)
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
                def publisherService = ctx.getBean(listener.domain() + 'Service')
                if (publisherService && publisherService instanceof IceScrumEventPublisher) {
                    def listenerClosure = { event ->
                        listenerService."$method.name"(event)
                    }
                    if (listener.eventType() == IceScrumSynchronousEvent.EventType.UGLY_HACK_BECAUSE_ANNOTATION_CANT_BE_NULL) {
                        println 'Add listener on all ' + listener.domain() + ' events: ' + serviceGrailsClass.propertyName + '.' + method.name
                        publisherService.registerListener(listenerClosure)
                    } else {
                        println 'Add listener on ' + listener.domain() + ' ' + listener.eventType().toString() + ' events: ' + serviceGrailsClass.propertyName + '.' + method.name
                        publisherService.registerListener(listener.eventType(), listenerClosure)
                    }
                }
            }
        }
    }
}
