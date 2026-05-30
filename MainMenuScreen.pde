// ============================================================
// MAIN MENU SCREEN
// ============================================================
class MainMenuScreen extends BaseScreen {
  String    playerName    = "Alex Reyes";
  String    nationality   = "Spain";
  PlayStyle playStyle     = PlayStyle.ALL_COURT;
  String    dominantSide  = "Right";
  Sport     selectedSport = Sport.TENNIS;
  int       focusedField  = -1;

  final String[] natOptions = {
    "Algeria","Argentina","Australia","Austria","Belgium","Bolivia",
    "Brazil","Cameroon","Canada","Chile","China","Colombia",
    "Costa Rica","Croatia","Cuba","Czech Republic","Denmark","Ecuador",
    "Egypt","England","Finland","France","Germany","Ghana",
    "Greece","Honduras","Hungary","India","Ireland","Ivory Coast",
    "Jamaica","Japan","Kazakhstan","Kenya","Latvia","Lithuania",
    "Mexico","Morocco","Netherlands","New Zealand","N. Ireland","Nigeria",
    "Norway","Panama","Paraguay","Peru","Philippines","Poland",
    "Portugal","Puerto Rico","Romania","Russia","Scotland","Senegal",
    "Serbia","Slovakia","Slovenia","South Africa","South Korea","Spain",
    "Sweden","Switzerland","Trinidad & Tobago","Tunisia","Turkey",
    "Ukraine","Uruguay","USA","Venezuela","Wales"
  };
  boolean dropdownOpen   = false;
  int     dropdownScroll = 0;
  float   dropListX, dropListY, dropListW;
  final int VISIBLE_ITEMS = 8;
  final float ITEM_H      = 30;

  String  hometown      = "Madrid";
  int     birthYear     = 2000;
  boolean hoverHometown = false;

  boolean hoverStart = false;
  boolean hoverName  = false;

  float titleY    = -80;
  float formAlpha = 0;

  MainMenuScreen(GameEngine e) { super(e); }

  void onEnter() { titleY = -80; formAlpha = 0; }

  SportConfig cfg() { return getSportConfig(selectedSport); }

  void render() {
    drawGrid();

    titleY    = lerp(titleY,    90,  0.06);
    formAlpha = lerp(formAlpha, 255, 0.04);

    fill(theme.ACCENT);
    textSize(46); textAlign(CENTER, CENTER);
    text("CAREER LEGACY", width / 2, titleY);

    fill(theme.TEXT_DIM);
    textSize(12);
    text(cfg().subtitle + "  ·  POWERED BY AI", width / 2, titleY + 46);
    fill(CLAUDE_API_KEY.isEmpty() ? theme.DANGER : theme.SUCCESS);
    textSize(10);
    text(CLAUDE_API_KEY.isEmpty() ? "● AI KEY NOT SET — procedural events only" : "● AI ACTIVE", width/2, titleY + 62);

    stroke(theme.BORDER); strokeWeight(1);
    line(width / 2 - 160, titleY + 76, width / 2 + 160, titleY + 76);
    noStroke();

    float cardW = 520, cardH = 570;
    float cx = width / 2 - cardW / 2;
    float cy = 155;
    tint(255, formAlpha);
    drawFormCard(cx, cy, cardW, cardH);
    noTint();
  }

  void drawGrid() {
    stroke(20, 28, 42); strokeWeight(1);
    for (int x = 0; x < width; x += 40) line(x, 0, x, height);
    for (int y = 0; y < height; y += 40) line(0, y, width, y);
    noStroke();
    for (int r = 300; r > 0; r -= 30) {
      fill(theme.ACCENT, map(r, 0, 300, 0, 8));
      ellipse(width / 2, height / 2, r * 2, r * 2);
    }
  }

  void drawFormCard(float cx, float cy, float cw, float ch) {
    fill(theme.PANEL); stroke(theme.BORDER); strokeWeight(1);
    rect(cx, cy, cw, ch, 8);
    noStroke();

    fill(theme.TEXT_DIM); textSize(11); textAlign(LEFT, TOP);
    text("CREATE YOUR PLAYER", cx + 20, cy + 16);

    float fy = cy + 40;
    float fw = cw - 40;

    // Sport selector
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("SELECT SPORT", cx + 20, fy);
    fy += 16;

    Sport[] sports = Sport.values();
    float sw2 = (fw - (sports.length - 1) * 3) / sports.length;
    for (int i = 0; i < sports.length; i++) {
      boolean sel = sports[i] == selectedSport;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * (sw2 + 3), fy, sw2, 34, 4);
      noStroke();
      fill(sel ? color(10, 12, 22) : theme.TEXT_DIM);
      textSize(11); textAlign(CENTER, CENTER);
      text(sportLabel(sports[i]), cx + 20 + i * (sw2 + 3) + sw2 / 2, fy + 17);
    }
    fy += 48;

    // Player name
    drawField("PLAYER NAME", playerName, cx + 20, fy, fw, focusedField == 0 || hoverName);
    fy += 64;

    // Nationality dropdown (closed view)
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("NATIONALITY", cx + 20, fy);

    boolean natHover = !dropdownOpen && theme.isHover(hoverX, hoverY, cx + 20, fy + 16, fw, 36);
    fill(dropdownOpen ? theme.PANEL_2 : (natHover ? theme.PANEL_2 : color(16, 20, 28)));
    stroke(dropdownOpen ? theme.ACCENT : (natHover ? theme.ACCENT2 : theme.BORDER));
    strokeWeight(1);
    rect(cx + 20, fy + 16, fw, 36, dropdownOpen ? 4 : 4);
    noStroke();

    fill(theme.TEXT); textSize(14); textAlign(LEFT, CENTER);
    text(nationality, cx + 20 + 12, fy + 16 + 18);

    fill(theme.TEXT_DIM); textSize(11); textAlign(RIGHT, CENTER);
    text(dropdownOpen ? "▲" : "▼", cx + 20 + fw - 14, fy + 16 + 18);

    dropListX = cx + 20;
    dropListY = fy + 16 + 36 + 2;
    dropListW = fw;
    fy += 64;

    // Hometown
    drawField("HOMETOWN / CITY", hometown, cx + 20, fy, fw, focusedField == 1 || hoverHometown);
    fy += 64;

    // Birth year
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("BIRTH YEAR", cx + 20, fy);
    fy += 16;
    int[] birthYears   = {1990, 1993, 1995, 1998, 2000, 2002, 2005};
    String[] yearShort = {"'90","'93","'95","'98","'00","'02","'05"};
    float byw = (fw - (birthYears.length - 1) * 3) / birthYears.length;
    for (int i = 0; i < birthYears.length; i++) {
      boolean sel = birthYears[i] == birthYear;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * (byw + 3), fy, byw, 32, 4);
      noStroke();
      fill(sel ? color(10, 12, 22) : theme.TEXT_DIM);
      textSize(10); textAlign(CENTER, CENTER);
      text(yearShort[i], cx + 20 + i * (byw + 3) + byw / 2, fy + 16);
    }
    fy += 48;

    // Role / Play Style
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text(cfg().name.toUpperCase() + " STYLE / ROLE", cx + 20, fy);
    fy += 16;

    PlayStyle[] styles = PlayStyle.values();
    String[] shorts = cfg().roleShort;
    float sw = (fw - (styles.length - 1) * 3) / styles.length;
    for (int i = 0; i < styles.length; i++) {
      boolean sel = styles[i] == playStyle;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * (sw + 3), fy, sw, 36, 4);
      noStroke();
      fill(sel ? color(10, 12, 22) : theme.TEXT_DIM);
      textSize(10); textAlign(CENTER, CENTER);
      text(i < shorts.length ? shorts[i] : styles[i].toString(),
           cx + 20 + i * (sw + 3) + sw / 2, fy + 18);
    }
    fy += 50;

    // Dominant hand / foot
    String sideLabel = (selectedSport == Sport.SOCCER) ? "DOMINANT FOOT" : "DOMINANT HAND";
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text(sideLabel, cx + 20, fy);
    fy += 16;

    String[] sides = {"Right", "Left"};
    for (int i = 0; i < sides.length; i++) {
      boolean sel = sides[i].equals(dominantSide);
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * 100, fy, 90, 32, 4);
      noStroke();
      fill(sel ? color(10, 12, 22) : theme.TEXT_DIM);
      textSize(12); textAlign(CENTER, CENTER);
      text(sides[i], cx + 20 + i * 100 + 45, fy + 16);
    }
    fy += 50;

    // Begin button
    boolean hover = theme.isHover(hoverX, hoverY, cx + 20, fy, fw, 48);
    hoverStart = hover;
    fill(hover ? theme.ACCENT : color(40, 50, 30));
    stroke(theme.ACCENT); strokeWeight(hover ? 2 : 1);
    rect(cx + 20, fy, fw, 48, 6);
    noStroke();
    fill(hover ? theme.BG : theme.ACCENT);
    textSize(14); textAlign(CENTER, CENTER);
    text("BEGIN YOUR LEGACY  ->", cx + 20 + fw / 2, fy + 24);

    // Dropdown list (rendered last so it appears on top)
    if (dropdownOpen) {
      float listH = VISIBLE_ITEMS * ITEM_H;

      fill(color(18, 24, 36));
      stroke(theme.ACCENT); strokeWeight(1);
      rect(dropListX, dropListY, dropListW, listH, 4);
      noStroke();

      for (int i = 0; i < VISIBLE_ITEMS; i++) {
        int idx = dropdownScroll + i;
        if (idx >= natOptions.length) break;

        boolean sel = natOptions[idx].equals(nationality);
        boolean hov = theme.isHover(hoverX, hoverY, dropListX,
                                    dropListY + i * ITEM_H, dropListW, ITEM_H);

        if (sel) {
          fill(theme.ACCENT, 50);
          rect(dropListX, dropListY + i * ITEM_H, dropListW, ITEM_H);
        } else if (hov) {
          fill(theme.PANEL_2);
          rect(dropListX, dropListY + i * ITEM_H, dropListW, ITEM_H);
        }

        fill(sel ? theme.ACCENT : (hov ? theme.TEXT : theme.TEXT_DIM));
        textSize(13); textAlign(LEFT, CENTER);
        text(natOptions[idx], dropListX + 14, dropListY + i * ITEM_H + ITEM_H / 2);

        if (i == 0 && dropdownScroll > 0) {
          fill(theme.TEXT_DIM); textSize(9); textAlign(RIGHT, CENTER);
          text("▲ " + dropdownScroll + " above",
               dropListX + dropListW - 10, dropListY + ITEM_H / 2);
        }
        if (i == VISIBLE_ITEMS - 1 && idx < natOptions.length - 1) {
          fill(theme.TEXT_DIM); textSize(9); textAlign(RIGHT, CENTER);
          text((natOptions.length - idx - 1) + " more ▼",
               dropListX + dropListW - 10, dropListY + i * ITEM_H + ITEM_H / 2);
        }
      }

      noFill(); stroke(theme.ACCENT); strokeWeight(1);
      rect(dropListX, dropListY, dropListW, listH, 4);
      noStroke();
    }
  }

  void drawField(String label, String value, float x, float y, float w, boolean active) {
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text(label, x, y);

    fill(active ? theme.PANEL_2 : color(16, 20, 28));
    stroke(active ? theme.ACCENT2 : theme.BORDER); strokeWeight(1);
    rect(x, y + 16, w, 36, 4);
    noStroke();

    fill(theme.TEXT); textSize(14); textAlign(LEFT, CENTER);
    text(value, x + 12, y + 16 + 18);

    if (active && (frameCount / 30) % 2 == 0) {
      stroke(theme.TEXT); strokeWeight(1.5);
      float cx = x + 12 + textWidth(value) + 2;
      line(cx, y + 24, cx, y + 42);
      noStroke();
    }
  }

  String sportLabel(Sport s) {
    switch (s) {
      case TENNIS:     return "Tennis";
      case BASKETBALL: return "Basketball";
      case SOCCER:     return "Soccer";
      case GOLF:       return "Golf";
      case BOXING:     return "Boxing";
      default:         return s.toString();
    }
  }

  void onClick(int mx, int my) {
    float cx = width / 2 - 260, cy = 155;
    float fw = 480;

    if (dropdownOpen) {
      float listH = VISIBLE_ITEMS * ITEM_H;
      if (theme.isHover(mx, my, dropListX, dropListY, dropListW, listH)) {
        int clickedRow = (int)((my - dropListY) / ITEM_H);
        int selected   = dropdownScroll + clickedRow;
        if (selected >= 0 && selected < natOptions.length) {
          nationality = natOptions[selected];
        }
      }
      dropdownOpen = false;
      return;
    }

    Sport[] sports = Sport.values();
    float sw2 = (fw - (sports.length - 1) * 3) / sports.length;
    float sty2 = cy + 56;
    for (int i = 0; i < sports.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (sw2 + 3), sty2, sw2, 34)) {
        selectedSport = sports[i];
        playStyle     = PlayStyle.ALL_COURT;
        return;
      }
    }

    if (theme.isHover(mx, my, cx + 20, cy + 120, fw, 36)) { focusedField = 0; return; }

    if (theme.isHover(mx, my, cx + 20, cy + 184, fw, 36)) {
      dropdownOpen = !dropdownOpen;
      focusedField = -1;
      if (dropdownOpen) scrollToSelected();
      return;
    }

    if (theme.isHover(mx, my, cx + 20, cy + 248, fw, 36)) { focusedField = 1; return; }

    int[] birthYears = {1990, 1993, 1995, 1998, 2000, 2002, 2005};
    float byw = (fw - (birthYears.length - 1) * 3) / birthYears.length;
    float yy  = cy + 312;
    for (int i = 0; i < birthYears.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (byw + 3), yy, byw, 32)) {
        birthYear = birthYears[i]; return;
      }
    }

    PlayStyle[] styles = PlayStyle.values();
    float sw  = (fw - (styles.length - 1) * 3) / styles.length;
    float styY = cy + 376;
    for (int i = 0; i < styles.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (sw + 3), styY, sw, 36)) {
        playStyle = styles[i]; return;
      }
    }

    float hy = cy + 442;
    if (theme.isHover(mx, my, cx + 20,  hy, 90, 32)) { dominantSide = "Right"; return; }
    if (theme.isHover(mx, my, cx + 120, hy, 90, 32)) { dominantSide = "Left";  return; }

    float by = cy + 492;
    if (theme.isHover(mx, my, cx + 20, by, fw, 48)) {
      if (!playerName.isEmpty()) {
        engine.newGame(playerName, nationality, hometown, playStyle, dominantSide, selectedSport, birthYear);
      }
    }

    focusedField = -1;
  }

  void onHover(int mx, int my) {
    super.onHover(mx, my);
    float cx = width / 2 - 260, cy = 155, fw = 480;
    hoverName     = theme.isHover(mx, my, cx + 20, cy + 120, fw, 36);
    hoverHometown = theme.isHover(mx, my, cx + 20, cy + 248, fw, 36);
  }

  void onKey(char k, int kc) {
    if (dropdownOpen) {
      if (kc == UP)     { dropdownScroll = max(0, dropdownScroll - 1); return; }
      if (kc == DOWN)   { dropdownScroll = min(natOptions.length - VISIBLE_ITEMS, dropdownScroll + 1); return; }
      if (k == ESC) { dropdownOpen = false; return; }
      if (k != CODED && Character.isLetter(k)) {
        char ch = Character.toUpperCase(k);
        for (int i = 0; i < natOptions.length; i++) {
          if (natOptions[i].charAt(0) == ch) {
            dropdownScroll = max(0, min(i, natOptions.length - VISIBLE_ITEMS));
            break;
          }
        }
      }
      return;
    }

    if (focusedField == 0) {
      if (kc == BACKSPACE && playerName.length() > 0)
        playerName = playerName.substring(0, playerName.length() - 1);
      else if (k != CODED && k != ENTER && k != RETURN && k != BACKSPACE && playerName.length() < 24)
        playerName += k;
    } else if (focusedField == 1) {
      if (kc == BACKSPACE && hometown.length() > 0)
        hometown = hometown.substring(0, hometown.length() - 1);
      else if (k != CODED && k != ENTER && k != RETURN && k != BACKSPACE && hometown.length() < 30)
        hometown += k;
    }
  }

  void scrollToSelected() {
    for (int i = 0; i < natOptions.length; i++) {
      if (natOptions[i].equals(nationality)) {
        dropdownScroll = constrain(i - VISIBLE_ITEMS / 2, 0, natOptions.length - VISIBLE_ITEMS);
        return;
      }
    }
    dropdownScroll = 0;
  }
}
