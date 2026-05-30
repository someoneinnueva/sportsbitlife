// ============================================================
// CAREER SCREEN - Main gameplay view (legacy week-based view)
// ============================================================
class CareerScreen extends BaseScreen {
  final float LEFT_COL  =  20;
  final float MID_COL   = 320;
  final float RIGHT_COL = 780;
  final float TOP_Y     =  62;

  boolean hoverAdvance = false;

  String toastText  = "";
  float  toastAlpha = 0;

  CareerScreen(GameEngine e) { super(e); }

  void onEnter() {}

  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    background(theme.BG);

    if (engine.ai.hasNewEvent && engine.state.pendingEvent == null) {
      engine.state.pendingEvent = engine.ai.pendingEvent;
      engine.ai.hasNewEvent     = false;
    }

    drawLeftColumn(p);
    drawMiddleColumn(p);
    drawRightColumn(p);
    drawToast();
  }

  void drawLeftColumn(Player p) {
    float x = LEFT_COL, y = TOP_Y;
    theme.drawCard(x, y, 285, 340);

    fill(theme.TEXT);
    textSize(20);
    textAlign(LEFT, TOP);
    text(p.name, x + 16, y + 14);

    fill(theme.TEXT_DIM);
    textSize(11);
    String sideTerm = (engine.state.currentSport == Sport.SOCCER) ? "foot" : "hand";
    text(p.nationality + " - " + p.dominantHand + " " + sideTerm, x + 16, y + 40);

    fill(theme.ACCENT);
    textSize(11);
    text(p.playStyle.toString().replace("_", " "), x + 16, y + 56);

    CareerPhase phase = p.currentPhase(engine.state.currentYear);
    color phaseCol = phaseColor(phase);
    fill(phaseCol, 40);
    rect(x + 16, y + 72, 120, 20, 10);
    fill(phaseCol);
    textSize(10);
    textAlign(CENTER, CENTER);
    text(phase.toString(), x + 76, y + 82);

    y += 104;
    PlayerAttributes eff = p.effectiveAttributes();
    SportConfig sc = engine.state.sportConfig != null ? engine.state.sportConfig : getSportConfig(Sport.TENNIS);
    String[] statNames = sc.statNames;
    int[]    statVals  = {eff.serve, eff.forehand, eff.backhand, eff.volley, eff.speed, eff.stamina, eff.mental};
    color[]  statCols  = {theme.ACCENT, theme.ACCENT2, theme.ACCENT2, theme.SUCCESS, theme.SUCCESS, theme.SUCCESS, theme.DANGER};

    for (int i = 0; i < statNames.length; i++) {
      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
      text(statNames[i], x + 16, y + 8);
      fill(theme.TEXT); textAlign(RIGHT, CENTER);
      text(statVals[i], x + 260, y + 8);
      theme.drawStatBar(x + 16, y + 16, 253, 6, statVals[i], 99, statCols[i]);
      y += 32;
    }

    y += 8;
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
    text("FATIGUE", LEFT_COL + 16, y);
    fill(p.form.fatigue > 70 ? theme.DANGER : theme.TEXT_DIM);
    textAlign(RIGHT, CENTER);
    text((int)p.form.fatigue + "%", LEFT_COL + 260, y);
    theme.drawStatBar(LEFT_COL + 16, y + 8, 253, 6, p.form.fatigue, 100,
                      p.form.fatigue > 70 ? theme.DANGER : theme.ACCENT2);

    y += 24;
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
    text("CONFIDENCE", LEFT_COL + 16, y);
    fill(theme.TEXT); textAlign(RIGHT, CENTER);
    text((int)p.form.confidence + "%", LEFT_COL + 260, y);
    theme.drawStatBar(LEFT_COL + 16, y + 8, 253, 6, p.form.confidence, 100, theme.SUCCESS);

    if (p.health.status != InjuryStatus.HEALTHY) {
      y += 28;
      fill(theme.DANGER, 40);
      rect(LEFT_COL + 16, y, 250, 22, 4);
      fill(theme.DANGER); textSize(11); textAlign(CENTER, CENTER);
      text("! " + p.health.injuredPart + " - " + p.health.weeksRemaining + " wks",
           LEFT_COL + 141, y + 11);
    }
  }

  void drawMiddleColumn(Player p) {
    float x = MID_COL, y = TOP_Y;
    theme.drawCard(x, y, 440, 120);

    float[] vals = {
      p.career.worldRanking,
      p.career.rankingPoints,
      p.career.titlesWon,
      p.career.grandSlamTitles,
      p.career.weeksAtNumberOne,
      p.career.prizeMoney / 1000
    };
    SportConfig sc2 = engine.state.sportConfig != null ? engine.state.sportConfig : getSportConfig(Sport.TENNIS);
    String[] lbls = {sc2.rankLabel, sc2.ptsLabel, sc2.titlesLabel, sc2.majorLabel, "WKS #1", "PRIZE ($K)"};

    for (int i = 0; i < 3; i++) {
      float cx = x + 16 + i * 140;
      fill(theme.ACCENT);
      textSize(24);
      textAlign(LEFT, TOP);
      text(i == 0 ? "#" + (int)vals[i] : (int)vals[i] + "", cx, y + 16);
      fill(theme.TEXT_DIM);
      textSize(10);
      text(lbls[i], cx, y + 50);
    }
    for (int i = 3; i < 6; i++) {
      float cx = x + 16 + (i - 3) * 140;
      fill(theme.TEXT);
      textSize(20);
      textAlign(LEFT, TOP);
      text(i == 5 ? formatMoney(p.career.prizeMoney) : (int)vals[i] + "", cx, y + 66);
      fill(theme.TEXT_DIM);
      textSize(10);
      text(lbls[i], cx, y + 92);
    }

    y += 132;

    if (engine.ai.isLoading) {
      theme.drawCard(x, y, 440, 80, true);
      fill(theme.ACCENT); textSize(14); textAlign(CENTER, CENTER);
      text(engine.ai.getLoadingText(), x + 220, y + 40);
    } else if (engine.state.pendingEvent != null) {
      drawEventPanel(engine.state.pendingEvent, x, y, 440);
    } else {
      theme.drawCard(x, y, 440, 80);
      fill(theme.TEXT_DIM); textSize(12); textAlign(LEFT, CENTER);
      if (!engine.state.lastEvent.isEmpty()) text(engine.state.lastEvent, x + 16, y + 40);
      else                                   text("Press SPACE to advance one week", x + 16, y + 40);
    }

    y += 340;

    if (!p.career.recentResults.isEmpty()) {
      sectionHeader("RECENT RESULTS", x, y);
      y += 16;
      int shown = min(4, p.career.recentResults.size());
      for (int i = 0; i < shown; i++) {
        MatchResult r = p.career.recentResults.get(i);
        drawResultRow(r, x, y, 440);
        y += 36;
      }
    }
  }

  void drawResultRow(MatchResult r, float x, float y, float w) {
    fill(r.playerWon ? color(20, 45, 25) : color(45, 20, 20));
    rect(x, y, w, 32, 4);
    fill(r.playerWon ? theme.SUCCESS : theme.DANGER);
    textSize(11); textAlign(LEFT, CENTER);
    text(r.playerWon ? "W" : "L", x + 12, y + 16);
    fill(theme.TEXT); textAlign(LEFT, CENTER);
    text(r.tournamentName + " " + r.round, x + 30, y + 16);
    fill(theme.TEXT_DIM); textAlign(RIGHT, CENTER);
    text(r.score + " +" + r.rankingPointsAwarded + "pts", x + w - 12, y + 16);
  }

  void drawRightColumn(Player p) {
    float x = RIGHT_COL, y = TOP_Y;
    hoverAdvance = theme.isHover(hoverX, hoverY, x, y, 380, 56);
    fill(hoverAdvance ? theme.ACCENT : color(30, 38, 22));
    stroke(theme.ACCENT);
    strokeWeight(hoverAdvance ? 2 : 1);
    rect(x, y, 380, 56, 6);
    noStroke();
    fill(hoverAdvance ? theme.BG : theme.ACCENT);
    textSize(15);
    textAlign(CENTER, CENTER);
    text("ADVANCE WEEK [SPACE]", x + 190, y + 28);

    y += 70;
    theme.drawCard(x, y, 380, 260);
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("SEASON CALENDAR", x + 16, y + 14);

    float cy = y + 30;
    int week = 0;
    ArrayList<Tournament> all = engine.calendar.schedule;
    int shown = 0;
    for (Tournament t : all) {
      if (shown >= 7) break;
      boolean isCurrent = t.week == week;
      if (t.week >= week || shown == 0) {
        drawCalendarRow(t, x + 12, cy, 356, isCurrent);
        cy += 34;
        shown++;
      }
    }

    y += 274;
    sectionHeader("CAREER LOG", x, y);
    y += 16;
    fill(theme.TEXT_DIM); textSize(11); textAlign(LEFT, TOP);
    ArrayList<String> hist = p.career.careerHistory;
    int start = max(0, hist.size() - 6);
    for (int i = start; i < hist.size(); i++) {
      text("- " + hist.get(i), x, y);
      y += 18;
    }
  }

  void drawCalendarRow(Tournament t, float x, float y, float w, boolean current) {
    if (current) {
      fill(theme.ACCENT, 30);
      rect(x, y, w, 30, 3);
    }
    color tierCol = tierColor(t.tier);
    fill(tierCol);
    ellipse(x + 12, y + 15, 8, 8);

    fill(current ? theme.ACCENT : theme.TEXT_DIM);
    textSize(10); textAlign(LEFT, CENTER);
    text("W" + t.week, x + 24, y + 15);

    fill(current ? theme.ACCENT : theme.TEXT);
    textSize(12); textAlign(LEFT, CENTER);
    text(t.name, x + 50, y + 15);

    String vLabel = t.venueLabel.isEmpty() ? t.surface.toString().replace("_", " ") : t.venueLabel;
    fill(surfaceColor(t.surface), 60);
    rect(x + w - 70, y + 6, 62, 18, 9);
    fill(surfaceColor(t.surface));
    textSize(9); textAlign(CENTER, CENTER);
    text(vLabel, x + w - 39, y + 15);
  }

  color phaseColor(CareerPhase p) {
    switch (p) {
      case JUNIOR:    return color(100, 160, 255);
      case RISING:    return color( 80, 220, 120);
      case PRIME:     return color(220, 160,  40);
      case VETERAN:   return color(200, 100,  60);
      case DECLINING: return color(160,  80,  80);
      default:        return theme.TEXT_DIM;
    }
  }

  color tierColor(TournamentTier t) {
    switch (t) {
      case GRAND_SLAM:   return color(220, 160,  40);
      case MASTERS_1000: return color(180,  80, 220);
      case ATP_500:      return color( 60, 140, 220);
      case ATP_250:      return color( 60, 180, 120);
      default:           return theme.TEXT_DIM;
    }
  }

  color surfaceColor(Surface s) {
    switch (s) {
      case CLAY:        return color(200, 100,  60);
      case GRASS:       return color( 60, 160,  80);
      case INDOOR_HARD: return color(100, 100, 200);
      default:          return color(100, 140, 200);
    }
  }

  void drawToast() {
    if (toastAlpha > 0) {
      toastAlpha -= 3;
      fill(theme.PANEL, toastAlpha);
      stroke(theme.ACCENT, toastAlpha);
      strokeWeight(1);
      rect(width / 2 - 200, height - 80, 400, 44, 6);
      noStroke();
      fill(theme.TEXT, toastAlpha);
      textSize(13); textAlign(CENTER, CENTER);
      text(toastText, width / 2, height - 58);
    }
  }

  void showToast(String msg) {
    toastText  = msg;
    toastAlpha = 255;
  }

  void onClick(int mx, int my) {
    if (theme.isHover(mx, my, RIGHT_COL, TOP_Y, 380, 56)) {
      engine.ageUp();
      return;
    }
    if (engine.state.pendingEvent != null && engine.state.pendingEvent.choices != null) {
      ArrayList<EventChoice> choices = engine.state.pendingEvent.choices;
      float ex = MID_COL, ey = TOP_Y + 132 + 120;
      for (int i = 0; i < choices.size(); i++) {
        if (theme.isHover(mx, my, ex + 14, ey, 412, 52)) {
          engine.applyChoice(choices.get(i));
          showToast("Choice made: " + choices.get(i).label);
          return;
        }
        ey += 62;
      }
    }
  }

  void onHover(int mx, int my) { super.onHover(mx, my); }
}
