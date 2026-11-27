// SPDX-License-Identifier: AGPL-3.0-only
package com.couchsubs.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GTA 6 telefonunun yanındaki çeviri okunur boyda (X, beta-106: "yanda küçük panel, okunmuyor"). Panel yazısı hep eski
 * alt sınırda (0,022: TV'de 1080p'de ~23 piksel, altyazının yarısı) kalıyordu. Telefon yerleri gerçek karelerden
 * (testdata/frames gta6-telefon-*; sağ el ve aynalanmış sol el, #1407). Mac PhonePanelReadableTests.swift ve Windows
 * PhonePanelReadableTests.cs aynı.
 */
class PhonePanelReadableTest {
    private val subtitle = 46.0
    private val tvFont = 0.95

    private fun phone(x: Double, top: Double, bottom: Double) =
        ScreenPhoneCard.Handset(GamePhoneGeometry.Pose(x, top, x, bottom), 16.0 / 9)

    private fun chat(phone: ScreenPhoneCard.Handset): List<ScreenTranslation.Item> {
        val b = phone.bounds
        fun item(source: String, translation: String, role: ScreenPhoneCard.Role, v: Double): ScreenTranslation.Item {
            val top = b.top + (b.bottom - b.top) * v
            return ScreenTranslation.Item(source, translation, TextBox(b.left + 0.02, top, b.right - 0.02, top + 0.017), 0.017,
                unchanged = false, phone = ScreenPhoneCard.Placement(role, TextBox(0.1, v, 0.9, v + 0.03)))
        }
        return listOf(
            item("Where are you man", "Neredesin adamım", ScreenPhoneCard.Role.INCOMING, 0.25),
            item("On my way, five minutes", "Yoldayım, beş dakikaya oradayım", ScreenPhoneCard.Role.OUTGOING, 0.40),
            item("Thanks man! If you find anything else, bring it to me", "Sağ ol dostum! Başka bir şey bulursan bana getir",
                ScreenPhoneCard.Role.INCOMING, 0.55),
        )
    }

    private val phones = listOf(
        Triple(0.702, 0.195, 0.861), Triple(0.296, 0.195, 0.861), // gta6-telefon-sohbet-1 (sağ el, sol el)
        Triple(0.483, 0.131, 0.676), Triple(0.515, 0.131, 0.676), // gta6-telefon-sohbet-2
        Triple(0.704, 0.204, 0.896), Triple(0.293, 0.204, 0.896), // gta6-telefon-ana-1
    )

    @Test
    fun panelIsReadableAndNotSqueezed() {
        for ((x, top, bottom) in phones) {
            val phone = phone(x, top, bottom)
            val laid = ScreenPhoneCard.layout(chat(phone), phone)
            val card = laid.first { it.panel == ScreenPhoneCard.PanelPart.CARD }
            val tag = "$x"
            for (height in listOf(1080.0, 2160.0)) {
                assertTrue(tag, card.lineHeight * height * tvFont >= subtitle * 0.85 * height / 1080 - 0.5)
            }
            val box = card.box
            assertTrue(tag, box.right - box.left >= ScreenPhoneCard.CARD_MIN - 1e-9)
            assertTrue(tag, box.left >= ScreenPhoneCard.GAP - 1e-9 && box.right <= 1 - ScreenPhoneCard.GAP + 1e-9)
            if ((phone.bounds.left + phone.bounds.right) / 2 < 0.5) assertTrue(tag, box.left >= phone.bounds.right - 1e-9)
            else assertTrue(tag, box.right <= phone.bounds.left + 1e-9)
            for (part in laid.filter { it.panel == ScreenPhoneCard.PanelPart.THEIRS || it.panel == ScreenPhoneCard.PanelPart.MINE }) {
                assertTrue(tag, part.box.left >= box.left - 1e-9 && part.box.right <= box.right + 1e-9)
                val perLine = (box.right - box.left) * ScreenPhoneCard.BUBBLE_SHARE * 16 / 9 / (card.lineHeight * ScreenPhoneCard.CHAR_WIDTH)
                assertTrue("$tag $perLine", perLine >= 18)
            }
        }
    }

    @Test
    fun panelFontFollowsTheSubtitleSizeSetting() {
        val phone = phone(0.70, 0.195, 0.861)
        for (size in listOf(36, 46, 60, 72)) {
            val card = ScreenPhoneCard.layout(chat(phone), phone, ScreenPhoneCard.textScale(size))
                .first { it.panel == ScreenPhoneCard.PanelPart.CARD }
            assertTrue("$size", card.lineHeight * 1080 * tvFont >= size * 0.85 - 0.5)
        }
    }

    @Test
    fun cardIsNotSqueezedAgainstTheEdgeToAvoidOtherText() {
        val phone = TextBox(0.62, 0.2, 0.8, 0.8)
        val card = ScreenPhoneCard.cardBox(phone, listOf(TextBox(0.3, 0.4, 0.5, 0.6)))
        assertTrue(card.right <= phone.left)
        assertEquals(ScreenPhoneCard.CARD_MIN, card.right - card.left, 1e-9)
    }
}
