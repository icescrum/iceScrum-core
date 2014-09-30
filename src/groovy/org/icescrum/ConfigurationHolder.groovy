package org.icescrum

import grails.util.GrailsUtil
import grails.util.Holders

class ConfigurationHolder {

    static ConfigObject getConfig() {
        GroovyClassLoader classLoader = new GroovyClassLoader(Holders.classLoader)

        def slurper = new ConfigSlurper(GrailsUtil.environment)
        ConfigObject config = null
        try {
            config = slurper.parse(classLoader.loadClass('DefaultIceScrumCoreConfig'))
        }
        catch (e) {
        }
        config
    }
}

