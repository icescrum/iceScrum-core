package org.icescrum.core.event

import grails.plugin.springevents.GrailsApplicationEventMulticaster
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.codehaus.groovy.grails.plugin.springevents.ApplicationEventNotification
import org.slf4j.*

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 26/04/12
 * Time: 16:36
 * To change this template use File | Settings | File Templates.
 */
class IceScrumApplicationEventMulticaster extends GrailsApplicationEventMulticaster {

    private final Logger log = LoggerFactory.getLogger(GrailsApplicationEventMulticaster)

    @Override
    void multicastEvent(ApplicationEvent event) {
		getApplicationListeners(event).each { ApplicationListener listener ->
			def notification = new ApplicationEventNotification(listener, event)
            if (event.synchronous){
                notifyListener notification
            }else{
                taskExecutor.execute {
					withPersistenceSession {
						notifyListener notification
					}
				}
            }
		}
	}

    private void withPersistenceSession(Closure closure) {
		log.debug "Initializing PersistenceContextInterceptor ${persistenceInterceptor.getClass().name}"
		persistenceInterceptor?.init()
		try {
			closure()
		} finally {
			persistenceInterceptor?.flush()
			persistenceInterceptor?.destroy()
		}
	}
}
