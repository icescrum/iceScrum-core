package org.icescrum.components

public class FileUploadInfo {

    int chunkSize
    long totalSize
    int totalChunks
    String identifier
    String storageKey
    String filename
    String relativePath
    HashSet<ChunkNumber> uploadedChunks = new HashSet<ChunkNumber>()
    String filePath
    boolean completed = false

    public static class ChunkNumber {
        int number;

        public ChunkNumber(int number) {
            this.number = number
        }

        @Override
        boolean equals(Object obj) {
            return obj instanceof ChunkNumber ? ((ChunkNumber) obj).number == this.number : false
        }

        @Override
        int hashCode() {
            return number
        }
    }

    boolean valid() {
        return chunkSize > 0 &&
                totalSize > 0 &&
                totalChunks > 0 &&
                identifier?.trim() &&
                storageKey?.trim() &&
                filename?.trim() &&
                relativePath?.trim()
    }

    synchronized boolean hasUploadedChunk(int chunkNumber) {
        return uploadedChunks.contains(new ChunkNumber(chunkNumber))
    }

    synchronized boolean storeChunk(int chunkNumber, byte[] bytes) {
        ChunkNumber number = new ChunkNumber(chunkNumber)
        if (completed || uploadedChunks.contains(number)) {
            return false
        }
        long offset = (chunkNumber - 1L) * chunkSize
        RandomAccessFile raf = new RandomAccessFile(filePath, "rw")
        try {
            raf.seek(offset)
            raf.write(bytes)
        } finally {
            raf.close()
        }
        uploadedChunks.add(number)
        return checkIfUploadFinished()
    }

    public synchronized boolean checkIfUploadFinished() {
        //check if upload finished
        if (!completed && totalChunks == uploadedChunks.size()) {
            //Upload finished, change filename.
            File file = new File(filePath)
            if (file.absolutePath.endsWith(".temp")) {
                String new_path = file.absolutePath.substring(0, file.absolutePath.length() - ".temp".length())
                File completedFile = new File(new_path)
                if (!file.renameTo(completedFile) || !completedFile.exists()) {
                    throw new IOException('Unable to finalize uploaded file')
                }
                filePath = new_path
            }
            completed = true
            return true
        } else {
            return false
        }
    }
}