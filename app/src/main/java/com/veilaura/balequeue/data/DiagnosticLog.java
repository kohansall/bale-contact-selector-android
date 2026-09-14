package com.veilaura.balequeue.data;

import android.content.Context;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DiagnosticLog {
    private static final String FILE_NAME = "bale-selector-diagnostic.txt";
    private static final long MAX_BYTES = 300_000L;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private DiagnosticLog() {}

    public static synchronized void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) file.delete();
        write(context, "LOG START");
    }

    public static synchronized void write(Context context, String message) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (file.length() > MAX_BYTES) return;
            String line = LocalDateTime.now().format(TIME) + " | " + message + "\n";
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // Logging must never stop the selector.
        }
    }

    public static synchronized String read(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return "هنوز گزارشی ثبت نشده است.";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
            return result.toString();
        } catch (Exception error) {
            return "خواندن گزارش ناموفق بود: " + error.getClass().getSimpleName();
        }
    }

    public static void writeTree(Context context, AccessibilityNodeInfo root) {
        StringBuilder tree = new StringBuilder("ACCESSIBILITY TREE\n");
        int[] count = {0};
        appendNode(tree, root, 0, count);
        write(context, tree.toString());
    }

    private static void appendNode(StringBuilder out,
                                   AccessibilityNodeInfo node,
                                   int depth,
                                   int[] count) {
        if (node == null || depth > 10 || count[0] >= 300) return;
        count[0]++;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        out.append(depth)
                .append(" | class=").append(safe(node.getClassName()))
                .append(" text=").append(safe(node.getText()))
                .append(" desc=").append(safe(node.getContentDescription()))
                .append(" id=").append(safe(node.getViewIdResourceName()))
                .append(" bounds=").append(bounds.toShortString())
                .append(" click=").append(node.isClickable())
                .append(" checkable=").append(node.isCheckable())
                .append(" checked=").append(node.isChecked())
                .append(" selected=").append(node.isSelected())
                .append(" enabled=").append(node.isEnabled())
                .append(" visible=").append(node.isVisibleToUser())
                .append('\n');
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) appendNode(out, child, depth + 1, count);
        }
    }

    private static String safe(CharSequence value) {
        if (value == null) return "-";
        String text = value.toString().replace('\n', ' ').trim();
        return text.length() > 100 ? text.substring(0, 100) : text;
    }
}
