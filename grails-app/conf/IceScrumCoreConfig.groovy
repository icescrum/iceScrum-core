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
atmospherePlugin {
    servlet {
        // Servlet initialization parameters
        Example: initParams = ['org.atmosphere.useNative': 'true']
        urlPattern = '/stream/*'
    }
    handlers {
        // This closure is used to generate the atmosphere.xml using a MarkupBuilder instance in META-INF folder
        atmosphereDotXml = {
            'atmosphere-handler'('context-root': '/stream/app', 'class-name': 'org.icescrum.atmosphere.IceScrumAtmosphereHandler', 'broadcaster': 'org.atmosphere.util.ExcludeSessionBroadcaster')
        }
    }
}