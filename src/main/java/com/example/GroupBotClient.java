package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroupBotClient extends WebSocketClient {

    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)[^\\]]*\\]");
    private static final int CONNECT_TIMEOUT = 10000;
    private final long selfId;

    public interface ConnectionListener {
        void onDisconnected();
    }
    private ConnectionListener listener;

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public GroupBotClient(URI serverUri, long selfId) {
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
        System.out.println("✅ 群聊服务已连接到 NapCat");
    }

    @Override
public void onMessage(String message) {
    try {
        JsonObject json = JsonParser.parseString(message).getAsJsonObject();

        String postType = json.has("post_type") ? json.get("post_type").getAsString() : "";
        String messageType = json.has("message_type") ? json.get("message_type").getAsString() : "";

        if (!"message".equals(postType) && !"message_sent".equals(postType)) return;
        if (!"group".equals(messageType)) return;

        String rawMessage = json.has("raw_message") ? json.get("raw_message").getAsString() : "";
        if (rawMessage.isEmpty()) return;
        if (!isAtMe(rawMessage)) return;

        String command = rawMessage.replaceAll("\\[CQ:at,qq=\\d+[^\\]]*\\]", "").trim();
        long groupId = json.get("group_id").getAsLong();

        String param = "";
        int spaceIndex = command.indexOf(' ');
        if (spaceIndex > 0) param = command.substring(spaceIndex + 1).trim();

        // 如需启用敏感词过滤，取消下面注释
        /*
        if (!param.isEmpty() && SensitiveWordsFilter.containsSensitiveWord(param)) {
            sendGroupMessage(groupId, "❌ 查询内容包含违禁词，请求已被拒绝。");
            System.out.println("🚫 拦截违禁词查询（群聊）: " + param);
            return;
        }
        */

        if (command.startsWith("/town ") || command.startsWith("/t ")) {
            String townName = param;
            if (townName.isEmpty()) { sendGroupMessage(groupId, "❌ 请指定城镇名称"); return; }
            System.out.println("✅ [群聊] 城镇查询: " + townName);
            sendGroupMessage(groupId, EarthMCTownLookup.getTownDetailsAsString(townName));
        } else if (command.startsWith("/nation ") || command.startsWith("/n ")) {
            String nationName = param;
            if (nationName.isEmpty()) { sendGroupMessage(groupId, "❌ 请指定国家名称"); return; }
            System.out.println("✅ [群聊] 国家查询: " + nationName);
            sendGroupMessage(groupId, EarthMCNationLookup.getNationDetailsAsString(nationName));
        } else if (command.startsWith("/nearby ")) {
            String townName = param;
            if (townName.isEmpty()) { sendGroupMessage(groupId, "❌ 请指定城镇名称"); return; }
            System.out.println("✅ [群聊] 附近城镇查询: " + townName);
            sendGroupMessage(groupId, EarthMCNearbyNewTownsLookup.getNearbyNewTownsAsString(townName));
        } else if (command.startsWith("/player ") || command.startsWith("/res")) {
            String playerName = param;
            if (playerName.isEmpty()) { sendGroupMessage(groupId, "❌ 请指定玩家名称"); return; }
            System.out.println("✅ [群聊] 玩家查询: " + playerName);
            sendGroupMessage(groupId, EarthMCPlayerLookup.getPlayerDetailsAsString(playerName));
        } else if (command.trim().equals("/server")) {
            System.out.println("✅ [群聊] 服务器状态查询");
            sendGroupMessage(groupId, EarthMCServerLookup.getServerStatusAsString());
        } else if (command.trim().equals("/help")) {
            System.out.println("✅ [群聊] 帮助查询");
            sendGroupMessage(groupId, getHelpMessage());
        } else {
            sendGroupMessage(groupId, "❌ 未知指令，发送 /help 查看帮助");
        }
    } catch (Exception e) {
        System.err.println("💥 处理群聊消息出错: " + e.getMessage());
        e.printStackTrace();
    }
}

    private String getHelpMessage() {
        return "===== 机器人指令帮助 =====\n" +
               "/town <城镇> 或 /t <城镇>   - 查询城镇详细信息\n" +
               "/nation <国家> 或 /n <国家> - 查询国家详细信息\n" +
               "/player <玩家> 或 /res <玩家> - 查询玩家详细信息\n" +
               "/nearby <城镇名>     - 查询附近3000格内30天内新建城镇（威胁预警）\n" +
               "/server              - 查询服务器实时状态\n" +
               "/help                - 显示本帮助信息\n" +
               "==============================";
    }

    private boolean isAtMe(String rawMessage) {
    if (rawMessage == null || rawMessage.isEmpty()) {
        return false;
    }
    Matcher matcher = AT_PATTERN.matcher(rawMessage);
    while (matcher.find()) {
        String capturedQQ = matcher.group(1);
        String selfQQ = String.valueOf(selfId);
        
        // 强制 trim 后比较，并去除所有不可见字符
        if (capturedQQ.replaceAll("\\s+", "").equals(selfQQ)) {
            return true;
        }
    }
    return false;
}

    private void sendGroupMessage(long groupId, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "send_group_msg");
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("message", message);
        payload.add("params", params);
        this.send(payload.toString());
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("⚠️ 群聊连接已关闭：" + reason + " (code: " + code + ", remote: " + remote + ")");
        if (listener != null) listener.onDisconnected();

        if (remote && code != 1000) {
            System.out.println("🔄 群聊监听将在 5 秒后尝试重新连接...");
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    System.out.println("🔄 正在重新连接群聊...");
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
        System.err.println("⚠️ 群聊 WebSocket 错误：");
        ex.printStackTrace();
    }
}