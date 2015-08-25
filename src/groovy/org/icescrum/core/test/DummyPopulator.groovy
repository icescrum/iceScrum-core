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
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */


package org.icescrum.core.test

import grails.util.Holders
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.Sprint
import org.springframework.security.core.context.SecurityContextHolder as SCH

import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.preferences.UserPreferences
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils

import org.icescrum.core.domain.Activity
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import grails.plugin.springsecurity.userdetails.GrailsUser
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Mood

class DummyPopulator {

    public static dummyze = {
        initDummyData()
    }

    private static void initDummyData() {

        println "Dummy Data loading...."

        def app = Holders.grailsApplication
        def springSecurityService = app.mainContext.springSecurityService
        def securityService = app.mainContext.securityService
        def sessionFactory = app.mainContext.sessionFactory
        def releaseService = app.mainContext.releaseService
        def sprintService = app.mainContext.sprintService
        def storyService = app.mainContext.storyService

        // Users
        def usera, userz, userx
        if (User.count() <= 1) {
            usera = new User(username: "a", email: "a@gmail.com", firstName: "Roberto", password: springSecurityService.encodePassword('a'), preferences: new UserPreferences(language: 'en', activity: 'Consultant')).save()
            userz = new User(username: "z", email: "z@gmail.com", firstName: "Bernardo", password: springSecurityService.encodePassword('z'), preferences: new UserPreferences(language: 'en', activity: 'WebDesigner', menu: ["feature": "1", "backlog": "2"])).save()
            userx = new User(username: "x", email: "x@gmail.com", firstName: "Antonio", password: springSecurityService.encodePassword('x'), preferences: new UserPreferences(language: 'en', activity: 'Consultant')).save()
        } else {
            usera = User.findByUsername("a")
            userz = User.findByUsername("z")
            userx = User.findByUsername("x")
        }

        def mood1 = new Mood(feeling: 1, feelingDay: new Date() - 3, user: usera)
        mood1.save()
        def mood2 = new Mood(feeling: 1, feelingDay: new Date() - 2, user: usera)
        mood2.save()
        def mood3 = new Mood(feeling: 1, feelingDay: new Date() - 1, user: usera)
        mood3.save()

        loginAsAdmin()

        if (Product.count() == 0) {
            // Product
            def product = new Product(name: 'testProj')
            product.pkey = 'TESTPROJ'
            def startDate = new Date().parse('yyyy-M-d', String.format('%tF', new Date()))
            product.startDate = startDate
            product.endDate = startDate + 120
            product.preferences = new ProductPreferences()
            product.preferences.webservices = true
            product.save()
            securityService.secureDomain(product)
            // Teams and members
            def team = new Team(name: 'testProj Team').addToProducts(product).addToMembers(usera).addToMembers(userz)
            team.save()
            securityService.secureDomain(team)
            securityService.createTeamMemberPermissions(userz, team)
            securityService.createProductOwnerPermissions(usera, product)
            securityService.createScrumMasterPermissions(usera, team)
            securityService.changeOwner(usera, product)
            securityService.changeOwner(usera, team)
            def team3 = new Team(name: 'empty Team3').addToMembers(userx)
            team3.save()
            securityService.secureDomain(team3)
            securityService.createTeamMemberPermissions(userx, team3)
            // Releases
            def release1 = new Release(startDate: startDate, endDate: startDate + 120, goal: 'test Goal', description: 'bla', name: "dummy relesase")
            def release2 = new Release(startDate: startDate + 121, endDate: startDate + 241, goal: 'test Goal 2', description: 'bla 2', name: "dummy relesase 2")
            releaseService.save(release1, product)
            releaseService.save(release2, product)
            // Sprints
            sprintService.generateSprints(release1)
            // Features
            def feature = new Feature(uid: 1, name: 'La feature', value: 1, description: 'Une feature', backlog: product, rank: 1).save()
            def feature2 = new Feature(uid: 2, name: 'La feature 2', value: 1, description: 'Une feature', backlog: product, rank: 2, color: '#e778ff').save()
            def feature3 = new Feature(uid: 3, name: 'La feature 3', value: 1, description: 'Une feature', backlog: product, rank: 3, color: '#c3ed39').save()
            // Actors
            def actor = new Actor(uid: 1, name: 'ScrumMaster', description: 'Un ScrumMaster', backlog: product).save()
            def actor2 = new Actor(uid: 2, name: 'ProductOwner', description: 'Un ProductOwner', backlog: product).save()
            def actor3 = new Actor(uid: 3, name: 'StakeHolder', description: 'Un StakeHolder', backlog: product).save()
            def actor4 = new Actor(uid: 4, name: 'Team member', description: 'Un Team member', backlog: product).save()
            def actor5 = new Actor(uid: 5, name: 'visitor', description: 'Un visitor', backlog: product).save()
            product.addToActors(actor).addToActors(actor2).addToActors(actor3).addToActors(actor4).addToActors(actor5).save()
            // Stories
            def _storyCount = 0
                def createStory = { state ->
                def _act = _storyCount % 5 == 0 ? actor4 : _storyCount % 4 == 0 ? actor : _storyCount % 3 == 0 ? actor3 : actor
                def story = new Story(backlog: product,
                        feature: _storyCount % 4 == 0 ? feature : _storyCount % 3 == 0 ? feature3 : feature2,
                        actor: _act,
                        name: "A story $_storyCount with something awesome inside very awesome !!",
                        effort: 5,
                        uid: _storyCount + 1,
                        type: _storyCount % 6 == 0 ? Story.TYPE_TECHNICAL_STORY : _storyCount % 4 == 0 ? Story.TYPE_DEFECT : Story.TYPE_USER_STORY,
                        creationDate: new Date(),
                        suggestedDate: new Date(),
                        estimatedDate: new Date(),
                        state: state,
                        creator: usera,
                        rank: _storyCount++,
                        description: "As a A[${_act.uid}-${_act.name}], I can do something awesome, I can do something awesome, I can do something awesome, I can do something awesome, I can do something awesome, I can do something awesome, I can do something awesome, I can do something awesome,I can do something awesome",
                        notes: '*Un texte en gras* hahaha ! _et en italique_'
                ).save()
                addStoryActivity(story, usera, Activity.CODE_SAVE)
                if (story.state >= Story.STATE_ACCEPTED) {
                    story.acceptedDate = new Date()
                    addStoryActivity(story, usera, 'acceptAs')
                }
                if (story.state < Story.STATE_ESTIMATED) {
                    story.estimatedDate = null
                    story.effort = null
                } else {
                    addStoryActivity(story, usera, 'estimate')
                }
                return story
            }
            80.times {
                product.addToStories(createStory(it % 5 == 0 ? Story.STATE_SUGGESTED : Story.STATE_ESTIMATED))
            }
            product.save()
            sessionFactory.currentSession.flush()
            storyService.autoPlan(release1, 40)
            60.times {
                product.addToStories(createStory((it % 10) % 3 == 0 ? Story.STATE_ACCEPTED : Story.STATE_ESTIMATED))
            }
            product.save()
            rankStories(product)
            // Tasks and sprint progression
            int nextTaskUid = 1
            product.stories.findAll { it.state < Story.STATE_PLANNED }.eachWithIndex { Story story, int i ->
                if (i % 4 == 0) {
                    (i % 7).times {
                        story.addToTasks(new Task(parentProduct: product, uid: nextTaskUid, type: null, estimation: 3, name: "task ${it} story : ${story.id}", creator: usera, responsible: usera, parentStory: story, creationDate: new Date()))
                        nextTaskUid++
                    }
                    story.save()
                }
            }
            release1.sprints.findAll { it.orderNumber < 8 }.each { sprint ->
                sprint.stories.each { story ->
                    (sprint.orderNumber - 1).times {
                        def task = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: null, estimation: 3, name: "task ${it} story : ${story.id}", creator: usera, responsible: usera, parentStory: story, backlog: sprint, creationDate: new Date())
                        story.addToTasks(task)
                        sprint.addToTasks(task)
                        nextTaskUid++
                    }
                }
                20.times {
                    def task = new Task(parentProduct: product, uid: nextTaskUid, type: Task.TYPE_RECURRENT, estimation: 5, name: "task recurrent ${it} ${sprint.id}", creator: usera, responsible: usera, parentStory: null, backlog: sprint, creationDate: new Date())
                    sprint.addToTasks(task)
                    task.save()
                    nextTaskUid++
                    def task2 = new Task(parentProduct: product, uid: nextTaskUid, type: Task.TYPE_URGENT, estimation: 4, name: "task urgent ${it} ${sprint.id}", creator: usera, responsible: usera, parentStory: null, backlog: sprint, creationDate: new Date())
                    sprint.addToTasks(task2)
                    task2.save()
                    nextTaskUid++
                }
                if (sprint.orderNumber < 7) {
                    sprint.save(flush: true)
                    sprintService.activate(sprint)
                    if (sprint.orderNumber < 6) {
                        updateContentDoneSprint(sprint)
                        sprintService.close(sprint)
                    } else {
                        updateContentInProgressSprint(sprint)
                    }
                }
            }
            // Acceptance tests
            createAcceptanceTests(product, usera)
        }
        sessionFactory.currentSession.flush()
        SCH.clearContext()
    }

    private static void updateContentDoneSprint(Sprint sprint) {
        sprint.stories.each { story ->
            story.state = Story.STATE_DONE
            addStoryActivity(story, story.creator, 'done')
            story.doneDate = new Date()
            story.tasks?.each { t ->
                t.state = Task.STATE_DONE
                t.estimation = 0
                t.doneDate = new Date()
            }
            story.save()
        }
        sprint.tasks.findAll { it.type == Task.TYPE_RECURRENT }.each {
            it.state = Task.STATE_DONE
            it.save()
        }
        sprint.tasks.findAll { it.type == Task.TYPE_URGENT }.each {
            it.state = Task.STATE_DONE
            it.save()
        }
    }

    private static void updateContentInProgressSprint(Sprint sprint) {
        sprint.tasks.findAll { it.type == Task.TYPE_RECURRENT }.eachWithIndex { task, index ->
            if (index > 0 && index < 8) {
                task.state = Task.STATE_BUSY
                task.inProgressDate = new Date()
            }
            if (index == 8) {
                task.state = Task.STATE_DONE
                task.doneDate = new Date()
            }
            task.save()
        }
        sprint.tasks.findAll { it.type == Task.TYPE_URGENT }.eachWithIndex { task, index ->
            if (index > 0 && index < 8) {
                task.state = Task.STATE_BUSY
                task.inProgressDate = new Date()
            }
            if (index == 8) {
                task.state = Task.STATE_DONE
                task.doneDate = new Date()
            }
            task.save()
        }
        sprint.tasks.eachWithIndex { task, index ->
            if (index == 0) {
                task.state = Task.STATE_DONE
                task.inProgressDate = new Date()
                task.doneDate = new Date()
            } else if (index % 2 == 0) {
                task.state = Task.STATE_BUSY
                task.inProgressDate = new Date()
            }
            task.save()
        }
    }

    private static void rankStories(Product product) {
        product.stories.findAll {
            (it.state == Story.STATE_ACCEPTED) || (it.state == Story.STATE_ESTIMATED)
        }.eachWithIndex { story, index ->
            story.rank = index + 1
            story.save()
        }

        product.stories.findAll {
            (it.state != Story.STATE_ACCEPTED) && (it.state != Story.STATE_ESTIMATED)
        }.eachWithIndex { story, index ->
            story.rank = 0
            story.save()
        }
    }

    private static void createAcceptanceTests(Product product, User user) {
        product.stories.findAll { it.state == Story.STATE_SUGGESTED }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: "at $story.state $index", description: "desc $story.state $index", creator: user, parentStory: story)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: "at2 $story.state $index", description: "desc2 $story.state $index", creator: user, parentStory: story)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_ACCEPTED }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: "at $story.state $index", description: "desc $story.state $index", creator: user, parentStory: story)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: "at2 $story.state $index", description: "desc2 $story.state $index", creator: user, parentStory: story)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_INPROGRESS }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: "at $story.state $index", description: "desc $story.state $index", creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: "at2 $story.state $index", description: "desc2 $story.state $index", creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.FAILED.id)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
            if (index % 3 == 0) {
                def acceptanceTest3 = new AcceptanceTest(name: "at3 $story.state $index", description: "desc3 $story.state $index", creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.TOCHECK.id)
                acceptanceTest3.save()
                addAcceptanceTestActivity(acceptanceTest3, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_DONE }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: "at $story.state $index", description: "desc $story.state $index", creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: "at2 $story.state $index", description: "desc2 $story.state $index", creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
    }

    private static void loginAsAdmin() {
        def userDetails = new GrailsUser('admin', 'adminadmin!', true, true, true, true, AuthorityUtils.createAuthorityList('ROLE_ADMIN'), 1)
        SCH.context.authentication = new UsernamePasswordAuthenticationToken(userDetails, 'adminadmin!', AuthorityUtils.createAuthorityList('ROLE_ADMIN'))
    }

    private static addStoryActivity(Story story, User poster, String code) {
        def activity = new Activity(poster: poster, parentRef: story.id, parentType: 'story', code: code, label: story.name)
        activity.save()
        story.addToActivities(activity)
    }

    private static addAcceptanceTestActivity(AcceptanceTest acceptanceTest, User poster) {
        def activity = new Activity(poster: poster, parentRef: acceptanceTest.id, parentType: 'acceptanceTest', code: 'acceptanceTestSave', label: acceptanceTest.name)
        activity.save()
        acceptanceTest.addToActivities(activity)
    }
}
