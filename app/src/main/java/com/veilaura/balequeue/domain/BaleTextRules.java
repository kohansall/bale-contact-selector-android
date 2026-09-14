package com.veilaura.balequeue.domain;

/**
 * قواعد متنی خالص و قابل‌آزمایش برای رابط فارسی بله.
 *
 * این کلاس به Android وابسته نیست تا تشخیص پیام‌ها و نرمال‌سازی شناسه‌ها
 * بدون اجرای سرویس دسترسی‌پذیری قابل تست باشد.
 */
public final class BaleTextRules {
    private BaleTextRules() {
    }

    public static boolean isLimitMessage(String value) {
        if (value == null) return false;
        String normalized = normalizeWhitespace(value);
        return normalized.contains("امکان دعوت کردن عضو جدید") &&
                normalized.contains("سقف مجاز رسیده");
    }

    public static String extractContactName(String description) {
        if (description == null) return null;
        String[] parts = description.split(",", 4);
        if (parts.length < 3) return null;
        String name = normalizeWhitespace(parts[2]);
        return name.isEmpty() ? null : name;
    }

    public static boolean isPossibleContactName(String value) {
        if (value == null) return false;
        String normalized = normalizeWhitespace(value);
        if (normalized.length() < 2 || normalized.length() > 80) return false;
        if (normalized.matches("[\\d۰-۹٠-٩+@._-]+")) return false;
        return !normalized.equals("افزودن عضو") &&
                !normalized.equals("مخاطبین شما") &&
                !normalized.contains("دعوت به کانال") &&
                !normalized.contains("مدت‌ها پیش") &&
                !normalized.contains("آخرین بازدید") &&
                !normalized.contains("اینجا بوده") &&
                !normalized.equals("آنلاین") &&
                !normalized.equals("جستجو");
    }

    public static String normalizeContactKey(String value) {
        return value == null ? "" : normalizeWhitespace(value);
    }

    public static String normalizeChannel(String value) {
        if (value == null) return null;
        String channel = value.trim()
                .replace("https://ble.ir/", "")
                .replace("http://ble.ir/", "");
        while (channel.startsWith("@") || channel.startsWith("/")) {
            channel = channel.substring(1);
        }
        return channel.matches("[A-Za-z0-9_]{3,64}") ? channel : null;
    }

    public static String normalizeWhitespace(String value) {
        return value.replace('\u200c', ' ').replaceAll("\\s+", " ").trim();
    }
}
