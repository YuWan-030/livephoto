package cn.alini.livephoto.ffmpeg;

import cn.alini.livephoto.client.DownloadProgressHud;
import cn.alini.livephoto.core.ConfigState;
import net.minecraft.client.resources.language.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 精简版：直接下载 ffmpeg 可执行文件（不再解压）。
 * 下载到 <runDir>/livephoto/ffmpeg/<os-arch>/ffmpeg(.exe)
 */
public class FfmpegManager {

    private static final String FFMPEG_VERSION = "6.1.1";
    private static final String GH_PROXY = "https://ghproxy.com/";
    private static final long MIN_BYTES = 1_000_000;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "livephoto-ffmpeg-dl");
        t.setDaemon(true);
        return t;
    });
    private static volatile CompletableFuture<Path> downloading;

    /** 异步确保 ffmpeg 可用：若本地已有则立即 completed，不再启动下载线程 */
    public static CompletableFuture<Path> ensureFfmpegReadyAsync() {
        ConfigState cfg = ConfigState.get();
        if (cfg.ffmpegPath != null && !cfg.ffmpegPath.isBlank()) {
            Path p = Path.of(cfg.ffmpegPath);
            if (!Files.isRegularFile(p) || !Files.isExecutable(p)) {
                cfg.ffmpegPath = ""; // 无效则清空
            }
        }
        Optional<Path> local = findLocalExecutable();
        if (local.isPresent()) {
            ConfigState.get().ffmpegPath = local.get().toAbsolutePath().toString();
            return CompletableFuture.completedFuture(local.get());
        }
        if (downloading != null) return downloading;

        downloading = CompletableFuture.supplyAsync(() -> {
            try {
                DownloadProgressHud.begin(I18n.get("livephoto.hud.downloading"));
                Path r = ensureFfmpegReadyBlocking();
                DownloadProgressHud.end();
                return r;
            } catch (IOException e) {
                DownloadProgressHud.end();
                throw new RuntimeException(e);
            }
        }, EXEC).whenComplete((r, ex) -> downloading = null);
        return downloading;
    }

    /** 同步（供后台线程调用） */
    private static Path ensureFfmpegReadyBlocking() throws IOException {
        Optional<Path> local = findLocalExecutable();
        if (local.isPresent()) {
            ConfigState.get().ffmpegPath = local.get().toAbsolutePath().toString();
            return local.get();
        }

        String osArchKey = osKey();
        Path targetDir = Paths.get("livephoto", "ffmpeg", osArchKey);
        Files.createDirectories(targetDir);

        String exeName = isWindows() ? "ffmpeg.exe" : "ffmpeg";
        Path exePath = targetDir.resolve(exeName);

        downloadExecutable(exePath);

        local = findLocalExecutable();
        if (local.isEmpty()) throw new IOException("ffmpeg downloaded but not found");
        ConfigState.get().ffmpegPath = local.get().toAbsolutePath().toString();
        return local.get();
    }

    /** 查找配置路径 + root/bin 下的 ffmpeg */
    private static Optional<Path> findLocalExecutable() {
        ConfigState cfg = ConfigState.get();
        if (cfg.ffmpegPath != null && !cfg.ffmpegPath.isBlank()) {
            Path configPath = Path.of(cfg.ffmpegPath);
            if (Files.exists(configPath) && Files.isExecutable(configPath)) return Optional.of(configPath);
        }
        String osArchKey = osKey();
        String exeName = isWindows() ? "ffmpeg.exe" : "ffmpeg";
        Path targetDir = Paths.get("livephoto", "ffmpeg", osArchKey);
        Path exeRoot = targetDir.resolve(exeName);
        return Files.exists(exeRoot) && Files.isExecutable(exeRoot) ? Optional.of(exeRoot) : Optional.empty();
    }

    /** 直接下载 ffmpeg 可执行文件到目标路径 */
    private static void downloadExecutable(Path dest) throws IOException {
        String downloadUrl = pickDownloadUrl();
        URLConnection conn = new URL(downloadUrl).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "livephoto-mod");

        long total = conn.getContentLengthLong();
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[8192];
            long read = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    float p = (float) read / (float) total;
                    DownloadProgressHud.update(p, I18n.get("livephoto.hud.downloading"));
                } else {
                    DownloadProgressHud.indeterminate(I18n.get("livephoto.hud.downloading"));
                }
            }
        }
        long size = Files.size(dest);
        if (size < MIN_BYTES) throw new IOException("ffmpeg file too small");
        dest.toFile().setExecutable(true, false);
    }

    private static String pickDownloadUrl() {
        if (isWindows()) {
            return "http://127.0.0.1:5000/ffmpeg.exe";
        }
        if (isMac()) {
            return GH_PROXY + "https://evermeet.cx/ffmpeg/ffmpeg-" + FFMPEG_VERSION;
        }
        return "http://127.0.0.1:5000/ffmpeg-linux";
    }

    private static String osKey() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        arch = switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch;
        };
        if (os.contains("win")) os = "windows";
        else if (os.contains("mac")) os = "macos";
        else if (os.contains("linux")) os = "linux";
        return os + "-" + arch;
    }

    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
    private static boolean isMac() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"); }
}