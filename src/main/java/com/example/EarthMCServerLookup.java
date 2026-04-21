package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;

public class EarthMCServerLookup {

    private static final String SERVER_URL = "https://api.earthmc.net/v3/aurora/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    //private static final DateTimeFormatter DATE_FORMATTER =
    //       DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    
    //    供机器人调用的静态方法，返回服务器状态摘要字符串
    public static String getServerStatusAsString() {
        try {
            String responseJson = sendGetRequest(SERVER_URL);
            JsonObject server = JsonParser.parseString(responseJson).getAsJsonObject();
            return formatServerStatus(server);
        } catch (Exception e) {
            e.printStackTrace();
            return "查询服务器状态失败: " + e.getMessage();
        }
    }

    private static String sendGetRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP 请求失败，状态码: " + response.statusCode());
        }
        return response.body();
    }

    private static String formatServerStatus(JsonObject server) {
        StringBuilder sb = new StringBuilder();

        String version = getString(server, "version");
        String moonPhase = getString(server, "moonPhase");

        JsonObject timestamps = server.has("timestamps") && !server.get("timestamps").isJsonNull()
                ? server.getAsJsonObject("timestamps") : new JsonObject();
        JsonObject status = server.has("status") && !server.get("status").isJsonNull()
                ? server.getAsJsonObject("status") : new JsonObject();
        JsonObject stats = server.has("stats") && !server.get("stats").isJsonNull()
                ? server.getAsJsonObject("stats") : new JsonObject();
        JsonObject voteParty = server.has("voteParty") && !server.get("voteParty").isJsonNull()
                ? server.getAsJsonObject("voteParty") : new JsonObject();

        sb.append("===== EarthMC 服务器状态 =====\n");
        sb.append("版本: ").append(version).append("\n");
        sb.append("月相: ").append(moonPhase).append("\n");

        // 游戏内时间
        long serverTimeOfDay = timestamps.has("serverTimeOfDay") ? timestamps.get("serverTimeOfDay").getAsLong() : 0;
        long newDayTime = timestamps.has("newDayTime") ? timestamps.get("newDayTime").getAsLong() : 43200;
        sb.append("游戏时间: ").append(formatGameTime(serverTimeOfDay)).append("\n");

        // 天气
        boolean hasStorm = status.has("hasStorm") && status.get("hasStorm").getAsBoolean();
        boolean isThundering = status.has("isThundering") && status.get("isThundering").getAsBoolean();
        sb.append("天气: ");
        if (isThundering) sb.append("⛈️ 雷暴");
        else if (hasStorm) sb.append("🌧️ 雨天");
        else sb.append("☀️ 晴朗");
        sb.append("\n");

        // 玩家统计
        int onlinePlayers = stats.has("numOnlinePlayers") ? stats.get("numOnlinePlayers").getAsInt() : 0;
        int maxPlayers = stats.has("maxPlayers") ? stats.get("maxPlayers").getAsInt() : 200;
        int onlineNomads = stats.has("numOnlineNomads") ? stats.get("numOnlineNomads").getAsInt() : 0;
        sb.append("在线玩家: ").append(onlinePlayers).append("/").append(maxPlayers);
        sb.append(" (无城镇玩家: ").append(onlineNomads).append(")\n");

        // 城镇与国家统计
        int numTowns = stats.has("numTowns") ? stats.get("numTowns").getAsInt() : 0;
        int numNations = stats.has("numNations") ? stats.get("numNations").getAsInt() : 0;
        int numResidents = stats.has("numResidents") ? stats.get("numResidents").getAsInt() : 0;
        sb.append("城镇: ").append(numTowns).append("  国家: ").append(numNations);
        sb.append("  总居民: ").append(numResidents).append("\n");

        // 领地统计
        //int numTownBlocks = stats.has("numTownBlocks") ? stats.get("numTownBlocks").getAsInt() : 0;
        //int numQuarters = stats.has("numQuarters") ? stats.get("numQuarters").getAsInt() : 0;
        //sb.append("领地区块: ").append(numTownBlocks).append("  季度: ").append(numQuarters).append("\n");

        // 投票派对进度
        if (voteParty.has("target") && voteParty.has("numRemaining")) {
            int target = voteParty.get("target").getAsInt();
            int remaining = voteParty.get("numRemaining").getAsInt();
            int current = target - remaining;
            double percent = (current * 100.0) / target;
            sb.append("投票派对: ").append(current).append("/").append(target);
            sb.append(" (").append(String.format("%.1f", percent)).append("%)");
        }

        sb.append("\n==============================");
        return sb.toString();
    }

    private static String formatGameTime(long seconds) {
        // Minecraft 游戏内一天有 24000 ticks，但这里返回的是秒
        // 将秒转换为游戏内刻（20 ticks/秒），然后格式化
        long ticks = seconds * 20;
        long hours = (ticks / 1000 + 6) % 24;
        long minutes = (ticks % 1000) * 60 / 1000;
        return String.format("%02d:%02d (游戏内)", hours, minutes);
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }
}