package cn.alini.livephoto.client;

import cn.alini.livephoto.core.ConfigState;
import cn.alini.livephoto.ffmpeg.FfmpegInvoker;
import cn.alini.livephoto.ffmpeg.FfmpegManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenshotEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureController {
    private static final CaptureController INSTANCE = new CaptureController();
    public static CaptureController get() { return INSTANCE; }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "livephoto-ffmpeg-work");
        t.setDaemon(true);
        return t;
    });

    public void onScreenshot(ScreenshotEvent event) {
        ConfigState cfg = ConfigState.get();
        if (!cfg.enabled) return;

        event.setCanceled(true);

        try {
            Path targetPng = event.getScreenshotFile().toPath();
            Path screenshotsDir = targetPng.getParent();
            Files.createDirectories(screenshotsDir);

            if (cfg.keepVanillaPng && event.getImage() != null) {
                event.getImage().writeToFile(targetPng);
            }

            CompletableFuture<Path> futureFfmpeg = FfmpegManager.ensureFfmpegReadyAsync();
            if (!futureFfmpeg.isDone()) {
                event.setResultMessage(Component.translatable("livephoto.msg.downloading_ffmpeg"));
                return;
            }
            Path ffmpeg = futureFfmpeg.get();
            if (!Files.exists(ffmpeg) || !Files.isExecutable(ffmpeg)) {
                event.setResultMessage(Component.translatable("livephoto.msg.ffmpeg_unavailable"));
                return;
            }

            CompletableFuture<FrameBuffer.SessionResult> framesFuture = FrameBuffer.get()
                    .startSession(cfg.durationSeconds, cfg.fps, cfg.resolutionScale);

            String baseName = stripExtension(targetPng.getFileName().toString());
            Path videoMp4 = screenshotsDir.resolve(baseName + ".mp4");          // 中间文件
            Path coverJpg = screenshotsDir.resolve(baseName + "_cover.jpg");    // 中间文件

            String liveId = UUID.randomUUID().toString();

            framesFuture.whenCompleteAsync((res, ex) -> {
                if (ex != null || res == null) return;
                Path tempDir = res.tempDir();
                try {
                    if (tempDir == null) tempDir = Files.createTempDirectory("livephoto-frames-");

                    int idx = 0;
                    if (res.frames() != null && !res.frames().isEmpty()) {
                        for (Path p : res.frames()) {
                            Path dst = tempDir.resolve(String.format("f_%05d.png", idx++));
                            Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    if (idx == 0) { // 没帧则用截图填一帧（若存在）
                        Path first = tempDir.resolve("f_00000.png");
                        if (Files.exists(targetPng)) {
                            Files.copy(targetPng, first, StandardCopyOption.REPLACE_EXISTING);
                            idx = 1;
                        }
                    }

                    Path sourceForLast = null;
                    if (Files.exists(targetPng)) {
                        sourceForLast = targetPng;
                    } else if (res.frames() != null && !res.frames().isEmpty() && Files.exists(res.frames().get(0))) {
                        sourceForLast = res.frames().get(0);
                    }
                    if (sourceForLast == null) {
                        return; // 没有可用源帧
                    }

                    Path last = tempDir.resolve(String.format("f_%05d.png", idx));
                    Files.copy(sourceForLast, last, StandardCopyOption.REPLACE_EXISTING);
                    int totalFrames = idx + 1;

                    double durSec = Math.max(0.033, res.durationSeconds());
                    float realFps = Math.max(1f, (float) totalFrames / (float) durSec);

                    String audioDevice = (cfg.audio && cfg.audioDevice != null && !cfg.audioDevice.isBlank())
                            ? cfg.audioDevice : null;
                    Path audioFile = null; // 已取消用户自定义音频文件输入

                    FfmpegInvoker.createVideoFromDir(ffmpeg, tempDir, videoMp4, realFps, audioDevice, audioFile);
                    FfmpegInvoker.extractCover(ffmpeg, videoMp4, coverJpg);

                    Path motionJpg = screenshotsDir.resolve("Android_" + baseName + "_motion_live.jpg");
                    Path liveMov = screenshotsDir.resolve("iOS_" + baseName + "_live.mov");
                    Path liveJpg = screenshotsDir.resolve("iOS_" + baseName + "_live.jpg");

                    switch (cfg.protocol) {
                        case MOTION -> {
                            MotionPhotoPlaceholder.build(coverJpg, videoMp4, motionJpg);
                            Files.deleteIfExists(videoMp4);
                            Files.deleteIfExists(coverJpg);
                        }
                        case LIVE -> {
                            LivePhotoPlaceholder.build(coverJpg, videoMp4, liveJpg, liveMov, liveId);
                            Files.deleteIfExists(videoMp4);
                            Files.deleteIfExists(coverJpg);
                        }
                        case BOTH -> {
                            MotionPhotoPlaceholder.build(coverJpg, videoMp4, motionJpg);
                            LivePhotoPlaceholder.build(coverJpg, videoMp4, liveJpg, liveMov, liveId);
                            Files.deleteIfExists(videoMp4);
                            Files.deleteIfExists(coverJpg);
                        }
                    }

                    // 成功提示（屏幕中间下方），在主线程调用
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().gui.setOverlayMessage(
                                    Component.translatable("livephoto.msg.completed", baseName, screenshotsDir.toString()),
                                    false));

                } catch (Exception e) {
                    e.printStackTrace();
                    try { Files.deleteIfExists(videoMp4); } catch (Exception ignore) {}
                } finally {
                    try {
                        if (res.frames() != null) for (Path p : res.frames()) Files.deleteIfExists(p);
                        if (res.tempDir() != null) {
                            try (var s = Files.list(res.tempDir())) { s.forEach(f -> { try { Files.deleteIfExists(f); } catch (Exception ignored) {} }); }
                            Files.deleteIfExists(res.tempDir());
                        }
                    } catch (Exception ignore) {}
                }
            }, EXEC);

            event.setResultMessage(Component.translatable("livephoto.msg.recording", cfg.durationSeconds));
        } catch (Exception e) {
            event.setResultMessage(Component.translatable("livephoto.msg.error", e.getMessage()));
            e.printStackTrace();
        }
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx == -1 ? name : name.substring(0, idx);
    }
}