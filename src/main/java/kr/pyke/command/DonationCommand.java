package kr.pyke.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import kr.pyke.CheeseBridge;
import kr.pyke.PykeLib;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.integration.ChzzkBridge;
import kr.pyke.integration.ChzzkDataState;
import kr.pyke.integration.ChzzkDonationEvent;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.util.DonationLogger;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DonationCommand {
    private DonationCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("후원")
            .requires(sourceStack -> sourceStack.hasPermission(2))
            .then(Commands.argument("targetPlayer", EntityArgument.player())
                .then(Commands.argument("donationAmount", IntegerArgumentType.integer(0))
                    .executes(DonationCommand::executeManualDonation)
                )
            )
        );

        dispatcher.register(Commands.literal("후원연동")
            .executes(DonationCommand::executeIntegrationConnect)
        );
    }

    private static int executeManualDonation(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer targetPlayer = EntityArgument.getPlayer(ctx, "targetPlayer");
            int amount = IntegerArgumentType.getInteger(ctx, "donationAmount");

            CommandSourceStack source = ctx.getSource();
            String managerName = Objects.requireNonNull(source.getPlayer()).getName().getString();
            String targetPlayerName = targetPlayer.getDisplayName().getString();
            List<ServerPlayer> serverPlayers = source.getServer().getPlayerList().getPlayers();

            source.getServer().execute(() -> {
                DonationLogger.logDonationManager(targetPlayerName, String.valueOf(amount), managerName);

                ChzzkBridge.triggerDonation(targetPlayer, new ChzzkDonationEvent("운영자", String.valueOf(amount), "수동 지급"));

                PykeLib.sendSystemMessage(serverPlayers, COLOR.LIME.getColor(), String.format("&7%s&f님에게 보상을 수동 지급했습니다.", targetPlayerName));
            });

            return 1;
        }
        catch (Exception e) {
            List<ServerPlayer> serverPlayers = ctx.getSource().getServer().getPlayerList().getPlayers();
            PykeLib.sendSystemMessage(serverPlayers, COLOR.RED.getColor(), "명령어 실행 중 오류가 발생했습니다.");
            return 0;
        }
    }

    private static int executeIntegrationConnect(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            ChzzkDataState dataState = ChzzkDataState.getServerState(ctx.getSource().getServer());
            ChzzkDataState.TokenInfo tokenInfo = dataState.playerTokens.get(player.getUUID());
            String savedToken = (tokenInfo != null) ? tokenInfo.accessToken() : null;

            if (savedToken != null) {
                ServerPlayNetworking.send(player, new S2C_FinalTokenPayload(savedToken));
                PykeLib.sendSystemMessage(List.of(player), COLOR.LIME.getColor(), "기존 연동 정보를 사용하여 즉시 재연동합니다.");
            }
            else {
                String clientId = CheeseBridgeConfig.DATA.clientID;
                String state = UUID.randomUUID().toString();

                String authUrl = String.format("https://chzzk.naver.com/account-interlock?clientId=%s&redirectUri=%s&state=%s", clientId, "http://localhost:8080/callback", state);

                ServerPlayNetworking.send(player, new S2C_AuthUrlPayload(authUrl));
            }

            return 1;
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("ERROR: ", e);
            return 0;
        }
    }
}
