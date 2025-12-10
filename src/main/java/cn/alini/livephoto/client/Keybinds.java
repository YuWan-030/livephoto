package cn.alini.livephoto.client;

import cn.alini.livephoto.core.ConfigState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static KeyMapping OPEN_CONFIG;
    public static KeyMapping TOGGLE_LIVE;

    public static void register(RegisterKeyMappingsEvent event) {
        OPEN_CONFIG = new KeyMapping("key.livephoto.config", GLFW.GLFW_KEY_F8, "key.categories.misc");
        TOGGLE_LIVE = new KeyMapping("key.livephoto.toggle", GLFW.GLFW_KEY_F9, "key.categories.misc");
        event.register(OPEN_CONFIG);
        event.register(TOGGLE_LIVE);
    }

    public static void handleKeyInput() {
        if (OPEN_CONFIG != null && OPEN_CONFIG.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new LivephotoConfigScreen(mc.screen));
        }
        if (TOGGLE_LIVE != null && TOGGLE_LIVE.consumeClick()) {
            ConfigState cfg = ConfigState.get();
            cfg.enabled = !cfg.enabled;
            // TODO: 显示 HUD/Toast 提示当前开关
        }
    }
}