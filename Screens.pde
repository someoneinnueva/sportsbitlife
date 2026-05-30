// ============================================================
// UI THEME
// ============================================================
class UITheme {
  // Palette — vibrant modern sports aesthetic
  color BG        = color(8,  12, 22);
  color PANEL     = color(14, 20, 36);
  color PANEL_2   = color(20, 28, 50);
  color BORDER    = color(42, 58, 90);
  color ACCENT    = color(255, 200, 55);   // bright gold
  color ACCENT2   = color(65, 155, 245);   // electric blue
  color SUCCESS   = color(45, 210, 115);   // vivid green
  color DANGER    = color(240, 72,  72);   // vivid red
  color TEXT      = color(228, 234, 248);  // near white
  color TEXT_DIM  = color(110, 135, 175);  // blue-grey
  color TEXT_GOLD = color(255, 215, 80);   // warm gold
  color PURPLE    = color(185, 90,  255);  // electric purple
  color FAMILY    = color(255, 95,  165);  // hot pink

  // Typography
  int TITLE  = 28;
  int HEADER = 18;
  int BODY   = 13;
  int SMALL  = 11;

  // ── Card ─────────────────────────────────────────────────
  void drawCard(float x, float y, float w, float h, boolean highlighted) {
    fill(0, 0, 0, 50);
    rect(x + 2, y + 3, w, h, 8);
    if (highlighted) { stroke(ACCENT); strokeWeight(2); }
    else             { stroke(BORDER); strokeWeight(1); }
    fill(PANEL);
    rect(x, y, w, h, 6);
    noStroke();
    fill(255, 255, 255, 12);
    rect(x + 6, y + 1, w - 12, 3, 1);
  }

  void drawCard(float x, float y, float w, float h) {
    drawCard(x, y, w, h, false);
  }

  void drawAccentCard(float x, float y, float w, float h, color accentColor) {
    drawCard(x, y, w, h, false);
    fill(accentColor);
    rect(x, y + 8, 3, h - 16, 2);
  }

  // ── Stat bar ─────────────────────────────────────────────
  void drawStatBar(float x, float y, float w, float h, float value, float max, color barColor) {
    fill(PANEL_2);
    rect(x, y, w, h, 3);
    float filled = map(constrain(value, 0, max), 0, max, 0, w);
    if (filled > 2) {
      fill(barColor);
      rect(x, y, filled, h, 3);
      fill(barColor, 40);
      ellipse(x + filled, y + h / 2, h * 4, h * 4);
    }
  }

  // ── Button ───────────────────────────────────────────────
  boolean drawButton(float x, float y, float w, float h, String label, boolean hover) {
    color bg  = hover ? ACCENT   : PANEL_2;
    color txt = hover ? color(10,12,22) : TEXT;
    fill(0, 0, 0, 25); rect(x + 2, y + 2, w, h, 5);
    fill(bg); stroke(hover ? ACCENT : BORDER); strokeWeight(1);
    rect(x, y, w, h, 5); noStroke();
    if (hover) { fill(255, 255, 255, 25); rect(x + 2, y + 2, w - 4, h / 3, 3); }
    fill(txt); textSize(BODY); textAlign(CENTER, CENTER);
    text(label, x + w / 2, y + h / 2);
    return hover;
  }

  boolean drawDangerButton(float x, float y, float w, float h, String label, boolean hover) {
    fill(hover ? DANGER : color(50, 18, 18));
    stroke(hover ? DANGER : color(90, 28, 28)); strokeWeight(1);
    rect(x, y, w, h, 5); noStroke();
    fill(hover ? color(255, 240, 240) : color(210, 100, 100));
    textSize(BODY); textAlign(CENTER, CENTER);
    text(label, x + w / 2, y + h / 2);
    return hover;
  }

  void drawBadge(String label, float x, float y, color bg, color fg) {
    textSize(9);
    float w = textWidth(label) + 14;
    fill(bg); rect(x, y, w, 20, 10);
    fill(fg); textAlign(CENTER, CENTER); text(label, x + w / 2, y + 10);
  }

  void drawDotGrid() {
    int spacing = 32;
    for (int gx = spacing; gx < width; gx += spacing)
      for (int gy = 56; gy < height; gy += spacing) {
        fill(40, 55, 85, 80); noStroke(); ellipse(gx, gy, 2, 2);
      }
  }

  boolean isHover(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx <= x + w && my >= y && my <= y + h;
  }
}

// ── Base Screen ─────────────────────────────────────────────
abstract class BaseScreen {
  GameEngine engine;
  int hoverX, hoverY;

  BaseScreen(GameEngine e) { engine = e; }

  abstract void render();
  void onEnter() {}
  void onKey(char k, int kc) {}
  void onClick(int mx, int my)   {}
  void onRelease(int mx, int my) {}
  void onHover(int mx, int my)   { hoverX = mx; hoverY = my; }

  // ── Section header ────────────────────────────────────────
  void sectionHeader(String label, float x, float y) {
    fill(theme.TEXT_DIM); textSize(theme.SMALL); textAlign(LEFT, CENTER);
    text(label.toUpperCase(), x, y);
    stroke(theme.BORDER); strokeWeight(1);
    line(x + textWidth(label.toUpperCase()) + 8, y, x + 360, y);
    noStroke();
  }

  // ── Event panel with prominent choices ───────────────────
  void drawEventPanel(GameEvent evt, float x, float y, float w) {
    if (evt == null) return;
    int numChoices = (evt.choices != null) ? evt.choices.size() : 0;
    float h = 130 + numChoices * 68;
    theme.drawCard(x, y, w, h, true);

    // Left accent stripe by event type
    color stripe = theme.ACCENT;
    if (evt.eventType == EventType.FAMILY)  stripe = theme.FAMILY;
    if (evt.eventType == EventType.INJURY)  stripe = theme.DANGER;
    if (evt.eventType == EventType.MEDIA)   stripe = theme.ACCENT2;
    fill(stripe); rect(x, y + 8, 4, h - 16, 2);

    // Headline
    fill(stripe); textSize(17); textAlign(LEFT, TOP);
    text(evt.headline == null ? "" : evt.headline, x + 18, y + 14);

    // Description
    fill(theme.TEXT); textSize(12);
    drawWrappedText(evt.description == null ? "" : evt.description, x + 18, y + 40, w - 32, 18);

    // Choices
    if (evt.choices != null) {
      float cy = y + 110;
      for (int i = 0; i < evt.choices.size(); i++) {
        EventChoice ch = evt.choices.get(i);
        boolean hover = theme.isHover(hoverX, hoverY, x + 12, cy, w - 24, 60);

        // Choice box
        fill(hover ? color(30, 42, 70) : color(18, 24, 42));
        stroke(hover ? stripe : theme.BORDER); strokeWeight(hover ? 2 : 1);
        rect(x + 12, cy, w - 24, 60, 5); noStroke();

        // Number badge
        fill(stripe, hover ? 255 : 140);
        ellipse(x + 30, cy + 30, 22, 22);
        fill(hover ? color(10, 12, 22) : theme.BG);
        textSize(11); textAlign(CENTER, CENTER);
        text((i + 1) + "", x + 30, cy + 30);

        // Label
        fill(hover ? stripe : theme.TEXT); textSize(13); textAlign(LEFT, TOP);
        text(ch.label == null ? "" : ch.label, x + 46, cy + 10);

        // Description
        fill(theme.TEXT_DIM); textSize(10);
        drawWrappedText(ch.description == null ? "" : ch.description, x + 46, cy + 28, w - 170, 13);

        // Effect pills
        drawEffectPills(ch, x + w - 140, cy + 22);
        cy += 68;
      }
    }
  }

  // Effect pills
  void drawEffectPills(EventChoice ch, float x, float y) {
    float px = x;
    if (ch.confidenceEffect != 0)   px = drawPill("CON" + (ch.confidenceEffect > 0 ? "+" : "") + ch.confidenceEffect, px, y, ch.confidenceEffect > 0);
    if (ch.fatigueEffect    != 0)   px = drawPill("FAT" + (ch.fatigueEffect > 0 ? "+" : "") + ch.fatigueEffect, px, y, ch.fatigueEffect < 0);
    if (ch.mentalEffect     != 0)   px = drawPill("MNT" + (ch.mentalEffect > 0 ? "+" : "") + ch.mentalEffect, px, y, ch.mentalEffect > 0);
    if (ch.moneyEffect      != 0) {
      String ml = "$" + (ch.moneyEffect > 0 ? "+" : "") + ch.moneyEffect + "K";
      px = drawPill(ml, px, y, ch.moneyEffect > 0);
    }
    if (ch.familyEffect     != 0)   px = drawPill("FAM" + (ch.familyEffect > 0 ? "+" : "") + ch.familyEffect, px, y, ch.familyEffect > 0);
    if (ch.rankingPointsEffect != 0) drawPill("PTS" + (ch.rankingPointsEffect > 0 ? "+" : "") + ch.rankingPointsEffect, px, y, ch.rankingPointsEffect > 0);
  }

  float drawPill(String label, float x, float y, boolean positive) {
    textSize(9);
    float w = textWidth(label) + 10;
    fill(positive ? color(30, 70, 45) : color(70, 25, 25));
    rect(x, y, w, 17, 9);
    fill(positive ? theme.SUCCESS : theme.DANGER);
    textAlign(CENTER, CENTER); text(label, x + w / 2, y + 8);
    return x + w + 4;
  }

  void drawWrappedText(String txt, float x, float y, float maxW, float lineH) {
    if (txt == null || txt.isEmpty()) return;
    String[] words = txt.split(" ");
    String line = "";
    float cy = y;
    textAlign(LEFT, TOP);
    for (String word : words) {
      String test = line.isEmpty() ? word : line + " " + word;
      if (textWidth(test) > maxW) { text(line, x, cy); cy += lineH; line = word; }
      else line = test;
    }
    if (!line.isEmpty()) text(line, x, cy);
  }

  String formatMoney(float m) {
    if (m < 0) return "-$" + formatMoneyAbs(-m);
    return "$" + formatMoneyAbs(m);
  }
  String formatMoneyAbs(float m) {
    if (m >= 1000000) return nf(m / 1000000, 0, 1) + "M";
    if (m >= 1000)    return (int)(m / 1000) + "K";
    return "" + (int)m;
  }

  protected void logCareerEvent(String msg) {
    if (engine != null && engine.state != null && engine.state.player != null)
      engine.state.player.career.addHistory(msg);
  }
}
