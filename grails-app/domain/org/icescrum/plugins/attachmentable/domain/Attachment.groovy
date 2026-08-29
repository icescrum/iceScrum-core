package org.icescrum.plugins.attachmentable.domain

class Attachment implements Serializable {

    // File
    String name
    String ext
    String contentType
    Long length
    Date dateCreated

    // For generic cloud storage
    String url
    String provider

    // Poster
    String posterClass
    Long posterId

    // Input name
    String inputName

    static transients = ['previewable']

    static constraints = {
        name nullable: false, blank: false
        ext nullable: true, blank: true
        inputName nullable: false, blank: false
        contentType nullable: true, blank: true
        length min: 0L
        posterClass blank: false
        posterId min: 0L
        url(maxSize: 1000, nullable: true)
        provider nullable: true
    }

    static mapping = {
        cache true
        url length: 1000
        table 'attachmentable_attachment'
    }

    String toString() {
        return filename
    }

    def getFilename() {
        return ext ? "$name.$ext" : "$name"
    }

    def getPreviewable() {
        return this.contentType?.contains('image/') && this.url == null
    }

    def getPoster() {
        // Handle proxied class names
        def i = posterClass.indexOf('_$$_javassist')
        if (i > -1) {
            posterClass = posterClass[0..i - 1]
        }
        return getClass().classLoader.loadClass(posterClass).get(posterId)
    }

    def xml(builder) {
        builder.attachment(id: this.id) {
            builder.ext(this.ext ?: '')
            builder.length(this.length)
            builder.posterId(this.posterId)
            builder.posterClass(this.posterClass)
            builder.dateCreated(this.dateCreated)
            builder.url { builder.mkp.yieldUnescaped("<![CDATA[${this.url ?: ''}]]>") }
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.provider { builder.mkp.yieldUnescaped("<![CDATA[${this.provider ?: ''}]]>") }
            builder.inputName { builder.mkp.yieldUnescaped("<![CDATA[${this.inputName}]]>") }
            builder.contentType { builder.mkp.yieldUnescaped("<![CDATA[${this.contentType ?: ''}]]>") }
        }
    }
}
