package org.icescrum.core;

import org.codehaus.groovy.grails.web.context.GrailsContextLoaderListener;
import org.springframework.web.context.ContextLoader;

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
*/
public class IceScrumContextLoaderListener extends GrailsContextLoaderListener {
    @Override
    protected ContextLoader createContextLoader() {
        /*
            Prevent java 1.8
        */
        System.out.println("Java version: " + System.getProperty("java.specification.version"));
        if(System.getProperty("java.specification.version").equals("1.5")){
            throw new RuntimeException("Really? Incompatible Java version. iceScrum isn't compatible with Java 1.5 please update your Java plateform");
        }
        if(System.getProperty("java.specification.version").equals("1.8")){
            throw new RuntimeException("Incompatible Java version. iceScrum isn't compatible with Java 1.8 yet");
        }

        return super.createContextLoader();
    }
}
