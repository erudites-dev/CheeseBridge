package kr.pyke.client;

import kr.pyke.CheeseBridge;
import kr.pyke.command.IntegrationCommand;
import kr.pyke.network.CheeseBridgePacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class CheeseBridgeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CheeseBridgePacket.registerClient();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> IntegrationCommand.register(dispatcher));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChzzkManager.getInstance().disconnect();
            CheeseBridge.LOGGER.info("서버 연결 종료로 인해 치지직 소켓을 닫습니다.");
        });
    }
}
