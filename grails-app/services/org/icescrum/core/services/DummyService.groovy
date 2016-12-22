/*
 * Copyright (c) 2016 Kagilum SAS
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
 *
 */

package org.icescrum.core.services

import de.svenjacobs.loremipsum.LoremIpsum
import grails.transaction.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.domain.preferences.ProductPreferences

import java.text.Normalizer

@Transactional
class DummyService {

    def sprintService
    def releaseService
    def sessionFactory
    def securityService
    def templateService
    def grailsApplication
    def actorService
    def storyService

    void createSampleProject(User user) {
        // Project
        def projectName = ("Peetic " + user.username).take(100)
        def startDate = new Date() - 16
        startDate.clearTime()
        def product = new Product(name: projectName, pkey: toPkey(user), startDate: startDate, endDate: startDate + 102)
        product.description = '''*Peetic* is a dating website for your pets! Don't you think that they deserve to find their soul mate?\n\nThis project is yours: browse it and play with it to discover *iceScrum 7*!\n\nPeetic is inspired by this free "template":https://github.com/pablopernot/peetic.'''
        product.preferences = new ProductPreferences(webservices: true, hidden: true)
        product.save()
        // Backlogs
        new Backlog(product: product, shared: true, filter: '{"story":{"state":1}}', name: 'is.ui.sandbox', code: 'sandbox').save()
        new Backlog(product: product, shared: true, filter: '{"story":{"state":[2,3]}}', name: 'is.ui.backlog', code: 'backlog').save()
        new Backlog(product: product, shared: true, filter: '{"story":{"state":7}}', name: 'todo.is.ui.backlog.done', code: 'done').save()
        new Backlog(product: product, shared: true, filter: '{"story":{}}', name: 'todo.is.ui.backlog.all', code: 'all').save()
        securityService.secureDomain(product)
        // Team
        def team = new Team(name: projectName + ' Team').addToProducts(product).addToMembers(user)
        team.save()
        securityService.secureDomain(team)
        securityService.createProductOwnerPermissions(user, product)
        securityService.createScrumMasterPermissions(user, team)
        securityService.changeOwner(user, product)
        securityService.changeOwner(user, team)
        // Releases
        def release1 = new Release(startDate: startDate, endDate: startDate + 64, name: 'Peetic core', vision: 'Easily create and manage your pet profile and find its soul mate. Who knows, you could find yours in the process.', todoDate: startDate)
        def release2 = new Release(startDate: startDate + 65, endDate: startDate + 115, name: 'Peetic premium', vision: 'Premium features for paying accounts', todoDate: startDate)
        releaseService.save(release1, product)
        release1.inProgressDate = startDate
        release1.save()
        releaseService.save(release2, product)
        // Sprints
        sprintService.generateSprints(release1)
        // Features
        def featureProperties = [
                [name: 'Administration', value: 2, description: 'Administrate and moderate content created by the users'],
                [name: 'Pet profile', value: 4, description: 'Manage the profile of a pet', color: '#d91a2f'],
                [name: 'Advertising', value: 3, description: 'Advertise products related to the profile of pets', color: '#ba48c7'],
                [name: 'Search', value: 4, description: 'Search other pets to find the best match', color: '#a0dffa']
        ]
        def features = []
        featureProperties.eachWithIndex { featureProps, index ->
            def defaultProperties = [backlog: product, uid: index + 1, rank: index + 1]
            features << new Feature(defaultProperties + featureProps).save()
        }
        def featureAdmin = features[0]
        def featurePetProfile = features[1]
        def featureAdvertising = features[2]
        def featureSearch = features[3]
        // Actors
        Actor petOwner = new Actor(name: 'Pet Owner')
        actorService.save(petOwner, product)
        def petOwnerTag = "A[$petOwner.uid-$petOwner.name]"
        Actor administrator = new Actor(name: 'Administrator')
        actorService.save(administrator, product)
        def administratorTag = "A[$administrator.uid-$administrator.name]"
        // Stories
        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        def bugStory = new Story(type: Story.TYPE_DEFECT, backlog: product)
        templateService.save(new Template(name: g.message(code: 'is.ui.sandbox.story.template.default.defect')), bugStory)
        bugStory.delete()
        def sandboxStoryProperties = [
                [name: 'Email product digest', description: '', feature: featureAdvertising, value: 2, type: Story.TYPE_USER_STORY, state: Story.STATE_SUGGESTED],
                [name: 'Automatically detect location', description: '', feature: featureSearch, value: 3, type: Story.TYPE_USER_STORY, state: Story.STATE_SUGGESTED],
                [name: 'Batch delete pet profiles', description: '', feature: featureAdmin, value: 1, type: Story.TYPE_USER_STORY, state: Story.STATE_SUGGESTED],
                [name: 'Search by behavior', description: '', feature: featureSearch, type: Story.TYPE_USER_STORY, state: Story.STATE_SUGGESTED]
        ]
        def backlogStoryProperties = [
                [name: 'Add videos to my pet profile', description: "As a $petOwnerTag\nI can add videos to my pet profile \nIn order show how it is gorgeous to the other pet owners and make them choose it", feature: featurePetProfile, value: 3, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_ESTIMATED],
                [name: 'Resizing the pet profile breaks the styling', description: 'In IE and Firefox, resizing the window under 400px width causes an overlap of fields', feature: featurePetProfile, value: 3, type: Story.TYPE_DEFECT, affectedVersion: '0.1', effort: 5, state: Story.STATE_ESTIMATED],
                [name: 'Delete profiles', description: "As an $administratorTag\nI can delete profile\nIn order to remove unwanted content", feature: featureAdmin, value: 2, type: Story.TYPE_USER_STORY, effort: 1, state: Story.STATE_ESTIMATED],
                [name: 'Geographical search', description: '', feature: featureSearch, value: 2, type: Story.TYPE_USER_STORY, state: Story.STATE_ACCEPTED],
                [name: 'Metrics & reports', description: '', feature: featureAdmin, value: 3, type: Story.TYPE_USER_STORY, state: Story.STATE_ACCEPTED],
                [name: 'Search by physical characteristics', description: '', feature: featureSearch, type: Story.TYPE_USER_STORY, state: Story.STATE_ACCEPTED],
        ]
        def storyPropertiesBySprint = [
                0: [
                    [name: 'Setup CI & SCM', description: 'Create projects on SCM and build it automatically after each commit', value: 5, type: Story.TYPE_TECHNICAL_STORY, effort: 5, state: Story.STATE_PLANNED],
                    [name: 'Create a pet profile', description: "As a $petOwnerTag\nI can create a profile for my pet \nIn order for it to be found by the owner of its soul mate", value: 6, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_PLANNED],
                    [name: 'Display a pet profile', description: "As a $petOwnerTag\nI can display the profile of other pets \nIn order to find the soul mate of mine", value: 6, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_PLANNED],
                    [name: 'Spike advertising', description: 'Validate the business model by contacting advertising plaforms and pet products brands', feature: featureAdvertising, value: 5, type: Story.TYPE_TECHNICAL_STORY, effort: 2, state: Story.STATE_PLANNED],
                ],
                1: [
                    [name: 'Contact a pet owner', description: "As a $petOwnerTag\nI can contact another pet owner \nIn order to arrange a meeting for our pets", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 2, state: Story.STATE_PLANNED],
                    [name: 'Authenticate', description: "As a $petOwnerTag\nI can be recognized as my pet owner on the website \nIn order to manage my pet profile and prevent others to do so", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 8, state: Story.STATE_PLANNED],
                    [name: 'Search profiles by race', description: "As a $petOwnerTag\nI can search other pets by race \nIn order to find the right partner for my pets", feature: featureSearch, value: 5, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_PLANNED],
                    [name: 'Basic advertising', description: "As an $administratorTag\nI can offer pet product ads to my users\nIn order to earn money", feature: featureAdvertising, value: 4, type: Story.TYPE_USER_STORY, effort: 5, state: Story.STATE_PLANNED],
                ],
                2: [
                    [name: 'Add photos to my pet profile', description: "As a $petOwnerTag\nI can add photos to my pet profile \nIn order show how it is gorgeous to the other pet owners and make them choose it", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_PLANNED],
                    [name: 'Delete my pet profile', description: "As a $petOwnerTag\nI can delete my pet profile \nIn order to stop dating if it has found its soul mate", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 2, state: Story.STATE_PLANNED],
                    [name: 'Advertise according to the race of the pet', description: "As an $administratorTag\nI can advertise according to the race of the pet \nIn order to increase the click rates", feature: featureAdvertising, value: 4, type: Story.TYPE_USER_STORY, effort: 5, state: Story.STATE_PLANNED],
                    [name: 'Update my pet profile', description: "As a $petOwnerTag\nI can update my pet profile \nIn order to add information and correct mistakes", feature: featurePetProfile, value: 4, type: Story.TYPE_USER_STORY, effort: 8, state: Story.STATE_PLANNED],
                    [name: 'Advertise according to visited profiles', description: "As an $administratorTag\nI can advertise according to visited profile \nIn order to increase the click rates", feature: featureAdvertising, value: 3, type: Story.TYPE_USER_STORY, effort: 2, state: Story.STATE_PLANNED]
                ]
        ]
        def rankByIndex = { collection -> collection.eachWithIndex { item, index -> item.rank = index + 1 } }
        rankByIndex(sandboxStoryProperties)
        rankByIndex(backlogStoryProperties)
        storyPropertiesBySprint.each { sprintIndex, storyProperties -> rankByIndex(storyProperties) }
        def _storyCount = 0
        def createStory = { properties ->
            def suggestedDate = startDate
            def acceptedDate = startDate + 1
            def estimatedDate = startDate + 2
            def defaultProperties = [backlog: product, uid: _storyCount + 1, suggestedDate: suggestedDate, estimatedDate: estimatedDate, creator: user]
            def story = new Story(defaultProperties + properties).save()
            addStoryActivity(story, user, Activity.CODE_SAVE, suggestedDate)
            if (story.state >= Story.STATE_ACCEPTED) {
                story.acceptedDate = acceptedDate
                addStoryActivity(story, user, 'acceptAs', acceptedDate)
            }
            if (story.state < Story.STATE_ESTIMATED) {
                story.estimatedDate = null
                story.effort = null
            } else {
                addStoryActivity(story, user, 'estimate', estimatedDate)
            }
            _storyCount ++
            storyService.manageActors(story, product)
            return story
        }
        // Create stories
        def storiesBySprint = [:]
        storyPropertiesBySprint.each { sprintIndex, storyProperties ->
            storiesBySprint[sprintIndex] = storyProperties.collect { createStory(it) }
        }
        backlogStoryProperties.each { createStory(it) }
        sandboxStoryProperties.each { createStory(it) }
        product.save()
        sessionFactory.currentSession.flush()
        // Plan Stories
        storiesBySprint.each { sprintIndex, stories ->
            Date plannedDate = startDate + 2
            Sprint sprint = release1.sprints[sprintIndex]
            stories.each { Story story ->
                sprint.addToStories(story)
                story.parentSprint = sprint
                addStoryActivity(story, user, 'estimate', plannedDate)
                story.plannedDate = new Date()
            }
            sprint.capacity = sprint.totalEffort
            sprint.save(flush: true)
        }
        product.save()
        // Tasks and sprint progression
        int nextTaskUid = 1
        product.stories.findAll { it.state < Story.STATE_PLANNED }.eachWithIndex { Story story, int i ->
            if (i % 4 == 0) {
                (i % 7).times {
                    def task = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: null, estimation: 3, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), creator: user, responsible: user, parentStory: story)
                    task.save()
                    addTaskActivity(task, task.creator, 'taskSave')
                    story.addToTasks(task)
                    nextTaskUid++
                }
                story.save()
            }
        }
        product.refresh()
        release1.sprints.each { sprint ->
            sprint.todoDate = startDate
            sprint.goal = ''
            sprint.deliveredVersion = '0.' + sprint.orderNumber
            sprint.doneDefinition = "* All tasks are done\n* All code is merged in master branch\n* All acceptance tests pass\n* There are automated unit tests\n* There are automated functional tests\n* Implemented web UI features are compatible with modern browsers"
            sprint.stories.each { story ->
                (sprint.orderNumber).times {
                    def task = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: null, estimation: 3, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), creator: user, responsible: user, parentStory: story, backlog: sprint)
                    task.save()
                    addTaskActivity(task, task.creator, 'taskSave')
                    story.addToTasks(task)
                    sprint.addToTasks(task)
                    nextTaskUid++
                }
            }
            2.times {
                def task = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: Task.TYPE_RECURRENT, estimation: 5, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), creator: user, responsible: user, parentStory: null, backlog: sprint)
                sprint.addToTasks(task)
                task.save()
                addTaskActivity(task, task.creator, 'taskSave')
                nextTaskUid++
            }
            5.times {
                def task2 = new Task(parentProduct: product, uid: nextTaskUid, rank: it + 1, type: Task.TYPE_URGENT, estimation: 4, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), creator: user, responsible: user, parentStory: null, backlog: sprint)
                sprint.addToTasks(task2)
                task2.save()
                addTaskActivity(task2, task2.creator, 'taskSave')
                nextTaskUid++
            }
            if (sprint.orderNumber < 3) {
                sprint.save(flush: true)
                sprintService.activate(sprint)
                sprint.inProgressDate = sprint.startDate
                if (sprint.orderNumber == 1) {
                    updateContentDoneSprint(sprint, team.members)
                    sprint.goal = 'Set up the environment and minimal pet profile management'
                    sprint.retrospective = '''* Good communication in the team\n* This sprint was a success\n* Pair programming helped\n* Daily scrum meetings were too long\n* The last day was a rush to finish the sprint'''
                    sprintService.close(sprint)
                    sprint.doneDate = sprint.endDate
                } else {
                    sprint.goal = 'Minimum viable Peetic: search profile, contact owner, make money'
                    updateContentInProgressSprint(sprint)
                    sprint.cliches.each { it.delete() }
                    sprint.cliches.clear()
                    def clichesData = [
                            "<cliche><stories>4</stories><inprogressStories>4</inprogressStories><doneStories>0</doneStories><pointsStories>18.00</pointsStories><totalPointsStories>0</totalPointsStories><tasks>14</tasks><waitTasks>11</waitTasks><inprogressTasks>3</inprogressTasks><doneTasks>0</doneTasks><sprintTasks>6</sprintTasks><recurrentTasks>2</recurrentTasks><urgentTasks>4</urgentTasks><storyTasks>8</storyTasks><remainingHours>60</remainingHours></cliche>",
                            "<cliche><stories>4</stories><inprogressStories>3</inprogressStories><doneStories>1</doneStories><pointsStories>18.00</pointsStories><totalPointsStories>5.00</totalPointsStories><tasks>15</tasks><waitTasks>9</waitTasks><inprogressTasks>4</inprogressTasks><doneTasks>2</doneTasks><sprintTasks>7</sprintTasks><recurrentTasks>2</recurrentTasks><urgentTasks>5</urgentTasks><storyTasks>8</storyTasks><remainingHours>41.0</remainingHours></cliche>"
                    ]
                    clichesData.eachWithIndex { clicheData, index ->
                        Cliche cliche = new Cliche(type: Cliche.TYPE_DAILY, datePrise: sprint.startDate + index, data: clicheData, parentTimeBox: sprint)
                        sprint.addToCliches(cliche)
                    }
                }
                sprint.save(flush: true)
                product.refresh()
            }
        }
        // Acceptance tests
        product.refresh()
        createAcceptanceTests(product, user)
        sessionFactory.currentSession.flush()
    }

    private void updateContentDoneSprint(Sprint sprint, Set<User> members) {
        sprint.stories.each { story ->
            story.state = Story.STATE_DONE
            addStoryActivity(story, ((int) story.id) % 2 == 0 ? members.first() : members.last(), 'done', sprint.endDate)
            story.doneDate = sprint.endDate
            story.save()
        }
        sprint.tasks.each { Task task ->
            doneTask(task)
        }
    }

    private void inProgressTask(Task task) {
        task.state = Task.STATE_BUSY
        task.inProgressDate = new Date()
        task.save()
        addTaskActivity(task, task.responsible, 'taskInprogress')
    }

    private void doneTask(Task task) {
        task.state = Task.STATE_DONE
        if (!task.inProgressDate) {
            task.inProgressDate = new Date()
        }
        task.doneDate = new Date()
        task.estimation = 0
        task.save()
        addTaskActivity(task, task.responsible, 'taskInprogress')
        addTaskActivity(task, task.responsible, 'taskFinish')
    }

    private void updateContentInProgressSprint(Sprint sprint) {
        sprint.tasks.eachWithIndex { task, index ->
            if (index % 5 == 0) {
                doneTask(task)
            } else if (index % 2 == 0) {
                inProgressTask(task)
            }
        }
        def lastStory = sprint.stories.sort { it.rank }.last()
        lastStory.state = Story.STATE_DONE
        addStoryActivity(lastStory, lastStory.creator, 'done', new Date())
        lastStory.doneDate = new Date()
        lastStory.save()
        lastStory.tasks.each { Task task ->
            doneTask(task)
        }
        lastStory.parentSprint.velocity += lastStory.effort
        def rankTasks = { k, tasks ->
            tasks.eachWithIndex { task, index ->
                task.rank = index + 1
                task.save()
            }
        }
        sprint.tasks.findAll { it.parentStory == null }.groupBy { "$it.type" + "$it.state" }.each(rankTasks)
        sprint.tasks.findAll { it.parentStory != null }.groupBy { "$it.parentStory.id" + "$it.state" }.each(rankTasks)
    }

    private void createAcceptanceTests(Product product, User user) {
        product.stories.findAll { it.state == Story.STATE_SUGGESTED }.asList().eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_ACCEPTED }.asList().eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_INPROGRESS }.asList().eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.FAILED.id)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
            if (index % 3 == 0) {
                def acceptanceTest3 = new AcceptanceTest(name: randomWords(10, 2), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.TOCHECK.id)
                acceptanceTest3.save()
                addAcceptanceTestActivity(acceptanceTest3, user)
            }
        }
        product.stories.findAll { it.state == Story.STATE_DONE }.asList().eachWithIndex { Story story, index ->
            def acceptanceTest = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
            acceptanceTest.save()
            addAcceptanceTestActivity(acceptanceTest, user)
            if (index % 2 == 0) {
                def acceptanceTest2 = new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), creator: user, parentStory: story, state: AcceptanceTest.AcceptanceTestState.SUCCESS.id)
                acceptanceTest2.save()
                addAcceptanceTestActivity(acceptanceTest2, user)
            }
        }
    }

    private addStoryActivity(Story story, User poster, String code, dateCreated) {
        def activity = new Activity(poster: poster, parentRef: story.id, parentType: 'story', code: code, label: story.name, dateCreated: dateCreated)
        activity.save()
        story.addToActivities(activity)
    }

    private addTaskActivity(Task task, User poster, String code) {
        def activity = new Activity(poster: poster, parentRef: task.id, parentType: 'task', code: code, label: task.name)
        activity.save()
        task.addToActivities(activity)
    }

    private addAcceptanceTestActivity(AcceptanceTest acceptanceTest, User poster) {
        def activity = new Activity(poster: poster, parentRef: acceptanceTest.id, parentType: 'acceptanceTest', code: 'acceptanceTestSave', label: acceptanceTest.name)
        activity.save()
        acceptanceTest.addToActivities(activity)
    }

    private randomWords(int max = 1, int min = -1, int maxChar = 0) {
        def wordsGenerator = new LoremIpsum()
        def intGenerator = new Random()
        if (min == -1) {
            min = intGenerator.nextInt(max)
        }
        def wordsCount = intGenerator.nextInt(max + 1)
        while (wordsCount < min && min > 0) {
            wordsCount = intGenerator.nextInt(max + 1)
        };
        def words = wordsGenerator.getWords(wordsCount)
        while (maxChar > 0 && words.length() > maxChar) {
            words = wordsGenerator.getWords(wordsCount, intGenerator.nextInt(50))
        }
        words = words.split(/ /).toList()
        Collections.shuffle(words)
        Collections.shuffle(words)
        words = words.join(' ')
        return words
    }

    private String toPkey(User user) {
        String pkey = 'PET' + Normalizer.normalize(user.username, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").toUpperCase().replaceAll("[^A-Z0-9]+","")
        pkey = pkey.take(10)
        def countTaken = Product.countByPkey(pkey)
        if (countTaken > 0) {
            pkey = pkey.take(countTaken < 10 ? 9 : 8) + countTaken
        }
        return pkey
    }
}
