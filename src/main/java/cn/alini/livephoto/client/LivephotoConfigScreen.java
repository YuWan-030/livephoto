package cn.alini.livephoto.client;

import cn.alini.livephoto.core.ConfigState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public class LivephotoConfigScreen extends Screen {
    private final Screen parent;
    private final ConfigState cfg = ConfigState.get();

    private Button deviceButton;
    private List<String> devices = List.of("default");

    public LivephotoConfigScreen(Screen parent) {
        super(Component.translatable("screen.livephoto.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = 40;
        int gap = 24;
        int center = this.width / 2;
        int btnW = 200;

        addRenderableWidget(Button.builder(boolLabel("livephoto.enabled", cfg.enabled), b -> {
            cfg.enabled = !cfg.enabled;
            b.setMessage(boolLabel("livephoto.enabled", cfg.enabled));
        }).pos(center - btnW/2, y).size(btnW, 20).build());
        y += gap;

        addRenderableWidget(Button.builder(protocolLabel(), b -> {
            cfg.protocol = switch (cfg.protocol) {
                case MOTION -> ConfigState.Protocol.LIVE;
                case LIVE -> ConfigState.Protocol.BOTH;
                case BOTH -> ConfigState.Protocol.MOTION;
            };
            b.setMessage(protocolLabel());
        }).pos(center - btnW/2, y).size(btnW, 20).build());
        y += gap;

        addRenderableWidget(new SimpleSlider(center - btnW/2, y, btnW, 20,
                "livephoto.duration", 1f, 10f, cfg.durationSeconds, v -> cfg.durationSeconds = (float) v));
        y += gap;

        addRenderableWidget(new SimpleSlider(center - btnW/2, y, btnW, 20,
                "livephoto.fps", 10, 60, cfg.fps, v -> cfg.fps = (int) v));
        y += gap;

        addRenderableWidget(new SimpleSlider(center - btnW/2, y, btnW, 20,
                "livephoto.scale", 0.25f, 1.0f, cfg.resolutionScale, v -> cfg.resolutionScale = (float) v));
        y += gap;

        addRenderableWidget(Button.builder(boolLabel("livephoto.keep_png", cfg.keepVanillaPng), b -> {
            cfg.keepVanillaPng = !cfg.keepVanillaPng;
            b.setMessage(boolLabel("livephoto.keep_png", cfg.keepVanillaPng));
        }).pos(center - btnW/2, y).size(btnW, 20).build());
        y += gap;

        addRenderableWidget(Button.builder(boolLabel("livephoto.enable_audio", cfg.audio), b -> {
            cfg.audio = !cfg.audio;
            b.setMessage(boolLabel("livephoto.enable_audio", cfg.audio));
        }).pos(center - btnW/2, y).size(btnW, 20).build());
        y += gap;

        deviceButton = Button.builder(Component.translatable("livephoto.device", displayDevice(cfg.audioDevice)), b -> {
            if (devices.isEmpty()) return;
            int idx = Math.max(0, devices.indexOf(cfg.audioDevice));
            idx = (idx + 1) % devices.size();
            cfg.audioDevice = devices.get(idx);
            deviceButton.setMessage(Component.translatable("livephoto.device", displayDevice(cfg.audioDevice)));
        }).pos(center - btnW/2, y).size(btnW, 20).build();
        addRenderableWidget(deviceButton);
        y += gap;

        addRenderableWidget(Button.builder(Component.translatable("livephoto.refresh_devices"), b -> {
            Path ffmpegPath = Path.of(cfg.ffmpegPath == null ? "" : cfg.ffmpegPath);
            if (!java.nio.file.Files.exists(ffmpegPath)) return;
            List<String> list = AudioDeviceLister.listWasapiDevices(ffmpegPath);
            if (list != null && !list.isEmpty()) {
                devices = list;
                if (!devices.contains(cfg.audioDevice)) cfg.audioDevice = devices.get(0);
                deviceButton.setMessage(Component.translatable("livephoto.device", displayDevice(cfg.audioDevice)));
            }
        }).pos(center - btnW/2, y).size(btnW, 20).build());
        y += gap;

        addRenderableWidget(Button.builder(boolLabel("livephoto.auto_download_ffmpeg", cfg.autoDownloadFfmpeg), b -> {
            cfg.autoDownloadFfmpeg = !cfg.autoDownloadFfmpeg;
            b.setMessage(boolLabel("livephoto.auto_download_ffmpeg", cfg.autoDownloadFfmpeg));
        }).pos(center - btnW/2, y).size(btnW, 20).build());
        y += gap + 6;

        addRenderableWidget(Button.builder(Component.translatable("livephoto.save_back"), b -> {
            ConfigState.save();
            this.minecraft.setScreen(parent);
        }).pos(center - btnW/2, y).size(btnW, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("livephoto.cancel"), b -> this.minecraft.setScreen(parent))
                .pos(center - btnW/2, y + gap).size(btnW, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private Component boolLabel(String key, boolean v) {
        return Component.translatable(key, v ? "ON" : "OFF");
    }

    private Component protocolLabel() {
        String key = switch (cfg.protocol) {
            case MOTION -> "livephoto.protocol.motion";
            case LIVE -> "livephoto.protocol.live";
            case BOTH -> "livephoto.protocol.both";
        };
        return Component.translatable("livephoto.protocol", Component.translatable(key));
    }

    private String displayDevice(String name) {
        return (name == null || name.isBlank()) ? "default" : name;
    }

    private static class SimpleSlider extends AbstractSliderButton {
        private final double min, max;
        private final String translationKey;
        private final Setter setter;

        interface Setter { void set(double v); }

        public SimpleSlider(int x, int y, int w, int h,
                            String translationKey,
                            double min, double max, double current,
                            Setter setter) {
            super(x, y, w, h, Component.empty(), 0d);
            this.min = min; this.max = max; this.translationKey = translationKey; this.setter = setter;
            this.value = toSlider(current);
            applyValue();
            updateMessage();
        }

        private double toSlider(double v) { return (v - min) / (max - min); }
        private double fromSlider(double v) { return min + v * (max - min); }

        @Override
        protected void updateMessage() {
            double v = fromSlider(this.value);
            String text = (v % 1.0 == 0) ? String.valueOf((int) v) : String.format("%.2f", v);
            setMessage(Component.translatable(translationKey, text));
        }

        @Override
        protected void applyValue() {
            double v = fromSlider(this.value);
            setter.set(v);
        }
    }
}