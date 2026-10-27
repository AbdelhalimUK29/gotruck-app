package com.project.goTruck.media.processing;

import com.luciad.imageio.webp.WebPWriteParam;
import com.project.goTruck.config.MediaLogoProperties;
import com.project.goTruck.exception.ImageProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes, resizes and re-encodes a logo into the canonical output
 * format (WebP).
 *
 * Behaviour:
 *  - aspect ratio is always preserved;
 *  - images are only ever downscaled, never upscaled;
 *  - re-encoding to WebP drops the original metadata (EXIF etc.);
 *  - the original extension/format is irrelevant — output is WebP.
 *
 * A Spring bean because it carries configuration (limits/quality) and
 * is a genuine processing capability, unlike the stateless
 * ImageValidation utility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingService {

    private final MediaLogoProperties properties;

    /**
     * Processes raw image bytes into an optimized WebP image.
     *
     * @param data raw original bytes (already validated at upload)
     * @return WebP-encoded bytes
     * @throws ImageProcessingException when the image cannot be
     *                                  decoded or encoded
     */
    public byte[] processToWebp(byte[] data) {
        BufferedImage source = decode(data);
        BufferedImage resized = resizeIfNeeded(source);
        return encodeWebp(resized);
    }

    private BufferedImage decode(byte[] data) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new ImageProcessingException(
                        "The image could not be decoded.");
            }
            return image;
        } catch (IOException e) {
            throw new ImageProcessingException(
                    "The image could not be decoded.", e);
        }
    }

    /**
     * Downscales to fit within the configured maximum dimensions,
     * preserving aspect ratio. Images already within the limits are
     * returned unchanged (never upscaled).
     */
    private BufferedImage resizeIfNeeded(BufferedImage source) {
        int maxWidth = properties.getMaxWidth();
        int maxHeight = properties.getMaxHeight();

        int width = source.getWidth();
        int height = source.getHeight();

        if (width <= maxWidth && height <= maxHeight) {
            return source;
        }

        double scale = Math.min(
                (double) maxWidth / width,
                (double) maxHeight / height);

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage target = new BufferedImage(
                targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        log.debug("Resized logo from {}x{} to {}x{}",
                width, height, targetWidth, targetHeight);

        return target;
    }

    private byte[] encodeWebp(BufferedImage image) {
        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new ImageProcessingException(
                    "No WebP encoder is available.");
        }
        ImageWriter writer = writers.next();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream imageOut =
                     ImageIO.createImageOutputStream(out)) {

            writer.setOutput(imageOut);

            WebPWriteParam writeParam =
                    new WebPWriteParam(writer.getLocale());
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionType(
                    writeParam.getCompressionTypes()[0]);
            writeParam.setCompressionQuality(properties.getQuality());

            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOut.flush();

            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageProcessingException(
                    "The image could not be encoded to WebP.", e);
        } finally {
            writer.dispose();
        }
    }
}
