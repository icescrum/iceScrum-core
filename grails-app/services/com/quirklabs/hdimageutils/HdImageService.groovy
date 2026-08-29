package com.quirklabs.hdimageutils

import javax.imageio.ImageIO
import javax.imageio.stream.ImageInputStream
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Minimal reimplementation of the dead 'hd-image-utils' Grails 2 plugin service
 * (com.quirklabs.hdimageutils.HdImageService), whose source is no longer available.
 *
 * Only the API used by iceScrum is provided:
 *  - byte[] scale(String pathToImage, int width, int height)
 *  - byte[] scale(InputStream inputStream, int width, int height)
 *  - byte[] scale(byte[] imageBytes, int width, int height)
 *
 * Images are scaled down with high quality (progressive bilinear downscaling and
 * a bicubic final pass), preserving the aspect ratio so that the result fits
 * within the requested width x height box. Images are never upscaled.
 */
class HdImageService {

    static transactional = false

    byte[] scale(String pathToImage, int width, int height) {
        File file = new File(pathToImage)
        return scale(file.bytes, width, height)
    }

    byte[] scale(InputStream inputStream, int width, int height) {
        try {
            return scale(inputStream.bytes, width, height)
        } finally {
            try { inputStream.close() } catch (IOException ignored) {}
        }
    }

    byte[] scale(byte[] imageBytes, int width, int height) {
        String format = detectFormat(imageBytes) ?: 'jpg'
        if (format == 'gif') {
            // The original plugin converted GIF input to JPEG output
            // (see the gif -> jpg thumbnail extension swap in IcescrumCoreGrailsPlugin)
            format = 'jpg'
        }
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes))
        if (source == null) {
            throw new IOException("Unable to read image data (unsupported or corrupted image)")
        }
        BufferedImage scaled = scaleImage(source, width, height, format)
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        if (!ImageIO.write(scaled, format, out)) {
            // No writer for the source format (e.g. exotic formats), fall back to png to stay lossless
            out.reset()
            ImageIO.write(scaled, 'png', out)
        }
        return out.toByteArray()
    }

    private BufferedImage scaleImage(BufferedImage source, int maxWidth, int maxHeight, String format) {
        int sourceWidth = source.width
        int sourceHeight = source.height

        // Compute target size, preserving aspect ratio, never upscaling
        double ratio = Math.min(maxWidth / (double) sourceWidth, maxHeight / (double) sourceHeight)
        int targetWidth = Math.max(1, Math.min(sourceWidth, (int) Math.round(sourceWidth * ratio)))
        int targetHeight = Math.max(1, Math.min(sourceHeight, (int) Math.round(sourceHeight * ratio)))

        boolean opaque = format in ['jpg', 'jpeg', 'bmp']
        int imageType = opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB

        BufferedImage current = source
        int currentWidth = sourceWidth
        int currentHeight = sourceHeight

        // Progressive bilinear downscaling: halve the size until close to the target,
        // which gives much better quality than a single-step scaling
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            currentWidth = Math.max(targetWidth, currentWidth.intdiv(2))
            currentHeight = Math.max(targetHeight, currentHeight.intdiv(2))
            current = drawScaled(current, currentWidth, currentHeight, imageType, opaque, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        }

        // Final high-quality bicubic pass to the exact target size
        if (currentWidth != targetWidth || currentHeight != targetHeight || current == source) {
            current = drawScaled(current, targetWidth, targetHeight, imageType, opaque, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        }

        return current
    }

    private BufferedImage drawScaled(BufferedImage source, int width, int height, int imageType, boolean opaque, Object interpolation) {
        BufferedImage result = new BufferedImage(width, height, imageType)
        Graphics2D g = result.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (opaque) {
                // Avoid black background when writing images with alpha channel to JPEG
                g.setColor(Color.WHITE)
                g.fillRect(0, 0, width, height)
            }
            g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return result
    }

    private String detectFormat(byte[] imageBytes) {
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))
        try {
            def readers = ImageIO.getImageReaders(imageInputStream)
            if (readers.hasNext()) {
                String formatName = readers.next().formatName?.toLowerCase()
                return formatName == 'jpeg' ? 'jpg' : formatName
            }
        } finally {
            imageInputStream?.close()
        }
        return null
    }
}
