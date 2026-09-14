package com.veilaura.balequeue.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.veilaura.balequeue.core.BaleConstants;
import com.veilaura.balequeue.data.AutomationPrefs;
import com.veilaura.balequeue.data.DiagnosticLog;
import com.veilaura.balequeue.domain.BaleTextRules;

import java.util.ArrayList;
import java.util.List;

public final class BaleAccessibilityService extends AccessibilityService {
    private static final long STEP_DELAY_MS = 420L;
    private static final int PAGE_BATCH_SIZE = 8;
    private static final long BATCH_TAP_GAP_MS = 65L;
    private static final long BATCH_VERIFY_DELAY_MS = 220L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AutomationPrefs automation;
    private boolean stepScheduled;
    private boolean navigationHintShown;
    private boolean gestureInFlight;
    private boolean limitDetected;
    private int batchVerifyAttempts;
    private boolean addMemberTreeLogged;
    private boolean dumpTreeAfterAddClick;
    private long lastAddMemberClickAt;
    private long lastContentEventLogAt;
    private String lastScrollFingerprint = "";
    private int stagnantScrolls;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        automation = new AutomationPrefs(this);
        DiagnosticLog.write(this, "SERVICE CONNECTED");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (automation == null) automation = new AutomationPrefs(this);
        if (!automation.isActive()) return;
        if (event.getPackageName() == null ||
                !BaleConstants.PACKAGE_NAME.contentEquals(event.getPackageName())) return;

        String eventMessage = eventText(event);
        if (BaleTextRules.isLimitMessage(eventMessage)) {
            limitDetected = true;
            DiagnosticLog.write(this, "LIMIT EVENT text=" + eventMessage);
            if (!gestureInFlight) stopDueToLimit();
            return;
        }

        long now = System.currentTimeMillis();
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                now - lastContentEventLogAt >= 1500L) {
            DiagnosticLog.write(
                    this,
                    "EVENT type=" + event.getEventType() +
                            " class=" + event.getClassName()
            );
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                lastContentEventLogAt = now;
            }
        }
        scheduleStep();
    }

    @Override
    public void onInterrupt() {
        if (automation != null && automation.isActive()) {
            automation.stopByUser();
        }
    }

    private String eventText(AccessibilityEvent event) {
        StringBuilder value = new StringBuilder();
        if (event.getText() != null) {
            for (CharSequence item : event.getText()) {
                if (item != null) value.append(item).append(' ');
            }
        }
        if (event.getContentDescription() != null) {
            value.append(event.getContentDescription());
        }
        return value.toString().trim();
    }

    private void stopDueToLimit() {
        if (automation == null || !automation.isActive()) return;
        automation.finishSelection("سقف مجاز افزودن عضو در بله پر شده است");
        DiagnosticLog.write(this, "STOP exact Bale limit message");
        showToast("سقف مجاز بله پر شده؛ انتخاب‌گر متوقف شد");
    }

    private void scheduleStep() {
        if (stepScheduled) return;
        stepScheduled = true;
        handler.postDelayed(() -> {
            stepScheduled = false;
            runStep();
        }, STEP_DELAY_MS);
    }

    private void runStep() {
        if (automation == null || !automation.isActive() || gestureInFlight) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            scheduleStep();
            return;
        }

        {
            boolean addMemberScreen = isAddMemberScreen(root);
            boolean infoScreen = containsText(root, "درباره کانال") ||
                    containsText(root, "مدیریت کانال");
            int addMemberLabels = countVisibleExactText(root, "افزودن عضو");
            DiagnosticLog.write(
                    this,
                    "STEP addMember=" + addMemberScreen +
                            " info=" + infoScreen +
                            " addLabels=" + addMemberLabels +
                            " selected=" + automation.selectedCount() +
                            " processed=" + automation.processedCount(automation.channel())
            );
            if (dumpTreeAfterAddClick) {
                dumpTreeAfterAddClick = false;
                DiagnosticLog.write(this, "TREE after add-member click");
                DiagnosticLog.writeTree(this, root);
            } else if (addMemberScreen && !addMemberTreeLogged) {
                addMemberTreeLogged = true;
                DiagnosticLog.writeTree(this, root);
            } else if (!addMemberScreen) {
                addMemberTreeLogged = false;
            }

            if (hasBaleLimitWarning(root)) {
                DiagnosticLog.write(this, "STOP limit warning detected");
                automation.finishSelection("بله هشدار یا محدودیت نشان داد؛ ادامه متوقف شد");
                showToast("بله محدودیت نشان داد؛ انتخاب‌گر متوقف شد");
                return;
            }

            if (addMemberScreen) {
                navigationHintShown = false;
                handleAddMemberScreen(root);
            } else if (infoScreen) {
                long sinceLastClick = System.currentTimeMillis() - lastAddMemberClickAt;
                if (lastAddMemberClickAt > 0L && sinceLastClick < 2500L) {
                    automation.setStatus("منتظر بازشدن فهرست مخاطبین بله");
                    DiagnosticLog.write(
                            this,
                            "WAIT selector sinceAddClickMs=" + sinceLastClick
                    );
                    scheduleStep();
                } else if (clickFirstText(root, "افزودن عضو")) {
                    lastAddMemberClickAt = System.currentTimeMillis();
                    dumpTreeAfterAddClick = true;
                    DiagnosticLog.write(this, "ACTION click add-member");
                    automation.setStatus("ورود به فهرست مخاطبین بله");
                } else {
                    automation.setStatus("گزینه افزودن عضو پیدا نشد");
                }
                scheduleStep();
            } else {
                if (clickChannelHeader(root)) {
                    DiagnosticLog.write(this, "ACTION click channel header");
                    automation.setStatus("بازکردن اطلاعات کانال");
                    navigationHintShown = false;
                } else {
                    automation.setStatus("عنوان کانال را یک‌بار لمس کن");
                    if (!navigationHintShown) {
                        navigationHintShown = true;
                        showToast("اگر صفحه کانال باز است، عنوان بالای کانال را یک‌بار لمس کن");
                    }
                }
                scheduleStep();
            }
        }
    }

    private boolean isAddMemberScreen(AccessibilityNodeInfo root) {
        if (containsText(root, "دعوت به کانال از طریق لینک") ||
                (containsText(root, "مخاطبین شما") &&
                        containsText(root, "افزودن عضو"))) {
            return true;
        }
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        List<AccessibilityNodeInfo> nodes =
                root.findAccessibilityNodeInfosByText("افزودن عضو");
        int visibleExact = 0;
        if (nodes == null) return false;
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence raw = node.getText();
            if (raw == null || !raw.toString().trim().equals("افزودن عضو") ||
                    !node.isVisibleToUser()) continue;
            visibleExact++;
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (!bounds.isEmpty() &&
                    bounds.centerY() < screen.top + screen.height() / 4) {
                return true;
            }
        }
        return visibleExact >= 2;
    }

    private int countVisibleExactText(AccessibilityNodeInfo root, String wanted) {
        List<AccessibilityNodeInfo> nodes =
                root.findAccessibilityNodeInfosByText(wanted);
        if (nodes == null) return 0;
        int count = 0;
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence raw = node.getText();
            if (node.isVisibleToUser() && raw != null &&
                    raw.toString().trim().equals(wanted)) count++;
        }
        return count;
    }

    private boolean hasBaleLimitWarning(AccessibilityNodeInfo root) {
        boolean exactMessage =
                containsText(root, "امکان دعوت کردن عضو جدید") &&
                        containsText(root, "سقف مجاز");
        return exactMessage ||
                containsText(root, "محدودیت افزودن") ||
                containsText(root, "امکان افزودن عضو") ||
                containsText(root, "تعداد مجاز") ||
                containsText(root, "بعداً دوباره تلاش");
    }

    private void handleAddMemberScreen(AccessibilityNodeInfo root) {
        int selected = automation.selectedCount();
        DiagnosticLog.write(
                this,
                "ADD SCREEN batch selected=" + selected +
                        " processed=" + automation.processedCount(automation.channel())
        );

        List<ContactTarget> semanticTargets = findSemanticContactTargets(root);
        if (!semanticTargets.isEmpty()) {
            dispatchBatch(semanticTargets);
            return;
        }

        AccessibilityNodeInfo choice = findFirstUncheckedContactChoice(root);
        if (choice != null) {
            String key = contactKeyNear(root, choice);
            Rect bounds = new Rect();
            choice.getBoundsInScreen(bounds);
            if (clickNodeOrParent(choice)) {
                List<ContactTarget> single = new ArrayList<>();
                single.add(new ContactTarget(key, bounds.centerX(), bounds.centerY()));
                beginVerification(single);
                return;
            }
        }

        ContactTarget fallback = findFallbackContactTarget(root);
        if (fallback != null && dispatchBatch(singleTarget(fallback))) {
            return;
        }

        String fingerprint = screenFingerprint(root);
        if (fingerprint.equals(lastScrollFingerprint)) {
            stagnantScrolls++;
        } else {
            lastScrollFingerprint = fingerprint;
            stagnantScrolls = 0;
        }

        if (stagnantScrolls < 3 && scrollForward(root)) {
            DiagnosticLog.write(
                    this,
                    "ACTION scroll stagnant=" + stagnantScrolls +
                            " fingerprint=" + fingerprint.hashCode()
            );
            automation.setStatus("در حال رفتن به صفحه بعدی مخاطبین");
            scheduleStep();
            return;
        }

        selected = automation.selectedCount();
        String status = selected > 0
                ? selected + " مخاطب جدید تیک خورد؛ فرد تازه دیگری باقی نماند"
                : "مخاطب جدیدی باقی نماند";
        automation.finishSelection(status);
        DiagnosticLog.write(this, "STOP " + status);
        showToast(status);
    }

    private List<ContactTarget> findSemanticContactTargets(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> rows = new ArrayList<>();
        collectSemanticCheckboxRows(root, rows);
        DiagnosticLog.write(this, "SEMANTIC uncheckedRows=" + rows.size());
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        List<ContactTarget> targets = new ArrayList<>();
        for (AccessibilityNodeInfo row : rows) {
            CharSequence rawDescription = row.getContentDescription();
            if (rawDescription == null) continue;
            String description = rawDescription.toString().trim();
            String key = BaleTextRules.extractContactName(description);
            if (key == null || automation.hasProcessedKey(key)) continue;
            Rect bounds = new Rect();
            row.getBoundsInScreen(bounds);
            if (bounds.isEmpty() || !row.isVisibleToUser() ||
                    bounds.centerY() <= screen.top + dp(120) ||
                    bounds.centerY() >= screen.bottom - dp(80)) continue;
            int x = screen.right - dp(22);
            int y = bounds.centerY();
            targets.add(new ContactTarget(key, x, y));
            if (targets.size() >= PAGE_BATCH_SIZE) break;
        }
        DiagnosticLog.write(this, "BATCH candidates=" + targets.size());
        return targets;
    }

    private void collectSemanticCheckboxRows(AccessibilityNodeInfo node,
                                             List<AccessibilityNodeInfo> out) {
        CharSequence raw = node.getContentDescription();
        if (raw != null) {
            String description = raw.toString().trim();
            if (description.contains("checkbox") &&
                    description.startsWith("انتخاب نشده")) {
                out.add(node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectSemanticCheckboxRows(child, out);
        }
    }

    private AccessibilityNodeInfo findFirstUncheckedContactChoice(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        collectCheckableNodes(root, candidates);
        DiagnosticLog.write(this, "CHECKABLE candidates=" + candidates.size());
        for (AccessibilityNodeInfo node : candidates) {
            if (!node.isVisibleToUser() || !node.isEnabled() || node.isChecked()) continue;
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) continue;
            String key = contactKeyNear(root, node);
            if (automation.hasProcessedKey(key)) continue;
            return node;
        }
        return null;
    }

    private ContactTarget findFallbackContactTarget(AccessibilityNodeInfo root) {
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        int listTop = findSectionBottom(root, "مخاطبین شما");
        if (listTop <= 0) listTop = screen.top + dp(150);
        int listBottom = findLowestButtonTop(root, "افزودن عضو");
        if (listBottom <= listTop) listBottom = screen.bottom - dp(80);

        List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
        collectVisibleTextNodes(root, textNodes);
        DiagnosticLog.write(
                this,
                "FALLBACK screen=" + screen.toShortString() +
                        " listTop=" + listTop +
                        " listBottom=" + listBottom +
                        " visibleTexts=" + textNodes.size()
        );
        AccessibilityNodeInfo best = null;
        Rect bestBounds = null;
        String bestKey = null;
        for (AccessibilityNodeInfo node : textNodes) {
            CharSequence raw = node.getText();
            if (raw == null) continue;
            String value = raw.toString().trim();
            if (!BaleTextRules.isPossibleContactName(value)) continue;

            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            int centerY = bounds.centerY();
            if (bounds.isEmpty() || centerY <= listTop || centerY >= listBottom) continue;
            String key = BaleTextRules.normalizeContactKey(value);
            if (automation.hasProcessedKey(key) || rowLooksSelected(node, screen)) continue;
            if (bestBounds == null || bounds.top < bestBounds.top) {
                best = node;
                bestBounds = bounds;
                bestKey = key;
            }
        }
        if (best == null || bestBounds == null) {
            DiagnosticLog.write(this, "FALLBACK no contact-name candidate");
            return null;
        }
        int y = findRowCenterY(best, screen);
        int x = screen.right - dp(16);
        DiagnosticLog.write(
                this,
                "FALLBACK target key=" + bestKey +
                        " textBounds=" + bestBounds.toShortString() +
                        " tap=" + x + "," + y
        );
        return new ContactTarget(bestKey, x, y);
    }

    private List<ContactTarget> singleTarget(ContactTarget target) {
        List<ContactTarget> result = new ArrayList<>();
        result.add(target);
        return result;
    }

    private void beginVerification(List<ContactTarget> targets) {
        gestureInFlight = true;
        limitDetected = false;
        batchVerifyAttempts = 0;
        handler.postDelayed(() -> verifyBatch(targets), BATCH_VERIFY_DELAY_MS);
    }

    private boolean dispatchBatch(List<ContactTarget> targets) {
        if (gestureInFlight || targets.isEmpty()) return false;

        GestureDescription.Builder builder = new GestureDescription.Builder();
        long start = 0L;
        for (ContactTarget target : targets) {
            Path path = new Path();
            path.moveTo(target.x, target.y);
            builder.addStroke(new GestureDescription.StrokeDescription(
                    path, start, 45L
            ));
            start += BATCH_TAP_GAP_MS;
        }

        gestureInFlight = true;
        limitDetected = false;
        batchVerifyAttempts = 0;
        DiagnosticLog.write(
                this,
                "BATCH dispatch size=" + targets.size() +
                        " durationMs=" + start
        );
        automation.setStatus(targets.size() + " مخاطب این صفحه در حال انتخاب است");

        boolean dispatched = dispatchGesture(
                builder.build(),
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        DiagnosticLog.write(
                                BaleAccessibilityService.this,
                                "BATCH gesture completed size=" + targets.size()
                        );
                        handler.postDelayed(
                                () -> verifyBatch(targets),
                                BATCH_VERIFY_DELAY_MS
                        );
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        gestureInFlight = false;
                        DiagnosticLog.write(
                                BaleAccessibilityService.this,
                                "BATCH gesture cancelled"
                        );
                        automation.finishSelection("انتخاب گروهی توسط اندروید لغو شد");
                        showToast("انتخاب گروهی انجام نشد؛ انتخاب‌گر متوقف شد");
                    }
                },
                handler
        );

        if (!dispatched) {
            gestureInFlight = false;
            automation.finishSelection("اندروید اجازه اجرای انتخاب گروهی نداد");
            DiagnosticLog.write(this, "BATCH dispatch returned false");
        }
        return dispatched;
    }

    private void verifyBatch(List<ContactTarget> targets) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (batchVerifyAttempts++ < 1) {
                handler.postDelayed(() -> verifyBatch(targets), BATCH_VERIFY_DELAY_MS);
            } else {
                gestureInFlight = false;
                automation.finishSelection("صفحه بله برای بررسی انتخاب‌ها در دسترس نبود");
            }
            return;
        }

        int verified = 0;
        {
            for (ContactTarget target : targets) {
                if (isContactChecked(root, target.key)) {
                    automation.recordCheckedSelection(target.key);
                    verified++;
                }
            }
            boolean reachedLimit = limitDetected || hasBaleLimitWarning(root);
            DiagnosticLog.write(
                    this,
                    "BATCH verify checked=" + verified +
                            "/" + targets.size() +
                            " limit=" + reachedLimit +
                            " attempt=" + batchVerifyAttempts
            );

            if (!reachedLimit && verified < targets.size() && batchVerifyAttempts++ < 1) {
                handler.postDelayed(() -> verifyBatch(targets), BATCH_VERIFY_DELAY_MS);
                return;
            }

            gestureInFlight = false;
            if (reachedLimit) {
                stopDueToLimit();
                return;
            }
            if (verified < targets.size()) {
                automation.finishSelection(
                        "حداقل یک چک‌باکس انتخاب نشد؛ انتخاب‌گر متوقف شد"
                );
                showToast("چک‌باکس انتخاب نشد؛ انتخاب‌گر متوقف شد");
                return;
            }

            stagnantScrolls = 0;
            lastScrollFingerprint = "";
            automation.setStatus(
                    automation.selectedCount() + " مخاطب در این اجرا تیک خورده است"
            );
            scheduleStep();
        }
    }

    private boolean isContactChecked(AccessibilityNodeInfo node, String key) {
        CharSequence raw = node.getContentDescription();
        if (raw != null) {
            String description = raw.toString().trim();
            if (description.contains("checkbox") &&
                    description.startsWith("انتخاب شده")) {
                String currentName = BaleTextRules.extractContactName(description);
                if (key != null && key.equals(currentName)) return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && isContactChecked(child, key)) return true;
        }
        return false;
    }

    private String contactKeyNear(AccessibilityNodeInfo root,
                                  AccessibilityNodeInfo choice) {
        Rect choiceBounds = new Rect();
        choice.getBoundsInScreen(choiceBounds);
        List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
        collectVisibleTextNodes(root, textNodes);
        String closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (AccessibilityNodeInfo node : textNodes) {
            CharSequence raw = node.getText();
            if (raw == null) continue;
            String value = raw.toString().trim();
            if (!BaleTextRules.isPossibleContactName(value)) continue;
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            int distance = Math.abs(bounds.centerY() - choiceBounds.centerY());
            if (distance < closestDistance && distance <= dp(70)) {
                closest = BaleTextRules.normalizeContactKey(value);
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean rowLooksSelected(AccessibilityNodeInfo textNode, Rect screen) {
        AccessibilityNodeInfo current = textNode;
        for (int level = 0; level < 5 && current != null; level++) {
            if (current.isChecked() || current.isSelected()) return true;
            Rect bounds = new Rect();
            current.getBoundsInScreen(bounds);
            if (bounds.width() >= screen.width() / 2 &&
                    bounds.height() <= dp(150) &&
                    hasCheckedDescendant(current)) return true;
            current = current.getParent();
        }
        return false;
    }

    private boolean hasCheckedDescendant(AccessibilityNodeInfo node) {
        if (node.isChecked()) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && hasCheckedDescendant(child)) return true;
        }
        return false;
    }

    private int findRowCenterY(AccessibilityNodeInfo textNode, Rect screen) {
        AccessibilityNodeInfo current = textNode;
        int fallback = 0;
        for (int level = 0; level < 5 && current != null; level++) {
            Rect bounds = new Rect();
            current.getBoundsInScreen(bounds);
            if (level == 0) fallback = bounds.centerY();
            if (bounds.width() >= screen.width() / 2 &&
                    bounds.height() >= dp(44) &&
                    bounds.height() <= dp(150)) {
                return bounds.centerY();
            }
            current = current.getParent();
        }
        return fallback;
    }

    private int findSectionBottom(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        int bottom = 0;
        if (nodes == null) return bottom;
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence raw = node.getText();
            if (raw == null || !raw.toString().trim().equals(text)) continue;
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            bottom = Math.max(bottom, bounds.bottom);
        }
        return bottom;
    }

    private int findLowestButtonTop(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        int top = 0;
        if (nodes == null) return top;
        for (AccessibilityNodeInfo node : nodes) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.top > top) top = bounds.top;
        }
        return top;
    }

    private void collectVisibleTextNodes(AccessibilityNodeInfo node,
                                         List<AccessibilityNodeInfo> out) {
        if (node.isVisibleToUser() && node.getText() != null) out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectVisibleTextNodes(child, out);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void collectCheckableNodes(AccessibilityNodeInfo node,
                                       List<AccessibilityNodeInfo> out) {
        CharSequence className = node.getClassName();
        if (node.isCheckable() ||
                "android.widget.CheckBox".contentEquals(className) ||
                "android.widget.CheckedTextView".contentEquals(className)) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectCheckableNodes(child, out);
        }
    }

    private boolean clickChannelHeader(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        collectTextNodes(root, "VeilAura", candidates);
        collectTextNodes(root, "ویلورا", candidates);
        collectTextNodes(root, "veilaura3", candidates);
        for (AccessibilityNodeInfo node : candidates) {
            if (clickNodeOrParent(node)) return true;
        }
        return false;
    }

    private boolean clickFirstText(AccessibilityNodeInfo root, String wanted) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(wanted);
        if (nodes == null) return false;
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence text = node.getText();
            if (text != null && text.toString().trim().equals(wanted) &&
                    clickNodeOrParent(node)) return true;
        }
        for (AccessibilityNodeInfo node : nodes) {
            if (clickNodeOrParent(node)) return true;
        }
        return false;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable() &&
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private boolean containsText(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        return nodes != null && !nodes.isEmpty();
    }

    private void collectTextNodes(AccessibilityNodeInfo node, String fragment,
                                  List<AccessibilityNodeInfo> out) {
        CharSequence text = node.getText();
        if (text != null && text.toString().contains(fragment)) out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectTextNodes(child, fragment, out);
        }
    }

    private String screenFingerprint(AccessibilityNodeInfo root) {
        StringBuilder value = new StringBuilder();
        appendCheckboxDescriptions(root, value);
        return value.toString();
    }

    private void appendCheckboxDescriptions(AccessibilityNodeInfo node,
                                            StringBuilder value) {
        CharSequence raw = node.getContentDescription();
        if (raw != null && raw.toString().contains("checkbox")) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            value.append(raw).append('@').append(bounds.top).append(';');
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) appendCheckboxDescriptions(child, value);
        }
    }

    private boolean scrollForward(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo scrollable = findScrollable(root);
        return scrollable != null &&
                scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findScrollable(child);
            if (found != null) return found;
        }
        return null;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static final class ContactTarget {
        final String key;
        final int x;
        final int y;

        ContactTarget(String key, int x, int y) {
            this.key = key;
            this.x = x;
            this.y = y;
        }
    }
}
