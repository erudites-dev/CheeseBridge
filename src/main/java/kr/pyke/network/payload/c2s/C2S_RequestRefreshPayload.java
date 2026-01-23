package kr.pyke.network.payload.c2s;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import kr.pyke.CheeseBridge;
import kr.pyke.integration.ChzzkBridge;
import kr.pyke.integration.ChzzkDataState;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record C2S_RequestRefreshPayload() implements CustomPacketPayload {
    public static final Type<C2S_RequestRefreshPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(CheeseBridge.MOD_ID, "c2s_request_refresh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_RequestRefreshPayload> STREAM_CODEC = StreamCodec.unit(new C2S_RequestRefreshPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(C2S_RequestRefreshPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ChzzkDataState state = ChzzkDataState.getServerState(context.server());
            ChzzkDataState.TokenInfo tokenInfo = state.playerTokens.get(context.player().getUUID());

            if (tokenInfo != null && tokenInfo.refreshToken() != null) {
                CheeseBridge.LOGGER.info("[갱신] {} 님의 토큰 갱신을 시도합니다.", context.player().getName().getString());
                String jsonResponse = ChzzkBridge.refreshAccessToken(tokenInfo.refreshToken());

                if (jsonResponse != null) {
                    try {
                        JsonObject json = new Gson().fromJson(jsonResponse, JsonObject.class);
                        if (json.has("content")) { json = json.getAsJsonObject("content"); }

                        String newAccess = json.get("accessToken").getAsString();
                        String newRefresh = json.get("refreshToken").getAsString();

                        state.playerTokens.put(context.player().getUUID(), new ChzzkDataState.TokenInfo(newAccess, newRefresh));
                        state.setDirty();

                        ServerPlayNetworking.send(context.player(), new S2C_FinalTokenPayload(newAccess));
                        CheeseBridge.LOGGER.info("[갱신] 성공! 클라이언트에 새 토큰 전송 완료.");
                    }
                    catch (Exception e) { CheeseBridge.LOGGER.error("[갱신] JSON 파싱 실패", e); }
                }
            }
            else { CheeseBridge.LOGGER.warn("[갱신] 저장된 리프레시 토큰이 없습니다. 재인증이 필요합니다."); }
        });
    }
}
