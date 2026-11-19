package dev.luci.stands.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luci.stands.StandsMod;
import dev.luci.stands.net.StandKeyPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class StandsClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(StandsMod.id("stands"));

    /** Index = StandKeyPayload action: basic, skill, skill 2, ultimate, summon. Defaults follow the 2020 mod (M summon, N attack, B ultimate). */
    private static final KeyMapping[] KEYS = {
            key("basic", InputConstants.KEY_N),
            key("skill", InputConstants.KEY_J),
            key("skill2", InputConstants.KEY_K),
            key("ultimate", InputConstants.KEY_B),
            key("summon", InputConstants.KEY_M),
    };

    private static KeyMapping key(String name, int glfw) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.stands." + name, InputConstants.Type.KEYBOARD, glfw, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(StandsMod.STAND, StandRenderer::new);
        EntityRendererRegistry.register(StandsMod.SHOT, ThrownItemRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.gui.screen() != null) return;
            for (int i = 0; i < KEYS.length; i++)
                while (KEYS[i].consumeClick()) if (ClientPlayNetworking.canSend(StandKeyPayload.TYPE)) ClientPlayNetworking.send(new StandKeyPayload(i));
        });
    }
}
