// SPDX-License-Identifier: AGPL-3.0-only
package com.couchsubs.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telefon listesi kartları (Sinan Bey, beta.100 3397/3399): sohbet listesinin her satırı tek kart (avatar, kalın ad,
 * altında önizleme, rozet, seçim çerçevesi), ana ekran bileşeni tek kart; "…"sız kesilmiş önizleme kesik çevrilir, "$"
 * düşmez; avatar satır başına bir kez kesilir, rozet ve seçim art arda iki okumada değişir (iPhone ve Windows ile aynı).
 */
class PhoneListCardTest {
    private val upright = ScreenPhoneCard.Handset(GamePhoneGeometry.Pose(0.78, 0.17, 0.78, 0.85), 16.0 / 9)
    private val leftPhone = ScreenPhoneCard.Handset(GamePhoneGeometry.Pose(0.20, 0.17, 0.20, 0.85), 16.0 / 9)

    private fun item(source: String, translation: String, role: ScreenPhoneCard.Role, top: Double, unchanged: Boolean = false,
                     left: Double = 0.3, right: Double = 0.9) =
        ScreenTranslation.Item(source, translation, TextBox(0.8, 0.2 + top * 0.6, 0.9, 0.22 + top * 0.6), 0.018,
            unchanged = unchanged, phone = ScreenPhoneCard.Placement(role, TextBox(left, top, right, top + 0.03)))

    private fun list() = listOf(
        item("Messages", "Mesajlar", ScreenPhoneCard.Role.HEADING, 0.05, left = 0.05, right = 0.3),
        item("Joey", "Joey", ScreenPhoneCard.Role.CONTACT, 0.22, unchanged = true, right = 0.4),
        item("Hey this is Joey from Coa...", "Selam, ben Joey...", ScreenPhoneCard.Role.PREVIEW, 0.26),
        item("Shirley", "Shirley", ScreenPhoneCard.Role.CONTACT, 0.48, unchanged = true, right = 0.45),
        item("Oh that's nice hon.", "Ah, ne güzel tatlım.", ScreenPhoneCard.Role.PREVIEW, 0.52),
    )

    /** Ad ve önizleme tek kart; kartın üstü adın İngilizcesiyle aynı hizada; bölüm başlığı küçük başlık (not). */
    @Test
    fun listRowIsOneCardAlignedWithTheEnglishName() {
        val items = list()
        val laid = ScreenPhoneCard.layout(items, upright)
        assertEquals("aynı girdi aynı çıktı", laid, ScreenPhoneCard.layout(items, upright))
        assertEquals(ScreenPhoneCard.PanelPart.CARD, laid[0].panel)
        val heading = laid[1]
        assertEquals(ScreenPhoneCard.PanelPart.NOTE, heading.panel)
        assertEquals("Mesajlar", heading.translation)
        assertEquals(true, heading.look?.bold)
        assertEquals("left", heading.look?.align)
        val cards = laid.filter { it.panel == ScreenPhoneCard.PanelPart.ROW }
        assertEquals(2, cards.size)
        assertEquals(4, laid.size)
        val joey = cards[0]
        assertEquals("Joey", joey.listRow?.name)
        assertEquals("Selam, ben Joey...", joey.listRow?.preview)
        assertEquals("Joey\nHey this is Joey from Coa...", joey.source)
        assertEquals("Joey\nSelam, ben Joey...", joey.translation)
        assertEquals(ScreenPhoneCard.ROW_COLOR, joey.surface)
        assertEquals(ScreenPhoneCard.alignedTop(items[1], joey.lineHeight), joey.box.top, 1e-9)
        // Kart en az avatar boyunda.
        assertTrue(joey.box.bottom - joey.box.top >=
            ScreenPhoneCard.AVATAR_LINES * joey.lineHeight + 2 * ScreenPhoneCard.ROW_PADDING * joey.lineHeight - 1e-9)
        val row = joey.listRow!!
        assertNotNull(row.portraitSpot)
        assertNotNull(row.badgeSpot)
        assertNotNull(row.frameSpot)
        assertFalse(row.widget)
        // Kartlar üst üste binmez, telefondaki sırada.
        assertTrue(laid.drop(1).zipWithNext().all { (a, b) -> a.box.bottom <= b.box.top + 1e-9 })
        // Eşitlik karedeki arama yerlerini saymaz (#1279), görünüşü sayar.
        assertEquals(row, row.copy(portraitSpot = TextBox(0.0, 0.0, 0.1, 0.1), badgeSpot = null))
        assertEquals(row.hashCode(), row.copy(frameSpot = null).hashCode())
        assertNotEquals(row, row.copy(selected = "#3060F0"))
    }

    /** Kart telefonun boş tarafına gider (sağda ya da solda). */
    @Test
    fun listCardGoesToTheFreeSide() {
        val right = ScreenPhoneCard.layout(list(), leftPhone)
        assertTrue(right[0].box.left > leftPhone.bounds.right)
        assertTrue(right.filter { it.panel == ScreenPhoneCard.PanelPart.ROW }.all { it.box.left >= right[0].box.left })
        val left = ScreenPhoneCard.layout(list(), upright)
        assertTrue(left[0].box.right < upright.bounds.left)
    }

    /** Yalnız önizleme (adsız satır) da kart olur; JSON'da `row` alanı, seçim rengi yalnız geçerli renkse. */
    @Test
    fun lonelyPreviewAndJson() {
        val laid = ScreenPhoneCard.layout(listOf(item("Ok. There's some cash in t...", "Biraz para var...", ScreenPhoneCard.Role.PREVIEW, 0.3)), upright)
        val card = laid.single { it.panel == ScreenPhoneCard.PanelPart.ROW }
        assertEquals("", card.listRow?.name)
        assertEquals("Biraz para var...", card.listRow?.preview)
        val json = SubtitleServer.rowJson(card.listRow!!.copy(avatar = "data:image/png;base64,AA", selected = "#3060F0"))
        assertEquals(mapOf("name" to "", "preview" to "Biraz para var...", "avatar" to "data:image/png;base64,AA", "selected" to "#3060F0"), json)
        assertFalse(SubtitleServer.rowJson(card.listRow!!.copy(selected = "mavi")).containsKey("selected"))
        assertEquals(true, SubtitleServer.rowJson(card.listRow!!.copy(widget = true))["widget"])
    }

    /** Saydam pencerede liste kartı avatar boyunda, yazı avatarın sağında. */
    @Test
    fun stackedListCardIsAtLeastAvatarHigh() {
        val panel = ScreenPhoneCard.panel(ScreenPhoneCard.layout(list(), upright))!!
        val stacked = ScreenPhoneCard.stacked(panel, 1600.0, 900.0, 20.0)
        val line = stacked.card.lineHeight
        val rows = stacked.messages.filter { it.panel == ScreenPhoneCard.PanelPart.ROW }
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.box.bottom - it.box.top >= (ScreenPhoneCard.AVATAR_LINES + 2 * ScreenPhoneCard.ROW_PADDING) * line - 1e-9 })
    }

    /** Ana ekran bileşeni (başlık + sayaç) "Ekranı çevir"de ve otomatik okumada tek grup. */
    @Test
    fun widgetTitleAndCounterAreOneGroup() {
        assertTrue(PhoneChat.isCounter(" 6 Notifications "))
        assertFalse(PhoneChat.isCounter("Reminders"))
        fun group(text: String, left: Double, top: Double) = ScreenTranslation.Group(listOf(text),
            TextBox(0.8, top, 0.9, top + 0.02), 0.02, phone = ScreenPhoneCard.Placement(ScreenPhoneCard.Role.HOME, TextBox(left, top, 0.8, top + 0.03)))
        val merged = GamePhoneText.widgets(listOf(group("Reminders", 0.2, 0.30), group("6 Notifications", 0.08, 0.335),
            group("Hello there friend", 0.08, 0.6)))
        assertEquals(2, merged.size)
        assertEquals(listOf("Reminders", "6 Notifications"), merged[0].paragraphs)
        assertEquals(ScreenPhoneCard.Role.WIDGET, merged[0].phone?.role)
        assertEquals(0.08, merged[0].phone!!.spot.left, 1e-9)
        // Simge payından uzak sayaç birleşmez.
        assertEquals(2, GamePhoneText.widgets(listOf(group("Reminders", 0.4, 0.30), group("6 Notifications", 0.08, 0.335))).size)
        val laid = ScreenPhoneCard.layout(listOf(ScreenTranslation.Item("Reminders\n6 Notifications", "Hatırlatıcılar\n6 Bildirim",
            merged[0].box, 0.02, phone = merged[0].phone)), upright)
        val card = laid.single { it.panel == ScreenPhoneCard.PanelPart.ROW }
        assertEquals(ScreenPhoneCard.ListRow("Hatırlatıcılar", "6 Bildirim", widget = true), card.listRow)
        assertEquals(ScreenPhoneCard.CARD, card.surface)
        assertNotNull(card.listRow?.portraitSpot)
        assertNull(card.listRow?.badgeSpot)
    }

    /** "…"sız kesilmiş önizleme kesik çevrilir; "$" düşmez; bilinen tam mesajın çevirisinin başı kullanılır. */
    @Test
    fun clippedPreviewsAreTranslatedAsCut() {
        assertEquals("$ in your mailbox. You bailed", GamePhoneText.clippedInput("$ in your mailbox. You bailed"))
        assertEquals("Meet me", GamePhoneText.clippedInput("Meet me at the"))
        assertNull(GamePhoneText.clippedInput("Ok. See you there."))
        assertNull(GamePhoneText.clippedInput("Hello"))
        assertEquals("Meet me...", GamePhoneText.cutHint("Meet me"))
        assertEquals("$ posta kutunda", GamePhoneText.keepingLeadingSymbol("$ in your mailbox", "posta kutunda"))
        assertEquals("$5 ver", GamePhoneText.keepingLeadingSymbol("$5 now", "5 ver"))
        assertEquals("$ posta", GamePhoneText.keepingLeadingSymbol("$ mail", "$ posta"))
        assertEquals("posta", GamePhoneText.keepingLeadingSymbol("mail", "posta"))
        assertFalse("para simgesi kumanda simgesi değil", ButtonIcons.isIconToken("$"))
        assertFalse(ButtonIcons.isIconToken("€"))
        assertEquals("$ in your mailbox", PhoneChat.clean("$ in your mailbox"))
        assertEquals("in your mailbox", PhoneChat.clean("© in your mailbox"))
        assertTrue(OcrNoise.clean("$ in your mailbox", keepIdentifiers = true).startsWith("$"))

        val known = mapOf("Hey this is Joey from Coastal Realty, call me back." to "Selam, ben Coastal Realty'den Joey, beni ara.",
            "Hey" to "Selam")
        val preview = GamePhoneText.knownPreview("Hey this is Joey from Coa...", known)
        assertNotNull(preview)
        assertTrue(preview!!.startsWith("Selam, ben"))
        assertTrue(preview.endsWith("..."))
        assertTrue(preview.length < known.values.first().length)
        assertNull("kısa önizleme eşleşmez", GamePhoneText.knownPreview("Hey...", known))
        assertNull("bilinmeyen mesaj", GamePhoneText.knownPreview("Where are you going tonight", known))
    }

    private fun card(name: String): ScreenTranslation.Item = ScreenTranslation.Item(name, name, TextBox(0.1, 0.1, 0.5, 0.2), 0.02,
        panel = ScreenPhoneCard.PanelPart.ROW, listRow = ScreenPhoneCard.ListRow(name, "önizleme",
            portraitSpot = TextBox(0.05, 0.1, 0.15, 0.3), badgeSpot = TextBox(0.8, 0.1, 0.9, 0.3), frameSpot = TextBox(0.3, 0.02, 0.8, 0.08)))

    /** Sentetik kare: avatar yeri, rozet yeri ve (istenirse) mavi seçim çerçevesi. */
    private class Frame(var selected: Boolean, var badge: Boolean, var shade: Int = 0x804020) {
        val width = 200
        val height = 100
        fun rgb(x: Int, y: Int): Int = when {
            x < 30 && y in 10..30 -> shade
            selected && y in 2..8 && x in 60..160 -> 0x2050F0
            badge && x in 163..177 && y in 13..27 -> 0xE02020
            else -> 0xF4F1EC
        }
    }

    @Test
    fun listLookCutsTheAvatarOnceAndVotesBadgeAndSelection() {
        val look = PhoneListLook()
        val frame = Frame(selected = false, badge = true)
        fun apply() = look.apply(listOf(card("Joey")), frame.width, frame.height, frame::rgb)[0].listRow!!
        val first = apply()
        assertNotNull(first.avatar)
        assertTrue(first.avatar!!.startsWith("data:image/png;base64,"))
        assertNotNull("rozet ilk okumada", first.badge)
        assertNull(first.selected)
        // Avatar bir kez kesilir: kare değişse de aynı resim.
        frame.shade = 0x204080
        assertEquals(first.avatar, apply().avatar)
        // Seçim ancak art arda iki okumada değişir.
        frame.selected = true
        assertNull(apply().selected)
        val chosen = apply().selected
        assertNotNull(chosen)
        assertEquals("#2050F0", chosen)
        // Tek okumalık sapma geri döner, değişmez.
        frame.selected = false
        assertEquals(chosen, apply().selected)
        frame.selected = true
        assertEquals(chosen, apply().selected)
        // Rozet kalkınca iki okuma sonra düşer.
        frame.badge = false
        assertNotNull(apply().badge)
        assertNull(apply().badge)
        // Unutulunca yeniden kesilir.
        look.clear()
        assertNotEquals(first.avatar, apply().avatar)
        // Liste kartı olmayan öğeye dokunulmaz.
        val plain = ScreenTranslation.Item("x", "y", TextBox(0.0, 0.0, 0.1, 0.1), 0.02)
        assertEquals(listOf(plain), look.apply(listOf(plain), frame.width, frame.height, frame::rgb))
    }

    @Test
    fun frameAndBadgeDetectionOnSyntheticPixels() {
        val frame = Frame(selected = true, badge = true)
        assertEquals("#2050F0", PhoneListLook.frameColor(TextBox(0.3, 0.02, 0.8, 0.08), frame.width, frame.height, frame::rgb))
        assertNull(PhoneListLook.frameColor(TextBox(0.3, 0.4, 0.8, 0.6), frame.width, frame.height, frame::rgb))
        assertTrue(PhoneListLook.hasBadge(TextBox(0.8, 0.1, 0.9, 0.3), frame.width, frame.height, frame::rgb))
        assertFalse(PhoneListLook.hasBadge(TextBox(0.6, 0.4, 0.7, 0.6), frame.width, frame.height, frame::rgb))
        assertTrue(PhoneListLook.isBlue(0x2050F0))
        assertFalse(PhoneListLook.isBlue(0xF4F1EC))
        // Avatar en çok 48 piksel.
        val image = TextPainter.portraitImage(TextBox(0.0, 0.0, 1.0, 1.0), 200, 100, frame::rgb, PhoneListLook.AVATAR_MAX)
        assertNotNull(image)
    }
}
