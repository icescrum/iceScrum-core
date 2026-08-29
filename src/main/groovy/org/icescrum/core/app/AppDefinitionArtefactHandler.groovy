package org.icescrum.core.app

import org.icescrum.core.artefact.AbstractScrumArtefactHandler

/*
 * Copyright (c) 2017 Kagilum SAS
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Vincent Barrier (vbarrier@kagilum.com)
 *
 */

class AppDefinitionArtefactHandler extends AbstractScrumArtefactHandler {

    static public final String TYPE = 'Apps'

    AppDefinitionArtefactHandler() {
        super(TYPE, AppDefinitionClass.class, DefaultAppDefinitionClass.class, TYPE)
    }
}
