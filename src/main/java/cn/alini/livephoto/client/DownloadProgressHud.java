package cn.alini.livephoto.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 粉红色“原版经验条”风格的下载/解压进度 HUD。
 * - 仅在下载/解压进行时显示。
 * - 确定进度：左→右填充，文本显示百分比。
 * - 不确定进度：粉色滑块往返移动。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DownloadProgressHud {
    private static volatile boolean active = false;
    private static volatile float progress = 0f; // 0..1
    private static volatile String message = I18n.get("livephoto.hud.downloading");
    private static volatile boolean indeterminate = false;

    // 颜色（粉红主题）
    private static final int BG_COLOR     = 0x80101020; // 半透明深色底
    private static final int BORDER_COLOR = 0xFFFFFFFF; // 细白边
    private static final int FILL_START   = 0xFFFF99DD; // 渐变起始（浅粉）
    private static final int FILL_END     = 0xFFFF66CC; // 渐变结束（亮粉）

    public static void begin(String msg) {
        message = msg != null ? msg : I18n.get("livephoto.hud.downloading");
        progress = 0f;
        indeterminate = false;
        active = true;
    }

    public static void update(float p, String msg) {
        active = true;
        indeterminate = false;
        progress = Math.max(0f, Math.min(1f, p));
        if (msg != null) message = msg;
    }

    public static void indeterminate(String msg) {
        active = true;
        indeterminate = true;
        if (msg != null) message = msg;
    }

    public static void end() {
        active = false;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!active) return;
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();

        int sw = mc.getWindow().getGuiScaledWidth();
        int barW = 182;
        int barH = 5;
        int x = (sw - barW) / 2;
        int y = 15;

        g.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, BORDER_COLOR);
        g.fill(x, y, x + barW, y + barH, BG_COLOR);

        if (indeterminate) {
            int segW = barW / 4;
            long t = System.currentTimeMillis() / 6;
            int start = (int) (t % (barW - segW));
            drawGradient(g, x + start, y, segW, barH, FILL_START, FILL_END);
        } else {
            int fill = (int) (barW * progress);
            if (fill > 0) drawGradient(g, x, y, fill, barH, FILL_START, FILL_END);
        }

        String text = message;
        if (!indeterminate) {
            int pct = Math.round(progress * 100f);
            text = text + " (" + pct + "%)";
        }
        int tw = mc.font.width(text);
        g.drawString(mc.font, text, x + (barW - tw) / 2, y - 10, 0xFFFFFF, false);
    }

    private static void drawGradient(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        g.fillGradient(x, y, x + w, y + h, c1, c2);
    }
}