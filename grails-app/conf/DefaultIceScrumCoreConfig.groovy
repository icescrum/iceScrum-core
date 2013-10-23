import org.icescrum.core.domain.Product
import org.springframework.cache.ehcache.EhCacheFactoryBean

/*
* Copyright (c) 2011 Kagilum.
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
icescrum {
    push {
        mainChannel = '/stream/app/*'
        enable = true
        websocket = false
        heartBeat {
            enable = true
            delay = 30
        }
        servlet {
            // Servlet initialization parameters
            initParams = ['org.atmosphere.useNative': false,
                          'org.atmosphere.useWebSocket': icescrum.push.websocket,
                          'org.atmosphere.cpr.AtmosphereInterceptor.disableDefaults': true,
                          'org.atmosphere.cpr.broadcaster.shareableThreadPool': true,
                          'org.atmosphere.cpr.broadcaster.maxProcessingThreads': 5,
                          'org.atmosphere.cpr.broadcaster.maxAsyncWriteThreads': 5,
                          'org.atmosphere.cpr.AtmosphereInterceptor' : 'org.atmosphere.interceptor.OnDisconnectInterceptor,org.atmosphere.interceptor.JavaScriptProtocol',
                          'org.atmosphere.cpr.broadcasterClass' : 'org.icescrum.atmosphere.IceScrumBroadcaster',
                          'org.atmosphere.cpr.broadcasterLifeCyclePolicy': 'EMPTY_DESTROY',
                          'org.atmosphere.cpr.broadcastFilterClasses': 'org.atmosphere.client.TrackMessageSizeFilter']
            urlPattern = '/stream/app'
        }
        handlers {
            // This closure is used to generate the atmosphere.xml using a MarkupBuilder instance in META-INF folder
            atmosphereDotXml = {
                'atmosphere-handler'('context-root': icescrum.push.mainChannel, 'class-name': 'org.icescrum.atmosphere.IceScrumAtmosphereHandler')
            }
        }
        redis {
            enable = false
            host = "http://localhost:6379"
        }
    }
    spaces {
        product {
            spaceClass = Product
            config = { product -> [key:product.pkey, path:'p'] }
            params = { product -> [product:product.id] }
            indexScrumOS = { productSpace, user, securityService, springSecurityService ->
                def product = productSpace.object
                if (product?.preferences?.hidden && !securityService.inProduct(product, springSecurityService.authentication) && !securityService.stakeHolder(product,springSecurityService.authentication,false)){
                    forward(action:springSecurityService.isLoggedIn() ? 'error403' : 'error401',controller:'errors')
                    return
                }

                if (product && user && !securityService.hasRoleAdmin(user) && user.preferences.lastProductOpened != product.pkey){
                    user.preferences.lastProductOpened = product.pkey
                    user.save()
                }
            }
        }
    }
}

springcache {
    autoCreate = { _cacheName, isCacheConfig, beanBuilder ->
        beanBuilder.beans {
            "$_cacheName"(EhCacheFactoryBean) { bean ->
                bean.parent = ref("springcacheDefaultCache", true)
                cacheName = _cacheName
                isCacheConfig?.each {
                    bean.setPropertyValue it.key, it.value
                }
            }
        }
        beanBuilder.createApplicationContext()
    }
}