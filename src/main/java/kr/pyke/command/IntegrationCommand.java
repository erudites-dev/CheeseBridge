package kr.pyke.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import kr.pyke.client.ChzzkAuthServer;
import kr.pyke.client.ChzzkManager;
import kr.pyke.client.PykeLibClient;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.Util;

public class IntegrationCommand {
    private static final ChzzkAuthServer AUTH_SERVER = new ChzzkAuthServer();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("연동해제")
            .executes(IntegrationCommand::integrationDisconnect)
        );

        dispatcher.register(ClientCommandManager.literal("연동테스트")
            .then(ClientCommandManager.argument("token", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String token = StringArgumentType.getString(ctx, "token");
                    ChzzkManager.getInstance().connect(token);
                    return 1;
                }))
        );
    }

    private static int integrationDisconnect(CommandContext<FabricClientCommandSource> ctx) {
        ChzzkManager.getInstance().disconnect();
        PykeLibClient.sendSystemMessage(COLOR.RED.getColor(), "치지직 후원 연동이 일시적으로 해제되었습니다.");
        PykeLibClient.sendSystemMessage(COLOR.RED.getColor(), "'/후원연동' 입력 시 즉시 다시 연결됩니다.");
        return 1;
    }

    public static void startAuthProcess(String url) {
        AUTH_SERVER.start();
        Util.getPlatform().openUri(url);
        PykeLibClient.sendSystemMessage(COLOR.LIME.getColor(), "브라우저에서 치지직 로그인을 진행해 주세요.");
    }
}
