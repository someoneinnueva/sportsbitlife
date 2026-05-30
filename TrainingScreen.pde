// ============================================================
// TRAINING SCREEN
// ============================================================
class TrainingScreen extends BaseScreen {
  String[] trainingTypes = {"SERVE", "FITNESS", "MENTAL", "TACTICAL"};
  float[]  intensities   = {60, 60, 60, 60};
  int      selectedType  = 0;

  // Track actual button position from render so onClick uses the same coords
  float trainBtnY  = -1;
  float tabY       = -1;
  float sliderXr   = 0, sliderYr = 0, sliderWr = 0;

  boolean hoverTrain  = false;
  String  lastResult  = "";
  float   resultAlpha = 0;

  TrainingScreen(GameEngine e) { super(e); }

  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    theme.drawDotGrid();

    float x = 30, y = 70;

    // Header
    fill(theme.ACCENT); textSize(24); textAlign(LEFT, TOP);
    text("TRAINING CAMP", x, y);
    fill(theme.TEXT_DIM); textSize(12);
    text("Choose type · set intensity · run session", x, y + 32);
    y += 68;

    // ── Type tabs ──────────────────────────────────────────
    tabY = y;
    color[] tabCols = { theme.ACCENT, theme.SUCCESS, theme.PURPLE, theme.ACCENT2 };
    for (int i = 0; i < trainingTypes.length; i++) {
      boolean sel  = (i == selectedType);
      color   tc   = tabCols[i];
      fill(sel ? tc : theme.PANEL_2);
      stroke(sel ? tc : theme.BORDER); strokeWeight(1);
      rect(x + i * 168, y, 156, 44, 6); noStroke();
      fill(sel ? color(10, 12, 22) : tc);
      textSize(12); textAlign(CENTER, CENTER);
      text(trainingTypes[i], x + i * 168 + 78, y + 22);
    }
    y += 58;

    // ── Intensity card ────────────────────────────────────
    theme.drawCard(x, y, 730, 190);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("INTENSITY", x + 20, y + 14);
    fill(theme.ACCENT); textSize(28); textAlign(LEFT, TOP);
    text((int)intensities[selectedType] + "%", x + 100, y + 8);

    // Labels
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
    text("LOW", x + 20, y + 48);
    textAlign(RIGHT, TOP);
    text("MAX", x + 710, y + 48);

    // Slider
    float sX = x + 20, sY = y + 62, sW = 690, sH = 14;
    float filled = map(intensities[selectedType], 0, 100, 0, sW);
    fill(theme.PANEL_2); rect(sX, sY, sW, sH, 7);
    fill(theme.ACCENT);  rect(sX, sY, filled, sH, 7);
    // Handle
    fill(theme.BG); stroke(theme.ACCENT); strokeWeight(2);
    ellipse(sX + filled, sY + sH / 2, 24, 24); noStroke();
    fill(theme.ACCENT); ellipse(sX + filled, sY + sH / 2, 12, 12);

    // Store slider coords for drag detection
    sliderXr = sX; sliderYr = sY; sliderWr = sW;

    // Projected gains
    TrainingSession sess = new TrainingSession(trainingTypes[selectedType], intensities[selectedType]);
    TrainingOutcome out  = sess.projectedGains();

    float gy = y + 96;
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("PROJECTED GAINS:", x + 20, gy);

    drawGainRow("Serve",    out.serveGain,    x + 130, gy);
    drawGainRow("Forehand", out.forehandGain, x + 250, gy);
    drawGainRow("Backhand", out.backhandGain, x + 370, gy);
    drawGainRow("Speed",    out.speedGain,    x + 490, gy);
    drawGainRow("Stamina",  out.staminaGain,  x + 590, gy);
    drawGainRow("Mental",   out.mentalGain,   x + 658, gy);

    gy += 22;
    fill(theme.DANGER); textSize(10); textAlign(LEFT, TOP);
    text("Fatigue +" + (int)out.fatigueAdded + "%", x + 130, gy);

    float risk = p.health.injuryRisk(p.form, intensities[selectedType] / 100.0);
    fill(risk > 0.1 ? theme.DANGER : theme.TEXT_DIM);
    text("Injury risk: " + (int)(risk * 100) + "%", x + 300, gy);

    y += 204;

    // ── Two-column layout ─────────────────────────────────
    // Left: RUN button + result
    trainBtnY = y;   // <-- saved for onClick
    hoverTrain = theme.isHover(hoverX, hoverY, x, y, 340, 54);
    fill(hoverTrain ? theme.ACCENT : color(22, 36, 18));
    stroke(theme.ACCENT); strokeWeight(hoverTrain ? 2 : 1);
    rect(x, y, 340, 54, 6); noStroke();
    fill(hoverTrain ? color(10, 12, 22) : theme.ACCENT);
    textSize(14); textAlign(CENTER, CENTER);
    text("▶  RUN TRAINING SESSION", x + 170, y + 27);

    if (resultAlpha > 0) {
      resultAlpha = max(0, resultAlpha - 2);
      theme.drawCard(x, y + 64, 340, 56);
      boolean ok = lastResult.startsWith("[OK]");
      fill(ok ? theme.SUCCESS : theme.DANGER, resultAlpha);
      textSize(11); textAlign(LEFT, CENTER);
      text(lastResult.replace("[OK] ", "").replace("[!] ", ""), x + 14, y + 92);
    }

    // Right: player current stats summary
    float cx = x + 360;
    theme.drawCard(cx, y, 370, 120);
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
    text("CURRENT STATS", cx + 16, y + 10);

    PlayerAttributes eff = p.effectiveAttributes();
    SportConfig sc = engine.state.sportConfig != null ? engine.state.sportConfig : getSportConfig(Sport.TENNIS);
    int[] vals = {eff.serve, eff.forehand, eff.backhand, eff.volley, eff.speed, eff.stamina, eff.mental};

    for (int i = 0; i < sc.statNames.length; i++) {
      float bx = cx + 16 + (i % 4) * 88;
      float by = y + 28 + (i / 4) * 44;
      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text(sc.statNames[i], bx, by);
      fill(theme.ACCENT2); textSize(16); textAlign(LEFT, TOP); text("" + vals[i], bx, by + 12);
    }

    // Fatigue + confidence bars
    y += 134;
    theme.drawCard(x, y, 730, 54);
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, CENTER);
    text("FATIGUE", x + 16, y + 14);
    theme.drawStatBar(x + 80, y + 8, 240, 10, p.form.fatigue, 100, p.form.fatigue > 70 ? theme.DANGER : theme.ACCENT2);
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, CENTER);
    text("CONFIDENCE", x + 360, y + 14);
    theme.drawStatBar(x + 452, y + 8, 256, 10, p.form.confidence, 100, theme.SUCCESS);

    // Injury warning
    if (p.health.status != InjuryStatus.HEALTHY) {
      y += 62;
      fill(theme.DANGER, 45); rect(x, y, 730, 36, 5);
      fill(theme.DANGER); textSize(11); textAlign(CENTER, CENTER);
      text("INJURED: " + p.health.injuredPart + " — " + p.health.weeksRemaining + " weeks remaining", x + 365, y + 18);
    }

    // Drag slider during mousePress
    if (mousePressed && theme.isHover(mouseX, mouseY, sliderXr - 12, sliderYr - 8, sliderWr + 24, 30)) {
      intensities[selectedType] = constrain(map(mouseX, sliderXr, sliderXr + sliderWr, 0, 100), 10, 100);
    }
  }

  void drawGainRow(String label, int gain, float x, float y) {
    fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text(label, x, y);
    fill(gain > 0 ? theme.SUCCESS : theme.TEXT_DIM); textSize(13); textAlign(LEFT, TOP);
    text((gain > 0 ? "+" : "") + gain, x, y + 10);
  }

  void onClick(int mx, int my) {
    // Tab selection
    if (tabY >= 0) {
      for (int i = 0; i < trainingTypes.length; i++) {
        if (theme.isHover(mx, my, 30 + i * 168, tabY, 156, 44)) { selectedType = i; return; }
      }
    }
    // Train button — uses saved trainBtnY so coords always match render
    if (trainBtnY >= 0 && theme.isHover(mx, my, 30, trainBtnY, 340, 54)) {
      Player p = engine.state.player;
      if (p == null) return;
      TrainingSession sess = new TrainingSession(trainingTypes[selectedType], intensities[selectedType]);
      p.progression.applyTrainingGains(sess);
      float risk = p.health.injuryRisk(p.form, intensities[selectedType] / 100.0);
      if (random(1) < risk) {
        String[] parts = {"wrist","knee","shoulder","back","ankle"};
        String part = parts[(int)random(parts.length)];
        int weeks = (int)random(1, 6);
        p.health.applyInjury(part, weeks);
        lastResult = "[!] Injured " + part + " — out " + weeks + " weeks";
        engine.state.lastEvent = lastResult;
      } else {
        int fatGain = (int)(intensities[selectedType] * 0.3);
        lastResult = "[OK] Training done! Fatigue +" + fatGain + "% — gains applied.";
        engine.state.lastEvent = "Training session completed — gains applied.";
      }
      p.addLifeEvent(engine.state.lastEvent, engine.state.currentYear);
      resultAlpha = 255;
    }
  }
}
