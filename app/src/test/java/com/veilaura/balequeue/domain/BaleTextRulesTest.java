package com.veilaura.balequeue.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BaleTextRulesTest {
    @Test
    public void detectsExactBaleLimitMessage() {
        assertTrue(BaleTextRules.isLimitMessage(
                "امکان دعوت کردن عضو جدید هنگام ساخت کانال به سقف مجاز رسیده است."
        ));
        assertFalse(BaleTextRules.isLimitMessage("مخاطب انتخاب شد"));
    }

    @Test
    public void extractsContactNameFromComposeDescription() {
        assertEquals(
                "آرمین آبادی",
                BaleTextRules.extractContactName(
                        "انتخاب نشده, checkbox, آرمین آبادی, مدت‌ها پیش اینجا بوده"
                )
        );
        assertNull(BaleTextRules.extractContactName("انتخاب نشده"));
    }

    @Test
    public void normalizesSupportedChannelForms() {
        assertEquals("veilaura3", BaleTextRules.normalizeChannel("@veilaura3"));
        assertEquals("veilaura3", BaleTextRules.normalizeChannel("https://ble.ir/veilaura3"));
        assertNull(BaleTextRules.normalizeChannel("bad channel"));
    }
}
