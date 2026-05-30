// ============================================================
// LIFESTYLE SCREEN - Life simulation view
// ============================================================
class LifestyleScreen extends BaseScreen {
  LifeTab activeTab = LifeTab.OVERVIEW;

  LifestyleScreen(GameEngine e) { super(e); }

  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    float x = 30, y = 70;
    fill(theme.ACCENT); textSize(22); textAlign(LEFT, TOP);
    text("LIFESTYLE", x, y);

    y += 36;
    LifeTab[] tabs    = LifeTab.values();
    String[]  tabLabels = {"OVERVIEW", "MENTAL HEALTH", "RELATIONSHIPS", "SOCIAL MEDIA", "LEGAL"};
    float tw = 140;
    for (int i = 0; i < tabs.length; i++) {
      boolean sel = tabs[i] == activeTab;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(x + i * (tw + 4), y, tw, 32, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(10); textAlign(CENTER, CENTER);
      text(tabLabels[i], x + i * (tw + 4) + tw / 2, y + 16);
    }
    y += 48;

    switch (activeTab) {
      case OVERVIEW:      drawOverview(p, x, y);      break;
      case MENTAL_HEALTH: drawMentalHealth(p, x, y);  break;
      case RELATIONSHIPS: drawRelationships(p, x, y); break;
      case SOCIAL_MEDIA:  drawSocialMedia(p, x, y);   break;
      case LEGAL:         drawLegal(p, x, y);          break;
    }
  }

  void drawOverview(Player p, float x, float y) {
    theme.drawCard(x, y, 560, 160);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("SAVINGS", x + 20, y + 14);
    fill(theme.ACCENT); textSize(24); textAlign(LEFT, TOP);
    text(formatMoney(p.finances.savings), x + 20, y + 30);

    fill(theme.TEXT_DIM); textSize(10);
    text("NET WORTH", x + 200, y + 14);
    fill(theme.TEXT); textSize(20);
    text(formatMoney(p.finances.netWorth()), x + 200, y + 30);

    fill(theme.TEXT_DIM); textSize(10);
    text("WEEKLY EXPENSES", x + 400, y + 14);
    fill(theme.DANGER); textSize(16);
    text(formatMoney(p.finances.weeklyExpenses) + "/wk", x + 400, y + 30);

    fill(theme.TEXT_DIM); textSize(10);
    text("LIFESTYLE", x + 20, y + 80);
    fill(theme.TEXT); textSize(14);
    text(p.finances.lifestyleLabel().toUpperCase(), x + 20, y + 96);

    fill(theme.TEXT_DIM); textSize(10);
    text("AGENT", x + 200, y + 80);
    fill(theme.TEXT); textSize(14);
    text(p.agent.tierLabel(), x + 200, y + 96);

    fill(theme.TEXT_DIM); textSize(10);
    text("ADDICTION", x + 400, y + 80);
    color addCol = p.addiction == AddictionLevel.NONE ? theme.SUCCESS : theme.DANGER;
    fill(addCol); textSize(14);
    text(p.addiction.toString(), x + 400, y + 96);

    y += 176;

    if (!p.finances.properties.isEmpty()) {
      sectionHeader("PROPERTIES", x, y); y += 20;
      for (OwnedProperty prop : p.finances.properties) {
        theme.drawCard(x, y, 560, 56);
        fill(theme.TEXT); textSize(13); textAlign(LEFT, TOP);
        text(prop.name, x + 16, y + 10);
        fill(theme.TEXT_DIM); textSize(11);
        text(prop.typeName() + "  -  Value: " + formatMoney(prop.currentValue), x + 16, y + 32);
        fill(theme.DANGER); textAlign(RIGHT, TOP);
        text("-" + formatMoney(prop.weeklyTotal()) + "/wk", x + 544, y + 32);
        y += 62;
      }
    }

    if (!p.finances.investments.isEmpty()) {
      sectionHeader("INVESTMENTS", x, y); y += 20;
      for (OwnedInvestment inv : p.finances.investments) {
        theme.drawCard(x, y, 560, 56);
        fill(theme.TEXT); textSize(13); textAlign(LEFT, TOP);
        text(inv.name, x + 16, y + 10);
        fill(theme.TEXT_DIM); textSize(11);
        text(inv.typeName() + "  -  Invested: " + formatMoney(inv.invested), x + 16, y + 32);
        color gainCol = inv.unrealizedGain() >= 0 ? theme.SUCCESS : theme.DANGER;
        fill(gainCol); textAlign(RIGHT, TOP);
        text(formatMoney(inv.unrealizedGain()) + " (" + (int)inv.gainPct() + "%)", x + 544, y + 32);
        y += 62;
      }
    }

    if (!p.finances.businesses.isEmpty()) {
      sectionHeader("BUSINESSES", x, y); y += 20;
      for (OwnedBusiness biz : p.finances.businesses) {
        theme.drawCard(x, y, 560, 56);
        fill(theme.TEXT); textSize(13); textAlign(LEFT, TOP);
        text(biz.name, x + 16, y + 10);
        fill(theme.TEXT_DIM); textSize(11);
        text("Value: " + formatMoney(biz.currentValue), x + 16, y + 32);
        color profCol = biz.weeklyProfit() >= 0 ? theme.SUCCESS : theme.DANGER;
        fill(profCol); textAlign(RIGHT, TOP);
        text(formatMoney(biz.weeklyProfit()) + "/wk", x + 544, y + 32);
        y += 62;
      }
    }
  }

  void drawMentalHealth(Player p, float x, float y) {
    MentalHealthSystem mh = p.mentalHealth;
    theme.drawCard(x, y, 560, 200);

    color stateCol = mh.stateColor();
    fill(stateCol, 40);
    rect(x + 16, y + 16, 180, 44, 6);
    fill(stateCol); textSize(18); textAlign(CENTER, CENTER);
    text(mh.stateLabel().toUpperCase(), x + 106, y + 38);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("STRESS LEVEL", x + 220, y + 20);
    theme.drawStatBar(x + 220, y + 34, 320, 10, mh.stressLevel, 100, theme.DANGER);

    fill(theme.TEXT_DIM); textSize(10);
    text("HAPPINESS", x + 220, y + 60);
    theme.drawStatBar(x + 220, y + 74, 320, 10, mh.happiness, 100, theme.SUCCESS);

    fill(theme.TEXT_DIM); textSize(10);
    text("PERFORMANCE MODIFIER", x + 220, y + 100);
    float mod = mh.performanceModifier();
    fill(mod >= 1.0 ? theme.SUCCESS : theme.DANGER); textSize(18); textAlign(LEFT, TOP);
    text(nf(mod, 1, 2) + "x", x + 220, y + 116);

    if (mh.inTherapy) {
      fill(theme.ACCENT2, 40);
      rect(x + 16, y + 152, 528, 28, 4);
      fill(theme.ACCENT2); textSize(12); textAlign(CENTER, CENTER);
      text("In therapy — " + mh.therapyWeeks + " weeks remaining", x + 280, y + 166);
    }
  }

  void drawRelationships(Player p, float x, float y) {
    Family fam = p.family;

    // Status header card
    color statCol = fam.spouse != null ?
      (fam.status == RelationshipStatus.MARRIED ? theme.FAMILY :
       fam.status == RelationshipStatus.ENGAGED  ? theme.ACCENT : theme.ACCENT2)
      : theme.TEXT_DIM;

    fill(statCol, 20); stroke(statCol); strokeWeight(1);
    rect(x, y, 560, 40, 6); noStroke();
    fill(statCol); textSize(16); textAlign(LEFT, CENTER);
    text(fam.statusDisplay(), x + 20, y + 20);
    if (fam.spouse != null) {
      fill(statCol, 160); textSize(10); textAlign(RIGHT, CENTER);
      text("Together " + (p.weeksInRelationship / 52) + " yrs", x + 540, y + 20);
    }
    y += 50;

    if (fam.spouse != null) {
      theme.drawCard(x, y, 560, 130);

      fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
      text("PARTNER", x + 20, y + 12);
      fill(theme.TEXT); textSize(20); textAlign(LEFT, TOP);
      text(fam.partnerName(), x + 20, y + 26);

      fill(theme.TEXT_DIM); textSize(9);
      text("FAMILY HAPPINESS", x + 20, y + 58);
      theme.drawStatBar(x + 20, y + 70, 240, 12, fam.familyHappiness, 100,
        fam.familyHappiness > 60 ? theme.SUCCESS : (fam.familyHappiness > 30 ? theme.ACCENT : theme.DANGER));
      fill(fam.familyHappiness > 60 ? theme.SUCCESS : (fam.familyHappiness > 30 ? theme.ACCENT : theme.DANGER));
      textSize(14); textAlign(LEFT, TOP);
      text((int)fam.familyHappiness + "%", x + 268, y + 64);

      fill(theme.TEXT_DIM); textSize(9);
      text("PARTNER RESENTMENT", x + 300, y + 58);
      theme.drawStatBar(x + 300, y + 70, 240, 12, fam.spouse.resentment, 100, theme.DANGER);
      fill(fam.spouse.resentment > 60 ? theme.DANGER : theme.TEXT_DIM);
      textSize(14); textAlign(LEFT, TOP);
      text((int)fam.spouse.resentment + "%", x + 548, y + 64);

      // Resentment advice
      if (fam.spouse.resentment > 60) {
        fill(theme.DANGER, 25); rect(x + 20, y + 100, 520, 22, 4);
        fill(theme.DANGER); textSize(9); textAlign(CENTER, CENTER);
        text("High resentment! Spend quality time together — use LOVE & FAM activities.", x + 280, y + 111);
      }
      y += 140;
    } else {
      theme.drawCard(x, y, 560, 60);
      fill(theme.TEXT_DIM); textSize(12); textAlign(CENTER, CENTER);
      text("You are single.  Use  LOVE & FAM → Go on a Date  to meet someone.", x + 280, y + 30);
      y += 70;
    }

    if (!fam.children.isEmpty()) {
      sectionHeader("CHILDREN", x, y); y += 20;
      for (Child c : fam.children) {
        theme.drawCard(x, y, 560, 52);
        fill(theme.ACCENT2); textSize(14); textAlign(LEFT, CENTER);
        text(c.name, x + 16, y + 18);
        fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
        text("Age " + c.age, x + 16, y + 36);
        fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
        text("HAPPINESS", x + 200, y + 12);
        theme.drawStatBar(x + 200, y + 26, 340, 10, c.happiness, 100,
          c.happiness > 60 ? theme.SUCCESS : (c.happiness > 30 ? theme.ACCENT : theme.DANGER));
        y += 58;
      }
    }

    // Relationship advice tips
    y += 10;
    sectionHeader("RELATIONSHIP TIPS", x, y); y += 18;
    String[][] tips = {
      {"Go on a Date", "Boosts happiness and family bond"},
      {"Spend Time Together", "Reduces partner resentment"},
      {"Propose", "Available when Dating — costs $15K for ring"},
      {"Have a Child", "Available when Married — huge life change"}
    };
    for (String[] tip : tips) {
      fill(theme.PANEL_2); rect(x, y, 560, 32, 4);
      fill(theme.ACCENT2); textSize(10); textAlign(LEFT, CENTER); text(tip[0], x + 14, y + 10);
      fill(theme.TEXT_DIM); textSize(9); text(tip[1], x + 14, y + 22);
      y += 36;
    }
  }

  void drawSocialMedia(Player p, float x, float y) {
    SocialMediaProfile sm = p.socialMedia;
    theme.drawCard(x, y, 560, 160);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("FOLLOWERS", x + 20, y + 14);
    fill(theme.ACCENT); textSize(28); textAlign(LEFT, TOP);
    text(sm.followersLabel(), x + 20, y + 28);
    fill(theme.TEXT_DIM); textSize(10);
    text(sm.tierLabel().toUpperCase(), x + 20, y + 64);

    fill(theme.TEXT_DIM); textSize(10);
    text("ENGAGEMENT RATE", x + 220, y + 14);
    fill(theme.TEXT); textSize(18);
    text(nf(sm.engagementRate * 100, 0, 1) + "%", x + 220, y + 28);

    fill(theme.TEXT_DIM); textSize(10);
    text("WEEKLY BRAND VALUE", x + 380, y + 14);
    fill(theme.SUCCESS); textSize(18);
    text(formatMoney(sm.weeklyBrandValue()), x + 380, y + 28);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("CONTROVERSY SCORE", x + 220, y + 70);
    theme.drawStatBar(x + 220, y + 86, 320, 10, sm.controversyScore, 100, theme.DANGER);

    if (sm.hasPRManager) {
      fill(theme.SUCCESS, 40);
      rect(x + 16, y + 120, 528, 24, 4);
      fill(theme.SUCCESS); textSize(11); textAlign(CENTER, CENTER);
      text("PR Manager active — boosting growth & brand value", x + 280, y + 132);
    }
  }

  void drawLegal(Player p, float x, float y) {
    theme.drawCard(x, y, 560, 120);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("LEGAL STATUS", x + 20, y + 14);
    color legalCol = p.legalStatus == LegalStatus.CLEAN ? theme.SUCCESS : theme.DANGER;
    fill(legalCol); textSize(18);
    text(p.legalStatus.toString().replace("_", " "), x + 20, y + 30);

    fill(theme.TEXT_DIM); textSize(10);
    text("ADDICTION", x + 320, y + 14);
    color addCol = p.addiction == AddictionLevel.NONE ? theme.SUCCESS : theme.DANGER;
    fill(addCol); textSize(18);
    text(p.addiction.toString(), x + 320, y + 30);

    if (!p.activeLegalCase.isEmpty()) {
      fill(theme.DANGER, 30);
      rect(x + 16, y + 72, 528, 32, 4);
      fill(theme.DANGER); textSize(12); textAlign(LEFT, CENTER);
      text("Active case: " + p.activeLegalCase + "  (" + p.legalWeeksRemaining + " wks remaining)", x + 28, y + 88);
      if (p.legalFineAmount > 0) {
        textAlign(RIGHT, CENTER);
        text("Fine: " + formatMoney(p.legalFineAmount), x + 536, y + 88);
      }
    }

    if (!p.awards.isEmpty()) {
      y += 136;
      sectionHeader("AWARDS & HONOURS", x, y); y += 20;
      for (Award aw : p.awards) {
        fill(theme.TEXT_GOLD); textSize(12); textAlign(LEFT, TOP);
        text("* " + aw.label(), x, y);
        y += 20;
      }
    }
  }

  void onClick(int mx, int my) {
    float x = 30, ty = 106;
    LifeTab[] tabs = LifeTab.values();
    float tw = 140;
    for (int i = 0; i < tabs.length; i++) {
      if (theme.isHover(mx, my, x + i * (tw + 4), ty, tw, 32)) {
        activeTab = tabs[i];
        return;
      }
    }
  }
}
