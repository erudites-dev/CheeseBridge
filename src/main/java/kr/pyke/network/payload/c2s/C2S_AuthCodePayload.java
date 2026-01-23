package kr.pyke.network.payload.c2s;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import kr.pyke.CheeseBridge;
import kr.pyke.integration.ChzzkBridge;
import kr.pyke.integration.ChzzkDataState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record C2S_AuthCodePayload(String code, String state) implements CustomPacketPayload {
    public static final Type<C2S_AuthCodePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(CheeseBridge.MOD_ID, "c2s_auth_code"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_AuthCodePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2S_AuthCodePayload::code,
        ByteBufCodecs.STRING_UTF8, C2S_AuthCodePayload::state,
        C2S_AuthCodePayload::new
    );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(C2S_AuthCodePayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            String jsonResponse = ChzzkBridge.exchangeCodeForToken(payload.code(), payload.state());

            if (jsonResponse != null) {
                try {
                    JsonObject json = new Gson().fromJson(jsonResponse, JsonObject.class);
                    if (json.has("content")) { json = json.getAsJsonObject("content"); }

                    if (json.has("accessToken") && json.has("refreshToken")) {
                        String accessToken = json.get("accessToken").getAsString();
                        String refreshToken = json.get("refreshToken").getAsString();

                        ChzzkDataState state = ChzzkDataState.getServerState(context.server());
                        state.playerTokens.put(context.player().getUUID(), new ChzzkDataState.TokenInfo(accessToken, refreshToken));
                        state.setDirty();

                        ServerPlayNetworking.send(context.player(), new kr.pyke.network.payload.s2c.S2C_FinalTokenPayload(accessToken));
                        CheeseBridge.LOGGER.info("[인증] 토큰 발급 및 저장 완료!");
                    }
                    else { CheeseBridge.LOGGER.error("[인증] 토큰 발급 실패 (응답 내용): {}", jsonResponse); }
                }
                catch (Exception e) { CheeseBridge.LOGGER.error("[인증] 응답 파싱 실패: ", e); }
            }
        });
    }
}