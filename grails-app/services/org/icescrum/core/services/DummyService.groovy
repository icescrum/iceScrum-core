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
import org.icescrum.core.domain.preferences.ProjectPreferences

import java.text.Normalizer

@Transactional
class DummyService {

    def sprintService
    def releaseService
    def sessionFactory
    def securityService
    def grailsApplication
    def actorService
    def storyService
    def projectService
    def teamService
    def featureService
    def acceptanceTestService
    def taskService
    def pushService

    void createSampleProject(User user) {
        // Avoid premature notification and thus access to the project, which would fail
        pushService.disablePushForThisThread()
        // Project & team
        def projectName = ("Peetic " + user.username).take(100)
        def startDate = new Date() - 16
        startDate.clearTime()
        Project project = new Project(name: projectName, pkey: toPkey(user), startDate: startDate, endDate: startDate + 102)
        project.description = '''*Peetic* is a dating website for your pets! Don't you think that they deserve to find their soul mate?\n\nThis project is yours: browse it and play with it to discover *iceScrum 7*!\n\nPeetic is inspired by this free "template":https://github.com/pablopernot/peetic.'''
        project.preferences = new ProjectPreferences(webservices: true, hidden: true)
        String teamName = projectName + ' Team'
        Team team = Team.findByName(teamName)
        if (!team || team.owner.username != user.username) {
            team = new Team(name: teamName)
            teamService.save(team, [], [user.id])
        }
        projectService.save(project, [user.id], [])
        projectService.addTeamToProject(project, team)
        // Releases & sprints
        Release release1 = new Release(startDate: startDate, endDate: startDate + 64, name: 'Peetic core', vision: 'Easily create and manage your pet profile and find its soul mate. Who knows, you could find yours in the process.', todoDate: startDate)
        Release release2 = new Release(startDate: startDate + 65, endDate: startDate + 115, name: 'Peetic premium', vision: 'Premium features for paying accounts', todoDate: startDate)
        releaseService.save(release1, project)
        release1.inProgressDate = startDate
        release1.save()
        releaseService.save(release2, project)
        sprintService.generateSprints(release1)
        // Features
        def features = []
        [
                [name: 'Administration', value: 2, description: 'Administrate and moderate content created by the users'],
                [name: 'Pet profile', value: 4, description: 'Manage the profile of a pet', color: '#d91a2f'],
                [name: 'Advertising', value: 3, description: 'Advertise projects related to the profile of pets', color: '#ba48c7'],
                [name: 'Search', value: 4, description: 'Search other pets to find the best match', color: '#a0dffa']
        ].each { featureProperties ->
            Feature feature = new Feature(featureProperties)
            featureService.save(feature, project)
            features << feature
        }
        def featureAdmin = features[0]
        def featurePetProfile = features[1]
        def featureAdvertising = features[2]
        def featureSearch = features[3]
        // Actors
        Actor petOwner = new Actor(name: 'Pet Owner')
        actorService.save(petOwner, project)
        def petOwnerTag = "A[$petOwner.uid-$petOwner.name]"
        Actor administrator = new Actor(name: 'Administrator')
        actorService.save(administrator, project)
        def administratorTag = "A[$administrator.uid-$administrator.name]"
        // Stories
        def sandboxStoryProperties = [
                [name: 'Email project digest', description: '', feature: featureAdvertising, value: 2, type: Story.TYPE_USER_STORY, state: Story.STATE_SUGGESTED],
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
                        [name: 'Setup CI & SCM', description: 'Create projects on SCM and build it automatically after each commit', value: 5, type: Story.TYPE_TECHNICAL_STORY, effort: 5, state: Story.STATE_ESTIMATED],
                        [name: 'Create a pet profile', description: "As a $petOwnerTag\nI can create a profile for my pet \nIn order for it to be found by the owner of its soul mate", feature: featurePetProfile, value: 6, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_ESTIMATED],
                        [name: 'Display a pet profile', description: "As a $petOwnerTag\nI can display the profile of other pets \nIn order to find the soul mate of mine", value: 6, feature: featurePetProfile, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_ESTIMATED],
                        [name: 'Spike advertising', description: 'Validate the business model by contacting advertising plaforms and pet projects brands', feature: featureAdvertising, value: 5, type: Story.TYPE_TECHNICAL_STORY, effort: 2, state: Story.STATE_ESTIMATED],
                ],
                1: [
                        [name: 'Contact a pet owner', description: "As a $petOwnerTag\nI can contact another pet owner \nIn order to arrange a meeting for our pets", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 2, state: Story.STATE_ESTIMATED],
                        [name: 'Authenticate', description: "As a $petOwnerTag\nI can be recognized as my pet owner on the website \nIn order to manage my pet profile and prevent others to do so", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 8, state: Story.STATE_ESTIMATED],
                        [name: 'Search profiles by race', description: "As a $petOwnerTag\nI can search other pets by race \nIn order to find the right partner for my pets", feature: featureSearch, value: 5, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_ESTIMATED],
                        [name: 'Basic advertising', description: "As an $administratorTag\nI can offer pet project ads to my users\nIn order to earn money", feature: featureAdvertising, value: 4, type: Story.TYPE_USER_STORY, effort: 5, state: Story.STATE_ESTIMATED],
                ],
                2: [
                        [name: 'Add photos to my pet profile', description: "As a $petOwnerTag\nI can add photos to my pet profile \nIn order show how it is gorgeous to the other pet owners and make them choose it", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 3, state: Story.STATE_ESTIMATED],
                        [name: 'Delete my pet profile', description: "As a $petOwnerTag\nI can delete my pet profile \nIn order to stop dating if it has found its soul mate", feature: featurePetProfile, value: 5, type: Story.TYPE_USER_STORY, effort: 2, state: Story.STATE_ESTIMATED],
                        [name: 'Advertise according to the race of the pet', description: "As an $administratorTag\nI can advertise according to the race of the pet \nIn order to increase the click rates", feature: featureAdvertising, value: 4, type: Story.TYPE_USER_STORY, effort: 5, state: Story.STATE_ESTIMATED],
                        [name: 'Update my pet profile', description: "As a $petOwnerTag\nI can update my pet profile \nIn order to add information and correct mistakes", feature: featurePetProfile, value: 4, type: Story.TYPE_USER_STORY, effort: 8, state: Story.STATE_ESTIMATED],
                        [name: 'Advertise according to visited profiles', description: "As an $administratorTag\nI can advertise according to visited profile \nIn order to increase the click rates", feature: featureAdvertising, value: 3, type: Story.TYPE_USER_STORY, effort: 2, state: Story.STATE_ESTIMATED]
                ]
        ]
        def createStory = { properties ->
            Story story = new Story([suggestedDate: startDate] + properties)
            if (story.state >= Story.STATE_ACCEPTED) {
                story.acceptedDate = startDate + 1
            }
            if (story.state >= Story.STATE_ESTIMATED) {
                story.estimatedDate = startDate + 2
            }
            storyService.save(story, project, user)
            return story
        }
        // Create stories
        def storiesBySprint = [:]
        storyPropertiesBySprint.each { sprintIndex, storyProperties ->
            storiesBySprint[sprintIndex] = storyProperties.collect { createStory(it) }
        }
        backlogStoryProperties.each { createStory(it) }
        sandboxStoryProperties.each { createStory(it) }
        project.save()
        sessionFactory.currentSession.flush()
        // Plan Stories
        storiesBySprint.each { sprintIndex, stories ->
            Sprint sprint = release1.sprints[sprintIndex]
            stories.each { Story story ->
                sprint.addToStories(story)
                story.parentSprint = sprint
                story.plannedDate = startDate + 2
                story.state = Story.STATE_PLANNED
            }
            sprint.capacity = sprint.totalEffort
            sprint.save(flush: true)
        }
        project.save()
        // Tasks and sprint progression
        project.stories.findAll { it.state < Story.STATE_PLANNED }.eachWithIndex { Story story, int i ->
            if (i % 4 == 0) {
                (i % 7).times {
                    taskService.save(new Task(estimation: 3, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), responsible: user, parentStory: story), user)
                }
            }
        }
        project.refresh()
        release1.sprints.each { sprint ->
            sprint.todoDate = startDate
            sprint.goal = ''
            sprint.deliveredVersion = sprint.orderNumber == release1.sprints.size() ? 'v1.0' : 'v0.' + sprint.orderNumber
            sprint.doneDefinition = "* All tasks are done\n* All code is merged in master branch\n* All acceptance tests pass\n* There are automated unit tests\n* There are automated functional tests\n* Implemented web UI features are compatible with modern browsers"
            sprint.stories.each { story ->
                (sprint.orderNumber).times {
                    taskService.save(new Task(estimation: 3, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), responsible: user, parentStory: story, backlog: sprint), user)
                }
            }
            2.times {
                taskService.save(new Task(type: Task.TYPE_RECURRENT, estimation: 5, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), responsible: user, backlog: sprint), user)
            }
            5.times {
                taskService.save(new Task(type: Task.TYPE_URGENT, estimation: 4, name: randomWords(15, 5, 200), description: randomWords(50, 0, 2900), responsible: user, backlog: sprint), user)
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
                project.refresh()
            }
        }
        // Acceptance tests
        project.refresh()
        createAcceptanceTests(project, user)
        sessionFactory.currentSession.flush()
        // Push project creation
        pushService.enablePushForThisThread()
        projectService.manageProjectEvents(project, [:])
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

    private void createAcceptanceTests(Project project, User user) {
        project.stories.findAll { it.state == Story.STATE_SUGGESTED }.asList().eachWithIndex { Story story, index ->
            acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5)), story, user)
            if (index % 2 == 0) {
                acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5)), story, user)
            }
        }
        project.stories.findAll { it.state == Story.STATE_ACCEPTED }.asList().eachWithIndex { Story story, index ->
            acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5)), story, user)
            if (index % 2 == 0) {
                acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5)), story, user)
            }
        }
        project.stories.findAll { it.state == Story.STATE_INPROGRESS }.asList().eachWithIndex { Story story, index ->
            acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), state: AcceptanceTest.AcceptanceTestState.SUCCESS.id), story, user)
            if (index % 2 == 0) {
                acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), state: AcceptanceTest.AcceptanceTestState.FAILED.id), story, user)
            }
            if (index % 3 == 0) {
                acceptanceTestService.save(new AcceptanceTest(name: randomWords(10, 2), description: randomWords(30, 5), state: AcceptanceTest.AcceptanceTestState.TOCHECK.id), story, user)
            }
        }
        project.stories.findAll { it.state == Story.STATE_DONE }.asList().eachWithIndex { Story story, index ->
            acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), state: AcceptanceTest.AcceptanceTestState.SUCCESS.id), story, user)
            if (index % 2 == 0) {
                acceptanceTestService.save(new AcceptanceTest(name: randomWords(15, 5, 200), description: randomWords(30, 5), state: AcceptanceTest.AcceptanceTestState.SUCCESS.id), story, user)
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
        String pkey = 'PET' + Normalizer.normalize(user.username, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").toUpperCase().replaceAll("[^A-Z0-9]+", "")
        pkey = pkey.take(10)
        def countTaken = Project.countByPkey(pkey)
        if (countTaken > 0) {
            pkey = pkey.take(countTaken < 10 ? 9 : 8) + countTaken
        }
        return pkey
    }
}
