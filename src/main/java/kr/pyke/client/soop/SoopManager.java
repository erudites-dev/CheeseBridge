package kr.pyke.client.soop;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kr.pyke.CheeseBridge;
import kr.pyke.client.PykeLibClient;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.util.SoopProtocol;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SoopManager {
    private static final SoopManager INSTANCE = new SoopManager();
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private WebSocketClient webSocket;
    private ScheduledExecutorService scheduler;
    private String chatNo, ticket, bjId;
    private boolean isLoggedIn = false;

    private SoopManager() { }
    public static SoopManager getInstance() { return INSTANCE; }

    public void connect(String accessToken) {
        disconnect();
        this.isLoggedIn = false;
        CheeseBridge.LOGGER.info("[SOOP] 연결 프로세스 시작...");

        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://openapi.sooplive.co.kr/broad/access/chatinfo"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("access_token=" + accessToken))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    CheeseBridge.LOGGER.error("[SOOP] 채팅 정보 실패: {}", response.body());
                    return;
                }

                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                int resultCode = json.get("result").getAsInt();

                CheeseBridge.LOGGER.info("[SOOP] 수신된 resultCode: {}", resultCode);

                if (resultCode != 1) {
                    String errorMsg = json.has("msg") ? json.get("msg").getAsString() : "Unknown Error";

                    CheeseBridge.LOGGER.error("[SOOP] API 상세 에러: {} (코드: {})", errorMsg, resultCode);
                    Minecraft.getInstance().execute(() -> PykeLibClient.sendSystemMessage(COLOR.RED.getColor(), "숲(SOOP) API 오류: " + errorMsg));

                    return;
                }

                JsonObject data = json.getAsJsonArray("data").get(0).getAsJsonObject();
                String chatIp = data.get("chat_ip").getAsString();
                int chatPort = data.get("chat_port").getAsInt();
                this.chatNo = data.get("chat_no").getAsString();
                this.ticket = data.get("key").getAsString();

                JsonElement idElement = data.get("id");
                this.bjId = idElement.isJsonObject() ? idElement.getAsJsonObject().get("userId").getAsString() : idElement.getAsString();

                String wsUrl = String.format("ws://%s:%d/Websocket/%s", chatIp, chatPort, bjId);

                webSocket = new WebSocketClient(URI.create(wsUrl)) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        CheeseBridge.LOGGER.info("[SOOP] 소켓 연결됨. 로그인 시도.");
                        List<String> loginBody = new ArrayList<>();
                        loginBody.add("");
                        loginBody.add("");
                        loginBody.add("16");
                        send(SoopProtocol.makePacket(SoopProtocol.SVC_LOGIN, loginBody));
                        startKeepAlive();
                    }

                    @Override
                    public void onMessage(String message) {
                        handlePacket(message);
                    }

                    @Override
                    public void onMessage(ByteBuffer bytes) {
                        byte[] data = new byte[bytes.remaining()];
                        bytes.get(data);
                        if (data.length > 14) {
                            String bodyOnly = new String(data, 14, data.length - 14, StandardCharsets.UTF_8);
                            handlePacket(bodyOnly);
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        CheeseBridge.LOGGER.warn("[SOOP] 소켓 닫힘 - 코드: {}, 사유: {}, 원격여부: {}", code, reason, remote);
                        stopKeepAlive();
                    }

                    @Override
                    public void onError(Exception ex) {
                        CheeseBridge.LOGGER.error("[SOOP] 에러", ex);
                    }
                };

                webSocket.connect();
            }
            catch (Exception e) { CheeseBridge.LOGGER.error("[SOOP] 연결 중 예외", e); }
        }, "Soop-Connect-Thread").start();
    }

    private void handlePacket(String body) {
        CheeseBridge.LOGGER.info("[SOOP] handlePacket 호출됨 - 바디: {}", body);

        try {
            List<String> parts = SoopProtocol.parseBody(body);
            if (parts.isEmpty()) { return; }

            if (!isLoggedIn && body.contains("|")) {
                this.isLoggedIn = true;
                CheeseBridge.LOGGER.info("[SOOP] 로그인 승인됨. 채널 입장 시도.");
                List<String> joinBody = new ArrayList<>();
                joinBody.add(chatNo);
                joinBody.add(ticket);
                joinBody.add("");
                joinBody.add("");
                joinBody.add("");
                webSocket.send(SoopProtocol.makePacket(SoopProtocol.SVC_JOINCH, joinBody));

                Minecraft.getInstance().execute(() -> PykeLibClient.sendSystemMessage(COLOR.LIME.getColor(), "숲(SOOP) 연동 성공!"));
                return;
            }

            int offset = parts.getFirst().isEmpty() ? 1 : 0;

            if (parts.size() >= 4 + offset) {
                String potentialAmount = parts.get(3 + offset);
                if (potentialAmount.matches("\\d+")) {
                    processFilteredSoopDonation(parts, offset);
                }
            }
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("[SOOP] 패킷 처리 중 오류: ", e);
        }
    }

    private void processFilteredSoopDonation(List<String> parts, int offset) {
        if (parts.size() < 4 + offset) { return; }

        String nickname = parts.get(2 + offset);
        String amount = parts.get(3 + offset);
        String donationType = "별풍선";

        CheeseBridge.LOGGER.info("[SOOP] {} 정산 감지: {} ({}개)", donationType, nickname, amount);
        ClientPlayNetworking.send(new C2S_DonationPayload(nickname, amount, donationType, "SOOP"));
    }

    private void startKeepAlive() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (webSocket != null && webSocket.isOpen()) {
                webSocket.send(SoopProtocol.makePacket(SoopProtocol.SVC_KEEPALIVE, new ArrayList<>()));
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void stopKeepAlive() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public void disconnect() {
        stopKeepAlive();
        if (webSocket != null) {
            webSocket.close();
            webSocket = null;
        }
        CheeseBridge.LOGGER.info("숲(SOOP) 연결 해제됨.");
    }
}