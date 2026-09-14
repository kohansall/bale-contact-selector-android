package com.veilaura.balequeue.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.veilaura.balequeue.core.BaleConstants;
import com.veilaura.balequeue.data.AutomationPrefs;
import com.veilaura.balequeue.data.DiagnosticLog;
import com.veilaura.balequeue.domain.BaleTextRules;
import com.veilaura.balequeue.service.BaleAccessibilityService;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(0, 177, 142);
    private static final int RED = Color.rgb(210, 53, 69);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private static final int INK = Color.rgb(31, 41, 55);
    private static final int MUTED = Color.rgb(102, 112, 133);

    private AutomationPrefs automation;
    private TextView statusBadge;
    private TextView statusView;
    private TextView addedCard;
    private TextView pendingCard;
    private TextView failedCard;
    private EditText channelView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        automation = new AutomationPrefs(this);
        setContentView(buildUi());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null) refreshStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 251));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(18), dp(24), dp(18), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("مدیریت افزودن اعضای بله", 24, INK);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = text(
                "انتخاب گروهی، توقف هوشمند و ثبت نتیجه واقعی",
                14,
                MUTED
        );
        subtitle.setPadding(0, dp(5), 0, dp(16));
        content.addView(subtitle, matchWrap());

        statusBadge = text("", 14, Color.WHITE);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(14), dp(9), dp(14), dp(9));
        content.addView(statusBadge, matchWrap());

        TextView statsTitle = text("خلاصه این کانال", 16, INK);
        statsTitle.setTypeface(null, Typeface.BOLD);
        statsTitle.setPadding(0, dp(20), 0, dp(8));
        content.addView(statsTitle, matchWrap());

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        cards.setWeightSum(3f);

        addedCard = statCard(Color.rgb(224, 247, 239), Color.rgb(0, 112, 91));
        pendingCard = statCard(Color.rgb(255, 244, 214), Color.rgb(146, 92, 0));
        failedCard = statCard(Color.rgb(255, 231, 235), Color.rgb(170, 34, 52));

        cards.addView(addedCard, weightedCardParams());
        cards.addView(pendingCard, weightedCardParams());
        cards.addView(failedCard, weightedCardParams());
        content.addView(cards, matchWrap());

        content.addView(sectionLabel("کانال مقصد"), matchWrap());
        channelView = new EditText(this);
        channelView.setSingleLine(true);
        channelView.setText("veilaura3");
        channelView.setHint("شناسه کانال");
        channelView.setTextDirection(View.TEXT_DIRECTION_LTR);
        channelView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        channelView.setPadding(dp(14), dp(12), dp(14), dp(12));
        channelView.setBackground(rounded(Color.WHITE, Color.rgb(210, 216, 226), 12));
        content.addView(channelView, matchWrap());

        content.addView(actionButton(
                "فعال‌کردن دسترسی انتخاب‌گر",
                Color.WHITE,
                INK,
                v -> openAccessibilitySettings()
        ), buttonParams());

        content.addView(actionButton(
                "شروع انتخاب گروهی",
                GREEN,
                Color.WHITE,
                v -> startBaleSelection()
        ), buttonParams());

        content.addView(actionButton(
                "توقف فوری",
                RED,
                Color.WHITE,
                v -> stopAutomation()
        ), buttonParams());

        content.addView(actionButton(
                "ثبت نتیجه بعد از دکمه سبز بله",
                BLUE,
                Color.WHITE,
                v -> resolvePending()
        ), buttonParams());

        statusView = text("", 14, INK);
        statusView.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusView.setBackground(rounded(Color.WHITE, Color.rgb(224, 228, 235), 12));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(16);
        content.addView(statusView, statusParams);

        TextView listsTitle = sectionLabel("سوابق و ابزارها");
        content.addView(listsTitle, matchWrap());
        content.addView(actionButton("مشاهده افراد عضوشده", Color.WHITE, GREEN,
                v -> showNames("افراد عضوشده", automation.addedNames(currentChannel()))), buttonParams());
        content.addView(actionButton("مشاهده افراد در انتظار نتیجه", Color.WHITE, Color.rgb(146, 92, 0),
                v -> showNames("در انتظار نتیجه", automation.pendingNames(currentChannel()))), buttonParams());
        content.addView(actionButton("مشاهده افراد ناموفق", Color.WHITE, RED,
                v -> showNames("افراد ناموفق", automation.failedNames(currentChannel()))), buttonParams());
        content.addView(actionButton("گزارش تشخیصی", Color.WHITE, MUTED,
                v -> showDiagnosticLog()), buttonParams());
        content.addView(actionButton("پاک‌کردن همه سوابق کانال", Color.WHITE, RED,
                v -> clearAllHistory()), buttonParams());

        TextView help = text(
                "بعد از انتخاب‌ها، دکمه سبز «افزودن عضو» را در بله بزن. سپس اینجا «ثبت نتیجه» را باز کن و فقط تیک افرادی را نگه دار که واقعاً عضو شدند. اگر بله اجازه افزودن فردی را نداد، تیک نام او را بردار.",
                13,
                MUTED
        );
        help.setPadding(0, dp(18), 0, 0);
        content.addView(help, matchWrap());
        return scroll;
    }

    private TextView statCard(int background, int foreground) {
        TextView card = text("", 13, foreground);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(5), dp(14), dp(5), dp(14));
        card.setBackground(rounded(background, background, 12));
        return card;
    }

    private LinearLayout.LayoutParams weightedCardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private TextView sectionLabel(String value) {
        TextView label = text(value, 15, INK);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(20), 0, dp(7));
        return label;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private Button actionButton(String label, int background, int foreground,
                                View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(background, Color.rgb(220, 225, 233), 12));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(9);
        params.height = dp(52);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void startBaleSelection() {
        if (!isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("انتخاب‌گر خاموش است")
                    .setMessage("در دسترسی‌پذیری، «انتخاب‌گر مخاطبین بله» را روشن کن.")
                    .setPositiveButton("بازکردن تنظیمات", (dialog, which) -> openAccessibilitySettings())
                    .setNegativeButton("لغو", null)
                    .show();
            return;
        }

        String channel = currentChannel();
        if (channel == null) {
            toast("شناسه کانال را درست وارد کن");
            return;
        }
        if (automation.pendingCount(channel) > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("نتیجه قبلی ثبت نشده")
                    .setMessage("اول نتیجه افراد در انتظار را ثبت کن؛ یا اگر دکمه سبز بله را نزدی، آن‌ها را برای تلاش دوباره آزاد کن.")
                    .setPositiveButton("ثبت نتیجه", (d, w) -> resolvePending())
                    .setNegativeButton("فعلاً نه", null)
                    .setNeutralButton("تلاش دوباره", (d, w) -> {
                        automation.retryPending(channel);
                        refreshStatus();
                    })
                    .show();
            return;
        }

        automation.start(channel);
        DiagnosticLog.clear(this);
        DiagnosticLog.write(
                this,
                "START batchMode=true channel=" + channel +
                        " processedBefore=" + automation.processedCount(channel)
        );
        refreshStatus();

        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(BaleConstants.CHANNEL_BASE_URL + channel)
        );
        intent.setPackage(BaleConstants.PACKAGE_NAME);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            automation.discard("برنامه بله پیدا نشد");
            showError("بله پیدا نشد", "ابتدا پیام‌رسان بله را نصب یا به‌روزرسانی کن.");
        }
    }

    private void stopAutomation() {
        automation.stopByUser();
        refreshStatus();
        toast("انتخاب‌گر متوقف شد");
    }

    private void resolvePending() {
        String channel = currentChannel();
        if (channel == null) {
            toast("شناسه کانال را درست وارد کن");
            return;
        }
        List<String> names = automation.pendingNames(channel);
        if (names.isEmpty()) {
            toast("فردی در انتظار ثبت نتیجه نیست");
            return;
        }

        CharSequence[] items = names.toArray(new CharSequence[0]);
        boolean[] checked = new boolean[items.length];
        for (int i = 0; i < checked.length; i++) checked[i] = true;

        new AlertDialog.Builder(this)
                .setTitle("چه کسانی واقعاً عضو شدند؟")
                .setMessage("همه نام‌ها فعلاً تیک دارند. تیک افراد ناموفق را بردار.")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) ->
                        checked[which] = isChecked)
                .setPositiveButton("ثبت نتیجه", (dialog, which) -> {
                    Set<String> added = new HashSet<>();
                    for (int i = 0; i < names.size(); i++) {
                        if (checked[i]) added.add(names.get(i));
                    }
                    automation.resolvePending(channel, added);
                    refreshStatus();
                    toast("نتیجه واقعی ذخیره شد");
                })
                .setNeutralButton("هیچ‌کدام عضو نشدند", (dialog, which) -> {
                    automation.resolvePending(channel, new HashSet<>());
                    refreshStatus();
                    toast("همه به‌عنوان ناموفق ثبت شدند");
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showNames(String title, List<String> names) {
        StringBuilder value = new StringBuilder();
        if (names.isEmpty()) {
            value.append("فهرست خالی است");
        } else {
            for (int i = 0; i < names.size(); i++) {
                value.append(i + 1).append(". ").append(names.get(i)).append("\n");
            }
        }
        TextView list = text(value.toString(), 15, INK);
        list.setTextIsSelectable(true);
        list.setPadding(dp(14), dp(12), dp(14), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this)
                .setTitle(title + " · " + names.size())
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .show();
    }

    private void clearAllHistory() {
        String channel = currentChannel();
        if (channel == null) {
            toast("شناسه کانال را درست وارد کن");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("پاک‌کردن همه سوابق")
                .setMessage("فهرست عضوشده، در انتظار، ناموفق و سابقه ادامه این کانال پاک شود؟")
                .setPositiveButton("پاک شود", (dialog, which) -> {
                    automation.clearAllHistory(channel);
                    refreshStatus();
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showDiagnosticLog() {
        String log = DiagnosticLog.read(this);
        TextView logView = text(log, 11, INK);
        logView.setTextIsSelectable(true);
        logView.setTextDirection(View.TEXT_DIRECTION_LTR);
        logView.setGravity(Gravity.LEFT);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        new AlertDialog.Builder(this)
                .setTitle("گزارش تشخیصی")
                .setView(scroll)
                .setPositiveButton("کپی گزارش", (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(
                                "Bale selector diagnostic", log
                        ));
                        toast("گزارش کپی شد");
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, BaleAccessibilityService.class);
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName current = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(current)) return true;
        }
        return false;
    }

    private String currentChannel() {
        if (channelView == null) return automation.channel();
        return normalizeChannel(channelView.getText().toString());
    }

    private String normalizeChannel(String value) {
        return BaleTextRules.normalizeChannel(value);
    }

    private void refreshStatus() {
        String channel = currentChannel();
        if (channel == null) channel = automation.channel();

        boolean active = automation.isActive();
        statusBadge.setText(active ? "● انتخاب‌گر در حال اجراست" : "● انتخاب‌گر متوقف است");
        statusBadge.setBackground(rounded(
                active ? GREEN : Color.rgb(94, 105, 123),
                active ? GREEN : Color.rgb(94, 105, 123),
                12
        ));

        addedCard.setText(String.format(new Locale("fa"), "عضوشده\n%,d",
                automation.addedCount(channel)));
        pendingCard.setText(String.format(new Locale("fa"), "در انتظار\n%,d",
                automation.pendingCount(channel)));
        failedCard.setText(String.format(new Locale("fa"), "ناموفق\n%,d",
                automation.failedCount(channel)));

        String service = isAccessibilityServiceEnabled() ? "روشن" : "خاموش";
        statusView.setText(String.format(
                new Locale("fa"),
                "بررسی‌شده برای ادامه: %,d\nانتخاب‌شده در اجرای فعلی: %,d\nدسترسی انتخاب‌گر: %s\nوضعیت: %s",
                automation.processedCount(channel),
                automation.selectedCount(),
                service,
                automation.status()
        ));
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("باشه", null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
