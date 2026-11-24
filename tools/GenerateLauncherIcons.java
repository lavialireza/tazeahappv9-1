import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.imageio.ImageIO;

/**
 * Creates Android launcher icon resources from custom_icons/*.png.
 * Uses only the Java standard library (Java 17+); no third-party packages.
 */
public class GenerateLauncherIcons {
    private static final int[][] LEGACY = {
        {48, 48}, {72, 72}, {96, 96}, {144, 144}, {192, 192}
    };
    private static final String[] DENSITIES = {
        "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
    };
    private static final int[] FOREGROUND_SIZES = {108, 162, 216, 324, 432};
    private static final String[] VARIANTS = {"admin", "viewer", "adminQa", "viewerQa"};
    private static final String[] INPUTS = {"admin.png", "viewer.png", "admin_test.png", "viewer_test.png"};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: GenerateLauncherIcons.java <custom_icons_dir> <output_dir>");
        }
        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        if (Files.exists(outputDir)) {
            try (var paths = Files.walk(outputDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
            }
        }
        Files.createDirectories(outputDir);
        int generated = 0;
        for (int i = 0; i < VARIANTS.length; i++) {
            Path input = inputDir.resolve(INPUTS[i]);
            if (!Files.isRegularFile(input)) {
                System.out.println("No custom icon for " + VARIANTS[i] + "; default launcher icon will be used.");
                continue;
            }
            BufferedImage source = ImageIO.read(input.toFile());
            if (source == null) throw new IOException("Not a readable PNG image: " + input);
            if (source.getWidth() < 1 || source.getHeight() < 1) throw new IOException("Empty image: " + input);
            Path res = outputDir.resolve(VARIANTS[i]).resolve("res");
            for (int d = 0; d < DENSITIES.length; d++) {
                String density = DENSITIES[d];
                int size = LEGACY[d][0];
                Path mipmap = res.resolve("mipmap-" + density);
                Files.createDirectories(mipmap);
                BufferedImage legacy = contain(source, size, size, Color.WHITE, true);
                ImageIO.write(legacy, "png", mipmap.resolve("ic_launcher.png").toFile());
                ImageIO.write(legacy, "png", mipmap.resolve("ic_launcher_round.png").toFile());

                int fgSize = FOREGROUND_SIZES[d];
                Path drawable = res.resolve("drawable-" + density);
                Files.createDirectories(drawable);
                BufferedImage foreground = contain(source, fgSize, fgSize, null, false, Math.round(fgSize * 0.68f));
                ImageIO.write(foreground, "png", drawable.resolve("ic_launcher_custom_foreground.png").toFile());
            }
            Path adaptive = res.resolve("mipmap-anydpi-v26");
            Files.createDirectories(adaptive);
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <background android:drawable=\"@color/custom_launcher_background\" />\n" +
                "    <foreground android:drawable=\"@drawable/ic_launcher_custom_foreground\" />\n" +
                "</adaptive-icon>\n";
            Files.writeString(adaptive.resolve("ic_launcher.xml"), xml);
            Files.writeString(adaptive.resolve("ic_launcher_round.xml"), xml);
            Path values = res.resolve("values");
            Files.createDirectories(values);
            Files.writeString(values.resolve("custom_launcher_colors.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources><color name=\"custom_launcher_background\">#FFFFFFFF</color></resources>\n");
            generated++;
            System.out.println("Generated custom launcher resources for " + VARIANTS[i] + " from " + input.getFileName());
        }
        System.out.println("Custom launcher icon generation finished. Custom icons generated: " + generated);
    }

    private static BufferedImage contain(BufferedImage source, int width, int height, Color background, boolean opaque) {
        return contain(source, width, height, background, opaque, Math.min(width, height));
    }

    private static BufferedImage contain(BufferedImage source, int width, int height, Color background, boolean opaque, int maxArtworkSize) {
        int type = opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage output = new BufferedImage(width, height, type);
        Graphics2D g = output.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (background != null) {
                g.setColor(background);
                g.fillRect(0, 0, width, height);
            } else {
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, width, height);
                g.setComposite(AlphaComposite.SrcOver);
            }
            double scale = Math.min((double) maxArtworkSize / source.getWidth(), (double) maxArtworkSize / source.getHeight());
            int drawW = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawH = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (width - drawW) / 2;
            int y = (height - drawH) / 2;
            g.drawImage(source, x, y, drawW, drawH, null);
        } finally {
            g.dispose();
        }
        return output;
    }
}
