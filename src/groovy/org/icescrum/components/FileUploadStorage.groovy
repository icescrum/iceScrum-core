package org.icescrum.components

/**
 * Created by vbarrier on 02/10/2014.
 */
public class FileUploadInfoStorage {

    private static FileUploadInfoStorage sInstance
    private HashMap<String, FileUploadInfo> mMap = new HashMap<String, FileUploadInfo>()

    private FileUploadInfoStorage() {}


    public static synchronized FileUploadInfoStorage getInstance() {
        if (sInstance == null) {
            sInstance = new FileUploadInfoStorage()
        }
        return sInstance
    }


    public synchronized FileUploadInfo get(Map properties) {

        String storageKey = properties.storageKey
        FileUploadInfo info = mMap.get(storageKey)

        if (info == null) {
            info = new FileUploadInfo(properties)
            // The original client filename is metadata only. A server-generated path prevents
            // traversal and avoids collisions between uploads with the same filename.
            info.filePath = File.createTempFile('icescrum-upload-', '.temp').absolutePath
            mMap.put(storageKey, info)
        } else {
            def immutableProperties = ['chunkSize', 'totalSize', 'totalChunks', 'identifier', 'filename', 'relativePath']
            if (immutableProperties.any { property -> info."$property" != properties[property] }) {
                throw new IllegalArgumentException('Upload metadata changed between chunks')
            }
        }
        return info
    }

    public synchronized void remove(FileUploadInfo info) {
        new File(info.filePath).delete()
        mMap.remove(info.storageKey)
    }
}
