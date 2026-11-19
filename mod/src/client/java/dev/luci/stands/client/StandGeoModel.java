package dev.luci.stands.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.luci.stands.StandsMod;
import dev.luci.stands.entity.StandEntity;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Picks the model, skin and animations by the stand id the server synced to the entity. */
public class StandGeoModel extends GeoModel<StandEntity> {
    static final DataTicket<String> STAND_ID = DataTicket.create("stands_stand_id", String.class);
    private static final String FALLBACK = "theworld";

    private static String id(String s) { return s == null || s.isEmpty() ? FALLBACK : s; }

    @Override
    public void addAdditionalStateData(StandEntity stand, @Nullable Object related, GeoRenderState state) {
        state.addGeckolibData(STAND_ID, id(stand.standId()));
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return StandsMod.id("stand/" + id(state.getOrDefaultGeckolibData(STAND_ID, FALLBACK)));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return StandsMod.id("textures/entity/stand/" + id(state.getOrDefaultGeckolibData(STAND_ID, FALLBACK)) + ".png");
    }

    @Override
    public Identifier getAnimationResource(StandEntity stand) {
        return StandsMod.id("stand/" + id(stand.standId()));
    }
}
