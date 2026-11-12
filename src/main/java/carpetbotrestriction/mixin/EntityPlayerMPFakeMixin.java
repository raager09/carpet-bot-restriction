package carpetbotrestriction.mixin;

import carpet.patches.EntityPlayerMPFake;
import carpetbotrestriction.CarpetBotRestriction;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(EntityPlayerMPFake.class)
public class EntityPlayerMPFakeMixin extends ServerPlayer {

    public EntityPlayerMPFakeMixin(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation clientOptions) {
        super(server, world, profile, clientOptions);
    }

    /**
     * Checks if the player can create the bot
     * Number of bots cannot exceed limit and bot cannot be a player that has logged
     * into the server
     *
     * As of Carpet 26.2, createFake() resolves the bot's GameProfile synchronously
     * (into the "gameprofile" local) before kicking off the async name-resolution future,
     * so this hooks right after that local is read via GameProfile#name() instead of the
     * old GameProfile#getName() call.
     */
    @Inject(
            method = "createFake",
            at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;name()Ljava/lang/String;", shift = At.Shift.AFTER),
            cancellable = true,
            remap = false
    )
    private static void checkIfBotCreateAllowed(@NotNull CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) GameProfile bot) {
        ServerPlayer player = CarpetBotRestriction.CREATE_BOT_SOURCE.getPlayer();
        if (player == null) return;
        UUID playerID = player.getUUID();
        UUID botID = bot.id();
        CarpetBotRestriction.LOGGER.info(playerID.toString());
        // Check if the bot about to be spawned is a real player that has logged onto the server
        if (!Permissions.check(CarpetBotRestriction.CREATE_BOT_SOURCE, "carpetbotrestriction.admin.create_real", 2) && CarpetBotRestriction.REAL_PLAYERS.contains(botID)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        ObjectOpenHashSet<UUID> players;
        if (!CarpetBotRestriction.PLAYERS.containsKey(playerID)) {
            players = new ObjectOpenHashSet<>();
            CarpetBotRestriction.PLAYERS.put(playerID, players);
        }
        else {
            players = CarpetBotRestriction.PLAYERS.get(playerID);
        }
        players.add(botID);
        CarpetBotRestriction.BOTS.put(botID, playerID);
        CarpetBotRestriction.LOGGER.debug("Assigned bot {} to player {}.", bot.name(), player.getName());
    }
}
