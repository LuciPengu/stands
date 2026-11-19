package dev.luci.stands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /stand … */
public final class StandCommand {
    private StandCommand() { }

    private static final SuggestionProvider<CommandSourceStack> STANDS = (c, b) -> SharedSuggestionProvider.suggest(StandsMod.stands().knownStands(), b);

    private static StandManager sm() { return StandsMod.stands(); }

    private static boolean admin(CommandSourceStack s) { return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) { c.getSource().sendSuccess(() -> Txt.of(msg, ChatFormatting.GREEN), false); }
    private static void fail(CommandContext<CommandSourceStack> c, String msg) { c.getSource().sendFailure(Component.literal(msg)); }

    private static String stand(CommandContext<CommandSourceStack> c) {
        String s = StringArgumentType.getString(c, "stand").toLowerCase();
        return sm().knownStands().contains(s) ? s : null;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("stand")
                .executes(c -> guide(c))
                .then(literal("guide").executes(StandCommand::guide))
                .then(literal("help").executes(c -> {
                    c.getSource().sendSystemMessage(Txt.of("""
                            /stand guide — controls and every stand's abilities
                            /stand info — your stand, level and XP
                            /stand pvp — turn stand PvP on or off
                            /stand duel <player> — challenge someone  ·  /stand accept
                            /stand levelup — spend XP levels to level your stand
                            Admin: /stand give <player> <stand> | arrow [player] | setlevel <player> <1-3> | remove <player> | list | reload | testmode | switch <stand>""", ChatFormatting.GOLD));
                    return 1;
                }))
                .then(literal("info").executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException(); Txt.msg(p, sm().describe(p)); return 1; }))
                .then(literal("pvp").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    boolean on = StandsMod.pvp().togglePvp(p);
                    Txt.msg(p, "Stand PvP is now " + (on ? "ON: stand powers work between you and other players with PvP on." : "OFF: other players' stand powers can't touch you (duels still work)."),
                            on ? ChatFormatting.GREEN : ChatFormatting.RED);
                    return 1;
                }))
                .then(literal("duel").then(argument("player", EntityArgument.player()).executes(c -> {
                    StandsMod.pvp().challenge(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"));
                    return 1;
                })))
                .then(literal("accept").executes(c -> { StandsMod.pvp().accept(c.getSource().getPlayerOrException()); return 1; }))
                .then(literal("levelup").executes(c -> { sm().levelUp(c.getSource().getPlayerOrException()); return 1; }))
                // ---- admin
                .then(literal("list").requires(StandCommand::admin).executes(c -> {
                    c.getSource().sendSystemMessage(Txt.of("Stands: " + sm().knownStands() + "  enabled: " + Config.get().getStringList("enabled-stands"), ChatFormatting.GOLD));
                    return 1;
                }))
                .then(literal("give").requires(StandCommand::admin).then(argument("player", EntityArgument.player())
                        .then(argument("stand", StringArgumentType.word()).suggests(STANDS).executes(c -> {
                            ServerPlayer t = EntityArgument.getPlayer(c, "player");
                            String s = stand(c);
                            if (s == null) { fail(c, "Unknown stand. /stand list"); return 0; }
                            sm().setStand(t, s);
                            ok(c, "Gave " + t.getName().getString() + " " + sm().displayName(s));
                            return 1;
                        }))))
                .then(literal("arrow").requires(StandCommand::admin)
                        .executes(c -> arrow(c, c.getSource().getPlayerOrException()))
                        .then(argument("player", EntityArgument.player()).executes(c -> arrow(c, EntityArgument.getPlayer(c, "player")))))
                .then(literal("setlevel").requires(StandCommand::admin).then(argument("player", EntityArgument.player())
                        .then(argument("level", IntegerArgumentType.integer(1, 3)).executes(c -> {
                            ServerPlayer t = EntityArgument.getPlayer(c, "player");
                            int lvl = IntegerArgumentType.getInteger(c, "level");
                            sm().setLevel(t, lvl);
                            ok(c, "Set " + t.getName().getString() + " to level " + lvl);
                            return 1;
                        }))))
                .then(literal("remove").requires(StandCommand::admin)
                        .executes(c -> remove(c, c.getSource().getPlayerOrException()))
                        .then(argument("player", EntityArgument.player()).executes(c -> remove(c, EntityArgument.getPlayer(c, "player")))))
                .then(literal("switch").requires(StandCommand::admin).then(argument("stand", StringArgumentType.word()).suggests(STANDS).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String s = stand(c);
                    if (s == null) { fail(c, "Unknown stand. /stand list"); return 0; }
                    sm().setStand(p, s);
                    sm().setLevel(p, 3);
                    ok(c, "Switched to " + sm().displayName(s) + " (level 3)");
                    return 1;
                })))
                .then(literal("testmode").requires(StandCommand::admin).executes(c -> {
                    boolean on = sm().toggleTestMode(c.getSource().getPlayerOrException());
                    ok(c, on ? "Test mode ON: mobs take stand-vs-stand damage from you (armor ignored, PvP numbers)." : "Test mode OFF: mobs take the unfair damage again.");
                    return 1;
                }))
                .then(literal("reload").requires(StandCommand::admin).executes(c -> {
                    Config.load();
                    ok(c, "Stands config reloaded");
                    return 1;
                })));
    }

    private static int guide(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Guide.open(c.getSource().getPlayerOrException(), admin(c.getSource()));
        return 1;
    }

    private static int arrow(CommandContext<CommandSourceStack> c, ServerPlayer t) {
        t.getInventory().add(new ItemStack(StandsMod.STAND_ARROW));
        ok(c, "Gave " + t.getName().getString() + " a Stand Arrow");
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c, ServerPlayer t) {
        sm().removeStand(t);
        ok(c, "Removed " + t.getName().getString() + "'s stand");
        return 1;
    }
}
