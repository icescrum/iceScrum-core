package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereConfig
import org.atmosphere.cpr.DefaultBroadcaster
import org.atmosphere.util.AbstractBroadcasterProxy
import org.icescrum.core.domain.Product

class IceScrumBroadcaster extends AbstractBroadcasterProxy {

    String pkey
    String pname

    IceScrumBroadcaster(String name, URI uri, AtmosphereConfig config) {
        super(name, uri, config)
        initValues()
    }


    IceScrumBroadcaster(String name, AtmosphereConfig config) {
        super(name, new URI("http://localhost:6379"), config)
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

    @Override
    void incomingBroadcast() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    void outgoingBroadcast(Object o) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
