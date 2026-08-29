/*
* Copyright (c) 2011 Vincent Barrier.
*
*
* entry-points is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License.
*
* entry-points is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.icescrum.plugins.entryPoints.artefacts;

import grails.core.ArtefactHandlerAdapter;
import grails.core.GrailsClass;
import org.grails.core.AbstractGrailsClass;

public class AbstractEntryPointsArtefactHandler extends ArtefactHandlerAdapter {

    public AbstractEntryPointsArtefactHandler(String type, Class<? extends GrailsClass> grailsClassType, Class<? extends AbstractGrailsClass> grailsClassImpl, String artefactSuffix) {
        super(type, grailsClassType, grailsClassImpl, artefactSuffix, true);
    }

    @Override
    public String getPluginName() {
        return "entryPoints";
    }

}
