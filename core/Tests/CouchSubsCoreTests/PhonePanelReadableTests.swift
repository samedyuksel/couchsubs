// SPDX-License-Identifier: AGPL-3.0-only

import CoreGraphics
import XCTest
@testable import CouchSubsCore

/// GTA 6 telefonunun yanındaki çeviri okunur boyda (X, @justwiever, beta-106, iki kez: "yanda küçük panel, okunmuyor").
/// Satır telefonun yazısından ölçülüyor, GTA 6'da telefon ekranda küçük olduğundan panel hep eski alt sınırda (0,022:
/// TV'de 1080p'de ~23 piksel, 46 piksellik altyazının yarısı) çiziliyordu. Gerçek kareler (testdata/frames; sağ el ve
/// aynalanmış sol el, #1407) telefonun yerini verir; panel yazısı altyazı boyunun en az ~%85'i, kart ekranın kenarına
/// sıkışmaz, telefonun ekranın ortasına bakan yanında. Windows PhonePanelReadableTests.cs ve Android
/// PhonePanelReadableTest.kt aynı ölçüleri denetler (gerçek kareden okunan telefon yerleri aşağıda sabit).
final class PhonePanelReadableTests: XCTestCase {
    /// Altyazı boyu (TV pikseli, 1080p) ve TV'nin panel yazısına satır oranı (app.js placePanel: line × 0,95).
    private let subtitle = 46.0, tvFont = 0.95

    private func chat(_ phone: ScreenPhoneCard.Handset) -> [ScreenTranslation.Item] {
        let b = phone.bounds
        func item(_ source: String, _ translation: String, _ role: ScreenPhoneCard.Role, _ v: Double) -> ScreenTranslation.Item {
            // GTA 6 telefonunun yazısı küçük: satır karenin ~%1,7'si.
            let top = b.top + (b.bottom - b.top) * v
            var item = ScreenTranslation.Item(source: source, translation: translation,
                                              box: TextBox(left: b.left + 0.02, top: top, right: b.right - 0.02, bottom: top + 0.017),
                                              lineHeight: 0.017)
            item.unchanged = false
            item.phone = ScreenPhoneCard.Placement(role: role, spot: TextBox(left: 0.1, top: v, right: 0.9, bottom: v + 0.03))
            return item
        }
        return [item("Where are you man", "Neredesin adamım", .incoming, 0.25),
                item("On my way, five minutes", "Yoldayım, beş dakikaya oradayım", .outgoing, 0.40),
                item("Thanks man! If you find anything else, bring it to me", "Sağ ol dostum! Başka bir şey bulursan bana getir", .incoming, 0.55)]
    }

    func testPanelOnRealFramesIsReadableAndNotSqueezed() throws {
        for name in ["gta6-telefon-sohbet-1", "gta6-telefon-sohbet-2", "gta6-telefon-ana-1", "gta6-telefon-ana-2"] {
            for transform in ["none", "mirror"] {
                let image = try GamePhoneFirstSightingTests.load(name, transform)
                let phone = try XCTUnwrap(ScreenPhoneCard.phone(in: image), "\(name) \(transform): telefon bulunamadı")
                let laid = ScreenPhoneCard.layout(chat(phone), handset: phone)
                let card = try XCTUnwrap(laid.first { $0.panel == .card }, "\(name) \(transform)")
                let tag = "\(name) \(transform)"
                // Yazı: 1080p'de ve 4K'da altyazı boyunun en az %85'i.
                for height in [1080.0, 2160.0] {
                    XCTAssertGreaterThanOrEqual(card.lineHeight * height * tvFont, subtitle * 0.85 * height / 1080 - 0.5, tag)
                }
                // Kart: geniş (1080p'de en az ~650 piksel), ekranın içinde, kenara yapışmadan; telefonun üstüne binmez ve
                // ekranın ortasına bakan yanda (sol elde sağda, sağ elde solda).
                let box = card.box
                XCTAssertGreaterThanOrEqual(box.right - box.left, ScreenPhoneCard.cardMin - 1e-9, tag)
                XCTAssertGreaterThanOrEqual(box.left, ScreenPhoneCard.gap - 1e-9, tag)
                XCTAssertLessThanOrEqual(box.right, 1 - ScreenPhoneCard.gap + 1e-9, tag)
                let phoneCenter = (phone.bounds.left + phone.bounds.right) / 2
                if phoneCenter < 0.5 { XCTAssertGreaterThanOrEqual(box.left, phone.bounds.right - 1e-9, tag) }
                else { XCTAssertLessThanOrEqual(box.right, phone.bounds.left + 1e-9, tag) }
                // Balonlar kartın içinde; satıra en az ~18 harf düşer (her satıra bir sözcük düşmez).
                for part in laid where part.panel == .theirs || part.panel == .mine {
                    XCTAssertTrue(part.box.left >= box.left - 1e-9 && part.box.right <= box.right + 1e-9, tag)
                    let perLine = (box.right - box.left) * ScreenPhoneCard.bubbleShare * 16 / 9 / (card.lineHeight * ScreenPhoneCard.charWidth)
                    XCTAssertGreaterThanOrEqual(perLine, 18, tag)
                }
            }
        }
    }

    /// Altyazı boyutu ayarı panel yazısını da büyütür/küçültür (çarpan 0,75–1,6).
    func testPanelFontFollowsTheSubtitleSizeSetting() {
        let phone = ScreenPhoneCard.Handset(pose: GamePhoneGeometry.Pose(topX: 0.70, topY: 0.195, bottomX: 0.70, bottomY: 0.861),
                                            aspect: 16.0 / 9)
        for size in [36, 46, 60, 72] {
            let scale = ScreenPhoneCard.textScale(fontSize: size)
            let card = ScreenPhoneCard.layout(chat(phone), handset: phone, textScale: scale).first { $0.panel == .card }!
            XCTAssertGreaterThanOrEqual(card.lineHeight * 1080 * tvFont, Double(size) * 0.85 - 0.5, "altyazı \(size)")
        }
    }

    /// Kart, başka bir çeviriden kaçmak için telefonun dar yanına (ekranın kenarına) sıkışmaz.
    func testCardIsNotSqueezedAgainstTheEdgeToAvoidOtherText() {
        let phone = TextBox(left: 0.62, top: 0.2, right: 0.8, bottom: 0.8)
        let card = ScreenPhoneCard.cardBox(phone, avoiding: [TextBox(left: 0.3, top: 0.4, right: 0.5, bottom: 0.6)])
        XCTAssertLessThanOrEqual(card.right, phone.left)
        XCTAssertEqual(card.right - card.left, ScreenPhoneCard.cardMin, accuracy: 1e-9)
    }
}
