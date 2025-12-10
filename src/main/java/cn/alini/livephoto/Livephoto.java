package cn.alini.livephoto;

import cn.alini.livephoto.core.ConfigState;
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
        // 加载配置
        ConfigState.load();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("[livephoto] mod loaded");
    }
}