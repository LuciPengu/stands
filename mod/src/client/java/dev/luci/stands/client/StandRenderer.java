package dev.luci.stands.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.luci.stands.entity.StandEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Draws stands see-through, like the plugin. Hidden stands (ghost abilities) aren't drawn at all. */
public class StandRenderer extends GeoEntityRenderer<StandEntity, EntityRenderState> {
    public StandRenderer(EntityRendererProvider.Context ctx) { super(ctx, new StandGeoModel()); }

    @Override
    public @Nullable RenderType getRenderType(EntityRenderState state, Identifier texture) {
        return state.isInvisible ? null : RenderTypes.entityTranslucent(texture);
    }
}
