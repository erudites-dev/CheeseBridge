package kr.pyke.client;

import com.sun.net.httpserver.HttpServer;
import kr.pyke.CheeseBridge;
import kr.pyke.network.payload.c2s.C2S_AuthCodePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ChzzkAuthServer {
    private HttpServer server;
    private final int PORT = 8080;

    public void start() {
        try {
            if (server != null) { server.stop(0); }

            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = null;
                String state = null;

                if (query != null) {
                    String[] pairs = query.split("&");
                    for (String pair : pairs) {
                        String[] kv = pair.split("=");
                        if (kv.length == 2) {
                            if ("code".equals(kv[0])) { code = kv[1]; }
                            else if ("state".equals(kv[0])) { state = kv[1]; }
                        }
                    }
                }

                String response;
                if (code != null && state != null) {
                    ClientPlayNetworking.send(new C2S_AuthCodePayload(code, state));
                    response = "<html><head><meta charset=\"utf-8\"></head><body style='text-align:center;padding-top:50px;'><h1>인증 완료!</h1><p>이제 브라우저를 닫고 게임으로 돌아가세요.</p></body></html>";
                }
                else { response = "<html><head><meta charset=\"utf-8\"></head><body><h1>연동 실패</h1><p>필수 정보가 누락되었습니다.</p></body></html>"; }

                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(response.getBytes(StandardCharsets.UTF_8)); }

                stop();
            });

            server.start();
            CheeseBridge.LOGGER.info("[Auth] 로컬 인증 서버 시작됨 (Port: {})", PORT);
        }
        catch (Exception e) { CheeseBridge.LOGGER.error("[Auth] 인증 서버 시작 실패: ", e); }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}