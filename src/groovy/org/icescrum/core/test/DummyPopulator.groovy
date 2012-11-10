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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */


package org.icescrum.core.test

import org.springframework.security.core.context.SecurityContextHolder as SCH

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.preferences.TeamPreferences
import org.icescrum.core.domain.preferences.UserPreferences
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils

import grails.plugin.fluxiable.Activity
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.codehaus.groovy.grails.plugins.springsecurity.GrailsUser
import org.icescrum.core.domain.Actor

class DummyPopulator {

    public static dummyze = {
        initDummyData()
    }

    private static void initDummyData() {

        println "Dummy Data loading...."

        def app = ApplicationHolder.application
        def springSecurityService = app.mainContext.springSecurityService
        def securityService = app.mainContext.securityService
        def sessionFactory = app.mainContext.sessionFactory
        def releaseService = app.mainContext.releaseService
        def sprintService = app.mainContext.sprintService
        def storyService = app.mainContext.storyService

        def ua, uz, ux
        if (User.count() <= 1) {
            ua = new User(username: "a",
                    email: "a@gmail.com",
                    enabled: true,
                    firstName: "Roberto",
                    password: springSecurityService.encodePassword('a'),
                    preferences: new UserPreferences(language: 'en', activity: 'Consultant')
            ).save()
            uz = new User(username: "z",
                    email: "z@gmail.com",
                    enabled: true,
                    firstName: "Bernardo",
                    password: springSecurityService.encodePassword('z'),
                    preferences: new UserPreferences(language: 'en', activity: 'WebDesigner', menu: ["sandbox": "1", "feature": "2", "backlog": "3"])
            ).save()
            ux = new User(username: "x",
                    email: "x@gmail.com",
                    enabled: true,
                    firstName: "Antonio",
                    password: springSecurityService.encodePassword('x'),
                    preferences: new UserPreferences(language: 'en', activity: 'Consultant')
            ).save()
        }

        else {
            ua = User.findByUsername("a")
            uz = User.findByUsername("z")
            ux = User.findByUsername("x")
        }

        loginAsAdmin()

        if (Product.count() == 0) {


            def p = new Product(name: 'testProj')
            p.pkey = 'TESTPROJ'
            p.startDate = new Date().parse('yyyy-M-d', String.format('%tF', new Date()))
            p.preferences = new ProductPreferences()
            p.preferences.webservices = true
            p.save()

            securityService.secureDomain(p)


            def team = new Team(name: 'testProj Team', preferences: new TeamPreferences()).addToProducts(p).addToMembers(ua).addToMembers(uz)
            team.save()
            securityService.secureDomain(team)


            def team3 = new Team(name: 'empty Team3', preferences: new TeamPreferences()).addToMembers(ux)
            team3.save()
            securityService.secureDomain(team3)

            securityService.createTeamMemberPermissions(ux, team3)

            securityService.createProductOwnerPermissions(ua, p)
            securityService.createScrumMasterPermissions(ua, team)
            securityService.createTeamMemberPermissions(uz, team)

            securityService.changeOwner(ua, p)
            securityService.changeOwner(ua, team)

            def rel = new Release(startDate: new Date().parse('yyyy-M-d',
                    String.format('%tF', new Date())), endDate: new Date().parse('yyyy-M-d', String.format('%tF', new Date())) + 120,
                    goal: 'test Goal', description: 'bla', name: "dummy relesase")

            def rel2 = new Release(startDate: new Date().parse('yyyy-M-d',
                    String.format('%tF', new Date())) + 121, endDate: new Date().parse('yyyy-M-d', String.format('%tF', new Date())) + 241,
                    goal: 'test Goal 2', description: 'bla 2', name: "dummy relesase 2")

            releaseService.save(rel, p)
            releaseService.save(rel2, p)
            sprintService.generateSprints(rel)


            def feature = new Feature(uid: 1, name: 'La feature', value: 1, description: 'Une feature', backlog: p, rank: 1).save()
            def feature2 = new Feature(uid: 2, name: 'La feature 2', value: 1, description: 'Une feature', backlog: p, rank: 2, color: 'pink').save()
            def feature3 = new Feature(uid: 3, name: 'La feature 3', value: 1, description: 'Une feature', backlog: p, rank: 3, color: 'orange').save()

            def actor = new Actor(uid: 1, name: 'ScrumMaster', description: 'Un ScrumMaster', backlog: p).save()
            def actor2 = new Actor(uid: 2, name: 'ProductOwner', description: 'Un ProductOwner', backlog: p).save()
            def actor3 = new Actor(uid: 3, name: 'StakeHolder', description: 'Un StakeHolder', backlog: p).save()
            def actor4 = new Actor(uid: 4, name: 'Team member', description: 'Un Team member', backlog: p).save()
            def actor5 = new Actor(uid: 5, name: 'visitor', description: 'Un visitor', backlog: p).save()

            p.addToActors(actor).addToActors(actor2).addToActors(actor3).addToActors(actor4).addToActors(actor5).save()

            def _storyCount = 0
            def s
            def createStory = {state ->
                s = new Story(backlog: p,
                        feature: _storyCount % 4 == 0 ? feature : _storyCount % 3 == 0 ? feature3 : feature2,
                        actor:_storyCount % 5 == 0 ? actor4 : _storyCount % 4 == 0 ? actor : _storyCount % 3 == 0 ? actor3 : actor2,
                        name: "A story $_storyCount",
                        effort: 5,
                        uid: _storyCount + 1,
                        type: _storyCount % 6 == 0 ? Story.TYPE_TECHNICAL_STORY : _storyCount % 4 == 0 ? Story.TYPE_DEFECT : Story.TYPE_USER_STORY,
                        creationDate: new Date(),
                        suggestedDate: new Date(),
                        estimatedDate: new Date(),
                        state: state,
                        creator: ua,
                        rank: _storyCount++,
                        description: 'As a user, I can do something awesome',
                        notes: '<b>Un texte en gras</b> hahaha ! <em>et en italique</em>'
                ).save()

                if (s.state == Story.STATE_ACCEPTED || s.state == Story.STATE_SUGGESTED){
                    s.estimatedDate = null
                    s.rank = 0
                    s.effort = null
                }

                if (s.state >= Story.STATE_ACCEPTED){
                    s.acceptedDate = new Date()
                }

                s.addActivity(ua, state == Story.STATE_SUGGESTED ? Activity.CODE_SAVE : 'acceptAs', s.name)

            }

            80.times {
                p.addToStories(createStory(it % 5 == 0 ? Story.STATE_SUGGESTED : Story.STATE_ESTIMATED))
            }
            p.save()

            sessionFactory.currentSession.flush()
            storyService.autoPlan(rel, 40)

            int i = 0
            int taskCount = 0

            for(sp in rel.sprints) {

                for (pbi in sp.stories) {
                    i.times {
                        taskCount++
                        def task = new Task(uid:taskCount, rank: it + 1, type: null, estimation: 3, name: "task ${it} story : ${pbi.id}", creator: ua, responsible: ua, parentStory: pbi, backlog: sp, creationDate: new Date())
                        pbi.addToTasks(task)
                        sp.addToTasks(task)
                    }
                }
                if (i == 0)
                    taskCount++
                20.times{
                    def task = new Task(uid:taskCount, type: Task.TYPE_RECURRENT, estimation: 5, name: "task recurrent ${it} ${sp.id}", creator: ua, responsible: ua, parentStory: null, backlog: sp, creationDate: new Date())
                    sp.addToTasks(task)
                    task.save()
                    taskCount++

                    def task2 = new Task(uid:taskCount, type: Task.TYPE_URGENT, estimation: 4, name: "task urgent ${it} ${sp.id}", creator: ua, responsible: ua, parentStory: null, backlog: sp, creationDate: new Date())
                    sp.addToTasks(task2)
                    task2.save()
                    taskCount++
                }
                if (i > 5)
                    break

                sp.save(flush:true)

                //to keep one sprint with stories in todo
                if (sp.orderNumber >= 7)
                    break



                sprintService.activate(sp)

                10.times {
                    p.addToStories(createStory(it % 3 == 0 ? Story.STATE_ACCEPTED : Story.STATE_ESTIMATED))
                }
                p.save()

                if (sp.orderNumber < 6){
                     for (pbi in sp.stories) {
                        pbi.state = Story.STATE_DONE
                        pbi.doneDate = new Date()
                        pbi.tasks?.each { t ->
                            t.state = Task.STATE_DONE
                            t.estimation = 0
                            t.doneDate = new Date()
                        }
                        pbi.save()
                    }
                    sp.tasks.findAll{it.type == Task.TYPE_RECURRENT}.each{
                            it.state = Task.STATE_DONE
                            it.save()
                    };
                    sp.tasks.findAll{it.type == Task.TYPE_URGENT}.each{
                            it.state = Task.STATE_DONE
                            it.save()
                    };
                    sprintService.close(sp)
                }else{
                    sp.tasks.findAll{it.type == Task.TYPE_RECURRENT}.eachWithIndex { it, index ->
                        if (index > 0 && index < 8){
                            it.state = Task.STATE_BUSY
                        }
                        if (index == 8){
                            it.state = Task.STATE_DONE
                        }
                        it.save()
                    };

                    sp.tasks.findAll{it.type == Task.TYPE_URGENT}.eachWithIndex { it, index ->
                        if (index > 0 && index < 8){
                            it.state = Task.STATE_BUSY
                        }
                        if (index == 8){
                            it.state = Task.STATE_DONE
                        }
                        it.save()
                    };

                    sp.tasks.eachWithIndex{ it, index ->
                        if (index == 0){
                            it.state = Task.STATE_DONE
                        }
                        else if (index % 2 == 0){
                            it.state = Task.STATE_BUSY

                        }
                        it.save()
                    }
                }

                i++
            }

            p.stories.findAll { (it.state == Story.STATE_ACCEPTED) || (it.state == Story.STATE_ESTIMATED)}.eachWithIndex { it, index ->
                index++
                it.rank = index
                it.save()
            }
        }

        sessionFactory.currentSession.flush()
        SCH.clearContext()
    }

    private static void loginAsAdmin() {
        // have to be authenticated as an admin to create ACLs
        def userDetails = new GrailsUser('admin', 'adminadmin!', true, true, true, true, AuthorityUtils.createAuthorityList('ROLE_ADMIN'), 1)
        SCH.context.authentication = new UsernamePasswordAuthenticationToken(userDetails, 'adminadmin!', AuthorityUtils.createAuthorityList('ROLE_ADMIN'))
    }

}
