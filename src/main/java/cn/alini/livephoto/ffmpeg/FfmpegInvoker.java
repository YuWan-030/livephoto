package cn.alini.livephoto.ffmpeg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class FfmpegInvoker {

    /** 从目录内的 f_%05d.png 序列编码，支持可选音轨；失败抛出日志并删除输出 */
    public static void createVideoFromDir(Path ffmpeg, Path framesDir, Path outVideo, float fps, Path audioInput)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(ffmpeg.toString());
        pb.command().add("-y");
        pb.command().add("-framerate"); pb.command().add(String.valueOf(fps));
        pb.command().add("-i"); pb.command().add(framesDir.resolve("f_%05d.png").toString());

        if (audioInput != null && Files.exists(audioInput)) {
            pb.command().add("-i"); pb.command().add(audioInput.toString());
            pb.command().add("-map"); pb.command().add("0:v:0");
            pb.command().add("-map"); pb.command().add("1:a:0");
        } else {
            // 加一条静音轨，避免缺音轨导致播放器识别不到音频流
            pb.command().add("-f"); pb.command().add("lavfi");
            pb.command().add("-i"); pb.command().add("anullsrc=channel_layout=stereo:sample_rate=48000");
            pb.command().add("-map"); pb.command().add("0:v:0");
            pb.command().add("-map"); pb.command().add("1:a:0");
        }

        pb.command().add("-vf"); pb.command().add("pad=ceil(iw/2)*2:ceil(ih/2)*2");
        pb.command().add("-c:v"); pb.command().add("libx264");
        pb.command().add("-preset"); pb.command().add("ultrafast");
        pb.command().add("-tune"); pb.command().add("zerolatency");
        pb.command().add("-pix_fmt"); pb.command().add("yuv420p");
        pb.command().add("-c:a"); pb.command().add("aac");
        pb.command().add("-b:a"); pb.command().add("128k");
        pb.command().add("-shortest");
        pb.command().add("-movflags"); pb.command().add("+faststart");
        pb.command().add(outVideo.toString());

        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) log.append(line).append('\n');
        }
        int code = p.waitFor();
        if (code != 0) {
            Files.deleteIfExists(outVideo);
            throw new IOException("ffmpeg frames->video failed, exit " + code + ", framesDir=" + framesDir + "\n" + log);
        }
    }

    /** 旧方法保留，默认无外部音频（将生成静音轨） */
    public static void createVideoFromDir(Path ffmpeg, Path framesDir, Path outVideo, float fps)
            throws IOException, InterruptedException {
        createVideoFromDir(ffmpeg, framesDir, outVideo, fps, null);
    }

    public static void createVideoFromFrames(Path ffmpeg, java.util.List<Path> frames, Path outVideo, float fps)
            throws IOException, InterruptedException {
        Path tempDir = java.nio.file.Files.createTempDirectory("livephoto-frames-");
        try {
            int idx = 0;
            for (Path f : frames) {
                String name = String.format("f_%05d.png", idx++);
                java.nio.file.Files.copy(f, tempDir.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            createVideoFromDir(ffmpeg, tempDir, outVideo, fps, null);
        } finally {
            try (var s = java.nio.file.Files.list(tempDir)) {
                s.forEach(f -> { try { java.nio.file.Files.deleteIfExists(f); } catch (IOException ignored) {} });
            } catch (Exception ignored) {}
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    public static void extractCover(Path ffmpeg, Path video, Path outJpg) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
                ffmpeg.toString(),
                "-y",
                "-i", video.toString(),
                "-ss", "0",
                "-vframes", "1",
                outJpg.toString()
        ).redirectErrorStream(true).start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) log.append(line).append('\n');
        }
        int code = p.waitFor();
        if (code != 0) {
            Files.deleteIfExists(outJpg);
            throw new IOException("ffmpeg extract cover failed, exit " + code + "\n" + log);
        }
    }

    public static void copy(Path src, Path dst) throws IOException {
        java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}