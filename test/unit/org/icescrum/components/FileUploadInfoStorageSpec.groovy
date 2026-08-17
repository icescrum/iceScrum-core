package org.icescrum.components

import spock.lang.Specification

class FileUploadInfoStorageSpec extends Specification {

    def "storage assigns a server-generated temporary path"() {
        given:
        def properties = uploadProperties()
        FileUploadInfo info

        when:
        info = FileUploadInfoStorage.instance.get(properties)

        then:
        File file = new File(info.filePath)
        file.exists()
        file.parentFile.canonicalFile == new File(System.getProperty('java.io.tmpdir')).canonicalFile
        file.name.startsWith('icescrum-upload-')
        !file.name.contains(properties.filename)

        cleanup:
        if (info) {
            FileUploadInfoStorage.instance.remove(info)
        }
    }

    def "storage keeps immutable metadata stable between chunks"() {
        given:
        def properties = uploadProperties()
        def info = FileUploadInfoStorage.instance.get(properties)
        def changedProperties = new LinkedHashMap(properties)
        changedProperties.totalSize = properties.totalSize + 1

        when:
        FileUploadInfoStorage.instance.get(changedProperties)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        FileUploadInfoStorage.instance.remove(info)
    }

    def "removing an upload deletes its temporary file and state"() {
        given:
        def properties = uploadProperties()
        def first = FileUploadInfoStorage.instance.get(properties)
        File firstFile = new File(first.filePath)

        when:
        FileUploadInfoStorage.instance.remove(first)
        def second = FileUploadInfoStorage.instance.get(properties)

        then:
        !firstFile.exists()
        !second.is(first)

        cleanup:
        FileUploadInfoStorage.instance.remove(second)
    }

    private static Map uploadProperties() {
        String identifier = UUID.randomUUID().toString()
        return [
                chunkSize: 1024,
                totalSize: 1024L,
                totalChunks: 1,
                identifier: identifier,
                storageKey: "session:endpoint:$identifier",
                filename: 'document.txt',
                relativePath: 'document.txt'
        ]
    }
}
