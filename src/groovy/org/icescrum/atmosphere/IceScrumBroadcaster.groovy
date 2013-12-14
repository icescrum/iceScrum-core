package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereConfig
import org.atmosphere.cpr.DefaultBroadcaster
import org.icescrum.core.domain.Product

class IceScrumBroadcaster extends DefaultBroadcaster {

    String pkey
    String pname

    IceScrumBroadcaster(String name, URI uri, AtmosphereConfig config) {
        super(name, uri, config)
        initValues()
    }

    IceScrumBroadcaster(String name, AtmosphereConfig config) {
        super(name, config)
        initValues()
    }

    private void initValues() {
        if (name.contains("product-")){
            def props = Product.createCriteria().get {
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
