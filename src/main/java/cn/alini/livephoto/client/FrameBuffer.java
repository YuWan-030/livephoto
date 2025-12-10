package cn.alini.livephoto.client;

import cn.alini.livephoto.core.ConfigState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 一次性采样缓冲：按下截图键后才启动采集。
 * 采集阶段仅抓帧到内存，不写盘；结束后再异步落盘，避免写 PNG 卡死导致低 fps/静态。
 */
public class FrameBuffer {
    public record SessionResult(List<Path> frames, double durationSeconds, Path tempDir) {}

    private static final FrameBuffer INSTANCE = new FrameBuffer();
    public static FrameBuffer get() { return INSTANCE; }

    private final Deque<NativeImage> memFrames = new LinkedList<>();

    private boolean active = false;
    private float scale = 1.0f;
    private long sessionStartNanos = 0;
    private long sessionDurationNanos = 0;
    private CompletableFuture<SessionResult> future;

    // 录制结束后写盘/完成 future 的后台线程
    private static final ExecutorService WRITE_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "livephoto-frame-write");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动一次采样会话，返回包含帧/时长/临时目录的 Future。
     */
    public synchronized CompletableFuture<SessionResult> startSession(float seconds, int fpsRequested, float resolutionScale) {
        if (future != null && !future.isDone()) future.cancel(true);
        clearFrames();

        this.scale = Math.max(0.1f, Math.min(1.0f, resolutionScale));
        this.sessionStartNanos = System.nanoTime();
        this.sessionDurationNanos = (long) (Math.max(0.1f, seconds) * 1_000_000_000L);
        this.active = true;
        this.future = new CompletableFuture<>();
        return this.future;
    }

    /** 在渲染帧事件里调用。 */
    public void onRenderTick() {
        if (!active) return;

        long now = System.nanoTime();
        if (now - sessionStartNanos >= sessionDurationNanos) {
            finishNow();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;

        captureFrame();
    }

    public synchronized CompletableFuture<SessionResult> currentFuture() {
        return future;
    }

    // ============ 内部逻辑 ============

    private void captureFrame() {
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget rt = mc.getMainRenderTarget();
            NativeImage img = Screenshot.takeScreenshot(rt);

            if (scale < 0.999f) {
                int nw = Math.max(1, (int) (img.getWidth() * scale));
                int nh = Math.max(1, (int) (img.getHeight() * scale));
                NativeImage scaled = new NativeImage(nw, nh, false);
                for (int y = 0; y < nh; y++) {
                    int srcY = Math.min(img.getHeight() - 1, (int) (y / scale));
                    for (int x = 0; x < nw; x++) {
                        int srcX = Math.min(img.getWidth() - 1, (int) (x / scale));
                        scaled.setPixelRGBA(x, y, img.getPixelRGBA(srcX, srcY));
                    }
                }
                img.close();
                img = scaled;
            }

            synchronized (this) {
                memFrames.addLast(img);
            }
        } catch (Exception ignore) {
            // 捕获失败忽略，避免影响帧率
        }
    }

    private synchronized void finishNow() {
        if (!active) return;
        active = false;
        double durSec = Math.max(0.001, (System.nanoTime() - sessionStartNanos) / 1_000_000_000.0);

        // 拷贝内存帧到本地列表并清空队列，随后后台写盘完成 future
        List<NativeImage> framesToWrite = new ArrayList<>(memFrames);
        memFrames.clear();

        WRITE_EXEC.submit(() -> {
            Path tempDir = null;
            List<Path> framePaths = new ArrayList<>();
            try {
                tempDir = Files.createTempDirectory("livephoto-frames-");
                int idx = 0;
                for (NativeImage img : framesToWrite) {
                    Path p = tempDir.resolve(String.format("f_%05d.png", idx++));
                    img.writeToFile(p);
                    img.close();
                    framePaths.add(p);
                }
                if (future != null && !future.isDone()) {
                    future.complete(new SessionResult(framePaths, durSec, tempDir));
                }
            } catch (Exception e) {
                if (future != null && !future.isDone()) {
                    future.completeExceptionally(e);
                }
                // 清理已写文件
                for (Path p : framePaths) {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                }
                if (tempDir != null) {
                    try { Files.deleteIfExists(tempDir); } catch (IOException ignored) {}
                }
            }
        });
    }

    private void clearFrames() {
        while (!memFrames.isEmpty()) {
            NativeImage img = memFrames.poll();
            if (img != null) img.close();
        }
    }
}