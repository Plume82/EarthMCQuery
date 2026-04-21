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

public class EarthMCTownLookup {

    private static final String BASE_URL = "https://api.earthmc.net/v3/aurora";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 供机器人调用的静态方法，根据城镇名称返回格式化的信息字符串
     */
    public static String getTownDetailsAsString(String townName) {
        try {
            JsonObject requestBody = new JsonObject();
            JsonArray queryArray = new JsonArray();
            queryArray.add(townName);
            requestBody.add("query", queryArray);

            String requestJson = GSON.toJson(requestBody);
            String responseJson = sendPostRequest(BASE_URL + "/towns", requestJson);

            JsonArray townsArray = JsonParser.parseString(responseJson).getAsJsonArray();
            if (townsArray.size() == 0) {
                return "❌ 未查询到此城镇";
            }

            JsonObject town = townsArray.get(0).getAsJsonObject();
            return formatTownDetails(town);
        } catch (Exception e) {
            e.printStackTrace();
            return "查询城镇失败: " + e.getMessage();
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

    private static String formatTownDetails(JsonObject town) {
        StringBuilder sb = new StringBuilder();

        // 基础信息（故意忽略 uuid）
        String name = getString(town, "name");
        String board = getStringOrNull(town, "board");
        String founder = getString(town, "founder");
        String wiki = getStringOrNull(town, "wiki");

        JsonObject mayor = null;
        if (town.has("mayor") && !town.get("mayor").isJsonNull()) {
            mayor = town.getAsJsonObject("mayor");
        }
        JsonObject nation = null;
        if (town.has("nation") && !town.get("nation").isJsonNull()) {
            nation = town.getAsJsonObject("nation");
        }
        JsonObject timestamps = town.has("timestamps") && !town.get("timestamps").isJsonNull()
                ? town.getAsJsonObject("timestamps") : new JsonObject();
        JsonObject status = town.has("status") && !town.get("status").isJsonNull()
                ? town.getAsJsonObject("status") : new JsonObject();
        JsonObject stats = town.has("stats") && !town.get("stats").isJsonNull()
                ? town.getAsJsonObject("stats") : new JsonObject();
        JsonObject coords = town.has("coordinates") && !town.get("coordinates").isJsonNull()
                ? town.getAsJsonObject("coordinates") : new JsonObject();

        sb.append("===== ").append(name).append(" =====\n");

        // 创始人 & 市长
        sb.append("创始人: ").append(founder).append("\n");
        sb.append("市长: ").append(mayor != null ? getString(mayor, "name") : "无").append("\n");

        // 国家
        if (nation != null) {
            sb.append("国家: ").append(getString(nation, "name")).append("\n");
        } else {
            sb.append("国家: 无\n");
        }

        // 公告板
        if (board != null && !board.isEmpty()) {
            sb.append("公告: ").append(board).append("\n");
        }
        // Wiki
        if (wiki != null && !wiki.isEmpty()) {
            sb.append("Wiki: ").append(wiki).append("\n");
        }

        // 状态标志
        sb.append("状态: ");
        sb.append(status.has("isPublic") && status.get("isPublic").getAsBoolean() ? "公共 " : "");
        sb.append(status.has("isOpen") && status.get("isOpen").getAsBoolean() ? "开放 " : "");
        sb.append(status.has("isNeutral") && status.get("isNeutral").getAsBoolean() ? "中立 " : "");
        sb.append(status.has("isCapital") && status.get("isCapital").getAsBoolean() ? "首都 " : "");
        sb.append(status.has("isOverClaimed") && status.get("isOverClaimed").getAsBoolean() ? "超限 " : "");
        sb.append(status.has("isRuined") && status.get("isRuined").getAsBoolean() ? "已废弃 " : "");
        sb.append(status.has("isForSale") && status.get("isForSale").getAsBoolean() ? "出售中 " : "");
        sb.append(status.has("canOutsidersSpawn") && status.get("canOutsidersSpawn").getAsBoolean() ? "外人可传送" : "");
        sb.append("\n");

        // 统计数据
        int numTownBlocks = stats.has("numTownBlocks") ? stats.get("numTownBlocks").getAsInt() : 0;
        int maxTownBlocks = stats.has("maxTownBlocks") ? stats.get("maxTownBlocks").getAsInt() : 0;
        int numResidents = stats.has("numResidents") ? stats.get("numResidents").getAsInt() : 0;
        int numTrusted = stats.has("numTrusted") ? stats.get("numTrusted").getAsInt() : 0;
        int numOutlaws = stats.has("numOutlaws") ? stats.get("numOutlaws").getAsInt() : 0;
        double balance = stats.has("balance") ? stats.get("balance").getAsDouble() : 0.0;

        sb.append("领地块: ").append(numTownBlocks).append("/").append(maxTownBlocks);
        sb.append("  居民: ").append(numResidents);
        sb.append("  信任: ").append(numTrusted);
        sb.append("  驱逐: ").append(numOutlaws).append("\n");
        sb.append("金锭: ").append(balance).append(" G");

        JsonElement priceElem = stats.get("forSalePrice");
        if (priceElem != null && !priceElem.isJsonNull()) {
            sb.append("  售价: ").append(priceElem.getAsDouble()).append(" G");
        }
        sb.append("\n");

        // 时间信息
        sb.append("创建时间: ");
        appendTimestamp(sb, timestamps.get("registered"));
        sb.append("  加入国家: ");
        appendTimestamp(sb, timestamps.get("joinedNationAt"));
        JsonElement ruinedAt = timestamps.get("ruinedAt");
        if (ruinedAt != null && !ruinedAt.isJsonNull()) {
            sb.append("  废弃时间: ");
            appendTimestamp(sb, ruinedAt);
        }
        sb.append("\n");

        // 坐标信息
        if (coords.has("spawn") && !coords.get("spawn").isJsonNull()) {
            JsonObject spawn = coords.getAsJsonObject("spawn");
            sb.append("出生点: ");
            sb.append(spawn.get("world").getAsString()).append(" ");
            sb.append(formatCoord(spawn.get("x").getAsDouble())).append(", ");
            sb.append(spawn.get("y").getAsInt()).append(", ");
            sb.append(formatCoord(spawn.get("z").getAsDouble())).append("\n");
        }
        if (coords.has("homeBlock") && !coords.get("homeBlock").isJsonNull()) {
            JsonArray homeBlock = coords.getAsJsonArray("homeBlock");
            sb.append("Home区块: [").append(homeBlock.get(0).getAsInt())
              .append(", ").append(homeBlock.get(1).getAsInt()).append("]\n");
        }
        if (coords.has("townBlocks") && !coords.get("townBlocks").isJsonNull()) {
            JsonElement tbElem = coords.get("townBlocks");
            if (tbElem.isJsonArray()) {
                JsonArray townBlocks = tbElem.getAsJsonArray();
                sb.append("领地区块总数: ").append(townBlocks.size()).append(" 个\n");
            }
        }

        // 居民、信任、被驱逐者列表
        appendPlayerList(sb, "居民", town.getAsJsonArray("residents"), 10);
        appendPlayerList(sb, "信任", town.getAsJsonArray("trusted"), 10);
        appendPlayerList(sb, "被驱逐", town.getAsJsonArray("outlaws"), 10);

        // 职位分配
        if (town.has("ranks") && !town.get("ranks").isJsonNull()) {
            JsonObject ranks = town.getAsJsonObject("ranks");
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
        } else {
            sb.append("头衔: 未设置\n");
        }

        // 季度数量
        if (town.has("quarters") && !town.get("quarters").isJsonNull()) {
            JsonArray quarters = town.getAsJsonArray("quarters");
            if (quarters.size() > 0) {
                sb.append("Quarter数量: ").append(quarters.size()).append(" 个\n");
            }
        }

        sb.append("==============================");
        return sb.toString();
    }

    // ================= 辅助方法 =================
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

    private static void appendPlayerList(StringBuilder sb, String title, JsonArray array, int max) {
        if (array == null || array.size() == 0) {
            return;
        }
        sb.append(title).append("(").append(array.size()).append("): ");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(max, array.size()); i++) {
            JsonElement elem = array.get(i);
            if (elem.isJsonObject()) {
                JsonObject player = elem.getAsJsonObject();
                if (player.has("name")) {
                    names.add(player.get("name").getAsString());
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