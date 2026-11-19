package dev.luci.stands.kits;

import com.mojang.math.Transformation;
import dev.luci.stands.Abilities;
import dev.luci.stands.Cfg;
import dev.luci.stands.Fx;
import dev.luci.stands.StandManager;
import dev.luci.stands.StandManager.Slot;
import dev.luci.stands.StandsMod;
import dev.luci.stands.TimeStopManager;
import dev.luci.stands.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The World — close-range time stopper. */
public final class TheWorld extends BaseKit {
    public TheWorld(StandManager m, TimeStopManager ts, Abilities a) { super("theworld", m, ts, a, "MUDA", "Knives", "Dash", "ZA WARUDO"); }

    @Override public String label(ServerPlayer p, Slot slot) { return slot == Slot.SKILL2 && ts.hasStop(p) ? "ROAD ROLLER" : super.label(p, slot); }
    @Override protected String section(ServerPlayer p, Slot slot) { return slot == Slot.SKILL2 && ts.hasStop(p) ? "roadroller" : super.section(p, slot); }

    @Override protected boolean basic(ServerPlayer p, Cfg cfg) {
        a.rush(p, cfg, "theworld-rush", "MUDA MUDA MUDA!", "WRYYYYY!");
        return true;
    }

    @Override protected boolean skill(ServerPlayer p, Cfg cfg) {
        m.anim(p, "throw");
        Fx.sound(p.level(), p.position(), SoundEvents.TRIDENT_THROW.value(), 1f, 1.6f);
        bar(p, "Throwing Knives!");
        a.volley(p, StandsMod.look(Items.IRON_SWORD), cfg.getInt("knives", 5), 24, 1.7, true, cfg.getDouble("damage-per-knife", 4.0), null);
        return true;
    }

    @Override protected boolean skill2(ServerPlayer p, Cfg cfg) {
        if (ts.hasStop(p)) return roadRoller(p, cfg);
        m.anim(p, "dash");
        a.dash(p, cfg.getDouble("distance", 5));
        Fx.sound(p.level(), p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 1.2f);
        return true;
    }

    private boolean roadRoller(ServerPlayer p, Cfg cfg) {
        ServerLevel l = p.level();
        BlockPos g = BlockPos.containing(a.aim(p, 16));
        for (int i = 0; i < 20 && g.getY() > l.getMinY() && l.getBlockState(g).getCollisionShape(l, g).isEmpty(); i++) g = g.below();
        final BlockPos ground = g;
        final BlockState groundState = l.getBlockState(ground);
        Vec3 land = Vec3.atBottomCenterOf(ground).add(0, 1, 0);
        m.sound(l, p.position(), "roadroller");
        m.anim(p, "pose");
        for (ServerPlayer near : l.players()) if (near.position().distanceTo(land) < 40)
            Txt.title(near, Txt.of("ROAD ROLLER DA!", ChatFormatting.GOLD, ChatFormatting.BOLD), null, 0, 24, 6);
        Display.BlockDisplay roller = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, l);
        roller.setBlockState(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState());
        roller.setTransformation(new Transformation(new Vector3f(-1.5f, 0, -1f), new Quaternionf(), new Vector3f(3f, 1.6f, 2f), new Quaternionf()));
        roller.setPosRotInterpolationDuration(10);
        roller.snapTo(land.x, land.y + 9, land.z, 0, 0);
        l.addFreshEntity(roller);
        later(2, () -> roller.snapTo(land.x, land.y, land.z, 0, 0));
        later(12, () -> {
            Fx.particle(l, ParticleTypes.EXPLOSION_EMITTER, land, 1);
            Fx.particle(l, new BlockParticleOption(ParticleTypes.BLOCK, groundState), land, 60, 1.5, .3, 1.5, 0);
            a.blast(p, land, cfg.getDouble("radius", 4.5), cfg.getDouble("damage", 14.0), 0);
        });
        later(70, roller::discard);
        return true;
    }

    @Override protected boolean ultimate(ServerPlayer p, Cfg cfg) {
        if (!ts.start(p, cfg.getDouble("duration", 5.0))) return false;
        m.sound(p, "timestop");
        m.standForward(p, 28);
        m.anim(p, "pose");
        title(p, "ZA WARUDO!", "Toki yo tomare!");
        return true;
    }
}
