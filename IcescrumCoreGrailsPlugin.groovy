/*
* Copyright (c) 2010 iceScrum Technologies / 2011 Kagilum SAS
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

import grails.converters.JSON
import grails.converters.XML
import org.atmosphere.cpr.BroadcasterFactory
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.scaffolding.view.ScaffoldingViewResolver
import org.icescrum.core.services.SecurityService
import org.icescrum.core.utils.IceScrumDomainClassMarshaller
import org.springframework.context.ApplicationContext
import org.springframework.web.context.request.RequestContextHolder
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
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import grails.plugins.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.plugins.jasper.JasperService
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.services.UiDefinitionService
import org.icescrum.core.ui.UiDefinitionArtefactHandler
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.icescrum.core.domain.Product

class IcescrumCoreGrailsPlugin {
    def groupId = 'org.icescrum'
    // the plugin version
    def version = "1.5-SNAPSHOT"
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

    def loadAfter = ['controllers', 'feeds', 'springcache']

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
    }

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
        SecurityService securityService = ctx.getBean('securityService')
        SpringSecurityService springSecurityService = ctx.getBean('springSecurityService')
        JasperService jasperService = ctx.getBean('jasperService')
        UiDefinitionService uiDefinitionService = ctx.getBean('uiDefinitionService')
        uiDefinitionService.loadDefinitions()

        application.controllerClasses.each {
            if(uiDefinitionService.hasDefinition(it.logicalPropertyName)) {
                def plugin = it.hasProperty('pluginName') ? it.getPropertyValue('pluginName') : null
                addUIControllerMethods(it, ctx, plugin)
            }
            addBroadcastMethods(it, securityService, application)
            addErrorMethod(it)
            addWithObjectsMethods(it)
            addJasperMethod(it, springSecurityService, jasperService)
        }
        application.serviceClasses.each {
            addBroadcastMethods(it, securityService, application)
        }
    }

    def doWithApplicationContext = { applicationContext ->
        JSON.registerObjectMarshaller(new IceScrumDomainClassMarshaller(true, application.config?.icescrum?.json))
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
            if(uiDefinitionService.hasDefinition(controller.logicalPropertyName)) {
                ScaffoldingViewResolver.clearViewCache()
                def plugin = controller.hasProperty('pluginName') ? controller.getPropertyValue('pluginName') : null
                addUIControllerMethods(controller, application.mainContext, plugin)
            }
            if (application.isControllerClass(event.source)) {
                SecurityService securityService = event.ctx.getBean('securityService')
                addBroadcastMethods(event.source, securityService, application)

                addErrorMethod(event.source)
                addWithObjectsMethods(event.source)

                SpringSecurityService springSecurityService = event.ctx.getBean('springSecurityService')
                JasperService jasperService = event.ctx.getBean('jasperService')
                addJasperMethod(event.source, springSecurityService, jasperService)
            }
        }
    }

    def onConfigChange = { event ->
        this.mergeConfig(application)
    }

    private addUIControllerMethods(clazz, ApplicationContext ctx, pluginName) {
        def mc = clazz.metaClass
        def dynamicActions = [
                toolbar: {->
                    try {
                        render(plugin: pluginName, template: "window/toolbar", model: [currentView: session.currentView, id: controllerName])
                    } catch (Exception e) {
                        render('')
                        e.printStackTrace()
                    }
                },
                toolbarWidget: {->
                    try {
                        render(plugin: pluginName, template: "widget/toolbar", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        e.printStackTrace()
                    }
                },
                titleBarContent: {
                    try {
                        render(plugin: pluginName, template: "window/titleBarContent", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        e.printStackTrace()
                    }
                },
                titleBarContentWidget: {
                    try {
                        render(plugin: pluginName, template: "widget/titleBarContent", model: [id: controllerName])
                    } catch (Exception e) {
                        render('')
                        e.printStackTrace()
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

    private addBroadcastMethods(source, securityService, application) {

        source.metaClass.bufferBroadcast = { attrs ->
            if (!application.config.icescrum.push?.enable)
                return
            attrs = attrs ?: [channel: '']
            def request = RequestContextHolder.requestAttributes?.request
            if (!request)
                return
            if (!attrs.channel) {
                def id = securityService.parseCurrentRequestProduct()
                attrs.channel = id ? 'product-' + id : '/push/app'
            }

            if (!request.bufferBroadcast) {
                request.bufferBroadcast = [:]
            }

            if (request.bufferBroadcast."${attrs.channel}" == null) {
                request.bufferBroadcast."${attrs.channel}" = []
            }
        }

        source.metaClass.resumeBufferedBroadcast = { attrs ->
            if (!application.config.icescrum.push?.enable)
                return
            attrs = attrs ?: [channel: '']
            def request = RequestContextHolder.requestAttributes?.request
            attrs.excludeCaller = attrs.excludeCaller ?: true
            def size = attrs.batchSize ?: 10
            if (!request)
                return
            if (!attrs.channel) {
                def id = securityService.parseCurrentRequestProduct()
                attrs.channel = id ? 'product-' + id : '/push/app'
            }

            if (attrs.channel instanceof String) {
                attrs.channel = [attrs.channel]
            }
            attrs.channel.each {
                if (request.bufferBroadcast && request.bufferBroadcast."${it}") {
                    if(BroadcasterFactory.default){
                        Class<? extends org.atmosphere.cpr.Broadcaster> bc = (Class<? extends org.atmosphere.cpr.Broadcaster>)((GrailsApplication) application).getClassLoader().loadClass(application.config.icescrum.push?.broadcaster?:'org.atmosphere.util.ExcludeSessionBroadcaster')
                        def broadcaster = BroadcasterFactory.default.lookup(bc, it)
                        def batch = []
                        def messages = request.bufferBroadcast."${it}"
                        int partitionCount = messages.size() / size
                        partitionCount.times { partitionNumber ->
                            def start = partitionNumber * size
                            def end = start + size - 1
                            batch << messages[start..end]
                        }
                        if (messages.size() % size) batch << messages[partitionCount * size..-1]
                        def session = request.getSession(false)
                        batch.each {
                            if (attrs.excludeCaller && session) {
                                broadcaster?.broadcast((it as JSON).toString(), session)
                            } else {
                                broadcaster?.broadcast((it as JSON).toString())
                            }
                        }
                    }
                }
                request.bufferBroadcast."${it}" = null
            }
        }

        source.metaClass.broadcast = {attrs ->
            if (!application.config.icescrum.push?.enable)
                return
            assert attrs.function, attrs.message
            attrs.excludeCaller = attrs.excludeCaller ?: true
            def request = RequestContextHolder.requestAttributes?.request
            if (!request)
                return
            if (!attrs.channel) {
                def id = securityService.parseCurrentRequestProduct()
                attrs.channel = id ? 'product-' + id : '/push/app'
            }
            if (attrs.channel instanceof String) {
                attrs.channel = [attrs.channel]
            }


            def message = [call: attrs.function, object: attrs.message]
            attrs.channel.each {
                if(BroadcasterFactory.default){
                    Class<? extends org.atmosphere.cpr.Broadcaster> bc = (Class<? extends org.atmosphere.cpr.Broadcaster>)((GrailsApplication) application).getClassLoader().loadClass(application.config.icescrum.push?.broadcaster?:'org.atmosphere.util.ExcludeSessionBroadcaster')
                    def broadcaster = BroadcasterFactory.default.lookup(bc, it)
                    if (request.bufferBroadcast?."${it}" != null) {
                        request.bufferBroadcast."${it}" << message
                    } else {
                        try {
                            def session = request.getSession(false)
                            if (attrs.excludeCaller && session) {
                                broadcaster?.broadcast((message as JSON).toString(), session)
                            } else {
                                broadcaster?.broadcast((message as JSON).toString())
                            }
                        }catch(IllegalStateException e){
                            log.error("Error when broadcasting, message: ${e.getMessage()}", e)
                        }
                    }
                }
            }
        }

        source.metaClass.broadcastToSingleUser = {attrs ->
            ConfigObject conf = application.config.icescrum.push
            if (!conf?.enable)
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
            attrs.user.each {
                if(BroadcasterFactory.default){
                    Class<? extends org.atmosphere.cpr.Broadcaster> bc = (Class<? extends org.atmosphere.cpr.Broadcaster>)((GrailsApplication) application).getClassLoader().loadClass(application.config.icescrum.push?.userBroadcaster?:'org.atmosphere.cpr.DefaultBroadcaster')
                    def broadcaster = BroadcasterFactory.default.lookup(bc, it)
                    try {
                        broadcaster?.broadcast((message as JSON).toString())
                    }catch(IllegalStateException e){
                        log.error("Error when broadcasting, message: ${e.getMessage()}", e)
                    }
                }

            }
        }
    }

    private addErrorMethod(source) {
        source.metaClass.returnError = { attrs ->
            if (attrs.exception){
                if (attrs.object && attrs.exception instanceof RuntimeException){
                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text: renderErrors(bean: attrs.object)]] as JSON) }
                        json { render(status: 500, contentType: 'application/json', text: is.renderErrors(bean: attrs.object, as:'json')) }
                        xml  { render(status: 500, contentType: 'text/xml', text: is.renderErrors(bean: attrs.object, as:'xml')) }
                    }
                }else if(attrs.text){
                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text:attrs.text?:'error']] as JSON) }
                        json { render(status: 500, contentType: 'application/json', text: [error: attrs.text?:'error'] as JSON) }
                        xml  { render(status: 500, contentType: 'text/xml', text: [error: attrs.text?:'error'] as XML) }
                    }
                }else{
                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: attrs.exception.getMessage())]] as JSON) }
                        json { render(status: 500, contentType: 'application/json', text: [error: message(code: attrs.exception.getMessage())] as JSON) }
                        xml {  render(status: 500, contentType: 'text/xml', text: [error: message(code: attrs.exception.getMessage())] as XML) }
                    }
                }
                if (log.debugEnabled) attrs.exception.printStackTrace()
            }else{
                withFormat {
                    html { render(status: 400, contentType: 'application/json', text: [notice: [text:attrs.text?:'error']] as JSON) }
                    json { render(status: 500, contentType: 'application/json', text: [error: attrs.text?:'error'] as JSON) }
                    xml  { render(status: 500, contentType: 'text/xml', text: [error: attrs.text?:'error'] as XML) }
                }
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
                                                    locale: springSecurityService.isLoggedIn() ? new Locale(User.get(springSecurityService.principal?.id)?.preferences?.language) : RCU.getLocale(request),
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

    private addWithObjectsMethods = {source ->
        source.metaClass.withFeature = { String id = 'id', Closure c ->
            Feature feature = Feature.getInProduct(params.long('product'), params."$id"?.toLong()).list()[0]
            if (feature) {
                try {
                    c.call feature
                } catch (AttachmentException e) {
                    returnError(exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (feature.errors)
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
                returnError(text: message(code: 'is.actor.error.not.exist'))
            }
        }

        source.metaClass.withActor = { String id = 'id', Closure c ->
            Actor actor = Actor.getInProduct(params.long('product'), params."$id"?.toLong()).list()[0]
            if (actor) {
                try {
                    c.call actor
                } catch (AttachmentException e) {
                    returnError(exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (actor.errors)
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

        source.metaClass.withStory = { String id = 'id', Closure c ->
            Story story = Story.getInProduct(params.long('product'), params."$id"?.toLong()).list()[0]
            if (story) {
                try {
                    c.call story
                } catch (AttachmentException e) {
                    returnError(exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (story.errors)
                        returnError(object: story, exception: e)
                    else
                        returnError(exception: e)
                }
            } else {
                returnError(text: message(code: 'is.story.error.not.exist'))
            }
        }

        source.metaClass.withStories = { String id = 'id', Closure c ->
            List<Story> stories = Story.getAll(params.list(id))
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

        source.metaClass.withTask = { String id = 'id', Closure c ->
            Task task = Task.getInProduct(params.long('product'), params."$id"?.toLong())
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

        source.metaClass.withSprint = { String id = 'id', Closure c ->
            Sprint sprint = Sprint.getInProduct(params.long('product'), params."$id"?.toLong()).list()[0]
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
                    if (sprint.errors)
                        returnError(object: sprint, exception: e)
                    else
                        returnError(exception: e)
                }
            } else {
                returnError(text: message(code: 'is.sprint.error.not.exist'))
            }
        }

        source.metaClass.withRelease = { String id = 'id', Closure c ->
            Release release = Release.getInProduct(params.long('product'), params."$id"?.toLong()).list()[0]
            if (release) {
                try {
                    c.call release
                } catch (AttachmentException e) {
                    returnError(object: release, exception: e)
                } catch (IllegalStateException e) {
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (release.errors)
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
                    returnError(exception: e)
                } catch (RuntimeException e) {
                    if (user.errors)
                        returnError(object: user, exception: e)
                    else
                        returnError(exception: e)
                }
            } else {
                returnError(text: message(code: 'is.user.error.not.exist'))
            }
        }

        source.metaClass.withProduct = { String id = 'product', Closure c ->
            def pid = params."$id"?.decodeProductKey()
            Product product = Product.get(pid?.toLong())
            if (product) {
                try {
                    c.call product
                }catch (RuntimeException e) {
                    if (product.errors)
                        returnError(object: product, exception: e)
                    else
                        returnError(exception: e)
                }
            } else {
                returnError(text: message(code: 'is.product.error.not.exist'))
            }
        }
    }
}
