eventCompileEnd = {

    if (!isPluginProject) {
        def classLoader = Thread.currentThread().contextClassLoader
        classLoader.addURL(new File(classesDirPath).toURL())
        def clazz = classLoader.loadClass('org.icescrum.ConfigurationHelper')
        // Create the atmosphere.xml file
        clazz.newInstance().buildConfiguration(basedir)
    }

}
