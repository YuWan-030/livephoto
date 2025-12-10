package cn.alini.livephoto.client;

import cn.alini.livephoto.Livephoto;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenshotEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Livephoto.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class LivephotoClient {
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        Keybinds.register(event);
    }
}

@Mod.EventBusSubscriber(modid = Livephoto.MODID, value = Dist.CLIENT)
class LivephotoClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Keybinds.handleKeyInput();
    }

    /**
     * 使用 RenderLevelStageEvent 在每帧渲染阶段采样，以获得 30/60fps。
     * 选择 AFTER_LEVEL 阶段保证每帧只触发一次。
     */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            FrameBuffer.get().onRenderTick();
        }
    }

    @SubscribeEvent
    public static void onScreenshot(ScreenshotEvent event) {
        CaptureController.get().onScreenshot(event);
    }
}