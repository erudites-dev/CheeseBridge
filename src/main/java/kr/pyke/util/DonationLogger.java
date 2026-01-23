package kr.pyke.util;

import kr.pyke.CheeseBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DonationLogger {
    private static final File LOG_FILE = FabricLoader.getInstance().getGameDir().resolve("logs/donation_history.log").toFile();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static synchronized void writeToFile(String logLevel, String content) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) { return; }

        try {
            if (!LOG_FILE.getParentFile().exists()) { LOG_FILE.getParentFile().mkdirs(); }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, StandardCharsets.UTF_8, true))) {
                String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
                writer.write(String.format("[%s] [%s] %s", timestamp, logLevel, content));
                writer.newLine();
            }
        }
        catch (IOException e) { CheeseBridge.LOGGER.error("후원 로그 파일 쓰기 실패: ", e); }
    }

    public static void logInfo(String playerName, String message) {
        writeToFile("INFO", String.format("%s(%s)", message, playerName));
    }

    public static void logDonation(String playerName, String amount) {
        String formattedAmount = amount;

        try { formattedAmount = NumberFormat.getInstance().format(Integer.parseInt(amount)); }
        catch(Exception ignored) { }

        writeToFile("DONATION", String.format("%s → %s", playerName, formattedAmount));
    }

    public static void logDonation(String playerName, String amount, String managerName) {
        String formattedAmount = amount;

        try { formattedAmount = NumberFormat.getInstance().format(Integer.parseInt(amount)); }
        catch(Exception ignored) { }

        writeToFile("DONATION", String.format("%s → %s | 담당자: %s", playerName, formattedAmount, managerName));
    }

    public static void logReward(String playerName, String reward) {
        writeToFile("REWARD", String.format("%s → %s", playerName, reward));
    }
}
