package com.example;

import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EarthMCPlayerLookup {

    private static final String BASE_URL = "https://api.earthmc.net/v3/aurora";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 供机器人调用的静态方法，根据玩家名返回格式化的信息字符串
     */
    public static String getPlayerDetailsAsString(String playerName) {
        try {
            JsonObject requestBody = new JsonObject();
            JsonArray queryArray = new JsonArray();
            queryArray.add(playerName);
            requestBody.add("query", queryArray);

            String requestJson = GSON.toJson(requestBody);
            String responseJson = sendPostRequest(BASE_URL + "/players", requestJson);

            JsonArray playersArray = JsonParser.parseString(responseJson).getAsJsonArray();
            if (playersArray.size() == 0) {
                return "❌ 未查询到此玩家";
            }

            JsonObject player = playersArray.get(0).getAsJsonObject();
            return formatPlayerDetails(player);
        } catch (Exception e) {
            e.printStackTrace();
            return "查询玩家失败: " + e.getMessage();
        }
    }

    private static String sendPostRequest(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP 请求失败，状态码: " + response.statusCode() + "，响应: " + response.body());
        }
        return response.body();
    }

    private static String formatPlayerDetails(JsonObject player) {
        StringBuilder sb = new StringBuilder();

        // 注意：不显示玩家真实用户名，统一称呼
        String uuid = getString(player, "uuid");
        String title = getStringOrNull(player, "title");
        //String surname = getStringOrNull(player, "surname");
        //String formattedName = getStringOrNull(player, "formattedName");
        String about = getStringOrNull(player, "about");

        JsonObject town = null;
        if (player.has("town") && !player.get("town").isJsonNull()) {
            town = player.getAsJsonObject("town");
        }
        JsonObject nation = null;
        if (player.has("nation") && !player.get("nation").isJsonNull()) {
            nation = player.getAsJsonObject("nation");
        }
        JsonObject timestamps = player.has("timestamps") && !player.get("timestamps").isJsonNull()
                ? player.getAsJsonObject("timestamps") : new JsonObject();
        JsonObject status = player.has("status") && !player.get("status").isJsonNull()
                ? player.getAsJsonObject("status") : new JsonObject();
        JsonObject stats = player.has("stats") && !player.get("stats").isJsonNull()
                ? player.getAsJsonObject("stats") : new JsonObject();
        JsonObject perms = player.has("perms") && !player.get("perms").isJsonNull()
                ? player.getAsJsonObject("perms") : null;
        JsonObject ranks = player.has("ranks") && !player.get("ranks").isJsonNull()
                ? player.getAsJsonObject("ranks") : null;

        sb.append("===== 玩家信息 =====\n");
        sb.append("您查询的玩家信息如下：\n\n");

        // 基本信息
        sb.append("UUID: ").append(uuid).append("\n");
        if (title != null) sb.append("头衔: ").append(title).append("\n");
        //if (surname != null) sb.append("姓氏: ").append(surname).append("\n");
        //if (formattedName != null) sb.append("格式化名称: ").append(formattedName).append("\n");
        if (about != null) sb.append("简介: ").append(about).append("\n");

        // 城镇与国家
        sb.append("所属城镇: ").append(town != null ? getString(town, "name") : "无").append("\n");
        sb.append("所属国家: ").append(nation != null ? getString(nation, "name") : "无").append("\n");

        // 状态
        sb.append("\n状态:\n");
        sb.append("  在线: ").append(boolToStr(status, "isOnline")).append("\n");
        sb.append("  是NPC: ").append(boolToStr(status, "isNPC")).append("\n");
        sb.append("  是市长: ").append(boolToStr(status, "isMayor")).append("\n");
        sb.append("  是国王: ").append(boolToStr(status, "isKing")).append("\n");
        sb.append("  有城镇: ").append(boolToStr(status, "hasTown")).append("\n");
        sb.append("  有国家: ").append(boolToStr(status, "hasNation")).append("\n");

        // 统计数据
        double balance = stats.has("balance") ? stats.get("balance").getAsDouble() : 0.0;
        int numFriends = stats.has("numFriends") ? stats.get("numFriends").getAsInt() : 0;
        sb.append("\n统计:\n");
        sb.append("  资金: ").append(balance).append(" G\n");
        sb.append("  好友数: ").append(numFriends).append("\n");

        // 时间戳
        sb.append("\n时间信息:\n");
        appendTimestampLine(sb, "  注册时间", timestamps.get("registered"));
        appendTimestampLine(sb, "  加入城镇时间", timestamps.get("joinedTownAt"));
        appendTimestampLine(sb, "  最后在线", timestamps.get("lastOnline"));

        // 权限概要（简化显示）
        if (perms != null) {
            sb.append("\n权限 (城镇内):\n");
            sb.append("  建筑: ").append(permArrayToStr(perms, "build")).append("\n");
            sb.append("  破坏: ").append(permArrayToStr(perms, "destroy")).append("\n");
            sb.append("  开关: ").append(permArrayToStr(perms, "switch")).append("\n");
            sb.append("  物品使用: ").append(permArrayToStr(perms, "itemUse")).append("\n");
            if (perms.has("flags") && !perms.get("flags").isJsonNull()) {
                JsonObject flags = perms.getAsJsonObject("flags");
                sb.append("  PvP: ").append(boolToStr(flags, "pvp")).append("\n");
                sb.append("  爆炸: ").append(boolToStr(flags, "explosion")).append("\n");
                sb.append("  火焰: ").append(boolToStr(flags, "fire")).append("\n");
                sb.append("  生物生成: ").append(boolToStr(flags, "mobs")).append("\n");
            }
        }

        // 职位
        if (ranks != null) {
            JsonArray townRanks = ranks.has("townRanks") ? ranks.getAsJsonArray("townRanks") : new JsonArray();
            JsonArray nationRanks = ranks.has("nationRanks") ? ranks.getAsJsonArray("nationRanks") : new JsonArray();
            sb.append("\n头衔:\n");
            sb.append("  城镇头衔: ").append(jsonArrayToString(townRanks, "无")).append("\n");
            sb.append("  国家头衔: ").append(jsonArrayToString(nationRanks, "无")).append("\n");
        }

        // 好友列表
        JsonArray friends = player.has("friends") ? player.getAsJsonArray("friends") : new JsonArray();
        sb.append("\n好友列表(").append(friends.size()).append("):\n");
        List<String> friendNames = new ArrayList<>();
        for (int i = 0; i < Math.min(10, friends.size()); i++) {
            JsonObject friend = friends.get(i).getAsJsonObject();
            friendNames.add(getString(friend, "name"));
        }
        if (!friendNames.isEmpty()) {
            sb.append("  ").append(String.join(", ", friendNames));
            if (friends.size() > 10) sb.append(" 等");
        } else {
            sb.append("  无");
        }

        sb.append("\n======================");
        return sb.toString();
    }

    // ----- 辅助方法 -----
    private static String getString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : "";
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : null;
    }

    private static String boolToStr(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull() && elem.getAsBoolean()) ? "是" : "否";
    }

    private static void appendTimestampLine(StringBuilder sb, String label, JsonElement timestampElem) {
        sb.append(label).append(": ");
        if (timestampElem == null || timestampElem.isJsonNull()) {
            sb.append("无");
        } else {
            long millis = timestampElem.getAsLong();
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            sb.append(DATE_FORMATTER.format(dateTime));
        }
        sb.append("\n");
    }

    private static String permArrayToStr(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        if (elem == null || !elem.isJsonArray()) return "未知";
        JsonArray arr = elem.getAsJsonArray();
        List<String> flags = new ArrayList<>();
        String[] roles = {"居民", "盟友", "外来者", "敌人"};
        for (int i = 0; i < Math.min(arr.size(), roles.length); i++) {
            if (arr.get(i).getAsBoolean()) {
                flags.add(roles[i]);
            }
        }
        return flags.isEmpty() ? "无" : String.join(", ", flags);
    }

    private static String jsonArrayToString(JsonArray arr, String emptyMsg) {
        if (arr == null || arr.size() == 0) return emptyMsg;
        List<String> items = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive()) items.add(e.getAsString());
            else if (e.isJsonObject() && e.getAsJsonObject().has("name"))
                items.add(e.getAsJsonObject().get("name").getAsString());
        }
        return String.join(", ", items);
    }
}