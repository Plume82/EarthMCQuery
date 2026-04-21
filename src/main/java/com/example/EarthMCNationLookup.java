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
import java.util.Map;

public class EarthMCNationLookup {

    private static final String BASE_URL = "https://api.earthmc.net/v3/aurora";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 供机器人调用的静态方法，根据国家名称返回格式化的信息字符串
     */
    public static String getNationDetailsAsString(String nationName) {
        try {
            JsonObject requestBody = new JsonObject();
            JsonArray queryArray = new JsonArray();
            queryArray.add(nationName);
            requestBody.add("query", queryArray);

            String requestJson = GSON.toJson(requestBody);
            String responseJson = sendPostRequest(BASE_URL + "/nations", requestJson);

            JsonArray nationsArray = JsonParser.parseString(responseJson).getAsJsonArray();
            if (nationsArray.size() == 0) {
                return "❌ 未查询到此国家";
            }

            JsonObject nation = nationsArray.get(0).getAsJsonObject();
            return formatNationDetails(nation);
        } catch (Exception e) {
            e.printStackTrace();
            return "查询国家失败: " + e.getMessage();
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

    private static String formatNationDetails(JsonObject nation) {
        StringBuilder sb = new StringBuilder();

        String name = getString(nation, "name");
        String board = getStringOrNull(nation, "board");
        String wiki = getStringOrNull(nation, "wiki");
        String dynmapColour = getStringOrNull(nation, "dynmapColour");
        String dynmapOutline = getStringOrNull(nation, "dynmapOutline");

        JsonObject king = null;
        if (nation.has("king") && !nation.get("king").isJsonNull()) {
            king = nation.getAsJsonObject("king");
        }
        JsonObject capital = null;
        if (nation.has("capital") && !nation.get("capital").isJsonNull()) {
            capital = nation.getAsJsonObject("capital");
        }
        JsonObject timestamps = nation.has("timestamps") && !nation.get("timestamps").isJsonNull()
                ? nation.getAsJsonObject("timestamps") : new JsonObject();
        JsonObject status = nation.has("status") && !nation.get("status").isJsonNull()
                ? nation.getAsJsonObject("status") : new JsonObject();
        JsonObject stats = nation.has("stats") && !nation.get("stats").isJsonNull()
                ? nation.getAsJsonObject("stats") : new JsonObject();
        JsonObject coords = nation.has("coordinates") && !nation.get("coordinates").isJsonNull()
                ? nation.getAsJsonObject("coordinates") : new JsonObject();

        sb.append("===== ").append(name).append(" =====\n");

        // 国王
        sb.append("首领: ").append(king != null ? getString(king, "name") : "无").append("\n");
        // 首都
        sb.append("首都: ").append(capital != null ? getString(capital, "name") : "无").append("\n");

        // 公告板
        if (board != null && !board.isEmpty()) {
            sb.append("公告: ").append(board).append("\n");
        }
        // Wiki
        if (wiki != null && !wiki.isEmpty()) {
            sb.append("Wiki: ").append(wiki).append("\n");
        }
        // Dynmap 颜色
        if (dynmapColour != null) sb.append("地图颜色: #").append(dynmapColour).append("\n");
        if (dynmapOutline != null) sb.append("轮廓颜色: #").append(dynmapOutline).append("\n");

        // 状态
        sb.append("状态: ");
        sb.append(status.has("isPublic") && status.get("isPublic").getAsBoolean() ? "公共 " : "");
        sb.append(status.has("isOpen") && status.get("isOpen").getAsBoolean() ? "开放 " : "");
        sb.append(status.has("isNeutral") && status.get("isNeutral").getAsBoolean() ? "中立 " : "");
        sb.append("\n");

        // 统计数据
        int numTownBlocks = stats.has("numTownBlocks") ? stats.get("numTownBlocks").getAsInt() : 0;
        int numResidents = stats.has("numResidents") ? stats.get("numResidents").getAsInt() : 0;
        int numTowns = stats.has("numTowns") ? stats.get("numTowns").getAsInt() : 0;
        int numAllies = stats.has("numAllies") ? stats.get("numAllies").getAsInt() : 0;
        int numEnemies = stats.has("numEnemies") ? stats.get("numEnemies").getAsInt() : 0;
        double balance = stats.has("balance") ? stats.get("balance").getAsDouble() : 0.0;

        sb.append("领地: ").append(numTownBlocks);
        sb.append("  居民: ").append(numResidents);
        sb.append("  城镇数: ").append(numTowns).append("\n");
        sb.append("盟友: ").append(numAllies);
        sb.append("  敌人: ").append(numEnemies);
        sb.append("  金锭: ").append(balance).append(" G\n");

        // 创建时间
        sb.append("创建时间: ");
        appendTimestamp(sb, timestamps.get("registered"));
        sb.append("\n");

        // 坐标
        if (coords.has("spawn") && !coords.get("spawn").isJsonNull()) {
            JsonObject spawn = coords.getAsJsonObject("spawn");
            sb.append("出生点: ");
            sb.append(spawn.get("world").getAsString()).append(" ");
            sb.append(formatCoord(spawn.get("x").getAsDouble())).append(", ");
            sb.append(spawn.get("y").getAsInt()).append(", ");
            sb.append(formatCoord(spawn.get("z").getAsDouble())).append("\n");
        }

        // 城镇列表（最多显示前8个）
        appendNameList(sb, "城镇", nation.getAsJsonArray("towns"), 8);
        // 盟友列表
        appendNameList(sb, "盟友", nation.getAsJsonArray("allies"), 8);
        // 敌人列表
        appendNameList(sb, "敌人", nation.getAsJsonArray("enemies"), 8);

        // 职位
        if (nation.has("ranks") && !nation.get("ranks").isJsonNull()) {
            JsonObject ranks = nation.getAsJsonObject("ranks");
            boolean hasRank = false;
            for (Map.Entry<String, JsonElement> entry : ranks.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonArray()) {
                    JsonArray members = value.getAsJsonArray();
                    if (members.size() > 0) {
                        List<String> names = new ArrayList<>();
                        for (JsonElement memberElem : members) {
                            if (memberElem.isJsonPrimitive()) {
                                names.add(memberElem.getAsString());
                            } else if (memberElem.isJsonObject()) {
                                JsonObject memberObj = memberElem.getAsJsonObject();
                                if (memberObj.has("name")) {
                                    names.add(memberObj.get("name").getAsString());
                                }
                            }
                        }
                        if (!names.isEmpty()) {
                            sb.append(entry.getKey()).append(": ");
                            sb.append(String.join(", ", names)).append("\n");
                            hasRank = true;
                        }
                    }
                }
            }
            if (!hasRank) {
                sb.append("头衔: 无\n");
            }
        }

        sb.append("==============================");
        return sb.toString();
    }

    // 辅助方法
    private static String getString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : "";
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : null;
    }

    private static void appendTimestamp(StringBuilder sb, JsonElement timestampElem) {
        if (timestampElem == null || timestampElem.isJsonNull()) {
            sb.append("无");
            return;
        }
        long millis = timestampElem.getAsLong();
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        sb.append(DATE_FORMATTER.format(dateTime));
    }

    private static String formatCoord(double value) {
        return String.format("%.2f", value);
    }

    private static void appendNameList(StringBuilder sb, String title, JsonArray array, int max) {
        if (array == null || array.size() == 0) {
            return;
        }
        sb.append(title).append("(").append(array.size()).append("): ");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(max, array.size()); i++) {
            JsonElement elem = array.get(i);
            if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                if (obj.has("name")) {
                    names.add(obj.get("name").getAsString());
                }
            } else if (elem.isJsonPrimitive()) {
                names.add(elem.getAsString());
            }
        }
        sb.append(String.join(", ", names));
        if (array.size() > max) {
            sb.append(" 等");
        }
        sb.append("\n");
    }
}