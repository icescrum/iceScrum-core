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
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Jeroen Broekhuizen (Jeroen.Broekhuizen@quintiq.com)
 */
package org.icescrum.core.utils

import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.security.Authority

class BundleUtils {

    static actorInstances = [
            (Actor.NUMBER_INSTANCES_INTERVAL_1): '1',
            (Actor.NUMBER_INSTANCES_INTERVAL_2): '2-5',
            (Actor.NUMBER_INSTANCES_INTERVAL_3): '6-10',
            (Actor.NUMBER_INSTANCES_INTERVAL_4): '11-100',
            (Actor.NUMBER_INSTANCES_INTERVAL_5): '100+'
    ]

    static actorLevels = [
            (Actor.EXPERTNESS_LEVEL_LOW): 'is.actor.it.low',
            (Actor.EXPERTNESS_LEVEL_MEDIUM): 'is.actor.it.medium',
            (Actor.EXPERTNESS_LEVEL_HIGH): 'is.actor.it.high'
    ]

    static actorFrequencies = [
            (Actor.USE_FREQUENCY_HOUR): 'is.actor.use.frequency.hour',
            (Actor.USE_FREQUENCY_DAY): 'is.actor.use.frequency.day',
            (Actor.USE_FREQUENCY_WEEK): 'is.actor.use.frequency.week',
            (Actor.USE_FREQUENCY_MONTH): 'is.actor.use.frequency.month',
            (Actor.USE_FREQUENCY_TRIMESTER): 'is.actor.use.frequency.quarter'
    ]

    static featureTypes = [
            (Feature.TYPE_FUNCTIONAL): 'is.feature.type.functional',
            (Feature.TYPE_ARCHITECTURAL): 'is.feature.type.architectural'
    ]

    static storyStates = [
            (Story.STATE_SUGGESTED): 'is.story.state.suggested',
            (Story.STATE_ACCEPTED): 'is.story.state.accepted',
            (Story.STATE_ESTIMATED): 'is.story.state.estimated',
            (Story.STATE_PLANNED): 'is.story.state.planned',
            (Story.STATE_INPROGRESS): 'is.story.state.inprogress',
            (Story.STATE_DONE): 'is.story.state.done'
    ]

    static storyTypes = [
            (Story.TYPE_USER_STORY): 'is.story.type.story',
            (Story.TYPE_DEFECT): 'is.story.type.defect',
            (Story.TYPE_TECHNICAL_STORY): 'is.story.type.technical'
    ]

    static releaseStates = [
            (Release.STATE_WAIT): 'is.release.state.wait',
            (Release.STATE_INPROGRESS): 'is.release.state.inprogress',
            (Release.STATE_DONE): 'is.release.state.done'
    ]

    static sprintStates = [
            (Sprint.STATE_WAIT): 'is.sprint.state.wait',
            (Sprint.STATE_INPROGRESS): 'is.sprint.state.inprogress',
            (Sprint.STATE_DONE): 'is.sprint.state.done'
    ]

    static colorsSelect = [
            'blue': 'is.postit.color.blue',
            'green': 'is.postit.color.green',
            'red': 'is.postit.color.red',
            'orange': 'is.postit.color.orange',
            'violet': 'is.postit.color.violet',
            'gray': 'is.postit.color.gray',
            'pink': 'is.postit.color.pink',
            'bluelight': 'is.postit.color.bluelight'
    ]

	static taskColorsSelect = [
			'yellow': 'is.postit.color.yellow',
            'blue': 'is.postit.color.blue',
            'green': 'is.postit.color.green',
            'red': 'is.postit.color.red',
            'orange': 'is.postit.color.orange',
            'violet': 'is.postit.color.violet',
            'gray': 'is.postit.color.gray',
            'pink': 'is.postit.color.pink',
            'bluelight': 'is.postit.color.bluelight'
	]

    static taskStates = [
            (Task.STATE_WAIT): 'is.task.state.wait',
            (Task.STATE_BUSY): 'is.task.state.inprogress',
            (Task.STATE_DONE): 'is.task.state.done'
    ]

    static taskTypes = [
            (Task.TYPE_RECURRENT) : 'is.task.type.recurrent',
            (Task.TYPE_URGENT) : 'is.task.type.urgent'
    ]

    static roles = [
            (Authority.MEMBER): 'is.role.teamMember',
            (Authority.SCRUMMASTER): 'is.role.scrumMaster',
            (Authority.PRODUCTOWNER): 'is.role.productOwner',
            (Authority.STAKEHOLDER): 'is.role.stakeHolder',
            (Authority.PO_AND_SM): 'is.role.poAndSm'
    ]

    static rolesPublic = [
            (Authority.MEMBER): 'is.role.teamMember',
            (Authority.SCRUMMASTER): 'is.role.scrumMaster',
            (Authority.PRODUCTOWNER): 'is.role.productOwner',
            (Authority.PO_AND_SM): 'is.role.poAndSm'
    ]

}
