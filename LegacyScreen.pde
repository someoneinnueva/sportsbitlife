// ============================================================
// LEGACY SCREEN
// ============================================================
class LegacyScreen extends BaseScreen {
  String  narrative = "";
  boolean generated = false;
  boolean hoverGen  = false;

  LegacyScreen(GameEngine e) { super(e); }

  void onEnter() {
    if (!generated && engine.state.player != null) {
      rivalNarCtx = computeRivalsContext();   // sketch-level global, readable by AIEngine
      engine.ai.generateLegacyNarrative(engine.state.player, engine.state.currentYear,
        engine.state.currentSport,
        new LegacyCallback() {
          public void onComplete(String txt) {
            narrative = txt;
            generated = true;
          }
        });
    }
  }

  String computeRivalsContext() {
    if (engine.state.rivals == null || engine.state.rivals.isEmpty()) return "";
    int dominated = 0, lost = 0;
    for (Rival r : engine.state.rivals) {
      if (r.h2hWins > r.h2hLosses)  dominated++;
      else if (r.h2hLosses > r.h2hWins) lost++;
    }
    return "Rivals dominated: " + dominated + " | Rivals lost head-to-head: " + lost;
  }

  int computeLegacyScore(Player p) {
    int careerYears = engine.state.proCareerStartYear > 0
      ? engine.state.currentYear - engine.state.proCareerStartYear : 0;
    int score = p.career.grandSlamTitles * 120
              + p.career.titlesWon       * 18
              + p.career.weeksAtNumberOne * 2
              + (int)(min(p.career.prizeMoney, 50000000) / 100000)
              - max(0, careerYears - 38) * 15;
    if (engine.state.rivals != null) {
      for (Rival r : engine.state.rivals) {
        if (r.h2hWins > r.h2hLosses)   score += 40;
        else if (r.h2hLosses > r.h2hWins) score -= 20;
      }
    }
    return constrain(score, 0, 1000);
  }

  String hofStatus(int score) {
    if (score >= 800) return "FIRST BALLOT HALL OF FAMER";
    if (score >= 600) return "HALL OF FAMER";
    if (score >= 400) return "STRONG CANDIDATE";
    if (score >= 200) return "BORDERLINE";
    return "NOT ELIGIBLE";
  }

  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    float x = 40, y = 70;
    fill(theme.ACCENT); textSize(26); textAlign(LEFT, TOP);
    text("LEGACY", x, y);
    y += 50;

    LegacyTier tier    = computeTier(p);
    color      tierCol = legacyColor(tier);
    fill(tierCol, 40); rect(x, y, 200, 36, 6);
    fill(tierCol); textSize(18); textAlign(CENTER, CENTER);
    text(tier.toString().replace("_", " "), x + 100, y + 18);

    if (!p.hometown.isEmpty()) {
      fill(theme.ACCENT2, 30); rect(x + 210, y, 160, 36, 6);
      fill(theme.ACCENT2); textSize(11); textAlign(CENTER, CENTER);
      text(p.hometown + ", " + p.nationality, x + 290, y + 18);
    }
    y += 54;

    // Career stats card
    theme.drawCard(x, y, 500, 180);
    int proYears = engine.state.proCareerStartYear > 0 ? engine.state.currentYear - engine.state.proCareerStartYear : 0;
    drawLegacyStat("Grand Slams",  p.career.grandSlamTitles + "",    x +  20, y + 20);
    drawLegacyStat("Total Titles", p.career.titlesWon + "",          x + 180, y + 20);
    drawLegacyStat("Best Ranking", "#" + p.career.worldRanking,      x + 340, y + 20);
    drawLegacyStat("Prize Money",  formatMoney(p.career.prizeMoney), x +  20, y + 80);
    drawLegacyStat("Wks at No.1",  p.career.weeksAtNumberOne + "",   x + 180, y + 80);
    drawLegacyStat("Pro Years",    proYears + "",                     x + 340, y + 80);
    y += 200;

    // Family & Relationships
    sectionHeader("FAMILY & RELATIONSHIPS", x, y); y += 20;
    theme.drawCard(x, y, 500, 120); float fy = y + 12;
    if (p.parents != null) {
      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
      text("Mom: " + p.parents.mom.name + " (" + p.parents.mom.relationshipLabel() + ")", x + 14, fy);
      text("Dad: " + p.parents.dad.name + " (" + p.parents.dad.relationshipLabel() + ")", x + 240, fy);
      fy += 20;
    }
    if (p.family.spouse != null) {
      fill(theme.FAMILY); textSize(10); textAlign(LEFT, TOP);
      text("♥ " + p.family.statusDisplay(), x + 14, fy); fy += 20;
    }
    if (!p.family.children.isEmpty()) {
      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
      String kidsStr = "Children: ";
      for (int i = 0; i < p.family.children.size(); i++) {
        Child c = p.family.children.get(i);
        kidsStr += c.name + " (age " + c.age + ")";
        if (i < p.family.children.size() - 1) kidsStr += ", ";
      }
      text(kidsStr, x + 14, fy);
    }
    y += 128;

    // RIVALS section
    if (engine.state.rivals != null && !engine.state.rivals.isEmpty()) {
      sectionHeader("RIVALS", x, y); y += 20;
      for (Rival r : engine.state.rivals) {
        color rv = r.h2hWins > r.h2hLosses ? theme.SUCCESS :
                   (r.h2hLosses > r.h2hWins ? theme.DANGER : theme.TEXT_DIM);
        fill(rv, 25); rect(x, y, 500, 26, 3);
        fill(rv); textSize(11); textAlign(LEFT, CENTER);
        text(r.name, x + 12, y + 13);
        fill(theme.TEXT_DIM); textAlign(CENTER, CENTER);
        text("H2H " + r.h2hWins + "-" + r.h2hLosses, x + 260, y + 13);
        fill(rv); textAlign(RIGHT, CENTER);
        text(r.verdict(), x + 488, y + 13);
        y += 30;
      }
      y += 6;
    }

    // Career highlights
    sectionHeader("CAREER HIGHLIGHTS", x, y); y += 20;
    fill(theme.TEXT_DIM); textSize(12); textAlign(LEFT, TOP);
    ArrayList<String> hist = p.career.careerHistory;
    int startIdx = max(0, hist.size() - 7);
    for (int i = startIdx; i < hist.size(); i++) {
      text("- " + hist.get(i), x, y); y += 20;
    }

    // ── RIGHT PANEL ─────────────────────────────────────────
    y = 70; x = 580;

    // HOF / Legacy Score card (when retirement eligible)
    if (engine.state.retirementEligible) {
      int legacyScore = computeLegacyScore(p);
      String hof      = hofStatus(legacyScore);
      color hofCol    = legacyScore >= 600 ? theme.ACCENT :
                        (legacyScore >= 400 ? theme.ACCENT2 : theme.TEXT_DIM);

      // Plaque card
      fill(theme.ACCENT, 20); stroke(theme.ACCENT); strokeWeight(1);
      rect(x, y, 560, 130, 6); noStroke();

      fill(theme.ACCENT); textSize(11); textAlign(LEFT, TOP);
      text("RETIREMENT ASSESSMENT", x + 20, y + 14);

      fill(hofCol); textSize(36); textAlign(LEFT, TOP);
      text("" + legacyScore, x + 20, y + 32);
      fill(theme.TEXT_DIM); textSize(10);
      text("LEGACY SCORE / 1000", x + 20, y + 74);

      fill(hofCol); textSize(15); textAlign(LEFT, TOP);
      text(hof, x + 160, y + 46);

      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
      text(p.name + "  ·  " + proYears + " years on tour  ·  " + p.nationality, x + 20, y + 100);
      y += 146;
    }

    // AI narrative card
    float narrativeH = engine.state.retirementEligible ? 360 : 540;
    theme.drawCard(x, y, 560, narrativeH);
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("AI LEGACY NARRATIVE", x + 20, y + 16);

    if (engine.ai.isLoading) {
      fill(theme.ACCENT); textSize(13); textAlign(CENTER, CENTER);
      text(engine.ai.getLoadingText(), x + 280, y + narrativeH / 2);
    } else if (!narrative.isEmpty()) {
      fill(theme.TEXT); textSize(13); textAlign(LEFT, TOP);
      drawWrappedText(narrative, x + 20, y + 40, 520, 20);
    } else {
      hoverGen = theme.isHover(hoverX, hoverY, x + 20, y + narrativeH / 2 - 20, 200, 40);
      theme.drawButton(x + 20, y + (int)(narrativeH / 2) - 20, 200, 40, "GENERATE NARRATIVE", hoverGen);
    }
  }

  void drawLegacyStat(String label, String val, float x, float y) {
    fill(theme.ACCENT); textSize(22); textAlign(LEFT, TOP);
    text(val, x, y);
    fill(theme.TEXT_DIM); textSize(10);
    text(label.toUpperCase(), x, y + 28);
  }

  LegacyTier computeTier(Player p) {
    int gs = p.career.grandSlamTitles;
    int tt = p.career.titlesWon;
    if      (gs >= 10)               return LegacyTier.GOAT;
    else if (gs >=  5 || tt >= 30)   return LegacyTier.LEGEND;
    else if (gs >=  1 || tt >= 10)   return LegacyTier.STAR;
    else if (tt >=  3)               return LegacyTier.SOLID_PRO;
    else                              return LegacyTier.JOURNEYMAN;
  }

  color legacyColor(LegacyTier t) {
    switch (t) {
      case GOAT:      return color(220, 180,  40);
      case LEGEND:    return color(180,  80, 220);
      case STAR:      return color( 60, 140, 220);
      case SOLID_PRO: return color( 60, 180, 100);
      default:        return theme.TEXT_DIM;
    }
  }

  void onClick(int mx, int my) {
    float x = 580, y = 70;
    float btnY = engine.state.retirementEligible ? y + 146 + 180 : y + 260;
    if (theme.isHover(mx, my, x + 20, btnY, 200, 40)) {
      generated = false;
      narrative = "";
      onEnter();
    }
  }
}
