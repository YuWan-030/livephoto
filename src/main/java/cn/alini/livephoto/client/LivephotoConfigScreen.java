package cn.alini.livephoto.client;

import cn.alini.livephoto.core.ConfigState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LivephotoConfigScreen extends Screen {
    private final Screen parent;
    private final ConfigState cfg = ConfigState.get();

    public LivephotoConfigScreen(Screen parent) {
        super(Component.literal("Live Photo Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // TODO: 添加按钮/滑条/下拉，用于：
        // 开关实况、协议选择（Motion / Live / Both）、时长、帧率、分辨率缩放、码率、音频开关、
        // FFmpeg 路径/自动下载、是否保留原版 PNG 等
        super.init();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}