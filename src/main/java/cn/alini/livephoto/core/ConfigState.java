package cn.alini.livephoto.core;

public class ConfigState {
    public boolean enabled = true;
    public Protocol protocol = Protocol.BOTH;
    public float durationSeconds = 3.0f;
    public float preSeconds = 1.0f;
    public int fps = 30;
    public float resolutionScale = 1f;
    public int videoBitrateK = 8000;
    public boolean audio = false;
    public String audioInputPath = ""; // 若设置且存在则作为音轨使用，否则生成静音轨
    public boolean keepVanillaPng = false;
    public String ffmpegPath = "";
    public boolean autoDownloadFfmpeg = true;

    public enum Protocol { MOTION, LIVE, BOTH }

    private static final ConfigState INSTANCE = new ConfigState();
    public static ConfigState get() { return INSTANCE; }
}