// SPDX-License-Identifier: AGPL-3.0-only

using CouchSubs.Core;

namespace CouchSubs.Core.Tests;

/// <summary>
/// GTA 6 telefonunun yanındaki çeviri okunur boyda (X, beta-106: "yanda küçük panel, okunmuyor"). Panel yazısı hep eski
/// alt sınırda (0,022: TV'de 1080p'de ~23 piksel, altyazının yarısı) kalıyordu. Telefon yerleri gerçek karelerden
/// (testdata/frames gta6-telefon-*; sağ el ve aynalanmış sol el, #1407). Mac PhonePanelReadableTests.swift ve Android
/// PhonePanelReadableTest.kt aynı.
/// </summary>
public class PhonePanelReadableTests
{
    private const double Subtitle = 46, TvFont = 0.95;

    private static ScreenPhoneCard.Handset Phone(double x, double top, double bottom) =>
        new(new GamePhoneGeometry.Pose(x, top, x, bottom), 16.0 / 9);

    private static List<ScreenTranslation.Item> Chat(ScreenPhoneCard.Handset phone)
    {
        var b = phone.Bounds;
        ScreenTranslation.Item Item(string source, string translation, ScreenPhoneCard.Role role, double v)
        {
            var top = b.Top + (b.Bottom - b.Top) * v;
            return new(source, translation, new TextBox(b.Left + 0.02, top, b.Right - 0.02, top + 0.017), 0.017)
            {
                Unchanged = false, Phone = new ScreenPhoneCard.Placement(role, new TextBox(0.1, v, 0.9, v + 0.03)),
            };
        }
        return
        [
            Item("Where are you man", "Neredesin adamım", ScreenPhoneCard.Role.Incoming, 0.25),
            Item("On my way, five minutes", "Yoldayım, beş dakikaya oradayım", ScreenPhoneCard.Role.Outgoing, 0.40),
            Item("Thanks man! If you find anything else, bring it to me", "Sağ ol dostum! Başka bir şey bulursan bana getir",
                ScreenPhoneCard.Role.Incoming, 0.55),
        ];
    }

    public static TheoryData<double, double, double> Phones => new()
    {
        { 0.702, 0.195, 0.861 }, { 0.296, 0.195, 0.861 }, // gta6-telefon-sohbet-1 (sağ el, sol el)
        { 0.483, 0.131, 0.676 }, { 0.515, 0.131, 0.676 }, // gta6-telefon-sohbet-2
        { 0.704, 0.204, 0.896 }, { 0.293, 0.204, 0.896 }, // gta6-telefon-ana-1
    };

    [Theory]
    [MemberData(nameof(Phones))]
    public void PanelIsReadableAndNotSqueezed(double x, double top, double bottom)
    {
        var phone = Phone(x, top, bottom);
        var laid = ScreenPhoneCard.Layout(Chat(phone), phone);
        var card = laid.First(item => item.Panel == ScreenPhoneCard.PanelPart.Card);
        foreach (var height in new[] { 1080.0, 2160.0 })
            Assert.True(card.LineHeight * height * TvFont >= Subtitle * 0.85 * height / 1080 - 0.5, $"{height}: {card.LineHeight}");
        var box = card.Box;
        Assert.True(box.Right - box.Left >= ScreenPhoneCard.CardMin - 1e-9);
        Assert.True(box.Left >= ScreenPhoneCard.Gap - 1e-9 && box.Right <= 1 - ScreenPhoneCard.Gap + 1e-9);
        if ((phone.Bounds.Left + phone.Bounds.Right) / 2 < 0.5) Assert.True(box.Left >= phone.Bounds.Right - 1e-9);
        else Assert.True(box.Right <= phone.Bounds.Left + 1e-9);
        foreach (var part in laid.Where(item => item.Panel is ScreenPhoneCard.PanelPart.Theirs or ScreenPhoneCard.PanelPart.Mine))
        {
            Assert.True(part.Box.Left >= box.Left - 1e-9 && part.Box.Right <= box.Right + 1e-9);
            var perLine = (box.Right - box.Left) * ScreenPhoneCard.BubbleShare * 16 / 9 / (card.LineHeight * ScreenPhoneCard.CharWidth);
            Assert.True(perLine >= 18, $"{perLine}");
        }
    }

    [Theory]
    [InlineData(36)]
    [InlineData(46)]
    [InlineData(60)]
    [InlineData(72)]
    public void PanelFontFollowsTheSubtitleSizeSetting(int size)
    {
        var phone = Phone(0.70, 0.195, 0.861);
        var card = ScreenPhoneCard.Layout(Chat(phone), phone, ScreenPhoneCard.TextScale(size))
            .First(item => item.Panel == ScreenPhoneCard.PanelPart.Card);
        Assert.True(card.LineHeight * 1080 * TvFont >= size * 0.85 - 0.5, $"{size}: {card.LineHeight}");
    }

    [Fact]
    public void CardIsNotSqueezedAgainstTheEdgeToAvoidOtherText()
    {
        var phone = new TextBox(0.62, 0.2, 0.8, 0.8);
        var card = ScreenPhoneCard.CardBox(phone, [new TextBox(0.3, 0.4, 0.5, 0.6)]);
        Assert.True(card.Right <= phone.Left);
        Assert.Equal(ScreenPhoneCard.CardMin, card.Right - card.Left, 9);
    }
}
