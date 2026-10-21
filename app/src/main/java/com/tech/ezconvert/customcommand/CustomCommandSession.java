package com.tech.ezconvert.customcommand;

import java.util.ArrayList;
import java.util.List;

/** 自定义命令会话的数据模型 */
public class CustomCommandSession {
    public String id = "";
    public String executionMode = "single";
    public String commandText = "";
    public List<InputSource> inputs = new ArrayList<>();
    public List<String> outputFileNames = new ArrayList<>();
    public String outputTreeUri = "";
    public String outputDocumentUri = "";
    public String outputDocumentName = "";
    public String outputCommandPath = "";
    public String configuredOutputMode = "download";
    public String configuredOutputTreeUri = "";
    public String customOutputTreeUri = "";

    public static class InputSource {
        public String uri = "";
        public String displayName = "";
        public String commandPath = "";

        public InputSource() {}

        public InputSource(String uri, String displayName) {
            this(uri, displayName, "");
        }

        public InputSource(String uri, String displayName, String commandPath) {
            this.uri = uri == null ? "" : uri;
            this.displayName = displayName == null ? "" : displayName;
            this.commandPath = commandPath == null ? "" : commandPath;
        }
    }
}
