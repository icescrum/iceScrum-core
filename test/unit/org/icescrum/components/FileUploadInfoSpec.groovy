package org.icescrum.components

import spock.lang.Specification

class FileUploadInfoSpec extends Specification {

    def "well-formed upload metadata is valid"() {
        expect:
        new FileUploadInfo(validProperties()).valid()
    }

    def "non-positive upload dimensions are rejected"() {
        given:
        def properties = validProperties()
        properties[property] = value

        expect:
        !new FileUploadInfo(properties).valid()

        where:
        property      | value
        'chunkSize'   | 0
        'totalSize'   | 0
        'totalChunks' | 0
    }

    def "blank upload identifiers and names are rejected"() {
        given:
        def properties = validProperties()
        properties[property] = ' '

        expect:
        !new FileUploadInfo(properties).valid()

        where:
        property << ['identifier', 'storageKey', 'filename', 'relativePath']
    }

    def "completed upload is finalized only once"() {
        given:
        File temporaryFile = File.createTempFile('icescrum-upload-test-', '.temp')
        def info = new FileUploadInfo(validProperties() + [filePath: temporaryFile.absolutePath])
        byte[] content = 'content'.getBytes('UTF-8')
        info.chunkSize = content.length
        info.totalSize = content.length

        when:
        boolean firstCompletion = info.storeChunk(1, content)
        boolean duplicateCompletion = info.storeChunk(1, content)

        then:
        firstCompletion
        !duplicateCompletion
        info.completed
        !info.filePath.endsWith('.temp')
        new File(info.filePath).getText('UTF-8') == 'content'

        cleanup:
        temporaryFile.delete()
        new File(info.filePath).delete()
    }

    private static Map validProperties() {
        return [
                chunkSize: 1024,
                totalSize: 1024L,
                totalChunks: 1,
                identifier: 'upload-id',
                storageKey: 'session:endpoint:upload-id',
                filename: 'document.txt',
                relativePath: 'document.txt'
        ]
    }
}
