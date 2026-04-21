package com.example;

import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    // NapCat WebSocket 地址
    private static final String WS_URL = "ws://127.0.0.1:3001?access_token=RxPd8soLuXsJ0IRN";
    // 机器人 QQ 号（请替换为你的机器人小号）
    private static final long BOT_QQ = 3482431851L;

    // 客户端实例
    private static GroupBotClient groupClient;
    private static BotClient privateClient;

    // 状态标志
    private static final AtomicBoolean groupEnabled = new AtomicBoolean(false);
    private static final AtomicBoolean privateEnabled = new AtomicBoolean(false);

    // 用于等待连接建立的辅助标志
    private static final Object lock = new Object();

    public static void main(String[] args) {
        System.out.println("===== EarthMC 查询机器人控制台 =====");
        System.out.println("可用命令：");
        System.out.println("  group on   - 启动群聊监听");
        System.out.println("  group off  - 停止群聊监听");
        System.out.println("  private on - 启动私聊监听");
        System.out.println("  private off- 停止私聊监听");
        System.out.println("  status     - 查看当前状态");
        System.out.println("  exit/quit  - 退出程序");
        System.out.println("===================================");

        // 使用 try-with-resources 确保 Scanner 自动关闭，消除警告
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n> ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    System.out.println("正在关闭所有连接...");
                    shutdown();
                    break;
                }

                switch (input.toLowerCase()) {
                    case "group on":
                        startGroupClient();
                        break;
                    case "group off":
                        stopGroupClient();
                        break;
                    case "private on":
                        startPrivateClient();
                        break;
                    case "private off":
                        stopPrivateClient();
                        break;
                    case "status":
                        printStatus();
                        break;
                    default:
                        System.out.println("❌ 未知命令。可用: group on/off, private on/off, status, exit");
                }
            }
        } catch (Exception e) {
            System.err.println("控制台异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------- 群聊客户端管理 --------------------
    private static void startGroupClient() {
    if (groupEnabled.get()) {
        System.out.println("⚠️ 群聊监听已在运行中");
        return;
    }
    try {
        groupClient = new GroupBotClient(new URI(WS_URL), BOT_QQ);
        groupClient.setConnectionListener(() -> {
            groupEnabled.set(false);
            System.out.println("⚠️ 群聊连接已断开，状态已更新。");
        });
        groupClient.connect();
        Thread.sleep(500);
        groupEnabled.set(true);
        System.out.println("✅ 群聊监听已启动");
    } catch (Exception e) {
        System.err.println("❌ 启动群聊监听失败: " + e.getMessage());
        groupClient = null;
    }
}

private static void startPrivateClient() {
    if (privateEnabled.get()) {
        System.out.println("⚠️ 私聊监听已在运行中");
        return;
    }
    try {
        privateClient = new BotClient(new URI(WS_URL), BOT_QQ);
        privateClient.setConnectionListener(() -> {
            privateEnabled.set(false);
            System.out.println("⚠️ 私聊连接已断开，状态已更新。");
        });
        privateClient.connect();
        Thread.sleep(500);
        privateEnabled.set(true);
        System.out.println("✅ 私聊监听已启动");
    } catch (Exception e) {
        System.err.println("❌ 启动私聊监听失败: " + e.getMessage());
        privateClient = null;
    }
}
    private static void stopGroupClient() {
        if (!groupEnabled.get() || groupClient == null) {
            System.out.println("⚠️ 群聊监听未在运行");
            return;
        }
        try {
            groupClient.closeBlocking();
        } catch (Exception e) {
            System.err.println("关闭群聊连接时发生异常: " + e.getMessage());
        }
        groupClient = null;
        groupEnabled.set(false);
        System.out.println("🛑 群聊监听已停止");
    }


    private static void stopPrivateClient() {
        if (!privateEnabled.get() || privateClient == null) {
            System.out.println("⚠️ 私聊监听未在运行");
            return;
        }
        try {
            privateClient.closeBlocking();
        } catch (Exception e) {
            System.err.println("关闭私聊连接时发生异常: " + e.getMessage());
        }
        privateClient = null;
        privateEnabled.set(false);
        System.out.println("🛑 私聊监听已停止");
    }

    // -------------------- 状态显示 --------------------
    private static void printStatus() {
        System.out.println("===== 当前状态 =====");
        System.out.println("群聊监听: " + (groupEnabled.get() ? "运行中 ✅" : "已停止 ❌"));
        System.out.println("私聊监听: " + (privateEnabled.get() ? "运行中 ✅" : "已停止 ❌"));
        System.out.println("===================");
    }

    // -------------------- 优雅关闭 --------------------
    private static void shutdown() {
        stopGroupClient();
        stopPrivateClient();
        System.out.println("程序已退出。");
    }
}