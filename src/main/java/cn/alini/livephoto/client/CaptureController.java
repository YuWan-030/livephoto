package cn.alini.livephoto.client;

import cn.alini.livephoto.core.ConfigState;
import cn.alini.livephoto.ffmpeg.FfmpegInvoker;
import cn.alini.livephoto.ffmpeg.FfmpegManager;
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
                event.setResultMessage(Component.literal("LivePhoto: 正在下载 ffmpeg，请稍后再试"));
                return;
            }
            Path ffmpeg = futureFfmpeg.get();
            if (!Files.exists(ffmpeg) || !Files.isExecutable(ffmpeg)) {
                event.setResultMessage(Component.literal("LivePhoto: ffmpeg 不可用"));
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

                    // 规范化帧
                    int idx = 0;
                    if (res.frames() != null && !res.frames().isEmpty()) {
                        for (Path p : res.frames()) {
                            Path dst = tempDir.resolve(String.format("f_%05d.png", idx++));
                            Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    if (idx == 0) { // 没帧则用截图填一帧
                        Path first = tempDir.resolve("f_00000.png");
                        Files.copy(targetPng, first, StandardCopyOption.REPLACE_EXISTING);
                        idx = 1;
                    }
                    // 追加当前截图为最后一帧
                    Path last = tempDir.resolve(String.format("f_%05d.png", idx));
                    Files.copy(targetPng, last, StandardCopyOption.REPLACE_EXISTING);
                    int totalFrames = idx + 1;

                    double durSec = Math.max(0.033, res.durationSeconds());
                    float realFps = Math.max(1f, (float) totalFrames / (float) durSec);

                    // 编码中间 MP4 + 提取封面
                    FfmpegInvoker.createVideoFromDir(ffmpeg, tempDir, videoMp4, realFps);
                    FfmpegInvoker.extractCover(ffmpeg, videoMp4, coverJpg);

                    // 生成最终文件
                    Path motionJpg = screenshotsDir.resolve("Android_"+baseName + "_motion_live.jpg");
                    Path liveMov = screenshotsDir.resolve("iOS_"+baseName + "_live.mov");
                    Path liveJpg = screenshotsDir.resolve("iOS_"+baseName + "_live.jpg");

                    switch (cfg.protocol) {
                        case MOTION -> {
                            MotionPhotoPlaceholder.build(coverJpg, videoMp4, motionJpg);
                            // 清理中间件
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
                } catch (Exception e) {
                    e.printStackTrace();
                    try { Files.deleteIfExists(videoMp4); } catch (Exception ignore) {}
                } finally {
                    // 清理临时帧和目录
                    try {
                        if (res.frames() != null) for (Path p : res.frames()) Files.deleteIfExists(p);
                        if (res.tempDir() != null) {
                            try (var s = Files.list(res.tempDir())) { s.forEach(f -> { try { Files.deleteIfExists(f); } catch (Exception ignored) {} }); }
                            Files.deleteIfExists(res.tempDir());
                        }
                    } catch (Exception ignore) {}
                }
            }, EXEC);

            event.setResultMessage(Component.literal("LivePhoto: 成功拍摄 " + cfg.durationSeconds + "s 的实况视频(稍后生成文件)"));
        } catch (Exception e) {
            event.setResultMessage(Component.literal("LivePhoto error: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx == -1 ? name : name.substring(0, idx);
    }
}