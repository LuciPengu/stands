package dev.luci.stands;

import dev.luci.stands.entity.StandEntity;
import dev.luci.stands.entity.StandShot;
import dev.luci.stands.net.StandKeyPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class StandsMod implements ModInitializer {
    public static final String MOD_ID = "stands";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    // ------------------------------------------------------------ registry objects

    public static final ResourceKey<DamageType> STAND_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, id("stand"));

    public static final EntityType<StandEntity> STAND = entity("stand",
            EntityType.Builder.<StandEntity>of(StandEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).noSave().noSummon()
                    .clientTrackingRange(8).updateInterval(1));
    public static final EntityType<StandShot> SHOT = entity("shot",
            EntityType.Builder.<StandShot>of(StandShot::new, MobCategory.MISC).sized(0.3f, 0.3f).noSave().noSummon()
                    .clientTrackingRange(6).updateInterval(2));

    public static final Item STAND_ARROW = item("stand_arrow", StandArrowItem::new, new Item.Properties().stacksTo(16));
    /** Projectile skins from the original mod. */
    public static final Item KNIFE = item("knife", Item::new, new Item.Properties());
    public static final Item EMERALD = item("emerald", Item::new, new Item.Properties());
    public static final Item NAIL = item("nail", Item::new, new Item.Properties());
    public static final Item SWORD_TIP = item("swordtip", Item::new, new Item.Properties());
    private static final Map<Item, Item> SKINS = Map.of(Items.IRON_SWORD, KNIFE, Items.EMERALD, EMERALD, Items.IRON_NUGGET, NAIL, Items.STICK, SWORD_TIP);


    // ------------------------------------------------------------ singletons

    private static TimeStopManager timeStop;
    private static StandManager stands;
    private static Abilities abilities;
    private static PvpManager pvp;

    public static TimeStopManager timeStop() { return timeStop; }
    public static StandManager stands() { return stands; }
    public static Abilities abilities() { return abilities; }
    public static PvpManager pvp() { return pvp; }

    /** Default key names, shown on the HUD and in the guide: basic, skill, skill 2, ultimate, summon. */
    public static String[] keyLabels() { return new String[]{"N", "J", "K", "B", "M"}; }

    /** An item that uses the original mod's projectile texture when there is one (iron sword → knife, ...). */
    public static ItemStack look(Item item) { return new ItemStack(SKINS.getOrDefault(item, item)); }

    public static DamageSource standDamage(ServerLevel l, ServerPlayer attacker) {
        var type = l.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(STAND_DAMAGE);
        return attacker == null ? new DamageSource(type) : new DamageSource(type, attacker, attacker);
    }

    @Override
    public void onInitialize() {
        Config.load();
        StandData.init();
        registerSounds();

        timeStop = new TimeStopManager();
        stands = new StandManager(timeStop);
        abilities = new Abilities(stands, timeStop);
        pvp = new PvpManager();
        stands.init(abilities);

        PayloadTypeRegistry.serverboundPlay().register(StandKeyPayload.TYPE, StandKeyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StandKeyPayload.TYPE, (payload, ctx) -> {
            ServerPlayer p = ctx.player();
            int a = payload.action();
            if (a == StandKeyPayload.SUMMON) stands.toggle(p);
            else if (a >= 0 && a < StandManager.Slot.values().length) stands.use(p, StandManager.Slot.values()[a]);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(stands::setServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            timeStop.endAll();
            stands.unsummonAll();
            abilities.restoreAll();
            Sched.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Sched.tick();
            timeStop.tick();
            stands.tick();
        });

        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
            ServerPlayer p = h.getPlayer();
            timeStop.clearMods(p);   // safety net: never leave someone stuck if the server stopped mid time-stop
            Sched.later(60, () -> Txt.msg(p, Txt.of("Stands: ", ChatFormatting.GOLD)
                    .append(Txt.of("/stand guide", ChatFormatting.YELLOW, ChatFormatting.BOLD).withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/stand guide"))))
                    .append(Txt.of(" shows the controls and every stand's abilities.", ChatFormatting.GRAY))));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            ServerPlayer p = h.getPlayer();
            pvp.onQuit(p);
            timeStop.onQuit(p);
            stands.onQuit(p);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p && stands.isUntargetable(p)) return false;
            if (source.getEntity() instanceof ServerPlayer att && stands.isUntargetable(att) && source.getDirectEntity() == att) return false;
            if (!pvp.allowDamage(entity)) return false;
            return timeStop.allowDamage(entity, source, amount);
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> pvp.allowDeath(entity, source));
        ServerLivingEntityEvents.AFTER_DEATH.register(stands::onDeath);

        // Frozen in stopped time: no using, attacking, breaking or placing.
        UseItemCallback.EVENT.register((p, l, hand) -> timeStop.isFrozen(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((p, l, hand, hit) -> timeStop.isFrozen(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((p, l, hand, e, hit) -> timeStop.isFrozen(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((p, l, hand, e, hit) -> timeStop.isFrozen(p) || stands.isUntargetable(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((p, l, hand, pos, dir) -> timeStop.isFrozen(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((l, p, pos, state, be) -> !timeStop.isFrozen(p));

        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> StandCommand.register(dispatcher));
        LOG.info("Stands loaded: {} stands", stands.knownStands().size());
    }

    /** Damage-amount hook called from LivingEntityMixin (vulnerability multipliers, time-stop cap). */
    public static float modifyDamage(LivingEntity victim, DamageSource src, float amount) {
        if (stands == null) return amount;
        amount *= stands.vulnerability(victim);
        return timeStop.modifyDamage(victim, src, amount);
    }

    // ------------------------------------------------------------ registration helpers

    /** Every sound event in the original mod's sounds.json (assets/jojo), so servers know their ids. */
    private static void registerSounds() {
        try (var in = StandsMod.class.getResourceAsStream("/assets/jojo/sounds.json")) {
            if (in == null) return;
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (String s : json.keySet()) {
                Identifier sid = Identifier.fromNamespaceAndPath("jojo", s);
                Registry.register(BuiltInRegistries.SOUND_EVENT, sid, SoundEvent.createVariableRangeEvent(sid));
            }
        } catch (Exception ex) {
            LOG.warn("Couldn't register stand sounds: {}", ex.getMessage());
        }
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> b) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
    }
}
