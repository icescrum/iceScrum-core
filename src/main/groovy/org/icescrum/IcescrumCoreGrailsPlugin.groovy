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
package org.icescrum

import grails.converters.JSON
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.ServiceArtefactHandler
import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugins.Plugin
import grails.util.GrailsClassUtils
import org.icescrum.atmosphere.AtmosphereUser
import org.icescrum.core.app.AppDefinitionArtefactHandler
import org.icescrum.core.cors.CorsFilter
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.jasper.JasperExportFormat
import org.icescrum.core.jasper.JasperReportDef
import org.icescrum.core.jasper.JasperService
import org.icescrum.core.security.IceScrumRedirectStrategy
import org.icescrum.core.security.IceScrumSimpleUrlLogoutSuccessHandler
import org.icescrum.core.security.ScrumUserDetailsService
import org.icescrum.core.security.rest.TokenAuthenticationFilter
import org.icescrum.core.security.rest.TokenAuthenticationProvider
import org.icescrum.core.security.rest.TokenStorageService
import org.icescrum.core.services.AppDefinitionService
import org.icescrum.core.services.UiDefinitionService
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.ui.UiDefinitionArtefactHandler
import org.icescrum.core.utils.JSONIceScrumDomainClassMarshaller
import org.icescrum.plugins.entryPoints.artefacts.EntryPointsArtefactHandler
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.servlet.support.RequestContextUtils as RCU

import jakarta.servlet.http.HttpServletResponse
import java.lang.reflect.Method

class IcescrumCoreGrailsPlugin extends Plugin {
    def group = 'org.icescrum'
    def version = "7.55"
    def grailsVersion = "7.0.0 > *"
    // EntryPointsArtefactHandler comes from the vendored entry-points plugin
    List artefacts = [UiDefinitionArtefactHandler, AppDefinitionArtefactHandler, new EntryPointsArtefactHandler()]
    def watchedResources = [
            "file:./grails-app/icescrum/*UiDefinition.groovy",
            "file:../plugins/*/grails-app/icescrum/*UiDefinition.groovy",
            "file:./grails-app/icescrum/*Apps.groovy",
            "file:../plugins/*/grails-app/icescrum/*Apps.groovy",
            "file:./grails-app/services/*Service.groovy"
    ]
    def observe = ['controllers', 'services']
    def loadAfter = ['controllers', 'hibernate', 'springSecurityCore', 'cache']
    def loadBefore = ['asset-pipeline']
    def author = "iceScrum"
    def authorEmail = "contact@icescrum.org"
    def title = "iceScrum core plugin (include domain / services / taglib)"
    def description = '''iceScrum core plugin (include domain / services / taglib)'''
    def documentation = "https://www.icescrum.com/documentation"

    def controllersWithDownloadAndPreview = ['story', 'task', 'feature', 'sprint', 'release', 'project']

    @Override
    Closure doWithSpring() {
        { ->
            println 'Configuring iceScrum...'
            def application = grailsApplication
            ApplicationSupport.initEnvironment(application.config) // DO NOT MOVE IT ELSEWHERE, IT MUST BE DONE BEFORE CREATING UUID
            ApplicationSupport.createUUID()
            System.setProperty('lbdsl.home', "${application.config.icescrum.baseDir.toString()}${File.separator}lbdsl")
            // Init config.icescrum.export for plugins to be able to register without an if exist / create test
            application.config?.icescrum?.export = [:]
            application.domainClasses.each {
                if (it.metaClass.methods*.name.any { it == 'xml' }) {
                    application.config?.icescrum?.export."${it.propertyName}" = []
                }
            }
            application.serviceClasses.each {
                if (it.metaClass.methods*.name.any { it == 'unMarshall' }) {
                    application.config?.icescrum?.import."${it.logicalPropertyName}" = []
                }
            }

            // Grails 2 note: service transaction proxies were patched here so that every
            // service method rolled back on any Throwable. GORM 7 @Transactional services
            // already roll back on any exception (CustomizableRollbackTransactionAttribute),
            // so that hack is no longer needed.

            userDetailsService(ScrumUserDetailsService) {
                grailsApplication = ref('grailsApplication')
            }

            tokenAuthenticationProvider(TokenAuthenticationProvider) {
                tokenStorageService = ref('tokenStorageService')
            }

            tokenAuthenticationFilter(TokenAuthenticationFilter) {
                authenticationManager = ref('authenticationManager')
            }

            tokenStorageService(TokenStorageService) {
                userDetailsService = ref('userDetailsService')
            }

            restAccessDeniedHandler(AccessDeniedHandlerImpl) {
                errorPage = null // 403
            }

            restExceptionTranslationFilter(ExceptionTranslationFilter, ref('restAuthenticationEntryPoint'), ref('restRequestCache')) {
                accessDeniedHandler = ref('restAccessDeniedHandler')
                authenticationTrustResolver = ref('authenticationTrustResolver')
                throwableAnalyzer = ref('throwableAnalyzer')
            }

            restAuthenticationEntryPoint(Http403ForbiddenEntryPoint)
            restRequestCache(NullRequestCache)

            // Replaces the atmosphere-meteor plugin bean of the same name
            atmosphereMeteor(org.icescrum.atmosphere.AtmosphereMeteorCompat)

            SpringSecurityUtils.registerProvider 'tokenAuthenticationProvider'
            SpringSecurityUtils.registerFilter 'tokenAuthenticationFilter', SecurityFilterPosition.BASIC_AUTH_FILTER.order - 1
            SpringSecurityUtils.registerFilter 'restExceptionTranslationFilter', SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order + 1

            redirectStrategy(IceScrumRedirectStrategy) {
                useHeaderCheckChannelSecurity = SpringSecurityUtils.securityConfig.secureChannel.useHeaderCheckChannelSecurity // false
                portResolver = ref('portResolver')
            }

            logoutSuccessHandler(IceScrumSimpleUrlLogoutSuccessHandler) {
                redirectStrategy = ref('redirectStrategy')
                defaultTargetUrl = SpringSecurityUtils.securityConfig.logout.afterLogoutUrl // '/'
                alwaysUseDefaultTargetUrl = SpringSecurityUtils.securityConfig.logout.alwaysUseDefaultTargetUrl // false
                targetUrlParameter = SpringSecurityUtils.securityConfig.logout.targetUrlParameter // null
                useReferer = SpringSecurityUtils.securityConfig.logout.redirectToReferer // false
            }


            // Replaces Grails 2 doWithWebDescriptor: web.xml no longer exists with the
            // embedded servlet container, use Spring Boot registration beans instead.
            if (application.config.getProperty('icescrum.push.enable', Boolean, true)) {
                // Websockets are not backed by HttpSessions; enable HttpSession support at the
                // atmosphere level so the security context can be looked up.
                // https://github.com/Atmosphere/atmosphere/wiki/Enabling-HttpSession-Support
                atmosphereSessionSupport(ServletListenerRegistrationBean) {
                    listener = new org.atmosphere.cpr.SessionSupport()
                }
            }
            def cors = application.config.icescrum.cors
            if (cors.enable) {
                corsFilterRegistration(FilterRegistrationBean) {
                    filter = new CorsFilter()
                    urlPatterns = (cors.urlPatterns instanceof List ? cors.urlPatterns : [cors.urlPatterns ?: '/*'])
                    initParameters = [
                            'allow.origin.regex': cors.allow.origin.regex ? cors.allow.origin.regex.toString() : null,
                            'allowedHeaders'    : cors.allowedHeaders instanceof List ? cors.allowedHeaders.join(', ') : null
                    ].findAll { it.value != null }
                }
            }
        }
    }

    @Override
    void doWithDynamicMethods() {
        def ctx = applicationContext
        def application = grailsApplication
        SpringSecurityService springSecurityService = ctx.getBean('springSecurityService')
        JasperService jasperService = ctx.getBean('jasperService')
        UiDefinitionService uiDefinitionService = ctx.getBean('uiDefinitionService')
        AppDefinitionService appDefinitionService = ctx.getBean('appDefinitionService')
        uiDefinitionService.loadDefinitions()
        appDefinitionService.loadAppDefinitions()
        def entryPointsService = ctx.getBean('entryPointsService')
        application.controllerClasses.each {
            addCleanBeforeBindData(it)
            addJasperMethod(it, springSecurityService, jasperService)
            addEntryPointsMethod(it, entryPointsService)
            // Grails 2 injected download/preview actions into controllers via the metaclass.
            // Grails 7 controller actions must exist at compile time: the concerned
            // controllers now declare download()/preview() actions themselves (see
            // org.icescrum.core.support.AttachmentDownloadSupport).
        }
        org.icescrum.core.event.IceScrumEventPublisher.clearListeners()
        application.serviceClasses.each {
            addListenerSupport(it, ctx)
        }
        application.domainClasses.each {
            addExportDomainsPlugins(it, application.config.icescrum.export)
        }
        application.serviceClasses.each {
            addImportDomainsPlugins(it, application.config.icescrum.import)
        }
    }

    @Override
    void doWithApplicationContext() {
        def application = grailsApplication
        // Startup duties of the vendored taggable and entry-points plugin descriptors
        applicationContext.taggableService.refreshDomainClasses()
        applicationContext.entryPointsService.reload()
        // Grails 7: config returns NavigableMaps whose missing keys are NullSafeNavigators;
        // the marshaller expects plain maps with null for missing keys
        Map properties = plainify(application.config?.icescrum?.marshaller)
        JSON.registerObjectMarshaller(new JSONIceScrumDomainClassMarshaller(application, properties), 1)
        JSON.registerObjectMarshaller(AtmosphereUser) {
            def marshalledUser = [:]
            marshalledUser['id'] = it.id
            marshalledUser['username'] = it.username
            marshalledUser['connections'] = it.connections?.collect {
                [
                        'window'   : it.window,
                        'ipAddress': it.ipAddress,
                        'uuid'     : it.uuid,
                        'transport': it.transport
                ]
            } ?: []
            return marshalledUser
        }
        // Grails 7: plugin lifecycle callbacks no longer run with a bound Hibernate
        // session, so open one for the startup service
        org.icescrum.core.domain.User.withNewSession {
            applicationContext.bootStrapService.start()
        }
    }

    @Override
    void onChange(Map<String, Object> event) {
        def application = grailsApplication
        UiDefinitionService uiDefinitionService = applicationContext.getBean('uiDefinitionService')
        def reloadArtefact = { type ->
            def oldClass = application.getArtefact(type, event.source.name)
            application.addArtefact(type, event.source)
            application.getArtefacts(type).each {
                if (it.clazz != event.source && oldClass.clazz.isAssignableFrom(it.clazz)) {
                    def newClass = application.classLoader.reloadClass(it.clazz.name)
                    application.addArtefact(type, newClass)
                }
            }
        }
        def uiDefinitionType = UiDefinitionArtefactHandler.TYPE
        def appsType = AppDefinitionArtefactHandler.TYPE
        if (application.isArtefactOfType(uiDefinitionType, event.source)) {
            reloadArtefact(uiDefinitionType)
            uiDefinitionService.reload()
        } else if (application.isArtefactOfType(appsType, event.source)) {
            reloadArtefact(appsType)
            ((AppDefinitionService) applicationContext.getBean('appDefinitionService')).reloadAppDefinitions()
        } else if (application.isArtefactOfType(ControllerArtefactHandler.TYPE, event.source)) {
            if (application.isControllerClass(event.source)) {
                SpringSecurityService springSecurityService = applicationContext.getBean('springSecurityService')
                JasperService jasperService = applicationContext.getBean('jasperService')
                addCleanBeforeBindData(event.source)
                addJasperMethod(event.source, springSecurityService, jasperService)
            }
        }
    }

    @Override
    void onConfigChange(Map<String, Object> event) {
        applicationContext.uiDefinitionService.reload()
        ((AppDefinitionService) applicationContext.appDefinitionService).reloadAppDefinitions()
    }

    private static Object plainify(Object value) {
        if (value instanceof Map) {
            def result = [:]
            value.each { k, v -> result[k] = plainify(v) }
            return result
        }
        if (value instanceof Collection) {
            return value.collect { plainify(it) }
        }
        return value
    }

    private void addExportDomainsPlugins(source, config) {
        source.metaClass.exportDomainsPlugins = { builder ->
            def domainObject = delegate
            def progress = RCH.currentRequestAttributes().getSession()?.progress
            if (progress) {
                if (!progress.buffer?.contains(source.propertyName)) {
                    if (!progress.buffer) {
                        progress.buffer = []
                    }
                    progress.buffer << source.propertyName
                    def divisor = (config?.size() ?: 0) * progress.multiple // Grails 7: guard divide-by-zero (see import)
                    def newValue = divisor ? (progress.buffer.size() * 90) / divisor : 90
                    progress.updateProgress(newValue, source.propertyName)
                }
            }
            config[source.propertyName]?.each { closure ->
                closure.delegate = domainObject
                closure(domainObject, builder)
            }
        }
    }

    private void addImportDomainsPlugins(source, config) {
        def name = source.logicalPropertyName
        source.metaClass.importDomainsPlugins = { objectXml, object, options ->
            def progress = RCH.currentRequestAttributes().getSession()?.progress
            if (progress) {
                if (!progress.buffer?.contains(name)) {
                    if (!progress.buffer) {
                        progress.buffer = []
                    }
                    progress.buffer << name
                    // Grails 7: the icescrum.import registry init loop was lost, so config can be empty;
                    // guard the progress computation against divide-by-zero (progress is only cosmetic).
                    def divisor = (config?.size() ?: 0) * progress.multiple
                    def newValue = divisor ? (progress.buffer.size() * 90) / divisor : 90
                    progress.updateProgress(newValue, name)
                }
            }
            config[name]?.each { closure ->
                closure(objectXml, object, options)
            }
            return object
        }
    }

    // From the vendored entry-points plugin descriptor
    private void addEntryPointsMethod(source, entryPointsService) {
        source.clazz.metaClass {
            entryPoints { String ref, Map model = null ->
                assert ref
                if (model instanceof Map) {
                    model.each { request."${it.key}" = it.value }
                } else {
                    request.model = model
                }
                entryPointsService.getEntriesToChain(ref)?.each {
                    forward(action: it.form?.action ?: it.action, controller: it.form?.controller ?: it.controller)
                }
            }
        }
    }

    private void addCleanBeforeBindData(source) {
        source.metaClass.cleanBeforeBindData = { def params, def elems ->
            def toRemove = params.keySet().findAll { String key ->
                return elems.find { prefix -> key != prefix + '.id' && key.startsWith(prefix + '.') }
            }
            def removeProperty // Recursive so must be declared beforehand to be in scope
            removeProperty = { obj, fullKey ->
                String[] key = fullKey.split(/\./, 2)
                if (key.size() == 2) {
                    obj."${key[0]}"?.remove(key[1])
                    if (key[1]?.contains('.') && obj."${key[0]}") {
                        removeProperty(obj."${key[0]}", key[1])
                    }
                    obj."${key[0]}"?.remove(key[1].split(/\./, 2)[0])
                }
                obj.remove(fullKey)
            }
            toRemove.each { String fullKey ->
                removeProperty(params, fullKey)
            }
            return params
        }
    }

    private void addJasperMethod(source, springSecurityService, jasperService) {
        try {
            source.metaClass.renderReport = { String reportName, String format, def data, String outputName = null, def parameters = null ->
                outputName = (outputName ? outputName.replaceAll("[^\\-a-zA-Z\\s]", "").replaceAll(" ", "") + '-' + reportName : reportName) + '-' + (g.formatDate(formatName: 'is.date.file'))
                if (!session.progress) {
                    session.progress = new ProgressSupport()
                }
                session.progress.updateProgress(50, message(code: 'is.report.processing'))
                // getRealPath is null when the WAR runs unexploded (java -jar): extract the
                // precompiled subreports to a temp dir once so $P{SUBREPORT_DIR} + '<name>.jasper'
                // stays resolvable as a plain file path.
                String subreportDir = servletContext.getRealPath('/reports/subreports')
                if (!subreportDir) {
                    File extracted = new File(System.getProperty('java.io.tmpdir'), 'icescrum-subreports')
                    extracted.mkdirs()
                    servletContext.getResourcePaths('/reports/subreports/')?.each { String path ->
                        File target = new File(extracted, path.tokenize('/').last())
                        if (!target.exists()) {
                            servletContext.getResourceAsStream(path)?.withStream { input ->
                                target.bytes = input.bytes
                            }
                        }
                    }
                    subreportDir = extracted.absolutePath
                }
                if (parameters) {
                    parameters.SUBREPORT_DIR = "${subreportDir}/"
                } else {
                    parameters = [SUBREPORT_DIR: "${subreportDir}/"]
                }

                def reportDef = new JasperReportDef(name: reportName,
                        reportData: data,
                        locale: springSecurityService.isLoggedIn() ? springSecurityService.currentUser.locale : RCU.getLocale(request),
                        parameters: parameters,
                        fileFormat: JasperExportFormat.determineFileFormat(format))

                response.characterEncoding = "UTF-8"
                response.setHeader("Content-disposition", "attachment; filename=" + outputName + "." + reportDef.fileFormat.extension)
                session.progress?.completeProgress(message(code: 'is.report.complete'))
                render(file: jasperService.generateReport(reportDef).toByteArray(), contentType: reportDef.fileFormat.mimeTyp)
            }
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            session.progress.progressError(message(code: 'is.report.error'))
        }
    }

    private void addListenerSupport(serviceGrailsClass, ctx) {
        serviceGrailsClass.clazz.declaredMethods.each { Method method ->
            IceScrumListener listener = method.getAnnotation(IceScrumListener)
            if (listener) {
                def domains = listener.domain() ? [listener.domain()] : listener.domains()
                domains.each { domain ->
                    def publisherService = domain != '*' ? ctx.getBean(domain + 'Service') : ctx.getBean('projectService')
                    if (publisherService && publisherService instanceof IceScrumEventPublisher) {
                        def serviceName = serviceGrailsClass.propertyName
                        if (listener.eventType() == IceScrumEventType.UGLY_HACK_BECAUSE_ANNOTATION_CANT_BE_NULL) {
                            publisherService.registerListener(domain) { eventType, object, dirtyProperties ->
                                ctx.getBean(serviceName)."$method.name"(eventType, object, dirtyProperties) // Service bean must be loaded in the callback, not extracted above, because we need the freshest one
                            }
                        } else {
                            publisherService.registerListener(domain, listener.eventType()) { eventType, object, dirtyProperties ->
                                ctx.getBean(serviceName)."$method.name"(object, dirtyProperties)  // Service bean must be loaded in the callback, not extracted above, because we need the freshest one
                            }
                        }
                    }
                }
            }
        }
    }
}
