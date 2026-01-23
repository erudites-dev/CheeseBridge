package kr.pyke.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import kr.pyke.CheeseBridge;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ChzzkManager {
    private static final ChzzkManager INSTANCE = new ChzzkManager();
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Socket socket;

    private ChzzkManager() { }

    public static ChzzkManager getInstance() { return INSTANCE; }

    public void connect(String accessToken) {
        CheeseBridge.LOGGER.info("[0] disconnect() 함수 호출. 연결 전 중복 방지를 위해 기존 연결 끊기");
        disconnect();

        CheeseBridge.LOGGER.info("[1] connect() 함수 호출됨. 스레드 시작 준비...");

        new Thread(() -> {
            CheeseBridge.LOGGER.info("[2] 스레드 진입 성공.");

            try {
                Class.forName("io.socket.client.IO");
                CheeseBridge.LOGGER.info("[3] Socket.IO 라이브러리 발견됨 (OK).");
            }
            catch (Throwable t) {
                CheeseBridge.LOGGER.error("[치명적 오류] Socket.IO 라이브러리가 없습니다! build.gradle을 확인하세요.", t);
                return;
            }

            try {
                CheeseBridge.LOGGER.info("[4] 치지직 인증 서버에 HTTP 요청을 보냅니다...");

                HttpRequest authRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://openapi.chzzk.naver.com/open/v1/sessions/auth"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

                HttpResponse<String> authResponse = httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());

                CheeseBridge.LOGGER.info("[5] HTTP 응답 받음. 상태 코드: " + authResponse.statusCode());

                if (authResponse.statusCode() == 401) {
                    CheeseBridge.LOGGER.warn("치지직 인증 실패 (만료됨). 토큰 갱신을 요청합니다.");
                    ClientPlayNetworking.send(new C2S_RequestRefreshPayload());
                    return;
                }

                if (authResponse.statusCode() != 200) {
                    CheeseBridge.LOGGER.error("치지직 인증 실패 (HTTP {}): {}", authResponse.statusCode(), authResponse.body());
                    return;
                }

                JsonObject responseJson = gson.fromJson(authResponse.body(), JsonObject.class);
                String socketUrl = responseJson.getAsJsonObject("content").get("url").getAsString();

                CheeseBridge.LOGGER.info("소켓 URL 획득: {}", socketUrl);

                IO.Options options = new IO.Options();
                options.transports = new String[]{"websocket"};
                options.reconnection = true;

                socket = IO.socket(socketUrl, options);

                socket.on(Socket.EVENT_CONNECT, args -> {
                    CheeseBridge.LOGGER.info("[7] 소켓 연결 성공 (EVENT_CONNECT)");

                    Minecraft.getInstance().execute(() -> PykeLibClient.sendSystemMessage(COLOR.LIME.getColor(), "치지직 소켓 서버에 연결 되었습니다."));
                });
                socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                    CheeseBridge.LOGGER.error("소켓 연결 에러: " + args[0]);

                    Minecraft.getInstance().execute(() -> PykeLibClient.sendSystemMessage(COLOR.AQUA.getColor(), "치지직 소켓 연결에 실패했습니다. 관리자에게 문의 해주세요."));
                });

                socket.on("SYSTEM", args -> {
                    CheeseBridge.LOGGER.info("[SYSTEM] 메시지 수신: " + args[0]);

                    try {
                        JsonObject payload = gson.fromJson(args[0].toString(), JsonObject.class);
                        String type = payload.get("type").getAsString();

                        if ("connected".equals(type)) {
                            String sessionKey = payload.getAsJsonObject("data").get("sessionKey").getAsString();
                            CheeseBridge.LOGGER.info("세션 키 획득: {}. 후원 구독을 요청합니다.", sessionKey);

                            requestSubscription(accessToken, sessionKey);
                        }
                        else if ("subscribed".equals(type)) {
                            CheeseBridge.LOGGER.info("후원 이벤트 구독 완료! 대기 중...");

                            Minecraft.getInstance().execute(() -> PykeLibClient.sendSystemMessage(COLOR.LIME.getColor(), "연동이 완료되었습니다."));
                        }
                    }
                    catch (Exception e) { CheeseBridge.LOGGER.error("시스템 메시지 처리 중 오류: ", e); }
                });

                socket.on("DONATION", args -> {
                    try {
                        JsonObject data = gson.fromJson(args[0].toString(), JsonObject.class);

                        String amount = data.get("payAmount").getAsString();
                        String text = data.has("donationText") ? data.get("donationText").getAsString() : "";
                        String nickname = data.has("donatorNickname") ? data.get("donatorNickname").getAsString() : "익명";

                        CheeseBridge.LOGGER.info("후원 발생! {}: {}원", nickname, amount);

                        ClientPlayNetworking.send(new C2S_DonationPayload(nickname, amount, text));
                    }
                    catch (Exception e) { CheeseBridge.LOGGER.error("후원 데이터 처리 중 오류: ", e); }
                });

                socket.connect();
                CheeseBridge.LOGGER.info("[6.5] socket.connect() 명령 내림 (비동기 대기중)");
            }
            catch (Throwable e) {
                CheeseBridge.LOGGER.error("치지직 연결 중 예외 발생: ", e);
            }
        }, "Chzzk-Connect-Thread").start();
    }

    private void requestSubscription(String accessToken, String sessionKey) {
        try {
            String url = "https://openapi.chzzk.naver.com/open/v1/sessions/events/subscribe/donation?sessionKey=" + sessionKey;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody()) // Body 없음 (Query Param 사용)
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) { CheeseBridge.LOGGER.info("구독 요청 전송 성공."); }
            else { CheeseBridge.LOGGER.error("구독 요청 실패 (Code {}): {}", response.statusCode(), response.body()); }
        }
        catch (Exception e) { CheeseBridge.LOGGER.error("구독 요청 중 예외 발생: ", e); }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket = null;
            CheeseBridge.LOGGER.info("치지직 소켓 연결이 해제되었습니다.");
        }
    }
}