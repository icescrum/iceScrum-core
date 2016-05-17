/*
 * Copyright (c) 2015 Kagilum SAS
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

import de.svenjacobs.loremipsum.LoremIpsum
import grails.util.Holders
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.Backlog
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

class DummyPopulator {

    public static dummyze = {
        initDummyData()
    }

    private static void initDummyData() {

        println "Dummy Data loading...."

        def app = Holders.grailsApplication
        def storyService = app.mainContext.storyService
        def sprintService = app.mainContext.sprintService
        def widgetService = app.mainContext.widgetService
        def releaseService = app.mainContext.releaseService
        def sessionFactory = app.mainContext.sessionFactory
        def securityService = app.mainContext.securityService
        def uiDefinitionService = app.mainContext.uiDefinitionService
        def springSecurityService = app.mainContext.springSecurityService

        // Users
        def usera, userz, userx
        if (User.count() <= 1) {
            usera = new User(username: "a", email: "a@gmail.com", firstName: "Roberto", password: springSecurityService.encodePassword('a'), preferences: new UserPreferences(language: 'en', activity: 'Consultant')).save(failOnError: true)
            userz = new User(username: "z", email: "z@gmail.com", firstName: "Bernardo", password: springSecurityService.encodePassword('z'), preferences: new UserPreferences(language: 'en', activity: 'WebDesigner', menu: ["feature": "1", "backlog": "2"])).save(failOnError: true)
            userx = new User(username: "x", email: "x@gmail.com", firstName: "Antonio", password: springSecurityService.encodePassword('x'), preferences: new UserPreferences(language: 'en', activity: 'Consultant')).save(failOnError: true)
            widgetService.save(usera, uiDefinitionService.getWidgetDefinitionById('feed'), true)
            widgetService.save(usera, uiDefinitionService.getWidgetDefinitionById('notes'), true)
        } else {
            usera = User.findByUsername("a")
            userz = User.findByUsername("z")
            userx = User.findByUsername("x")
        }

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
            product.description = randomWords(50, 20)
            product.save(failOnError: true)
            new Backlog(product: product, shared: true, filter: '{"story":{"state":1}}', name: 'is.ui.sandbox', code: 'sandbox').save(failOnError: true)
            new Backlog(product: product, shared: true, filter: '{"story":{"state":[2,3]}}', name: 'is.ui.backlog', code: 'backlog').save(failOnError: true)
            new Backlog(product: product, shared: true, filter: '{"story":{"state":7}}', name: 'todo.is.ui.backlog.done', code: 'done').save(failOnError: true)
            new Backlog(product: product, shared: true, filter: '{"story":{}}', name: 'todo.is.ui.backlog.all', code: 'all').save(failOnError: true)
            securityService.secureDomain(product)
            // Teams and members
            def team = new Team(name: 'testProj Team').addToProducts(product).addToMembers(usera).addToMembers(userz)
            team.save(failOnError: true)
            securityService.secureDomain(team)
            securityService.createTeamMemberPermissions(userz, team)
            securityService.createProductOwnerPermissions(usera, product)
            securityService.createScrumMasterPermissions(usera, team)
            securityService.changeOwner(usera, product)
            securityService.changeOwner(usera, team)
            def team3 = new Team(name: 'empty Team3').addToMembers(userx)
            team3.save(failOnError: true)
            securityService.secureDomain(team3)
            securityService.createTeamMemberPermissions(userx, team3)
            // Releases
            def release1 = new Release(startDate: startDate, endDate: startDate + 120, goal: randomWords(15, 5), description: randomWords(100, 50, 2900), name: randomWords(10, 2, 200))
            def release2 = new Release(startDate: startDate + 121, endDate: startDate + 241, goal: randomWords(15, 5), description: randomWords(100, 50, 2900), name: randomWords(10, 2, 200))
            releaseService.save(release1, product)
            releaseService.save(release2, product)
            // Sprints
            sprintService.generateSprints(release1)
            def sprint3 = release1.sprints.asList()[3]
            if (sprint3) {
                sprint3.deliveredVersion = "V3"
                sprint3.save(failOnError: true)
            }
            def sprint4 = release1.sprints.asList()[4]
            if (sprint4) {
                sprint4.deliveredVersion = "V4"
                sprint4.save(failOnError: true)
            }
            // Features
            def feature = new Feature(uid: 1, name: randomWords(15,  5, 200), value: 1, description: randomWords(50, 20, 2900), backlog: product, rank: 1).save(failOnError: true)
            def feature2 = new Feature(uid: 2, name: randomWords(15,  5, 200), value: 1, description: randomWords(50, 10, 2900), backlog: product, rank: 2, color: '#f0679e').save(failOnError: true)
            def feature3 = new Feature(uid: 3, name: randomWords(15,  5, 200), value: 1, description: randomWords(50, 10, 2900), backlog: product, rank: 3, color: '#a0dffa').save(failOnError: true)
            // Actors
            def actor = new Actor(uid: 1, name: randomWords(15,  5, 200), description: randomWords(50, 10, 2900), backlog: product).save(failOnError: true)
            def actor2 = new Actor(uid: 2, name: randomWords(15,  5, 200), description: randomWords(50, 10, 2900), backlog: product).save(failOnError: true)
            def actor3 = new Actor(uid: 3, name: randomWords(15,  5, 200), description: randomWords(50, 10, 2900), backlog: product).save(failOnError: true)
            def actor4 = new Actor(uid: 4, name: randomWords(15,  5, 200), description: randomWords(50, 10, 2900), backlog: product).save(failOnError: true)
            def actor5 = new Actor(uid: 5, name: randomWords(15,  5, 200), description: randomWords(50, 10, 2900), backlog: product).save(failOnError: true)
            product.addToActors(actor).addToActors(actor2).addToActors(actor3).addToActors(actor4).addToActors(actor5).save(failOnError: true)
            // Stories
            def _storyCount = 0
                def createStory = { state ->
                def _act = _storyCount % 5 == 0 ? actor4 : _storyCount % 4 == 0 ? actor : _storyCount % 3 == 0 ? actor3 : actor
                def value = _storyCount % 5
                def effort = 4 + _storyCount % 2
                def story = new Story(backlog: product,
                        feature: _storyCount % 4 == 0 ? feature : _storyCount % 3 == 0 ? feature3 : feature2,
                        actor: _act,
                        name: randomWords(15, 6, 200),
                        effort: effort,
                        value: value,
                        uid: _storyCount + 1,
                        type: _storyCount % 6 == 0 ? Story.TYPE_TECHNICAL_STORY : _storyCount % 4 == 0 ? Story.TYPE_DEFECT : Story.TYPE_USER_STORY,
                        suggestedDate: new Date(),
                        estimatedDate: new Date(),
                        state: state,
                        creator: usera,
                        rank: _storyCount++,
                        description: "As a A[${_act.uid}-${_act.name}], ${randomWords(30, 15, 2500)}",
                        notes: "${randomWords(3, 1)} *Un texte en gras* hahaha ! ${randomWords(5, 2)} _et en italique_ ${randomWords(20, 10)}"
                ).save(failOnError: true)
                addStoryActivity(story, usera, Activity.CODE_SAVE)
                if (story.state >= Story.STATE_ACCEPTED) {
                    story.acceptedDate = new Date()
                    addStoryActivity(story, usera, 'acceptAs')
                }
                if (story.state < Story.STATE_ESTIMATED) {
                    story.estimatedDate = null
                    story.effort = null
                } else {
                    addStoryActivity(story, userz, 'estimate')
                }
                return story
            }
            80.times {
                product.addToStories(createStory(it % 5 == 0 ? Story.STATE_SUGGESTED : Story.STATE_ESTIMATED))
            }
            product.save(failOnError: true)
            sessionFactory.currentSession.flush()
            storyService.autoPlan(release1.sprints.asList(), 40)
            60.times {
                product.addToStories(createStory((it % 10) % 3 == 0 ? Story.STATE_ACCEPTED : Story.STATE_ESTIMATED))
            }
            product.save(failOnError: true)
            rankStories(product)
            // Tasks and sprint progression
            int nextTaskUid = 1
            def getCreator = { number -> number % 3 == 0 ? userz : usera }
            def getResponsible = { number -> number % 5 == 0 ? userz : usera }
            product.stories.findAll { it.state < Story.STATE_PLANNED }.eachWithIndex { Story story, int i ->
                if (i % 4 == 0) {
                    (i % 7).times {
                        def task = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: null, estimation: 3, name: randomWords(15,  5, 200), description: randomWords(50, 0, 2900), creator: getCreator(nextTaskUid), responsible: getResponsible(nextTaskUid), parentStory: story)
                        task.save(failOnError: true)
                        addTaskActivity(task, task.creator, 'taskSave')
                        story.addToTasks(task)
                        nextTaskUid++
                    }
                    story.save(failOnError: true)
                }
            }
            release1.sprints.findAll { it.orderNumber < 8 }.each { sprint ->
                sprint.stories.each { story ->
                    (sprint.orderNumber - 1).times {
                        def task = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: null, estimation: 3, name: randomWords(15,  5, 200), description: randomWords(50, 0, 2900), creator: getCreator(nextTaskUid), responsible: getResponsible(nextTaskUid), parentStory: story, backlog: sprint)
                        task.save(failOnError: true)
                        addTaskActivity(task, task.creator, 'taskSave')
                        story.addToTasks(task)
                        sprint.addToTasks(task)
                        nextTaskUid++
                    }
                }
                15.times {
                    def task = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: Task.TYPE_RECURRENT, estimation: 5, name: randomWords(15,  5, 200), description: randomWords(50, 0, 2900), creator: getCreator(nextTaskUid), responsible: getResponsible(nextTaskUid), parentStory: null, backlog: sprint)
                    sprint.addToTasks(task)
                    task.save(failOnError: true)
                    addTaskActivity(task, task.creator, 'taskSave')
                    nextTaskUid++
                    def task2 = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: Task.TYPE_URGENT, estimation: 4, name: randomWords(15,  5, 200), description: randomWords(50, 0, 2900), creator: getCreator(nextTaskUid), responsible: getResponsible(nextTaskUid), parentStory: null, backlog: sprint)
                    sprint.addToTasks(task2)
                    task2.save(failOnError: true)
                    addTaskActivity(task2, task2.creator, 'taskSave')
                    nextTaskUid++
                }
                if (sprint.orderNumber < 7) {
                    sprint.save(flush: true)
                    sprintService.activate(sprint)
                    if (sprint.orderNumber < 6) {
                        updateContentDoneSprint(sprint, team.members)
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

    private static void updateContentDoneSprint(Sprint sprint, Set<User> members) {
        sprint.stories.each { story ->
            story.state = Story.STATE_DONE
            addStoryActivity(story, ((int)story.id) % 2 == 0 ? members.first() : members.last(), 'done')
            story.doneDate = new Date()
            story.save(failOnError: true)
        }
        sprint.tasks.each { Task task ->
            doneTask(task)
        }
    }

    private static void inProgressTask(Task task) {
        task.state = Task.STATE_BUSY
        task.inProgressDate = new Date()
        task.save(failOnError: true)
        addTaskActivity(task, task.responsible, 'taskInprogress')
    }

    private static void doneTask(Task task) {
        task.state = Task.STATE_DONE
        if (!task.inProgressDate) {
            task.inProgressDate = new Date()
        }
        task.doneDate = new Date()
        task.estimation = 0
        task.save(failOnError: true)
        addTaskActivity(task, task.responsible, 'taskInprogress')
        addTaskActivity(task, task.responsible, 'taskFinish')
    }

    private static void updateContentInProgressSprint(Sprint sprint) {
        sprint.tasks.eachWithIndex { task, index ->
            if (index % 5 == 0) {
                doneTask(task)
            } else if (index % 2 == 0) {
                inProgressTask(task)
            }
        }
        def lastStory = sprint.stories.sort { it.rank }.last()
        lastStory.state = Story.STATE_DONE
        addStoryActivity(lastStory, lastStory.creator, 'done')
        lastStory.doneDate = new Date()
        lastStory.save(failOnError: true)
        lastStory.tasks.each { Task task ->
            doneTask(task)
        }
        lastStory.parentSprint.velocity += lastStory.effort
        def rankTasks = { k, tasks ->
            tasks.eachWithIndex { task, index ->
                task.rank = index + 1
                task.save(failOnError: true)
            }
        }
        sprint.tasks.findAll { it.parentStory == null }.groupBy { "$it.type" + "$it.state" }.each(rankTasks)
        sprint.tasks.findAll { it.parentStory != null }.groupBy { "$it.parentStory.id" + "$it.state" }.each(rankTasks)
    }

    private static void rankStories(Product product) {
        product.stories.findAll {
            (it.state == Story.STATE_ACCEPTED) || (it.state == Story.STATE_ESTIMATED)
        }.eachWithIndex { story, index ->
            story.rank = index + 1
            story.save(failOnError: true)
        }
        product.stories.findAll {
            it.state == Story.STATE_SUGGESTED
        }.eachWithIndex { story, index ->
            story.rank = index + 1
            story.save(failOnError: true)
        }
    }

    private static void createAcceptanceTests(Product product, User user) {
        product.stories.findAll { it.state == Story.STATE_SUGGESTED }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
            acceptanceTest.save(failOnError: true)
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
                acceptanceTest2.save(failOnError: true)
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_ACCEPTED }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
            acceptanceTest.save(failOnError: true)
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
                acceptanceTest2.save(failOnError: true)
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_INPROGRESS }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
            acceptanceTest.save(failOnError: true)
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.FAILED.id)
                acceptanceTest2.save(failOnError: true)
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
            if (index % 3 == 0) {
                def acceptanceTest3 = new AcceptanceTest(name: randomWords(10, 2), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.TOCHECK.id)
                acceptanceTest3.save(failOnError: true)
                addAcceptanceTestActivity(acceptanceTest3, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_DONE }.asList()[0..3].eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
            acceptanceTest.save(failOnError: true)
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15,  5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
                acceptanceTest2.save(failOnError: true)
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
        activity.save(failOnError: true)
        story.addToActivities(activity)
    }

    private static addTaskActivity(Task task, User poster, String code) {
        def activity = new Activity(poster: poster, parentRef: task.id, parentType: 'task', code: code, label: task.name)
        activity.save(failOnError: true)
        task.addToActivities(activity)
    }

    private static addAcceptanceTestActivity(AcceptanceTest acceptanceTest, User poster) {
        def activity = new Activity(poster: poster, parentRef: acceptanceTest.id, parentType: 'acceptanceTest', code: 'acceptanceTestSave', label: acceptanceTest.name)
        activity.save(failOnError: true)
        acceptanceTest.addToActivities(activity)
    }

    public static randomWords(int max = 1, int min = -1, int maxChar = 0){
        def wordsGenerator = new LoremIpsum()
        def intGenerator = new Random()

        if(min == -1){
            min = intGenerator.nextInt(max)
        }

        def wordsCount = intGenerator.nextInt(max+1)
        while(wordsCount < min && min > 0){
            wordsCount = intGenerator.nextInt(max+1)
        };

        def words = wordsGenerator.getWords( wordsCount )
        while(maxChar > 0 && words.length() > maxChar){
            words = wordsGenerator.getWords( wordsCount , intGenerator.nextInt(50))
        }
        words = words.split(/ /).toList()
        Collections.shuffle(words)
        Collections.shuffle(words)
        words = words.join(' ')
        return words
    }
}
