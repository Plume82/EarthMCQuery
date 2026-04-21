package com.example;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.support.deny.WordDenys;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤器（纯本地模式）
 * 词库来源：
 * 1. lib/sensitive-word-0.29.5.jar 内置的默认词库
 * 2. resources/sensitive_dict 目录下的所有 .txt 自定义词库文件
 */
public class SensitiveWordsFilter {

    private static final String CUSTOM_DICT_DIR = "src/main/resources/sensitive_dict";
    private static volatile SensitiveWordBs wordBs;

    // 单例模式
    public static SensitiveWordBs getInstance() {
        if (wordBs == null) {
            synchronized (SensitiveWordsFilter.class) {
                if (wordBs == null) {
                    wordBs = initSensitiveWordBs();
                }
            }
        }
        return wordBs;
    }

    /**
     * 初始化敏感词过滤器，合并内置词库与自定义词库。
     */
    private static SensitiveWordBs initSensitiveWordBs() {
        System.out.println("🚀 正在初始化敏感词过滤器（纯本地模式）...");

        // 收集所有词源
        List<IWordDeny> allDenySources = new ArrayList<>();
        allDenySources.add(WordDenys.defaults()); // 内置词库
        System.out.println("✅ 已加载内置默认词库");

        // 加载自定义词库
        List<IWordDeny> customDenys = loadCustomDenysFromDirectory();
        if (!customDenys.isEmpty()) {
            allDenySources.addAll(customDenys);
            System.out.println("✅ 已加载 " + customDenys.size() + " 个自定义词库文件");
        } else {
            System.out.println("⚠️ 未找到自定义词库文件，仅使用内置词库");
        }

        // 构建最终的 IWordDeny 对象，兼容 chains 方法要求
        IWordDeny combinedDeny;
        if (allDenySources.size() == 1) {
            combinedDeny = allDenySources.get(0);
        } else {
            IWordDeny first = allDenySources.get(0);
            IWordDeny[] others = allDenySources.subList(1, allDenySources.size()).toArray(new IWordDeny[0]);
            combinedDeny = WordDenys.chains(first, others);
        }

        SensitiveWordBs bs = SensitiveWordBs.newInstance()
                .wordDeny(combinedDeny)
                .ignoreCase(true)          // 忽略大小写
                .ignoreWidth(true)         // 忽略全角/半角
                .ignoreChineseStyle(true)  // 忽略繁简体
                .ignoreRepeat(true)        // 忽略重复字符（如 "法法轮功"）
                .init();

        System.out.println("🎉 敏感词过滤器初始化完成！");
        return bs;
    }

    /**
     * 从 resources/sensitive_dict 目录加载所有 .txt 文件，并封装为 IWordDeny 列表。
     */
    private static List<IWordDeny> loadCustomDenysFromDirectory() {
        List<IWordDeny> customDenys = new ArrayList<>();
        File dictDir = new File(CUSTOM_DICT_DIR);

        if (!dictDir.exists() || !dictDir.isDirectory()) {
            System.err.println("❌ 自定义词库目录不存在: " + CUSTOM_DICT_DIR);
            return customDenys;
        }

        File[] txtFiles = dictDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (txtFiles == null || txtFiles.length == 0) {
            return customDenys;
        }

        for (File file : txtFiles) {
            IWordDeny fileDeny = new IWordDeny() {
                @Override
                public List<String> deny() {
                    List<String> words = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String word = line.trim();
                            if (!word.isEmpty() && !word.startsWith("#")) {
                                words.add(word);
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("❌ 读取自定义词库文件失败: " + file.getName() + " - " + e.getMessage());
                    }
                    return words;
                }
            };
            customDenys.add(fileDeny);
            System.out.println("  📄 加载自定义词库文件: " + file.getName());
        }
        return customDenys;
    }

    // ========== 便捷静态调用方法 ==========

    /**
     * 判断文本是否包含敏感词。
     */
    public static boolean containsSensitiveWord(String text) {
        return getInstance().contains(text);
    }

    /**
     * 查找文本中的所有敏感词。
     */
    public static List<String> findAll(String text) {
        return getInstance().findAll(text);
    }

    /**
     * 将文本中的敏感词替换为 * 号。
     */
    public static String replace(String text) {
        return getInstance().replace(text);
    }
}