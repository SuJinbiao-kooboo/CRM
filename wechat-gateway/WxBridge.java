import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WeChat 消息网关的 Java 桥接示例（JDK 自带，无第三方依赖）。
 *
 * 架构：Java 用 ProcessBuilder 拉起 Python 网关 (wx_gateway.py)，
 * Python 把每条消息作为一行 NDJSON 打到 stdout，Java 逐行读取解析。
 * 不需要常驻服务——你每次主动要数据时跑一次脚本读完即可。
 *
 * 三种调用（先确认微信 4.1.15.13 已登录、窗口打开）：
 *   javac -encoding UTF-8 WxBridge.java
 *
 *   ① 主动拉取：按时间(+可选群/好友名)取聊天记录
 *      java WxBridge fetch  <python.exe> "2026-10-05 12:00:00"
 *      java WxBridge fetch  <python.exe> "2026-10-05 12:00:00" "文件传输助手"
 *      java WxBridge fetchm <python.exe> "2026-10-05 12:00:00" [chat]   // +下载媒体
 *
 *   ② 实时监听所有会话新消息
 *      java WxBridge listen <python.exe>
 *      java WxBridge listenm <python.exe>                              // +下载媒体
 */
public class WxBridge {

    // ---- 消息结构 ------------------------------------------------
    public static final class Message {
        public String username = "";
        public String name = "";
        public long localId = -1;
        public String type = "";
        public String sender = "";
        public String content = "";
        public String quote = "";
        public String quoteKind = "";
        public String mediaPath = "";
        public String createTime = "";
        public long sortSeq = 0;
        public String md5 = "";
        @Override public String toString() {
            return String.format("[%s|%s] %s: %s (media=%s, t=%s)",
                    name, type, sender, content, mediaPath, createTime);
        }
    }

    /** 你在这里消费每一条消息：入库 / 推送给自己的业务逻辑。 */
    public static void handle(Message m) {
        System.out.println("RECV " + m);
    }

    // ---- 极简 NDJSON 解析（字段均为扁平的字符串/数字） ------------
    private static final Pattern STR = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern NUM = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+)");

    public static Message parseLine(String line) {
        Message m = new Message();
        Matcher s = STR.matcher(line);
        while (s.find()) {
            switch (s.group(1)) {
                case "username": m.username = s.group(2); break;
                case "name":     m.name = s.group(2); break;
                case "type":     m.type = s.group(2); break;
                case "sender":   m.sender = s.group(2); break;
                case "content":  m.content = s.group(2); break;
                case "quote":  m.quote = s.group(2); break;
                case "quote_kind": m.quoteKind = s.group(2); break;
                case "media_path": m.mediaPath = s.group(2); break;
                case "create_time": m.createTime = s.group(2); break;
                case "md5":      m.md5 = s.group(2); break;
            }
        }
        Matcher n = NUM.matcher(line);
        while (n.find()) {
            switch (n.group(1)) {
                case "local_id": m.localId = Long.parseLong(n.group(2)); break;
                case "sort_seq": m.sortSeq = Long.parseLong(n.group(2)); break;
            }
        }
        return m;
    }

    // ---- 拉起 python 子进程，逐行消费 stdout ----------------------
    public static void spawn(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); // 合并 stderr 便于调试
        Process p = pb.start();
        BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("{")) {
                handle(parseLine(line));   // 消息
            } else if (!line.isEmpty()) {
                System.out.println("[py] " + line); // 诊断
            }
        }
        System.out.println("[exit] " + p.waitFor());
    }

    private static final String SCRIPT =
        "C:\\Users\\亦钟人\\Documents\\Codex\\2026-10-03\\hi-2\\outputs\\wx_gateway.py";
    private static final String MEDIA =
        "C:\\Users\\亦钟人\\Documents\\Codex\\2026-10-03\\hi-2\\outputs\\media";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { usage(); return; }
        String mode = args[0];
        String py = args[1];

        switch (mode) {
            // ① 主动拉取：时间(必填,含秒) + 群/好友名(可选)
            case "fetch":
            case "fetchm": {
                if (args.length < 3) { usage(); return; }
                String since = args[2];                 // "2026-10-05 12:00:00"
                String chat  = args.length >= 4 ? args[3] : null;
                List<String> cmd = new ArrayList<>(List.of(py, SCRIPT, "dump", "--since", since));
                if (chat != null) { cmd.add("--chat"); cmd.add(chat); }
                if (mode.equals("fetchm")) { cmd.add("--media"); cmd.add(MEDIA); }
                spawn(cmd);
                break;
            }
            // ② 实时监听：所有会话新消息
            case "listen":
            case "listenm": {
                List<String> cmd = new ArrayList<>(List.of(py, SCRIPT, "listen"));
                if (mode.equals("listenm")) { cmd.add("--media"); cmd.add(MEDIA); }
                spawn(cmd);
                break;
            }
            default:
                usage();
        }
    }

    private static void usage() {
        System.out.println("用法:");
        System.out.println("  主动拉取(时间必填, 群/好友名可选):");
        System.out.println("    java WxBridge fetch  <python.exe> \"2026-10-05 12:00:00\" [群名或好友名]");
        System.out.println("    java WxBridge fetchm <python.exe> \"2026-10-05 12:00:00\" [群名或好友名]  // 同时下载媒体");
        System.out.println("  实时监听:");
        System.out.println("    java WxBridge listen  <python.exe>");
        System.out.println("    java WxBridge listenm <python.exe>  // 同时下载媒体");
    }
}


