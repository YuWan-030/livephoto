package cn.alini.livephoto;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Livephoto.MODID)
public class Livephoto {
    public static final String MODID = "livephoto";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Livephoto() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 这里可注册配置、网络消息等
        LOGGER.info("[livephoto] mod loaded");
    }
}