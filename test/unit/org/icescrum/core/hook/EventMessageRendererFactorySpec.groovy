package org.icescrum.core.hook

import spock.lang.Specification

class EventMessageRendererFactorySpec extends Specification {

    def "missing renderer name uses the default implementation"() {
        expect:
        EventMessageRendererFactory.create(null) instanceof DefaultEventMessageRenderer
        EventMessageRendererFactory.create('') instanceof DefaultEventMessageRenderer
    }

    def "configured renderer must implement the renderer contract"() {
        when:
        EventMessageRendererFactory.create(String.name)

        then:
        thrown(IllegalArgumentException)
    }

    def "unknown renderer class is rejected"() {
        when:
        EventMessageRendererFactory.create('example.missing.Renderer')

        then:
        thrown(ClassNotFoundException)
    }
}
