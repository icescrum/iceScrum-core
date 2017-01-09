package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereConfig
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.DefaultBroadcaster
import org.icescrum.core.domain.Project

class IceScrumBroadcaster extends DefaultBroadcaster {

    String pkey
    String pname

    public IceScrumBroadcaster(){}

    public Broadcaster initialize(String name, AtmosphereConfig config) {
        initValues()
        return super.initialize(name, config)
    }

    private void initValues() {
        if (name.contains("project-")){
            def props = Project.createCriteria().get {
                eq 'id', name.split('-')[1].toLong()
                projections {
                    property 'pkey'
                    property 'name'
                }
                cache true
            }
            pkey = props[0]
            pname = props[1]
        } else {
            pkey = ""
            pname = "Global"
        }
    }
}
