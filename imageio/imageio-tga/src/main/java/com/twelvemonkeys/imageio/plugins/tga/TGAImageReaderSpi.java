package com.twelvemonkeys.imageio.plugins.tga;

import com.twelvemonkeys.imageio.spi.ProviderInfo;
import com.twelvemonkeys.imageio.util.IIOUtil;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Locale;

public final class TGAImageReaderSpi extends ImageReaderSpi {

    /**
     * Creates a {@code TGAImageReaderSpi}.
     */
    public TGAImageReaderSpi() {
        this(IIOUtil.getProviderInfo(TGAImageReaderSpi.class));
    }

    private TGAImageReaderSpi(final ProviderInfo providerInfo) {
        super(
                providerInfo.getVendorName(),
                providerInfo.getVersion(),
                new String[]{
                        "tga", "TGA",
                        "targa", "TARGA"
                },
                new String[]{"tga", "tpic"},
                new String[]{
                        // No official IANA record exists
                        "image/tga", "image/x-tga",
                        "image/targa", "image/x-targa",
                },
                "com.twelvemkonkeys.imageio.plugins.tga.TGAImageReader",
                new Class[] {ImageInputStream.class},
                null,
                true, // supports standard stream metadata
                null, null, // native stream format name and class
                null, null, // extra stream formats
                true, // supports standard image metadata
                null, null,
                null, null // extra image metadata formats
        );
    }

    @Override public boolean canDecodeInput(final Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }

        ImageInputStream stream = (ImageInputStream) source;

        stream.mark();
        ByteOrder originalByteOrder = stream.getByteOrder();

        try {
            stream.setByteOrder(ByteOrder.LITTLE_ENDIAN);

            // NOTE: The TGA format does not have a magic identifier, so this is guesswork...
            // We'll try to match sane values, and hope no other files contains the same sequence.

            stream.readUnsignedByte();

            int colorMapType = stream.readUnsignedByte();
            switch (colorMapType) {
                case TGA.COLORMAP_NONE:
                case TGA.COLORMAP_PALETTE:
                    break;
                default:
                    return false;
            }

            int imageType = stream.readUnsignedByte();
            switch (imageType) {
                case TGA.IMAGETYPE_NONE:
                case TGA.IMAGETYPE_COLORMAPPED:
                case TGA.IMAGETYPE_TRUECOLOR:
                case TGA.IMAGETYPE_MONOCHROME:
                case TGA.IMAGETYPE_COLORMAPPED_RLE:
                case TGA.IMAGETYPE_TRUECOLOR_RLE:
                case TGA.IMAGETYPE_MONOCHROME_RLE:
                    break;
                default:
                    return false;
            }

            int colorMapStart = stream.readUnsignedShort();
            int colorMapSize = stream.readUnsignedShort();
            int colorMapDetph = stream.readUnsignedByte();

            if (colorMapSize == 0) {
                // No color map, all 3 fields should be 0
                if (colorMapStart!= 0 || colorMapDetph != 0) {
                    return false;
                }
            }
            else {
                if (colorMapType == TGA.COLORMAP_NONE) {
                    return false;
                }
                if (colorMapSize < 2) {
                    return false;
                }
                if (colorMapStart >= colorMapSize) {
                    return false;
                }
                if (colorMapDetph != 15 && colorMapDetph != 16 && colorMapDetph != 24 && colorMapDetph != 32) {
                    return false;
                }
            }

            // Skip x, y, w, h as these can be anything
            stream.readShort();
            stream.readShort();
            stream.readShort();
            stream.readShort();

            // Verify sane pixel depth
            int depth = stream.readUnsignedByte();
            switch (depth) {
                case 1:
                case 2:
                case 4:
                case 8:
                case 16:
                case 24:
                case 32:
                    break;
                default:
                    return false;
            }

            // We're pretty sure by now, but there can still be false positives...
            // For 2.0 format, we could skip to end, and read "TRUEVISION-XFILE.\0" but it would be too slow
            return true;
        }
        finally {
            stream.reset();
            stream.setByteOrder(originalByteOrder);
        }
    }

    @Override public ImageReader createReaderInstance(final Object extension) throws IOException {
        return new TGAImageReader(this);
    }

    @Override public String getDescription(final Locale locale) {
        return "TrueVision TGA image reader";
    }
}
