package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class BotClient extends WebSocketClient {

    private static final int CONNECT_TIMEOUT = 10000;
    private final long selfId;

    // ---------- 重连与监听支持 ----------
    public interface ConnectionListener {
        void onDisconnected();
    }

    private ConnectionListener listener;

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public BotClient(URI serverUri, long selfId) {
        super(serverUri);
        this.selfId = selfId;
        this.setConnectionLostTimeout(CONNECT_TIMEOUT);
    }

    public void connectWithTimeout() {
        try {
            if (!this.connectBlocking(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                System.err.println("❌ 连接超时，无法连接到 " + this.getURI());
                System.exit(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ 连接被中断");
            System.exit(1);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("✅ 私聊服务已连接到 NapCat");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            String postType = json.has("post_type") ? json.get("post_type").getAsString() : "";
            String messageType = json.has("message_type") ? json.get("message_type").getAsString() : "";

            if (!"message".equals(postType) && !"message_sent".equals(postType)) return;
            if (!"private".equals(messageType)) return;

            String rawMessage = json.has("raw_message") ? json.get("raw_message").getAsString() : "";
            if (rawMessage.isEmpty()) return;

            String command = rawMessage.trim().replaceAll("\\s+", " ");
            System.out.println("🔍 原始指令: [" + command + "]");
            long userId = json.get("user_id").getAsLong();

            // 统一拦截层（使用 SensitiveWordsFilter）
            String param = "";
            int spaceIndex = command.indexOf(' ');
            if (spaceIndex > 0) param = command.substring(spaceIndex + 1).trim();

            /* 
            if (!param.isEmpty() && SensitiveWordsFilter.containsSensitiveWord(param)) {
                sendPrivateMessage(userId, "❌ 查询内容包含违禁词，请求已被拒绝。");
                System.out.println("🚫 拦截违禁词查询（私聊）: " + param);
                return;
            }
            */

            if (command.startsWith("/town ") || command.startsWith("/t ")) {
                String townName = param;
                if (townName.isEmpty()) { sendPrivateMessage(userId, "❌ 请指定城镇名称"); return; }
                sendPrivateMessage(userId, EarthMCTownLookup.getTownDetailsAsString(townName));
            } else if (command.startsWith("/nation ") || command.startsWith("/n ")) {
                String nationName = param;
                if (nationName.isEmpty()) { sendPrivateMessage(userId, "❌ 请指定国家名称"); return; }
                sendPrivateMessage(userId, EarthMCNationLookup.getNationDetailsAsString(nationName));
            } else if (command.startsWith("/nearby ")) {
                String townName = param;
                if (townName.isEmpty()) { sendPrivateMessage(userId, "❌ 请指定城镇名称"); return; }
                sendPrivateMessage(userId, EarthMCNearbyNewTownsLookup.getNearbyNewTownsAsString(townName));
            } else if (command.startsWith("/player ") || command.startsWith("/res ")) {
                String playerName = param;
                if (playerName.isEmpty()) { sendPrivateMessage(userId, "❌ 请指定玩家名称"); return; }
                sendPrivateMessage(userId, EarthMCPlayerLookup.getPlayerDetailsAsString(playerName));
            } else if (command.trim().equals("/server")) {
                sendPrivateMessage(userId, EarthMCServerLookup.getServerStatusAsString());
            } else if (command.trim().equals("/help")) {
                sendPrivateMessage(userId, getHelpMessage());
            } else {
                sendPrivateMessage(userId, "❌ 未知指令，发送 /help 查看帮助");
            }
        } catch (Exception e) {
            System.err.println("💥 处理私聊消息出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getHelpMessage() {
        return "===== 机器人指令帮助（私聊）=====\n" +
               "/town <城镇名> 或 /t <城镇名>   - 查询城镇详细信息\n" +
               "/nation <国家名> 或 /n <国家名> - 查询国家详细信息\n" +
               "/player <玩家名> 或 /res <玩家名> - 查询玩家详细信息\n" +
               "/nearby <城镇名> - 查询附近3000格内30天内新建城镇\n" +
               "/server          - 查询服务器实时状态\n" +
               "/help            - 显示本帮助信息\n" +
               "================================";
    }

    private void sendPrivateMessage(long userId, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "send_private_msg");
        JsonObject params = new JsonObject();
        params.addProperty("user_id", userId);
        params.addProperty("message", message);
        payload.add("params", params);
        this.send(payload.toString());
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("⚠️ 私聊连接已关闭：" + reason + " (code: " + code + ", remote: " + remote + ")");
        if (listener != null) listener.onDisconnected();

        if (remote && code != 1000) {
            System.out.println("🔄 私聊监听将在 5 秒后尝试重新连接...");
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    System.out.println("🔄 正在重新连接私聊...");
                    if (!this.isClosed()) this.closeBlocking();
                    this.connectBlocking();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("⚠️ 私聊 WebSocket 错误：");
        ex.printStackTrace();
    }
}