package kr.pyke.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import kr.pyke.CheeseBridge;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.util.DonationLogger;
import net.minecraft.server.level.ServerPlayer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class ChzzkBridge {
    public static BiConsumer<ServerPlayer, ChzzkDonationEvent> DONATION_HANDLER = null;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static void triggerDonation(ServerPlayer player, ChzzkDonationEvent event) {
        if (DONATION_HANDLER != null) { DONATION_HANDLER.accept(player, event); }
    }

    public static void logReward(ServerPlayer player, String reward) {
        DonationLogger.logReward(player.getName().getString(), reward);
    }

    public static String exchangeCodeForToken(String authCode, String authState) {
        try {
            String clientId = CheeseBridgeConfig.DATA.clientID;
            String clientSecret = CheeseBridgeConfig.DATA.clientSecret;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("grantType", "authorization_code");
            jsonBody.addProperty("clientId", clientId);
            jsonBody.addProperty("clientSecret", clientSecret);
            jsonBody.addProperty("code", authCode);
            jsonBody.addProperty("state", authState);

            String requestBody = GSON.toJson(jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://chzzk.naver.com/auth/v1/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            CheeseBridge.LOGGER.info("치지직 토큰 응답: {}", response.body());

            return response.body();
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("토큰 교환 중 오류 발생: ", e);
            return null;
        }
    }

    public static String refreshAccessToken(String refreshToken) {
        try {
            String clientId = CheeseBridgeConfig.DATA.clientID;
            String clientSecret = CheeseBridgeConfig.DATA.clientSecret;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("grantType", "refresh_token");
            jsonBody.addProperty("refreshToken", refreshToken);
            jsonBody.addProperty("clientId", clientId);
            jsonBody.addProperty("clientSecret", clientSecret);

            String requestBody = GSON.toJson(jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://chzzk.naver.com/auth/v1/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            CheeseBridge.LOGGER.info("토큰 갱신 응답: {}", response.body());

            if (response.statusCode() == 200) { return response.body(); }
            else {
                CheeseBridge.LOGGER.error("토큰 갱신 실패 (HTTP {}): {}", response.statusCode(), response.body());
                return null;
            }
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("토큰 갱신 중 예외 발생: ", e);
            return null;
        }
    }
}