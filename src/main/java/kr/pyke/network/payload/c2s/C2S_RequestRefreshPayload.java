package kr.pyke.network.payload.c2s;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import kr.pyke.CheeseBridge;
import kr.pyke.PykeLib;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.integration.ChzzkBridge;
import kr.pyke.integration.ChzzkDataState;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record C2S_RequestRefreshPayload() implements CustomPacketPayload {
    public static final Type<C2S_RequestRefreshPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(CheeseBridge.MOD_ID, "c2s_request_refresh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_RequestRefreshPayload> STREAM_CODEC = StreamCodec.unit(new C2S_RequestRefreshPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(C2S_RequestRefreshPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ChzzkDataState state = ChzzkDataState.getServerState(context.server());
            ChzzkDataState.TokenInfo tokenInfo = state.playerTokens.get(context.player().getUUID());

            boolean refreshSuccess = false;

            if (tokenInfo != null && tokenInfo.refreshToken() != null) {
                CheeseBridge.LOGGER.info("[갱신] {} 님의 토큰 갱신을 시도합니다.", context.player().getName().getString());
                String jsonResponse = ChzzkBridge.refreshAccessToken(tokenInfo.refreshToken());

                if (jsonResponse != null) {
                    try {
                        JsonObject json = new Gson().fromJson(jsonResponse, JsonObject.class);
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonObject content = json.getAsJsonObject("content");
                            String newAccess = content.get("accessToken").getAsString();
                            String newRefresh = content.get("refreshToken").getAsString();

                            state.playerTokens.put(context.player().getUUID(), new ChzzkDataState.TokenInfo(newAccess, newRefresh));
                            state.setDirty();

                            ServerPlayNetworking.send(context.player(), new S2C_FinalTokenPayload(newAccess));
                            CheeseBridge.LOGGER.info("[갱신] 성공! 클라이언트에 새 토큰 전송 완료.");
                            refreshSuccess = true;
                        }
                        else { CheeseBridge.LOGGER.error("[갱신] 실패 응답 수신: {}", jsonResponse); }
                    }
                    catch (Exception e) { CheeseBridge.LOGGER.error("[갱신] JSON 파싱 중 오류 발생", e); }
                }
            }

            if (!refreshSuccess) {
                CheeseBridge.LOGGER.warn("[갱신] 토큰 갱신 불가. 재인증을 요청합니다.");

                String clientId = CheeseBridgeConfig.DATA.clientID;
                String authState = UUID.randomUUID().toString();
                String authUrl = String.format("https://chzzk.naver.com/account-interlock?clientId=%s&redirectUri=%s&state=%s", clientId, "http://localhost:8080/callback", authState);

                PykeLib.sendSystemMessage(java.util.List.of(context.player()), COLOR.RED.getColor(), "인증 세션이 만료되었습니다. 다시 로그인을 진행해주세요.");
                ServerPlayNetworking.send(context.player(), new S2C_AuthUrlPayload(authUrl));
            }
        });
    }
}
