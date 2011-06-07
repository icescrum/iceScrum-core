package org.icescrum

import grails.util.GrailsUtil

class ConfigurationHolder {

    static ConfigObject getConfig() {
        GroovyClassLoader classLoader = new GroovyClassLoader(ConfigurationHolder.classLoader)

        def slurper = new ConfigSlurper(GrailsUtil.environment)
        ConfigObject config = null
        try {
            config = slurper.parse(classLoader.loadClass('IceScrumCoreConfig'))
        }
        catch (e) {
        }
        config
    }
}

