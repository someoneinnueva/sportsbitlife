// ============================================================
// LIFE SCREEN  —  BitLife-style main gameplay view
// ============================================================
class LifeScreen extends BaseScreen {

  ActivityCategory selectedCategory = null;
  boolean hoverAgeUp = false;

  // Layout constants
  final float LP_X = 12,  LP_W = 268;
  final float MP_X = 290, MP_W = 520;
  final float RP_X = 820, RP_W = 368;
  final float PY   = 60,  PH   = 710;

  // Track event panel top so click detection always matches render
  float eventPanelY = -1;

  LifeScreen(GameEngine e) { super(e); }

  // ── Phase-aware category labels ───────────────────────────
  String[] getCatLabels() {
    LifePhase ph = engine.state.lifePhase;
    if (ph == LifePhase.CHILDHOOD)    return new String[]{"PLAY","LEARN","FAMILY","HEALTH","POCKET $","SOCIAL"};
    if (ph == LifePhase.EARLY_SCHOOL) return new String[]{"SPORT","STUDY","FAMILY","HEALTH","MONEY","SOCIAL"};
    if (ph == LifePhase.TEEN)         return new String[]{"SPORT","STUDY","LOVE","HEALTH","MONEY","SOCIAL"};
    if (ph == LifePhase.UNIVERSITY)   return new String[]{"SPORT","STUDY","LOVE","HEALTH","FINANCES","SOCIAL"};
    return new String[]{"CAREER","TRAINING","LOVE & FAM","HEALTH","ASSETS","SOCIAL"};
  }

  String[][] getSubLabels() {
    LifePhase ph = engine.state.lifePhase;
    switch (ph) {
      case CHILDHOOD:
        return new String[][]{
          {"Play Outside","Draw & Create","Watch Sports","Play Sport"},
          {"Read Books","Do Homework","Explore Nature","Build Things"},
          {"Family Time","Talk to Mom","Talk to Dad","Visit Family"},
          {"Eat Healthy","Doctor Visit","Sleep Early","Rest Day"},
          {"Save Allowance","Buy a Toy","Help at Home","Yard Sale"},
          {"Make a Friend","Birthday Party","School Show","Join Club"}};
      case EARLY_SCHOOL:
        return new String[][]{
          {"School Sports","Youth Training","Watch Pro Match","School Tournament"},
          {"Study Hard","Library Time","Creative Project","Extra Tutoring"},
          {"Hang with Friends","Call Parents","Family Dinner","Visit Family"},
          {"Eat Healthy","Doctor Checkup","Physical Training","Rest Day"},
          {"Do Chores","Save Up","Buy Equipment","Part-time Help"},
          {"Post Clip Online","Local Event","Community Work","School Club"}};
      case TEEN:
        return new String[][]{
          {"Youth Tournament","Approach Scout","Hire First Coach","Amateur Match"},
          {"Intensive Drill","Light Practice","Mental Focus","Recovery Week"},
          {"Go on a Date","Call Parents","Family Time","Date Together"},
          {"Doctor Checkup","Start Therapy","Hit the Gym","Rest Day"},
          {"Part-time Job","Buy Equipment","Save Up","First Investment"},
          {"Post on Socials","Charity Work","Give Interview","Seek Sponsor"}};
      case UNIVERSITY:
        return new String[][]{
          {"Collegiate Match","Approach Scout","Hire Coach","Study Film"},
          {"Intensive Training","Light Practice","Mental Coaching","Recovery Week"},
          {"Go on a Date","Call Parents","Family Dinner","Date Together"},
          {"Doctor Checkup","Start Therapy","Hit the Gym","Spa Day"},
          {"Part-time Job","Invest Savings","Apply Scholarship","Buy Equipment"},
          {"Post on Socials","Charity Event","Give Interview","Seek Sponsor"}};
      default: // PRO
        return new String[][]{
          {"Enter Tournament","Seek Sponsor","Hire Coach","Press Day"},
          {"Intensive Training","Light Practice","Mental Coaching","Recovery Week"},
          {"Go on a Date","Spend Time Together","Have a Child","Call Family"},
          {"Doctor Checkup","Start Therapy","Hit the Gym","Spa Day"},
          {"Buy Property","Invest Money","Buy a Car","Start Business"},
          {"Post on Socials","Charity Event","Give Interview","Hire PR Manager"}};
    }
  }

  String[][] getActionKeys() {
    LifePhase ph = engine.state.lifePhase;
    switch (ph) {
      case CHILDHOOD:
        return new String[][]{
          {"play_outside","draw","watch_sport","play_sport"},
          {"read","homework","explore","build"},
          {"family_time","talk_mom","talk_dad","visit_family"},
          {"eat_healthy","doctor_visit","sleep","rest"},
          {"save_allowance","buy_toy","help_home","help_home"},
          {"make_friend","make_friend","make_friend","join_club"}};
      case EARLY_SCHOOL:
        return new String[][]{
          {"school_sports","youth_train","watch_pro","school_tourney"},
          {"study_hard","library","creative","tutor"},
          {"hangout","call_parents","family_dinner","family_time"},
          {"eat_healthy","doctor_visit","phys_train","rest"},
          {"chores","save_up","buy_equip","part_time_help"},
          {"post_clip","local_event","community","school_club"}};
      case TEEN:
        return new String[][]{
          {"youth_tourney","approach_scout","hire_first_coach","amateur_match"},
          {"intensive","light","mental","recovery"},
          {"date","call_parents","family_time","date_together"},
          {"doctor","therapy","gym","rest"},
          {"part_time_job","buy_equip","save_up","first_invest"},
          {"post","charity","interview","sponsor"}};
      case UNIVERSITY:
        return new String[][]{
          {"collegiate_match","approach_scout","hire_coach","study_film"},
          {"intensive","light","mental","recovery"},
          {"date","call_parents","family_dinner","date_together"},
          {"doctor","therapy","gym","spa"},
          {"part_time_job","invest","scholarship","buy_equip"},
          {"post","charity","interview","sponsor"}};
      default:
        return new String[][]{
          {"tournament","sponsor","coach","media"},
          {"intensive","light","mental","recovery"},
          {"date","propose","child","family"},
          {"doctor","therapy","gym","spa"},
          {"property","invest","car","business"},
          {"post","charity","interview","pr"}};
    }
  }

  // ── Main render ───────────────────────────────────────────
  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    if (engine.ai.hasNewEvent && engine.state.pendingEvent == null) {
      engine.state.pendingEvent = engine.ai.pendingEvent;
      engine.ai.hasNewEvent     = false;
    }

    theme.drawDotGrid();
    drawLeftPanel(p);
    drawMiddlePanel(p);
    drawRightPanel(p);
  }

  // ════════════════════════════════════════════════════════
  // LEFT PANEL — vitals + relationships
  // ════════════════════════════════════════════════════════
  void drawLeftPanel(Player p) {
    float x = LP_X, y = PY, w = LP_W;
    theme.drawCard(x, y, w, PH);

    // Name + age
    fill(theme.TEXT); textSize(16); textAlign(LEFT, TOP);
    text(p.name, x + 14, y + 12);
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
    text("Age " + p.age(engine.state.currentYear) + "  ·  " + p.nationality, x + 14, y + 32);
    if (!p.hometown.isEmpty()) { fill(theme.TEXT_DIM); textSize(8); text(p.hometown, x + 14, y + 44); }

    // Phase badge
    LifePhase phase = engine.state.lifePhase;
    SportConfig sc  = engine.state.sportConfig != null ? engine.state.sportConfig : getSportConfig(Sport.TENNIS);
    fill(theme.ACCENT, 30); noStroke(); rect(x + 14, y + 56, w - 28, 18, 9);
    fill(theme.ACCENT); textSize(8); textAlign(CENTER, CENTER);
    if (phase == LifePhase.PRO)
      text(engine.state.currentSport + "  ·  " + sc.rankLabel + " #" + p.career.worldRanking, x + w/2, y + 65);
    else
      text(phaseLabel(phase) + "  ·  Age " + p.age(engine.state.currentYear), x + w/2, y + 65);

    y += 84;

    // ── Core life stats ───────────────────────────────────
    statBar("HEALTH",    p.wellbeing, theme.SUCCESS,   x + 14, y, w - 28); y += 34;
    statBar("HAPPINESS", p.happiness, theme.TEXT_GOLD, x + 14, y, w - 28); y += 34;
    statBar("SMARTS",    p.smarts,    theme.ACCENT2,   x + 14, y, w - 28); y += 34;
    statBar("LOOKS",     p.looks,     theme.PURPLE,    x + 14, y, w - 28); y += 34;

    stroke(theme.BORDER); strokeWeight(1);
    line(x + 14, y + 2, x + w - 14, y + 2); noStroke();
    y += 12;

    // ── PRO career stats ──────────────────────────────────
    if (phase == LifePhase.PRO) {
      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP);
      text("TITLES", x + 14, y); text("GS", x + 80, y); text("PRIZE", x + 116, y);
      y += 12;
      fill(theme.ACCENT); textSize(18); textAlign(LEFT, TOP); text("" + p.career.titlesWon, x + 14, y);
      fill(theme.TEXT);   textSize(18); text("" + p.career.grandSlamTitles, x + 80, y);
      fill(theme.SUCCESS); textSize(12); text(formatMoney(p.career.prizeMoney), x + 116, y);
      y += 28;

      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("SAVINGS", x + 14, y); y += 11;
      fill(p.finances.savings > 50000 ? theme.SUCCESS : theme.DANGER); textSize(14);
      text(formatMoney(p.finances.savings), x + 14, y); y += 24;

      // Mental health badge
      color mc = p.mentalHealth.stateColor();
      fill(mc, 35); rect(x + 14, y, w - 28, 20, 4);
      fill(mc); textSize(8); textAlign(CENTER, CENTER);
      text("MENTAL: " + p.mentalHealth.stateLabel().toUpperCase(), x + w/2, y + 10); y += 26;

      // Injury badge
      if (p.health.status != InjuryStatus.HEALTHY) {
        fill(theme.DANGER, 35); rect(x + 14, y, w - 28, 20, 4);
        fill(theme.DANGER); textSize(8); textAlign(CENTER, CENTER);
        text("INJURY: " + p.health.injuredPart + " (" + p.health.weeksRemaining + "wk)", x + w/2, y + 10); y += 26;
      }

      stroke(theme.BORDER); strokeWeight(1);
      line(x + 14, y + 2, x + w - 14, y + 2); noStroke();
      y += 10;

      // ── RELATIONSHIPS section ─────────────────────────
      fill(theme.FAMILY); textSize(9); textAlign(LEFT, TOP);
      text("RELATIONSHIPS", x + 14, y); y += 14;

      if (p.family.spouse != null) {
        // Partner card
        color relCol = p.family.status == RelationshipStatus.MARRIED ? theme.FAMILY :
                       (p.family.status == RelationshipStatus.ENGAGED ? theme.ACCENT : theme.ACCENT2);
        fill(relCol, 25); rect(x + 14, y, w - 28, 52, 5);
        fill(relCol); textSize(9); textAlign(LEFT, TOP);
        String statusLabel = p.family.status == RelationshipStatus.MARRIED ? "MARRIED" :
                             p.family.status == RelationshipStatus.ENGAGED  ? "ENGAGED" : "DATING";
        text(statusLabel, x + 18, y + 5);
        fill(theme.TEXT); textSize(11); text(p.family.partnerName(), x + 18, y + 18);
        // Happiness bar
        fill(theme.TEXT_DIM); textSize(8); text("HAPPINESS", x + 18, y + 34);
        theme.drawStatBar(x + 80, y + 30, w - 94, 8,
          p.family.familyHappiness, 100,
          p.family.familyHappiness > 60 ? theme.SUCCESS : (p.family.familyHappiness > 30 ? theme.ACCENT : theme.DANGER));
        // Resentment warning
        if (p.family.spouse.resentment > 50) {
          fill(theme.DANGER); textSize(7); textAlign(RIGHT, TOP);
          text("⚠ " + (int)p.family.spouse.resentment + "% resentful", x + w - 18, y + 5);
        }
        y += 58;

        // Kids
        if (!p.family.children.isEmpty()) {
          for (Child c : p.family.children) {
            fill(theme.ACCENT2, 20); rect(x + 14, y, w - 28, 20, 4);
            fill(theme.ACCENT2); textSize(8); textAlign(LEFT, CENTER);
            text("♦ " + c.name + "  (age " + c.age + ")", x + 18, y + 10);
            theme.drawStatBar(x + w - 60, y + 7, 42, 6, c.happiness, 100, theme.SUCCESS);
            y += 24;
          }
        }
      } else {
        fill(theme.TEXT_DIM, 120); rect(x + 14, y, w - 28, 28, 5);
        fill(theme.TEXT_DIM); textSize(9); textAlign(CENTER, CENTER);
        text("Single — use LOVE & FAM to date", x + w/2, y + 14);
        y += 32;
      }

      // ── Parents ───────────────────────────────────────
      if (p.parents != null) {
        stroke(theme.BORDER); strokeWeight(1);
        line(x + 14, y + 4, x + w - 14, y + 4); noStroke(); y += 10;
        fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("PARENTS", x + 14, y); y += 12;

        for (int pi = 0; pi < 2; pi++) {
          Parent pr = pi == 0 ? p.parents.mom : p.parents.dad;
          String lbl = pi == 0 ? "Mom" : "Dad";
          color rc = pr.relationshipColor();
          fill(rc, 20); rect(x + 14, y, w - 28, 18, 4);
          fill(rc); textSize(8); textAlign(LEFT, CENTER);
          text(lbl + ": " + pr.name, x + 18, y + 9);
          textAlign(RIGHT, CENTER);
          text(pr.relationshipLabel(), x + w - 18, y + 9);
          y += 22;
        }
      }

    } else {
      // ── EARLY LIFE stats ─────────────────────────────
      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("SAVINGS", x + 14, y); y += 11;
      fill(theme.SUCCESS); textSize(14); text(formatMoney(p.finances.savings), x + 14, y); y += 24;

      if (p.parents != null) {
        fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("PARENTS", x + 14, y); y += 12;
        for (int pi = 0; pi < 2; pi++) {
          Parent pr = pi == 0 ? p.parents.mom : p.parents.dad;
          String lbl = pi == 0 ? "Mom" : "Dad";
          color rc = pr.relationshipColor();
          fill(rc, 20); rect(x + 14, y, w - 28, 20, 4);
          fill(rc); textSize(8); textAlign(LEFT, CENTER);
          text(lbl + ": " + pr.name, x + 18, y + 10);
          textAlign(RIGHT, CENTER); text(pr.relationshipLabel(), x + w - 18, y + 10);
          y += 24;
        }
      }

      if (p.family.spouse != null) {
        y += 4;
        fill(theme.FAMILY, 25); rect(x + 14, y, w - 28, 24, 5);
        fill(theme.FAMILY); textSize(9); textAlign(CENTER, CENTER);
        text("♥  " + p.family.statusDisplay(), x + w/2, y + 12); y += 28;
      }
    }

    // ── Sport attributes (compact) ─────────────────────
    stroke(theme.BORDER); strokeWeight(1);
    line(x + 14, y + 4, x + w - 14, y + 4); noStroke(); y += 10;
    fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("SPORT ATTRIBUTES", x + 14, y); y += 12;

    PlayerAttributes eff = p.effectiveAttributes();
    String[] sn = sc.statNames;
    int[] sv = {eff.serve, eff.forehand, eff.backhand, eff.volley, eff.speed, eff.stamina, eff.mental};
    for (int i = 0; i < min(sn.length, 7); i++) {
      if (y > PY + PH - 18) break;
      fill(theme.TEXT_DIM); textSize(7); textAlign(LEFT, TOP); text(sn[i], x + 14, y);
      fill(theme.ACCENT2); textAlign(RIGHT, TOP); text("" + sv[i], x + w - 14, y);
      theme.drawStatBar(x + 14, y + 9, w - 28, 4, sv[i], 99, theme.ACCENT2);
      y += 20;
    }
  }

  String phaseLabel(LifePhase ph) {
    switch (ph) {
      case CHILDHOOD:   return "CHILDHOOD";
      case EARLY_SCHOOL: return "SCHOOL";
      case TEEN:        return "TEENAGER";
      case UNIVERSITY:  return "UNIVERSITY";
      default:          return "PRO";
    }
  }

  void statBar(String label, float val, color col, float x, float y, float w) {
    fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text(label, x, y);
    fill(col); textAlign(RIGHT, TOP); text((int)val + "%", x + w, y);
    theme.drawStatBar(x, y + 11, w, 10, val, 100, col);
  }

  // ════════════════════════════════════════════════════════
  // MIDDLE PANEL — life feed / active event card
  // ════════════════════════════════════════════════════════
  void drawMiddlePanel(Player p) {
    float x = MP_X, y = PY, w = MP_W;

    // AI loading bar at top
    boolean isLoading = engine.ai.isLoading;
    if (isLoading) {
      theme.drawCard(x, y, w, 46, true);
      fill(theme.ACCENT); textSize(11); textAlign(CENTER, CENTER);
      text(engine.ai.getLoadingText(), x + w / 2, y + 23);
      y += 52;
    }

    eventPanelY = y;  // save so onClick always uses the same origin

    if (engine.state.pendingEvent != null) {
      // Draw label above the event card
      fill(theme.ACCENT, 180); textSize(9); textAlign(LEFT, TOP);
      text("▼ CHOOSE YOUR RESPONSE", x + 14, y);
      y += 14;
      eventPanelY = y;
      drawEventPanel(engine.state.pendingEvent, x, y, w);
    } else {
      float cardH = PH - (isLoading ? 52 : 0);
      theme.drawCard(x, y, w, cardH);

      // Last event banner
      if (!engine.state.lastEvent.isEmpty()) {
        fill(theme.ACCENT, 22); noStroke(); rect(x + 10, y + 10, w - 20, 30, 4);
        fill(theme.ACCENT); textSize(10); textAlign(LEFT, CENTER);
        text("» " + engine.state.lastEvent, x + 20, y + 25);
        y += 44;
      } else { y += 10; }

      // Life feed header
      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("LIFE FEED", x + 14, y + 6);
      stroke(theme.BORDER); strokeWeight(1);
      line(x + 14, y + 18, x + w - 14, y + 18); noStroke();
      y += 24;

      ArrayList<String> feed = p.lifeEvents;
      if (feed.isEmpty()) {
        fill(theme.TEXT_DIM); textSize(14); textAlign(CENTER, CENTER);
        text("Your story begins here.", x + w/2, y + 90);
        fill(theme.TEXT_DIM); textSize(10); textAlign(CENTER, CENTER);
        text("Press SPACE or click AGE UP  ·  use activities on the right", x + w/2, y + 114);
      } else {
        float fy = y;
        for (int i = 0; i < feed.size(); i++) {
          if (fy > PY + PH - 22) break;
          String entry = feed.get(i);
          color dot = feedColor(entry);
          if (i == 0) { fill(theme.PANEL_2); rect(x + 8, fy - 2, w - 16, 22, 3); }
          fill(dot); noStroke(); ellipse(x + 20, fy + 9, 7, 7);
          fill(i == 0 ? theme.TEXT : theme.TEXT_DIM);
          textSize(i == 0 ? 10 : 9); textAlign(LEFT, TOP);
          text(entry, x + 32, fy);
          fy += (i == 0 ? 24 : 19);
        }
      }

      // Recent results strip
      if (engine.state.lifePhase == LifePhase.PRO && !p.career.recentResults.isEmpty()) {
        float ry = PY + PH - 140;
        stroke(theme.BORDER); line(x + 14, ry, x + w - 14, ry); noStroke();
        ry += 8;
        fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("RECENT RESULTS", x + 14, ry); ry += 14;
        int shown = min(3, p.career.recentResults.size());
        for (int i = 0; i < shown; i++) {
          MatchResult r = p.career.recentResults.get(i);
          fill(r.playerWon ? color(15, 45, 25) : color(45, 15, 15)); rect(x + 8, ry, w - 16, 26, 3);
          fill(r.playerWon ? theme.SUCCESS : theme.DANGER); textSize(9); textAlign(LEFT, CENTER);
          text(r.playerWon ? "W" : "L", x + 18, ry + 13);
          fill(theme.TEXT); textAlign(LEFT, CENTER); text(r.tournamentName + " " + r.round, x + 34, ry + 13);
          fill(theme.TEXT_DIM); textAlign(RIGHT, CENTER); text(r.score + " +" + r.rankingPointsAwarded + "pts", x + w - 14, ry + 13);
          ry += 30;
        }
      }
    }
  }

  color feedColor(String e) {
    String s = e.toLowerCase();
    if (s.contains("won") || s.contains("title") || s.contains("champion") || s.contains("graduated")) return theme.ACCENT;
    if (s.contains("born") || s.contains("married") || s.contains("partner") || s.contains("dating") || s.contains("engaged") || s.contains("child") || s.contains("love")) return theme.FAMILY;
    if (s.contains("injur") || s.contains("hospital") || s.contains("legal") || s.contains("charged")) return theme.DANGER;
    if (s.contains("$") || s.contains("deal") || s.contains("invest") || s.contains("pts") || s.contains("money")) return theme.SUCCESS;
    if (s.contains("mom") || s.contains("dad") || s.contains("parent") || s.contains("family")) return theme.FAMILY;
    return theme.ACCENT2;
  }

  // ════════════════════════════════════════════════════════
  // RIGHT PANEL — activity grid + Age Up
  // ════════════════════════════════════════════════════════
  void drawRightPanel(Player p) {
    float x = RP_X, w = RP_W, y = PY;
    theme.drawCard(x, y, w, PH);

    // AGE UP button at bottom
    float btnY = PY + PH - 62;
    hoverAgeUp = theme.isHover(hoverX, hoverY, x + 10, btnY, w - 20, 50);
    fill(hoverAgeUp ? theme.ACCENT : color(30, 50, 20));
    stroke(theme.ACCENT); strokeWeight(hoverAgeUp ? 2 : 1);
    rect(x + 10, btnY, w - 20, 50, 6); noStroke();
    fill(hoverAgeUp ? color(10, 12, 22) : theme.ACCENT);
    textSize(13); textAlign(CENTER, CENTER);
    String btnLabel = engine.state.lifePhase == LifePhase.PRO ? "▶  AGE UP  [SPACE]" : "▶  NEXT YEAR  [SPACE]";
    text(btnLabel, x + w/2, btnY + 25);
    fill(theme.TEXT_DIM); textSize(8); textAlign(CENTER, TOP);
    text(engine.state.currentYear + "  ·  Age " + p.age(engine.state.currentYear), x + w/2, btnY + 54);

    // Activities header
    fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP);
    text("ACTIVITIES", x + 14, y + 12);
    float ay = y + 28;

    if (selectedCategory == null) drawActivityGrid(x + 10, ay, w - 20);
    else                          drawSubMenu(x + 10, ay, w - 20);

    // Overview mini-stats
    float sumY = y + 28 + 248;
    fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("OVERVIEW", x + 14, sumY); sumY += 12;
    fill(theme.PANEL_2); rect(x + 10, sumY, w - 20, 92, 4); sumY += 8;

    if (engine.state.lifePhase == LifePhase.PRO) {
      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("RANKING PTS", x + 18, sumY);
      fill(theme.ACCENT); textSize(15); text("" + p.career.rankingPoints, x + 18, sumY + 12);
      fill(theme.TEXT_DIM); textSize(8); text("FATIGUE", x + 18, sumY + 36);
      theme.drawStatBar(x + 18, sumY + 48, w - 36, 8, p.form.fatigue, 100,
        p.form.fatigue > 70 ? theme.DANGER : theme.ACCENT2);
      fill(theme.TEXT_DIM); textSize(8); text("CONFIDENCE", x + 140, sumY + 36);
      theme.drawStatBar(x + 140, sumY + 48, w - 158, 8, p.form.confidence, 100, theme.SUCCESS);
    } else {
      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text("SMARTS", x + 18, sumY);
      theme.drawStatBar(x + 18, sumY + 12, w - 36, 8, p.smarts, 100, theme.ACCENT2);
      fill(theme.TEXT_DIM); textSize(8); text("SAVINGS", x + 18, sumY + 36);
      fill(p.finances.savings > 500 ? theme.SUCCESS : theme.TEXT_DIM); textSize(12); textAlign(LEFT, TOP);
      text(formatMoney(p.finances.savings), x + 18, sumY + 48);
      if (p.parents != null) {
        fill(theme.TEXT_DIM); textSize(7); textAlign(RIGHT, TOP);
        text("Mom " + (int)p.parents.mom.relationship + " / Dad " + (int)p.parents.dad.relationship, x + w - 18, sumY + 60);
      }
    }
  }

  void drawActivityGrid(float x, float y, float w) {
    String[] labels = getCatLabels();
    color[] cols = {theme.ACCENT, theme.ACCENT2, theme.FAMILY, theme.SUCCESS, theme.TEXT_GOLD, theme.PURPLE};
    String[] icons = {"★", "⚡", "♥", "✚", "$", "✦"};
    float bw = (w - 8) / 2f, bh = 70;
    for (int i = 0; i < 6; i++) {
      int col = i % 2, row = i / 2;
      float bx = x + col * (bw + 8);
      float by = y + row * (bh + 6);
      boolean hov = theme.isHover(hoverX, hoverY, bx, by, bw, bh);
      fill(hov ? cols[i] : theme.PANEL_2);
      stroke(cols[i]); strokeWeight(hov ? 2 : 1);
      rect(bx, by, bw, bh, 6); noStroke();
      fill(hov ? color(10, 12, 22) : cols[i]);
      textSize(16); textAlign(CENTER, CENTER); text(icons[i], bx + bw/2, by + bh/2 - 12);
      textSize(9); text(labels[i], bx + bw/2, by + bh/2 + 8);
    }
  }

  void drawSubMenu(float x, float y, float w) {
    int ci = catIndex();
    String[][] subs = getSubLabels();
    color col  = catColor(ci);
    String[] labels = subs[ci];

    boolean hBack = theme.isHover(hoverX, hoverY, x, y, 80, 26);
    fill(hBack ? theme.PANEL_2 : theme.PANEL);
    stroke(theme.BORDER); strokeWeight(1); rect(x, y, 80, 26, 4); noStroke();
    fill(theme.TEXT_DIM); textSize(9); textAlign(CENTER, CENTER); text("← BACK", x + 40, y + 13);
    fill(col); textSize(11); textAlign(LEFT, CENTER); text(getCatLabels()[ci], x + 90, y + 13);
    y += 32;

    for (int i = 0; i < labels.length; i++) {
      boolean hov = theme.isHover(hoverX, hoverY, x, y, w, 50);
      fill(hov ? col : theme.PANEL_2); stroke(hov ? col : theme.BORDER); strokeWeight(hov ? 2 : 1);
      rect(x, y, w, 50, 5); noStroke();
      fill(hov ? color(10, 12, 22) : theme.TEXT); textSize(12); textAlign(LEFT, CENTER);
      text(labels[i], x + 14, y + 25); y += 56;
    }
  }

  int catIndex() {
    ActivityCategory[] cats = ActivityCategory.values();
    for (int i = 0; i < cats.length; i++) if (cats[i] == selectedCategory) return i;
    return 0;
  }

  color catColor(int i) {
    color[] cols = {theme.ACCENT, theme.ACCENT2, theme.FAMILY, theme.SUCCESS, theme.TEXT_GOLD, theme.PURPLE};
    return cols[constrain(i, 0, cols.length - 1)];
  }

  // ════════════════════════════════════════════════════════
  // INPUT — choice detection uses saved eventPanelY
  // ════════════════════════════════════════════════════════
  void onClick(int mx, int my) {
    // Age Up button
    float btnY = PY + PH - 62;
    if (theme.isHover(mx, my, RP_X + 10, btnY, RP_W - 20, 50)) {
      engine.ageUp(); return;
    }

    // Event choices — computed from eventPanelY so render and click always agree
    if (engine.state.pendingEvent != null && engine.state.pendingEvent.choices != null) {
      ArrayList<EventChoice> choices = engine.state.pendingEvent.choices;
      // choices start at eventPanelY + 14 (label) + 110 inside drawEventPanel
      float cy = eventPanelY + 14 + 110;
      for (int i = 0; i < choices.size(); i++) {
        if (theme.isHover(mx, my, MP_X + 12, cy, MP_W - 24, 60)) {
          engine.applyChoice(choices.get(i)); return;
        }
        cy += 68;
      }
      return; // swallow all other clicks while event is pending
    }

    // Activity grid / submenu
    float ax = RP_X + 10, aw = RP_W - 20, ay = PY + 28;
    if (selectedCategory == null) {
      float bw = (aw - 8) / 2f, bh = 70;
      ActivityCategory[] cats = ActivityCategory.values();
      for (int i = 0; i < 6; i++) {
        float bx = ax + (i % 2) * (bw + 8);
        float by = ay + (i / 2) * (bh + 6);
        if (theme.isHover(mx, my, bx, by, bw, bh)) { selectedCategory = cats[i]; return; }
      }
    } else {
      if (theme.isHover(mx, my, ax, ay, 80, 26)) { selectedCategory = null; return; }
      ay += 32;
      String[][] subs = getSubLabels();
      int ci = catIndex();
      for (int i = 0; i < subs[ci].length; i++) {
        if (theme.isHover(mx, my, ax, ay, aw, 50)) {
          dispatchActivity(ci, i); selectedCategory = null; return;
        }
        ay += 56;
      }
    }
  }

  void dispatchActivity(int catIdx, int optIdx) {
    String[][] keys = getActionKeys();
    String k = keys[catIdx][optIdx];
    LifePhase phase = engine.state.lifePhase;
    if (phase == LifePhase.PRO) {
      switch (catIdx) {
        case 0: engine.handleCareerAction(k);       break;
        case 1: engine.handleTrainingAction(k);     break;
        case 2: engine.handleRelationshipAction(k); break;
        case 3: engine.handleHealthAction(k);       break;
        case 4: engine.handleAssetAction(k);        break;
        case 5: engine.handleSocialAction(k);       break;
      }
    } else {
      engine.handleEarlyLifeActivity(k);
    }
  }

  void onHover(int mx, int my) { super.onHover(mx, my); }
}
