package org.icescrum

import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder

/**
 *
 * @author BGoetzmann (from atmosphere plugin)
 */
class ConfigurationHelper {

    def buildConfiguration(basedir) {
        def config = ConfigurationHolder.config
        // Generate the atmosphere.xml file in META-INF folder?
        def atmosphereDotXmlClosure = config.icescrum.push.handlers.atmosphereDotXml
        if (atmosphereDotXmlClosure) {
            def writer = new FileWriter("$basedir/web-app/META-INF/atmosphere.xml")
            def xh = new MarkupBuilder(writer)
            xh."atmosphere-handlers" {
                atmosphereDotXmlClosure.delegate = xh
                atmosphereDotXmlClosure()
            }
            writer.close()
        }

        // Write the atmosphere-decorators.xml file in WEB-INF
        if (config?.icescrum?.push?.servlet?.urlPattern) {
            def decoratorsDotXml = """\
<decorators>
    <excludes>
        <pattern>${config.icescrum.push.servlet.urlPattern}</pattern>
    </excludes>
</decorators>"""
            new File("$basedir/web-app/WEB-INF/atmosphere-decorators.xml").write decoratorsDotXml
        }

        // Modify if necessary the sitemesh.xml file that is in WEB-INF?
        def file = new File("$basedir/web-app/WEB-INF/sitemesh.xml")
        def doc = new XmlSlurper().parse(file)
        if (!doc.excludes.find { it.@file == '/WEB-INF/atmosphere-decorators.xml' }.size()) {
            doc.appendNode({ excludes(file: '/WEB-INF/atmosphere-decorators.xml') })
            // Save the XML document with pretty print
            def xml = new StreamingMarkupBuilder().bind {
                mkp.yield(doc)
            }
            def node = new XmlParser().parseText(xml.toString())
            file.withWriter {
                new XmlNodePrinter(new PrintWriter(it)).print(node)
            }
        }
    }

}

