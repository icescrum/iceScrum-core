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

import org.icescrum.components.UiControllerArtefactHandler
import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.scaffolding.view.ScaffoldingViewResolver
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.atmosphere.cpr.BroadcasterFactory
import grails.converters.JSON
import org.springframework.web.context.request.RequestContextHolder
import org.icescrum.core.services.SecurityService
import org.icescrum.core.utils.IceScrumDomainClassMarshaller
import org.atmosphere.util.ExcludeSessionBroadcaster
import org.icescrum.ConfigurationHolder
import grails.converters.XML

class IcescrumCoreGrailsPlugin {
    def groupId = 'org.icescrum'
    // the plugin version
    def version = "1.4.2.5"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def artefacts = [new UiControllerArtefactHandler()]

    def observe = ['controllers']

    def loadAfter = ['controllers', 'feeds']

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
        def config = ConfigurationHolder.config
        if (config) {
            def servlets = xml.'servlet'
            servlets[servlets.size() - 1] + {
                'servlet' {
                    'description'('AtmosphereServlet')
                    'servlet-name'('AtmosphereServlet')
                    'servlet-class'('org.atmosphere.cpr.AtmosphereServlet')
                    config?.atmospherePlugin?.servlet?.initParams.each { initParam ->
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
                    def urlPattern = config?.atmospherePlugin?.servlet?.urlPattern ?: '/atmosphere/*'
                    'url-pattern'(urlPattern)
                }
            }
        }
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
    }

    def doWithDynamicMethods = { ctx ->
        // Manually match the UIController classes
        application.controllerClasses.each {
            if (it.hasProperty(UiControllerArtefactHandler.PROPERTY)) {
                application.addArtefact(UiControllerArtefactHandler.TYPE, it)
                def plugin = it.hasProperty(UiControllerArtefactHandler.PLUGINNAME) ? it.getPropertyValue(UiControllerArtefactHandler.PLUGINNAME) : null
                addUIControllerMethods(it, ctx, plugin)
            }
        }
        SecurityService securityService = ctx.getBean('securityService')
        application.controllerClasses.each {
            addBroadcastMethods(it, securityService)
            addErrorMethod(it)
        }
        application.serviceClasses.each {
            addBroadcastMethods(it, securityService)
        }
    }

    def doWithApplicationContext = { applicationContext ->
        JSON.registerObjectMarshaller(new IceScrumDomainClassMarshaller(true, application.config?.icescrum?.json))
        applicationContext.bootStrapService.start()
    }

    def onChange = { event ->
        def controller = application.getControllerClass(event.source?.name)
        if (controller?.hasProperty(UiControllerArtefactHandler.PROPERTY)) {
            ScaffoldingViewResolver.clearViewCache()
            application.addArtefact(UiControllerArtefactHandler.TYPE, controller)
            def plugin = controller.hasProperty(UiControllerArtefactHandler.PLUGINNAME) ? controller.getPropertyValue(UiControllerArtefactHandler.PLUGINNAME) : null
            addUIControllerMethods(controller, application.mainContext, plugin)
        }
        if (application.isControllerClass(event.source)) {
            SecurityService securityService = event.ctx.getBean('securityService')
            addBroadcastMethods(event.source, securityService)
            addErrorMethod(event.source)
        }
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    private addUIControllerMethods(clazz, ApplicationContext ctx, pluginName) {
        def mc = clazz.metaClass
        def dynamicActions = [
                toolbar: {->
                    try {
                        render(plugin: pluginName, template: "window/toolbar", model: [currentView: session.currentView, id: id])
                    } catch (Exception e) {
                        render('')
                        e.printStackTrace()
                    }
                },
                toolbarWidget: {->
                    try {
                        render(plugin: pluginName, template: "widget/toolbar", model: [id: id])
                    } catch (Exception e) {
                        render('')
                        e.printStackTrace()
                    }
                },
                titleBarContent: {
                    try {
                        render(plugin: pluginName, template: "window/titleBarContent", model: [id: id])
                    } catch (Exception e) {
                        render('')
                        e.printStackTrace()
                    }
                },
                titleBarContentWidget: {
                    try {
                        render(plugin: pluginName, template: "widget/titleBarContent", model: [id: id])
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

    private addBroadcastMethods(source, securityService) {

        source.metaClass.bufferBroadcast = { attrs ->
            attrs = attrs ?: [channel: '']
            def request = RequestContextHolder.requestAttributes?.request
            if (!request)
                return
            if (!attrs.channel) {
                def id = securityService.parseCurrentRequestProduct()
                attrs.channel = id ? 'product-' + id : null

                if (!attrs.channel) {
                    id = securityService.parseCurrentRequestTeam()
                    attrs.channel = id ? 'team-' + id : '/push/app'
                }
            }

            if (!request.bufferBroadcast) {
                request.bufferBroadcast = [:]
            }

            if (request.bufferBroadcast."${attrs.channel}" == null) {
                request.bufferBroadcast."${attrs.channel}" = []
            }
        }

        source.metaClass.resumeBufferedBroadcast = { attrs ->
            attrs = attrs ?: [channel: '']
            def request = RequestContextHolder.requestAttributes?.request
            attrs.excludeCaller = attrs.excludeCaller ?: true
            def size = attrs.batchSize ?: 10
            if (!request)
                return
            if (!attrs.channel) {
                def id = securityService.parseCurrentRequestProduct()
                attrs.channel = id ? 'product-' + id : null

                if (!attrs.channel) {
                    id = securityService.parseCurrentRequestTeam()
                    attrs.channel = id ? 'team-' + id : '/push/app'
                }
            }

            if (attrs.channel instanceof String) {
                attrs.channel = [attrs.channel]
            }
            attrs.channel.each {
                if (request.bufferBroadcast && request.bufferBroadcast."${it}") {
                    def broadcaster = BroadcasterFactory.default.lookup(ExcludeSessionBroadcaster.class, it)
                    def batch = []
                    def messages = request.bufferBroadcast."${it}"
                    int partitionCount = messages.size() / size
                    partitionCount.times { partitionNumber ->
                        def start = partitionNumber * size
                        def end = start + size - 1
                        batch << messages[start..end]
                    }
                    if (messages.size() % size) batch << messages[partitionCount * size..-1]
                    batch.each {
                        if (attrs.excludeCaller) {
                            broadcaster?.broadcast((it as JSON).toString(), request.session)
                        } else {
                            broadcaster?.broadcast((it as JSON).toString())
                        }
                    }
                }
                request.bufferBroadcast."${it}" = null
            }
        }

        source.metaClass.broadcast = {attrs ->
            assert attrs.function, attrs.message
            attrs.excludeCaller = attrs.excludeCaller ?: true
            def request = RequestContextHolder.requestAttributes?.request
            if (!request)
                return
            if (!attrs.channel) {
                def id = securityService.parseCurrentRequestProduct()
                attrs.channel = id ? 'product-' + id : null

                if (!attrs.channel) {
                    id = securityService.parseCurrentRequestTeam()
                    attrs.channel = id ? 'team-' + id : '/push/app'
                }
            }
            if (attrs.channel instanceof String) {
                attrs.channel = [attrs.channel]
            }


            def message = [call: attrs.function, object: attrs.message]
            attrs.channel.each {
                def broadcaster = BroadcasterFactory.default.lookup(ExcludeSessionBroadcaster.class, it)
                if (request.bufferBroadcast?."${it}" != null) {
                    request.bufferBroadcast."${it}" << message
                } else {
                    if (attrs.excludeCaller) {
                        broadcaster?.broadcast((message as JSON).toString(), request.session)
                    } else {
                        broadcaster?.broadcast((message as JSON).toString())
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
                        json { render(status: 500, text: is.renderErrors(bean: attrs.object, as:'json')) }
                        xml  { render(status: 500, text: is.renderErrors(bean: attrs.object, as:'xml')) }
                    }
                }else if(attrs.text){
                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text:attrs.text?:'error']] as JSON) }
                        json { render(status: 500, text: [error: attrs.text?:'error'] as JSON) }
                        xml  { render(status: 500, text: [error: attrs.text?:'error'] as XML) }
                    }
                }else{
                    withFormat {
                        html { render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: attrs.exception.getMessage())]] as JSON) }
                        json { render(status: 500, text: [error: message(code: attrs.exception.getMessage())] as JSON) }
                        xml {  render(status: 500, text: [error: message(code: attrs.exception.getMessage())] as XML) }
                    }
                }
                if (log.debugEnabled) attrs.exception.printStackTrace()
            }else{
                withFormat {
                    html { render(status: 400, contentType: 'application/json', text: [notice: [text:attrs.text?:'error']] as JSON) }
                    json { render(status: 500, text: [error: attrs.text?:'error'] as JSON) }
                    xml  { render(status: 500, text: [error: attrs.text?:'error'] as XML) }
                }
            }
        }
    }
}
