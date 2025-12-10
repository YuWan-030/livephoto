package cn.alini.livephoto.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigState {
    public boolean enabled = true;
    public Protocol protocol = Protocol.BOTH;
    public float durationSeconds = 3.0f;
    public float preSeconds = 1.0f;
    public int fps = 30;
    public float resolutionScale = 0.75f;
    public int videoBitrateK = 8000;
    public boolean audio = false;            // 是否启用音频
    public String audioDevice = "";          // 录音设备名（WASAPI）
    public boolean keepVanillaPng = true;
    public String ffmpegPath = "";           // 仅内部使用（自动下载/发现后写入）
    public boolean autoDownloadFfmpeg = true;

    public enum Protocol { MOTION, LIVE, BOTH }

    private static final ConfigState INSTANCE = new ConfigState();
    public static ConfigState get() { return INSTANCE; }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("livephoto.json");

    /** 从磁盘加载配置，不存在则写出默认 */
    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                save();
                return;
            }
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                ConfigState loaded = GSON.fromJson(r, ConfigState.class);
                if (loaded != null) INSTANCE.copyFrom(loaded);
            }
        } catch (Exception e) {
            System.err.println("[livephoto] failed to load config: " + e.getMessage());
        }
    }

    /** 保存当前配置到磁盘 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            System.err.println("[livephoto] failed to save config: " + e.getMessage());
        }
    }

    private void copyFrom(ConfigState o) {
        this.enabled = o.enabled;
        this.protocol = o.protocol;
        this.durationSeconds = o.durationSeconds;
        this.preSeconds = o.preSeconds;
        this.fps = o.fps;
        this.resolutionScale = o.resolutionScale;
        this.videoBitrateK = o.videoBitrateK;
        this.audio = o.audio;
        this.audioDevice = o.audioDevice;
        this.keepVanillaPng = o.keepVanillaPng;
        this.ffmpegPath = o.ffmpegPath;
        this.autoDownloadFfmpeg = o.autoDownloadFfmpeg;
    }
}