package dev.solaris.loader;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.encode.RateControl;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;
import org.jcodec.common.model.Size;

/**
 * H.264 pictures of the {@code solaris:screen} channel: Constrained-Baseline Annex B, one
 * picture per FRAME, in pure Java (JCodec 0.2.5, FreeBSD licence). The wire is the one the
 * native client (OpenH264) speaks; the server never decodes. Pixels are BT.601 limited range,
 * exactly the conversion OpenH264's helpers use.
 */
public final class ScreenVideo {
    private ScreenVideo() {}

    /** Decoded picture: row-major {@code 0xAARRGGBB}. */
    public record Picture(int width, int height, int[] argb) {}

    /** FRAME codec byte for H.264 Annex B. */
    public static final int CODEC = 1;

    // ---- colour ----------------------------------------------------------------------------

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    // ---- decoder (viewer) ------------------------------------------------------------------

    /** One stream decoder. A failed decode leaves it unusable: drop it and wait for a key frame. */
    public static final class Decoder {
        private final H264Decoder decoder = new H264Decoder();

        /** Decodes one picture of {@code width x height}; null when the data holds no picture yet. */
        public Picture decode(byte[] annexB, int width, int height) {
            if (annexB.length == 0 || annexB.length > ScreenProtocol.MAX_FRAME_BYTES || width < 1
                    || height < 1 || width > ScreenProtocol.MAX_EDGE || height > ScreenProtocol.MAX_EDGE) {
                throw new IllegalArgumentException("shared image size bound");
            }
            int alignedW = (width + 15) & ~15;
            int alignedH = (height + 15) & ~15;
            byte[][] planes = {new byte[alignedW * alignedH],
                    new byte[alignedW * alignedH / 4], new byte[alignedW * alignedH / 4]};
            org.jcodec.common.model.Picture decoded;
            try {
                // The decoder keeps the planes as its reference frame, so each call needs fresh ones.
                decoded = decoder.decodeFrame(ByteBuffer.wrap(annexB), planes);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("H.264 decode failed: " + error, error);
            }
            if (decoded == null) {
                return null;
            }
            if (decoded.getCroppedWidth() != width || decoded.getCroppedHeight() != height) {
                throw new IllegalArgumentException("shared image dimensions disagree with header");
            }
            return toArgb(decoded, width, height);
        }

        private static Picture toArgb(org.jcodec.common.model.Picture decoded, int width, int height) {
            byte[] yPlane = decoded.getPlaneData(0);
            byte[] uPlane = decoded.getPlaneData(1);
            byte[] vPlane = decoded.getPlaneData(2);
            int yStride = decoded.getPlaneWidth(0);
            int cStride = decoded.getPlaneWidth(1);
            Rect crop = decoded.getCrop();
            int x0 = crop == null ? 0 : crop.getX();
            int y0 = crop == null ? 0 : crop.getY();
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // JCodec stores samples minus 128.
                    int luma = (yPlane[(y0 + y) * yStride + x0 + x] + 128) - 16;
                    int ci = ((y0 + y) >> 1) * cStride + ((x0 + x) >> 1);
                    int u = (uPlane[ci] + 128) - 128;
                    int v = (vPlane[ci] + 128) - 128;
                    int scaled = 1192 * luma;
                    int r = clamp((scaled + 1634 * v) >> 10);
                    int g = clamp((scaled - 401 * u - 833 * v) >> 10);
                    int b = clamp((scaled + 2066 * u) >> 10);
                    argb[y * width + x] = 0xFF000000 | r << 16 | g << 8 | b;
                }
            }
            return new Picture(width, height, argb);
        }
    }

    // ---- encoder (owner) -------------------------------------------------------------------

    /** Fixed quantiser (screen text stays legible), with JCodec's per-slice overshoot guard. */
    private static final class ScreenRate implements RateControl {
        private static final int KEY_QP = 22;
        private static final int DELTA_QP = 24;
        private int bitsPerMb;
        private int totalQpDelta;
        private boolean justSwitched;

        @Override
        public int accept(int bits) {
            if (bits >= bitsPerMb) {
                totalQpDelta++;
                justSwitched = true;
                return 1;
            }
            if (totalQpDelta > 0 && !justSwitched && bitsPerMb - bits > bitsPerMb >> 3) {
                totalQpDelta--;
                justSwitched = true;
                return -1;
            }
            justSwitched = false;
            return 0;
        }

        @Override
        public int startPicture(Size size, int maxSize, SliceType type) {
            int mbs = ((size.getWidth() + 15) >> 4) * ((size.getHeight() + 15) >> 4);
            bitsPerMb = (maxSize << 3) / mbs;
            totalQpDelta = 0;
            justSwitched = false;
            return type == SliceType.P ? DELTA_QP : KEY_QP;
        }

        @Override
        public int initialQpDelta() {
            return 0;
        }
    }

    /**
     * One owner's picture stream. Scales the capture to at most {@code maximum} px (even sides),
     * sends nothing for a picture identical to the last one sent, and starts a self-contained key
     * frame on request, for a new stream, on a size change and after an oversize restart. Not
     * thread-safe.
     */
    public static final class Encoder {
        /** One picture ready to send: its size, key flag and Annex B bytes. */
        public record Encoded(int width, int height, boolean key, byte[] data) {}

        private static final int MAX_DELTAS = 60_000;
        private H264Encoder h264;
        private int[] previous;
        private int previousWidth;
        private int previousHeight;
        /** Largest side after an oversize picture forced a smaller one; 0 = whatever DEMAND allows. */
        private int limit;
        /** Delta pictures since the last key frame; a long stream restarts before frame_num wraps. */
        private int deltas;

        /**
         * Returns the next picture, or null when it equals the last sent one and no key frame is
         * owed.
         */
        public Encoded encode(int[] argb, int sourceWidth, int sourceHeight, int maximum, boolean keyWanted) {
            if (sourceWidth < 1 || sourceHeight < 1 || sourceWidth > 8192 || sourceHeight > 8192
                    || argb.length != sourceWidth * sourceHeight) {
                throw new IllegalArgumentException("invalid source frame");
            }
            int edge = Math.max(2, Math.min(maximum, ScreenProtocol.MAX_EDGE));
            if (limit != 0) {
                edge = Math.min(edge, limit);
            }
            boolean key = keyWanted;
            while (true) {
                int largest = Math.max(sourceWidth, sourceHeight);
                int width = even(Math.min(sourceWidth, (int) ((long) sourceWidth * edge / largest)));
                int height = even(Math.min(sourceHeight, (int) ((long) sourceHeight * edge / largest)));
                int[] scaled = scale(argb, sourceWidth, sourceHeight, width, height);
                boolean sameSize = previous != null && previousWidth == width && previousHeight == height;
                if (!key && sameSize && Arrays.equals(previous, scaled)) {
                    return null;
                }
                boolean idr = key || h264 == null || !sameSize || deltas >= MAX_DELTAS;
                byte[] data;
                try {
                    data = compress(scaled, width, height, idr);
                } catch (BufferOverflowException | IndexOutOfBoundsException oversize) {
                    data = null;
                }
                if (data == null || data.length > ScreenProtocol.MAX_FRAME_BYTES) {
                    // The encoder already took this picture as its reference: restart smaller.
                    h264 = null;
                    edge = Math.max(width, height) * 3 / 4;
                    if (edge < 16) {
                        throw new IllegalArgumentException("picture does not fit the frame bound");
                    }
                    limit = edge;
                    key = true;
                    continue;
                }
                previous = scaled;
                previousWidth = width;
                previousHeight = height;
                return new Encoded(width, height, idr, data);
            }
        }

        private byte[] compress(int[] scaled, int width, int height, boolean idr) {
            if (idr || h264 == null) {
                h264 = new H264Encoder(new ScreenRate());
                // Sets the SPS frame_num width: 1 << 15 gives the 16-bit maximum the standard allows.
                h264.setKeyInterval(1 << 15);
                deltas = 0;
            }
            ByteBuffer out = ByteBuffer.allocate(ScreenProtocol.MAX_FRAME_BYTES + 1);
            org.jcodec.common.model.Picture picture = yuv(scaled, width, height);
            // encodePFrame passes idr=true for P slices (JCodec then warns on every picture and
            // repeats the parameter sets), so a delta calls doEncodeFrame with its own frame_num.
            ByteBuffer written = idr
                    ? h264.encodeIDRFrame(picture, out)
                    : h264.doEncodeFrame(picture, out, false, deltas + 1, SliceType.P);
            byte[] data = new byte[written.remaining()];
            written.get(data);
            deltas = idr ? 0 : deltas + 1;
            return data;
        }

        private static int even(int value) {
            return Math.max(2, value & ~1);
        }

        private static int[] scale(int[] source, int sw, int sh, int width, int height) {
            if (sw == width && sh == height) {
                return source.clone();
            }
            int[] out = new int[width * height];
            for (int y = 0; y < height; y++) {
                int sy = (int) ((long) y * sh / height);
                for (int x = 0; x < width; x++) {
                    out[y * width + x] = source[sy * sw + (int) ((long) x * sw / width)];
                }
            }
            return out;
        }

        /** Macroblock-aligned picture (edge replicated) cropped to the real size. */
        private static org.jcodec.common.model.Picture yuv(int[] argb, int width, int height) {
            int alignedW = (width + 15) & ~15;
            int alignedH = (height + 15) & ~15;
            byte[] yPlane = new byte[alignedW * alignedH];
            byte[] uPlane = new byte[alignedW * alignedH / 4];
            byte[] vPlane = new byte[alignedW * alignedH / 4];
            for (int y = 0; y < alignedH; y++) {
                int sy = Math.min(y, height - 1);
                for (int x = 0; x < alignedW; x++) {
                    int pixel = argb[sy * width + Math.min(x, width - 1)];
                    int r = pixel >> 16 & 0xFF;
                    int g = pixel >> 8 & 0xFF;
                    int b = pixel & 0xFF;
                    int luma = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                    yPlane[y * alignedW + x] = (byte) (luma - 128);
                }
            }
            int chromaW = alignedW / 2;
            for (int cy = 0; cy < alignedH / 2; cy++) {
                for (int cx = 0; cx < chromaW; cx++) {
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    for (int dy = 0; dy < 2; dy++) {
                        int sy = Math.min(cy * 2 + dy, height - 1);
                        for (int dx = 0; dx < 2; dx++) {
                            int pixel = argb[sy * width + Math.min(cx * 2 + dx, width - 1)];
                            r += pixel >> 16 & 0xFF;
                            g += pixel >> 8 & 0xFF;
                            b += pixel & 0xFF;
                        }
                    }
                    r = (r + 2) >> 2;
                    g = (g + 2) >> 2;
                    b = (b + 2) >> 2;
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    uPlane[cy * chromaW + cx] = (byte) (clamp(u) - 128);
                    vPlane[cy * chromaW + cx] = (byte) (clamp(v) - 128);
                }
            }
            return new org.jcodec.common.model.Picture(alignedW, alignedH,
                    new byte[][] {yPlane, uPlane, vPlane, null}, null, ColorSpace.YUV420J, 0,
                    new Rect(0, 0, width, height));
        }
    }

    /** Annex B bytes of an encoded picture split into FRAME_PART messages. */
    public static List<byte[]> parts(ScreenProtocol.Id id, long sequence, Encoder.Encoded picture) {
        return ScreenProtocol.frameParts(id, sequence, picture.width(), picture.height(), picture.key(),
                picture.data());
    }
}
