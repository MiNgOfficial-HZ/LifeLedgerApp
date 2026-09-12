import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** Bauhaus / Braun 风格启动图标：黑底 + 红黄蓝三色扇形 + 白色圆盘 + 黑色¥ */
public class IconGen {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        int S = 512;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 深黑背景
        g.setColor(new Color(0x12, 0x12, 0x14));
        g.fillRoundRect(0, 0, S, S, 100, 100);

        // 三色扇形圆盘（包豪斯原色）
        int cx = S / 2;
        int cy = S / 2 + 8;
        int r = 196;
        g.setColor(new Color(0xE2, 0x3A, 0x2E));
        g.fillArc(cx - r, cy - r, r * 2, r * 2, -90, 120);
        g.setColor(new Color(0xF2, 0xB1, 0x35));
        g.fillArc(cx - r, cy - r, r * 2, r * 2, 30, 120);
        g.setColor(new Color(0x2F, 0x6F, 0xBF));
        g.fillArc(cx - r, cy - r, r * 2, r * 2, 150, 120);

        // 白色细环描边
        g.setColor(new Color(0xFF, 0xFF, 0xFF));
        g.setStroke(new BasicStroke(6f));
        g.drawOval(cx - r - 8, cy - r - 8, (r + 8) * 2, (r + 8) * 2);

        // 白色圆盘中心
        int cr = 86;
        g.setColor(Color.WHITE);
        g.fillOval(cx - cr, cy - cr, cr * 2, cr * 2);

        // 黑色 ¥
        g.setColor(new Color(0x12, 0x12, 0x14));
        Font f = new Font("Microsoft YaHei", Font.BOLD, 128);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String yen = "¥";
        int tw = fm.stringWidth(yen);
        int th = fm.getHeight();
        g.drawString(yen, cx - tw / 2, cy - th / 2 + fm.getAscent());

        // 左下角小方块（包豪斯点缀）
        g.setColor(new Color(0xF2, 0xB1, 0x35));
        g.fillRoundRect(52, S - 156, 104, 104, 24, 24);
        // 右上角小圆点
        g.setColor(new Color(0xE2, 0x3A, 0x2E));
        g.fillOval(S - 156, 52, 104, 104);

        g.dispose();
        File out = new File(args[0]);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("icon written: " + out.getAbsolutePath());
    }
}
