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
import java.util.*;

public class EarthMCNearbyNewTownsLookup {

    private static final String BASE_URL = "https://api.earthmc.net/v3/aurora";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final int SEARCH_RADIUS = 3000;
    private static final int NEW_TOWN_DAYS = 30;

    public static String getNearbyNewTownsAsString(String townName) {
        try {
            JsonArray nearbyArray = queryNearbyTownList(townName, SEARCH_RADIUS);
            if (nearbyArray == null) {
                return "❌ 未找到城镇 [" + townName + "]，请检查名称是否正确。";
            }
            if (nearbyArray.size() == 0) {
                return "✅ 已找到城镇 [" + townName + "]，但附近 " + SEARCH_RADIUS + " 格内没有其他城镇。";
            }

            Map<String, JsonObject> townDetailsMap = batchQueryTownDetails(nearbyArray);
            if (townDetailsMap.isEmpty()) {
                return "❌ 无法获取附近城镇的详细信息。";
            }

            List<JsonObject> newTowns = filterNewTowns(townDetailsMap.values(), NEW_TOWN_DAYS);
            if (newTowns.isEmpty()) {
                return "✅ 附近 " + SEARCH_RADIUS + " 格内共有 " + townDetailsMap.size() +
                       " 个城镇，但近 " + NEW_TOWN_DAYS + " 天内没有新建城镇，暂时安全。";
            }

            return formatNewTownsResult(townName, SEARCH_RADIUS, newTowns);
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 查询附近新建城镇失败，请稍后重试。";
        }
    }

    private static JsonArray queryNearbyTownList(String townName, int radius) throws Exception {
        JsonObject requestBody = new JsonObject();
        JsonArray queryArray = new JsonArray();

        JsonObject query = new JsonObject();
        query.addProperty("target_type", "TOWN");
        query.addProperty("target", townName);
        query.addProperty("search_type", "TOWN");
        query.addProperty("radius", radius);
        queryArray.add(query);

        requestBody.add("query", queryArray);
        String requestJson = GSON.toJson(requestBody);

        String responseJson = sendPostRequest(BASE_URL + "/nearby", requestJson);
        JsonArray responseArray = JsonParser.parseString(responseJson).getAsJsonArray();

        if (responseArray.size() == 0) {
            return null;
        }
        return responseArray.get(0).getAsJsonArray();
    }

    private static Map<String, JsonObject> batchQueryTownDetails(JsonArray uuidArray) throws Exception {
        Map<String, JsonObject> resultMap = new HashMap<>();
        if (uuidArray.size() == 0) return resultMap;

        JsonArray queryList = new JsonArray();
        for (JsonElement elem : uuidArray) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("uuid")) {
                queryList.add(safeGetString(obj, "uuid"));
            }
        }

        JsonObject requestBody = new JsonObject();
        requestBody.add("query", queryList);

        String responseJson = sendPostRequest(BASE_URL + "/towns", GSON.toJson(requestBody));
        JsonArray townsArray = JsonParser.parseString(responseJson).getAsJsonArray();

        for (JsonElement elem : townsArray) {
            JsonObject town = elem.getAsJsonObject();
            String uuid = safeGetString(town, "uuid");
            if (uuid != null) {
                resultMap.put(uuid, town);
            }
        }
        return resultMap;
    }

    private static List<JsonObject> filterNewTowns(Collection<JsonObject> towns, int maxDays) {
        List<JsonObject> newTowns = new ArrayList<>();
        long now = System.currentTimeMillis();
        long cutoffTime = now - (maxDays * 24L * 60 * 60 * 1000);

        for (JsonObject town : towns) {
            Long registered = getRegisteredTime(town);
            if (registered != null && registered >= cutoffTime) {
                newTowns.add(town);
            }
        }
        return newTowns;
    }

    private static String formatNewTownsResult(String targetTown, int radiusBlocks, List<JsonObject> newTowns) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ ===== 附近新建城镇威胁预警 ===== ⚠️\n");
        sb.append("查询中心: ").append(targetTown).append("\n");
        sb.append("搜索半径: ").append(radiusBlocks).append(" 格\n");
        sb.append("近 ").append(NEW_TOWN_DAYS).append(" 天内新建城镇 (").append(newTowns.size()).append(" 个):\n\n");

        newTowns.sort((a, b) -> {
            Long timeA = getRegisteredTime(a);
            Long timeB = getRegisteredTime(b);
            if (timeA == null) timeA = 0L;
            if (timeB == null) timeB = 0L;
            return Long.compare(timeB, timeA);
        });

        int count = 0;
        for (JsonObject town : newTowns) {
            count++;
            String name = safeGetString(town, "name");
            String founder = safeGetString(town, "founder");
            Long registered = getRegisteredTime(town);
            String dateStr = registered != null ? formatTimestamp(registered) : "未知";
            long daysAgo = registered != null ? (System.currentTimeMillis() - registered) / (24 * 60 * 60 * 1000) : -1;

            sb.append("🔥 ").append(count).append(". ").append(name != null ? name : "未知").append("\n");
            sb.append("   创始人: ").append(founder != null ? founder : "未知").append("\n");
            sb.append("   创建时间: ").append(dateStr);
            if (daysAgo >= 0) {
                sb.append(" (").append(daysAgo).append(" 天前)");
            }
            sb.append("\n");

            JsonObject nation = town.has("nation") && !town.get("nation").isJsonNull()
                    ? town.getAsJsonObject("nation") : null;
            if (nation != null) {
                String nationName = safeGetString(nation, "name");
                if (nationName != null) {
                    sb.append("   国家: ").append(nationName).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("⚠️ 以上新建城镇可能对您构成扩张威胁，请及时关注！");
        return sb.toString();
    }

    private static String safeGetString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : null;
    }

    private static Long getRegisteredTime(JsonObject town) {
        JsonObject timestamps = town.has("timestamps") && !town.get("timestamps").isJsonNull()
                ? town.getAsJsonObject("timestamps") : null;
        if (timestamps != null && timestamps.has("registered") && !timestamps.get("registered").isJsonNull()) {
            return timestamps.get("registered").getAsLong();
        }
        return null;
    }

    private static String formatTimestamp(long millis) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return DATE_FORMATTER.format(dateTime);
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
}