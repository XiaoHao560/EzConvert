package com.tech.ezconvert.customcommand;

import android.content.Context;

import com.tech.ezconvert.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析命令行参数并识别 EzConvert 的路径占位符 */
public final class CustomCommandParser {
    private static final Pattern INPUT_PLACEHOLDER = Pattern.compile("%INPUT(?:_([1-9][0-9]*))?%");
    private static final Pattern OUTPUT_PLACEHOLDER = Pattern.compile("%OUTPUT(?:_([1-9][0-9]*))?%");

    private CustomCommandParser() {}

    public static List<String> parse(Context context, String command) throws IllegalArgumentException {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException(context.getString(R.string.custom_command_parser_empty));
        }
        // 将命令行文本拆分为参数数组，保留引号内的空格，但不启动 Android Shell
        List<String> args = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean tokenStarted = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote == 0) {
                if (Character.isWhitespace(c)) {
                    if (tokenStarted) {
                        args.add(token.toString());
                        token.setLength(0);
                        tokenStarted = false;
                    }
                    continue;
                }
                if (c == '\'' || c == '"') {
                    quote = c;
                    tokenStarted = true;
                    continue;
                }
                if (c == '\\' && i + 1 < command.length()) {
                    char next = command.charAt(i + 1);
                    if (Character.isWhitespace(next) || next == '\\' || next == '\'' || next == '"') {
                        token.append(next);
                        i++;
                    } else {
                        token.append(c);
                    }
                    tokenStarted = true;
                    continue;
                }
                token.append(c);
                tokenStarted = true;
            } else if (c == quote) {
                quote = 0;
            } else if (c == '\\' && i + 1 < command.length()) {
                char next = command.charAt(i + 1);
                if (next == quote || next == '\\') {
                    token.append(next);
                    i++;
                } else {
                    token.append(c);
                }
            } else {
                token.append(c);
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException(context.getString(R.string.custom_command_parser_unclosed_quote));
        }
        if (tokenStarted) args.add(token.toString());
        if (args.isEmpty()) throw new IllegalArgumentException(context.getString(R.string.custom_command_parser_empty));

        // 允许用户按命令行习惯输入 ffmpeg 前缀，但实际执行时只传参数
        String first = args.get(0);
        if ("ffmpeg".equalsIgnoreCase(first) || first.endsWith("/ffmpeg")) {
            args.remove(0);
        }
        if (args.isEmpty()) throw new IllegalArgumentException(context.getString(R.string.custom_command_parser_ffmpeg_prefix_empty));
        validatePlaceholderTokens(context, args);
        return args;
    }

    /** 占位符必须独占一个参数，实际路径才能作为整体替换而不会拆坏路径或滤镜参数 */
    private static void validatePlaceholderTokens(Context context, List<String> args) {
        for (String arg : args) {
            if (arg.contains("%INPUT") && !INPUT_PLACEHOLDER.matcher(arg).matches()) {
                throw new IllegalArgumentException(context.getString(R.string.custom_command_parser_input_placeholder));
            }
            if (arg.contains("%OUTPUT") && !OUTPUT_PLACEHOLDER.matcher(arg).matches()) {
                throw new IllegalArgumentException(context.getString(R.string.custom_command_parser_output_placeholder));
            }
        }
    }

    public static int inputIndex(String token) {
        Matcher matcher = INPUT_PLACEHOLDER.matcher(token);
        if (!matcher.matches()) return -1;
        String index = matcher.group(1);
        return index == null ? 0 : Integer.parseInt(index);
    }

    public static int outputIndex(String token) {
        Matcher matcher = OUTPUT_PLACEHOLDER.matcher(token);
        if (!matcher.matches()) return -1;
        String index = matcher.group(1);
        return index == null ? 1 : Integer.parseInt(index);
    }

    public static boolean isInputPlaceholder(String token) {
        return INPUT_PLACEHOLDER.matcher(token).matches();
    }

    public static boolean isOutputPlaceholder(String token) {
        return OUTPUT_PLACEHOLDER.matcher(token).matches();
    }

    /**
     * 检查参数中是否包含空白字符，避免为简单字符判断启动正则表达式引擎
     * 与 Java 正则表达式默认的 \s 范围保持一致
     */
    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || (c >= '\t' && c <= '\r')) {
                return true;
            }
        }
        return false;
    }

    public static String quoteForDisplay(String arg) {
        if (arg == null) return "\"\"";
        if (arg.isEmpty() || containsWhitespace(arg) || arg.contains("\"") || arg.contains("'")) {
            return "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return arg;
    }
}
