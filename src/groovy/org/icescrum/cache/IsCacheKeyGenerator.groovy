/*
 * Copyright (c) 2011 Kagilum
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 *
 */
package org.icescrum.cache

import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.servlet.support.RequestContextUtils as RCU

import grails.plugin.springcache.CacheResolver
import grails.plugin.springcache.key.CacheKeyBuilder
import grails.plugin.springcache.web.ContentCacheParameters
import grails.plugin.springcache.web.key.WebContentKeyGenerator
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.BacklogElement
import org.icescrum.core.domain.TimeBox
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Team

public class LocaleKeyGenerator extends WebContentKeyGenerator {
    def iSKeyGeneratorHelper
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        def request = RCH.requestAttributes.currentRequest
        builder << iSKeyGeneratorHelper.retrieveLocale(request)
    }
}

public class RoleKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        def request = RCH.requestAttributes.currentRequest
        builder << iSKeyGeneratorHelper.retrieveRoles(request)
    }
}

public class UserKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveUser()
    }
}

public class ProjectKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveTimeBox(RCH.currentRequestAttributes().params,'product')
    }
}

public class ProjectUserKeyGenerator extends UserKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveTimeBox(RCH.currentRequestAttributes().params,'product')
    }
}

public class StoryKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveBacklogElement(RCH.currentRequestAttributes().params, 'story')
    }
}

public class StoriesKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveLastUpdatedStory(RCH.currentRequestAttributes().params)
    }
}

public class TaskKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveBacklogElement(RCH.currentRequestAttributes().params, 'task')
    }
}

public class TasksKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveLastUpdatedTask(RCH.currentRequestAttributes().params)
    }
}

public class ActorKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveBacklogElement(RCH.currentRequestAttributes().params, 'actor')
    }
}

public class ActorsKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveLastUpdatedActor(RCH.currentRequestAttributes().params)
    }
}

public class FeatureKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveBacklogElement(RCH.currentRequestAttributes().params, 'feature')
    }
}

public class SprintKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveTimeBox(RCH.currentRequestAttributes().params, 'sprint')
    }
}

public class FeaturesKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveLastUpdatedFeature(RCH.currentRequestAttributes().params)
    }
}

public class ReleaseKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveTimeBox(RCH.currentRequestAttributes().params, 'release')
    }
}

public class ReleasesKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveLastUpdatedRelease(RCH.currentRequestAttributes().params)
    }
}

public class ReleasesRoleKeyGenerator extends RoleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveLastUpdatedRelease(RCH.currentRequestAttributes().params)
    }
}

public class TeamKeyGenerator extends LocaleKeyGenerator {
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        builder << iSKeyGeneratorHelper.retrieveTeam(RCH.currentRequestAttributes().params)
    }
}


public class ISKeyGeneratorHelper {

    def securityService
    def springSecurityService

    public String retrieveRoles(def request){
        def role = ''
        if (!request.filtered){
            securityService.filterRequest()
        }
        if (request.admin) {
            role = 'adm'
        } else {
            if (request.scrumMaster)  {  role += 'scm'  }
            if (request.teamMember)   {  role += 'tm'  }
            if (request.productOwner) {  role += 'po'  }
            if (request.owner)        {  role += 'owner'  }
            if (!role && request.stakeHolder) {  role += 'sh'  }
        }
        role = role ?: 'anonymous'
        return role
    }

    public String retrieveLocale(def request){
        return RCU.getLocale(request).toString().substring(0, 2)
    }

    public Map retrieveTimeBox(GrailsParameterMap params, String type){
        def timeboxId = params.product ? params.product.decodeProductKey() : params.sprint?.id ?: params.release?.id ?: params.id ?: null
        if (timeboxId){
            def timebox
            switch (type){
                case 'sprint':
                    timebox = Sprint.get(timeboxId.toLong())
                    break
                case 'release':
                    timebox = Release.get(timeboxId.toLong())
                    break
                case 'product':
                    timebox = Product.get(timeboxId.toLong())
                    break
            }
            if (timebox)
                return [class:timebox.class,lastUpdated:timebox.lastUpdated,id:timebox.id]
        }
        return null
    }

    public Map retrieveBacklogElement(GrailsParameterMap params, String type){
        def backlogElementId = params.story?.id ?: params.task?.id ?: params.feature?.id ?: params.actor?.id ?: params.id ?: null
        if (backlogElementId){
            def backlogElement
            switch(type){
                case 'story':
                    backlogElement = Story.get(params.story?.id ?: params.id)
                    break
                case 'task':
                    backlogElement = Task.get(params.task?.id ?: params.id)
                    break
                case 'actor':
                    backlogElement = Actor.get(params.actor?.id ?: params.id)
                    break
                case 'feature':
                    backlogElement = Feature.get(params.feature?.id ?: params.id)
                    break
            }
            if (backlogElement)
                return [class:backlogElement.class,lastUpdated:backlogElement.lastUpdated,id:backlogElement.id]
        }
        return null
    }

    public String retrieveUser(){
        if (springSecurityService.isLoggedIn()){
            def u = springSecurityService.currentUser
            return [lastUpdated:u.lastUpdated,id:u.id]
        }else
            return 'anonymous'
    }

    public String retrieveUser(def params){
        def u = User.get(params.id)
        if (u)
            return [lastUpdated:u.lastUpdated,id:u.id]
        else
            return 'anonymous'
    }

    public String retrieveTeam(def params){
        def t = Team.get(params.id)
        if (t)
            return [lastUpdated:t.lastUpdated,id:t.id]
        else
            return null
    }

    public String retrieveLastUpdatedRelease(GrailsParameterMap params){
        params.product = params.product?.decodeProductKey()
        def result = Release.createCriteria().get{
            parentProduct{
                eq 'id', params.product?.toLong()
            }
            projections {
                max('lastUpdated')
                count('lastUpdated')
            }
            cache true
        }
        return result.join('_')
    }

    public String retrieveLastUpdatedFeature(GrailsParameterMap params){
        params.product = params.product?.decodeProductKey()
        def result = Feature.createCriteria().get{
            backlog{
                eq 'id', params.product?.toLong()
            }
            projections {
                max('lastUpdated')
                count('lastUpdated')
            }
            cache true
        }
        return result.join('_')
    }

    public String retrieveLastUpdatedStory(GrailsParameterMap params){
        params.product = params.product?.decodeProductKey()
        def result = Story.createCriteria().get{
            backlog{
                eq 'id', params.product?.toLong()
            }
            projections {
                max('lastUpdated')
                count('lastUpdated')
            }
            cache true
        }
        return result.join('_')
    }

    public String retrieveLastUpdatedActor(GrailsParameterMap params){
        params.product = params.product?.decodeProductKey()
        def result = Actor.createCriteria().get{
            backlog{
                eq 'id', params.product?.toLong()
            }
            projections {
                max('lastUpdated')
                count('lastUpdated')
            }
            cache true
        }
        return result.join('_')
    }

    public String retrieveLastUpdatedTask(GrailsParameterMap params){
        def result = Task.createCriteria().get{
            if (params.product && (params.sprint || params.id)){
                backlog{
                    eq 'id', params.sprint ? params.sprint.toLong() : params.long('id')
                }
            }else{
                params.product = params.product.decodeProductKey()
                def sprint = Sprint.findCurrentOrNextSprint(params.product.toLong()).list()[0]
                if (sprint){
                    backlog{
                        eq 'id', sprint.id
                    }
                }
            }
            projections {
                max('lastUpdated')
                count('lastUpdated')
            }
            cache true
        } ?: null
        return result.join('_')
    }

}
