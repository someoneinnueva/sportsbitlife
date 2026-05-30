// ============================================================
// GAME ENGINE  —  age-based loop, activity system, state
// ============================================================
class GameEngine {
  GameState           state;
  AIScenarioEngine    ai;
  MatchEngine         matchEngine;
  TournamentCalendar  calendar;
  RankingSystem       ranking;

  ScreenID        currentScreen = ScreenID.MAIN_MENU;
  BaseScreen      activeScreen;

  MainMenuScreen   menuScreen;
  LifeScreen       lifeScreen;
  MatchScreen      matchScreen;
  TrainingScreen   trainingScreen;
  WorldRankScreen  worldScreen;
  LifestyleScreen  lifestyleScreen;
  LegacyScreen     legacyScreen;

  GameEngine() {}

  void start() {
    ai          = new AIScenarioEngine(CLAUDE_API_KEY, CLAUDE_MODEL);
    matchEngine = new MatchEngine();
    calendar    = new TournamentCalendar(Sport.TENNIS);
    ranking     = new RankingSystem();
    state       = new GameState();
    state.sportConfig = getSportConfig(Sport.TENNIS);

    menuScreen      = new MainMenuScreen(this);
    lifeScreen      = new LifeScreen(this);
    matchScreen     = new MatchScreen(this);
    trainingScreen  = new TrainingScreen(this);
    worldScreen     = new WorldRankScreen(this);
    lifestyleScreen = new LifestyleScreen(this);
    legacyScreen    = new LegacyScreen(this);

    activeScreen = menuScreen;
  }

  void render() {
    activeScreen.render();
    if (currentScreen != ScreenID.MAIN_MENU && state.player != null) {
      drawStatusBar();
    }
  }

  void drawStatusBar() {
    // Background with subtle gradient feel
    fill(10, 14, 28); noStroke(); rect(0, 0, width, 54);
    fill(theme.ACCENT, 60); rect(0, 52, width, 2);
    noStroke();

    // Sport icon + player name
    fill(theme.ACCENT); textSize(13); textAlign(LEFT, CENTER);
    text(sportLabel(state.currentSport) + "  " + state.player.name.toUpperCase(), 16, 27);

    // Stats strip
    SportConfig sc = state.sportConfig != null ? state.sportConfig : getSportConfig(Sport.TENNIS);
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
    text(sc.rankLabel + " #" + state.player.career.worldRanking
       + "   " + sc.ptsLabel + " " + state.player.career.rankingPoints
       + "   Age " + state.player.age(state.currentYear)
       + "   " + state.currentYear
       + "   " + formatMoney(state.player.finances.savings),
       220, 27);

    // Partner heart
    if (state.player.family.spouse != null) {
      float fh = state.player.family.familyHappiness;
      fill(theme.FAMILY);
      textSize(10); textAlign(LEFT, CENTER);
      text("♥ " + state.player.family.partnerName()
         + (state.player.family.status == RelationshipStatus.MARRIED ? " (married)" : ""),
         width - 700, 27);
    }

    // Navigation tabs
    String[]   navLabels  = {"LIFE", "TRAINING", "WORLD", "LIFESTYLE", "LEGACY"};
    ScreenID[] navScreens = {ScreenID.CAREER, ScreenID.TRAINING, ScreenID.WORLD_RANKINGS, ScreenID.LIFESTYLE, ScreenID.LEGACY};
    for (int i = 0; i < navLabels.length; i++) {
      float nx = width - 570 + i * 114;
      boolean active = (currentScreen == navScreens[i]);
      fill(active ? theme.ACCENT : color(20, 28, 50));
      stroke(active ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(nx, 9, 108, 34, 5); noStroke();
      fill(active ? color(10, 12, 22) : theme.TEXT_DIM);
      textSize(10); textAlign(CENTER, CENTER);
      text(navLabels[i], nx + 54, 26);
    }
  }

  String sportLabel(Sport s) {
    switch (s) {
      case BASKETBALL: return "NBA";
      case SOCCER:     return "FOOTBALL";
      case GOLF:       return "PGA";
      case BOXING:     return "BOXING";
      default:         return "TENNIS";
    }
  }

  String formatMoney(float m) {
    if (m < 0) return "-$" + formatMoneyAbs(-m);
    return "$" + formatMoneyAbs(m);
  }
  String formatMoneyAbs(float m) {
    if (m >= 1_000_000) return nf(m / 1_000_000, 0, 1) + "M";
    if (m >= 1_000)     return (int)(m / 1_000) + "K";
    return "" + (int)m;
  }

  void switchTo(ScreenID id) {
    currentScreen = id;
    switch (id) {
      case MAIN_MENU:      activeScreen = menuScreen;      break;
      case CAREER:         activeScreen = lifeScreen;      break;
      case MATCH:          activeScreen = matchScreen;     break;
      case TRAINING:       activeScreen = trainingScreen;  break;
      case WORLD_RANKINGS: activeScreen = worldScreen;     break;
      case LIFESTYLE:      activeScreen = lifestyleScreen; break;
      case LEGACY:         activeScreen = legacyScreen;    break;
      default:             activeScreen = lifeScreen;      break;
    }
    activeScreen.onEnter();
  }

  // AGE UP — core BitLife-style year progression
  void ageUp() {
    if (state.player == null || ai.isLoading) return;

    state.currentYear++;
    Player p = state.player;
    int    age = p.age(state.currentYear);

    LifePhase prevPhase = state.lifePhase;
    updateLifePhase(p, age);

    if (prevPhase != LifePhase.PRO && state.lifePhase == LifePhase.PRO && state.proCareerStartYear < 0) {
      state.proCareerStartYear = state.currentYear;
      p.addLifeEvent("Turned professional — the journey begins.", state.currentYear);
    }

    p.progression.applyAgingDecay(state.currentYear);
    syncBitLifeStats(p);
    p.weeksInRelationship += 52;

    if (state.lifePhase == LifePhase.PRO) {
      simulateSeason();
      applyAnnualFinances();
      p.family.advanceYear(30);
      p.socialMedia.updateWeek(p.career.worldRanking, false);
      if (age >= 38) state.retirementEligible = true;
      updateRanking();
    } else {
      simulateEarlyLifeYear(p, age);
    }

    if (p.age(state.currentYear) >= 13) {
      ai.generateWeeklyEvent(p, state.currentYear, 0, state.currentSport);
    }
  }

  void updateLifePhase(Player p, int age) {
    if (age < 6) {
      state.lifePhase = LifePhase.CHILDHOOD;
    } else if (age < 13) {
      state.lifePhase = LifePhase.EARLY_SCHOOL;
    } else if (age < 18) {
      state.lifePhase = LifePhase.TEEN;
    } else if (state.inUniversity && age < 22) {
      state.lifePhase = LifePhase.UNIVERSITY;
    } else {
      state.lifePhase = LifePhase.PRO;
    }
  }

  void simulateEarlyLifeYear(Player p, int age) {
    switch (state.lifePhase) {
      case CHILDHOOD:
        p.smarts    = constrain(p.smarts    + random(-1, 4), 0, 100);
        p.happiness = constrain(p.happiness + random(-3, 7), 0, 100);
        p.wellbeing = constrain(p.wellbeing + random(-2, 5), 0, 100);
        if (p.parents != null) {
          float support = (p.parents.mom.relationship + p.parents.dad.relationship) / 2;
          p.happiness = constrain(p.happiness + map(support, 0, 100, -3, 5), 0, 100);
        }
        if (state.lastEvent.isEmpty()) {
          String[] evts = {
            "Grew another inch — childhood flying by.",
            "School awards ceremony — a small trophy, a big smile.",
            "Summer holidays with family.",
            "First sports practice at the local club."
          };
          state.lastEvent = evts[(int)random(evts.length)];
          p.addLifeEvent(state.lastEvent, state.currentYear);
        }
        break;

      case EARLY_SCHOOL:
        p.smarts    = constrain(p.smarts    + random(0, 3), 0, 100);
        p.baseAttributes.speed   = min(70, p.baseAttributes.speed   + (int)random(0, 2));
        p.baseAttributes.stamina = min(70, p.baseAttributes.stamina + (int)random(0, 1));
        if (state.lastEvent.isEmpty()) {
          String[] evts = {
            "Joined the school " + state.currentSport.toString().toLowerCase() + " team.",
            "Strong school year — grades and sport improving.",
            "Local coach noticed your natural talent.",
            "Youth tournament semi-final — big moment."
          };
          state.lastEvent = evts[(int)random(evts.length)];
          p.addLifeEvent(state.lastEvent, state.currentYear);
        }
        break;

      case TEEN:
        p.smarts    = constrain(p.smarts    + random(-1, 3), 0, 100);
        p.looks     = constrain(p.looks     + random(-1, 2), 0, 100);
        p.baseAttributes.serve    = min(80, p.baseAttributes.serve    + (int)random(0, 2));
        p.baseAttributes.forehand = min(80, p.baseAttributes.forehand + (int)random(0, 2));
        p.baseAttributes.speed    = min(80, p.baseAttributes.speed    + (int)random(0, 1));
        if (age == 17 && state.pendingMatchAction.isEmpty() && state.pendingEvent == null && !state.inUniversity && state.proCareerStartYear < 0) {
          state.pendingEvent = buildUniversityDecisionEvent(p);
        }
        if (state.lastEvent.isEmpty()) {
          String[] evts = {
            "Competed in junior regional championships.",
            "Signed first junior sponsorship — small, but meaningful.",
            "Intense summer training camp — major growth.",
            "First teenage heartbreak. Life goes on."
          };
          state.lastEvent = evts[(int)random(evts.length)];
          p.addLifeEvent(state.lastEvent, state.currentYear);
        }
        break;

      case UNIVERSITY:
        p.smarts    = constrain(p.smarts    + random(2, 6), 0, 100);
        p.baseAttributes.mental = min(85, p.baseAttributes.mental + (int)random(0, 2));
        if (age >= 21) {
          state.inUniversity = false;
          updateLifePhase(p, age);
          if (state.proCareerStartYear < 0) state.proCareerStartYear = state.currentYear;
          String grad = "Graduated from university with honors. Going professional!";
          state.lastEvent = grad;
          p.addLifeEvent(grad, state.currentYear);
        } else if (state.lastEvent.isEmpty()) {
          String[] evts = {
            "Strong semester — balancing books and sport.",
            "Collegiate championships silver medal.",
            "University scholarship renewed.",
            "Internship at a sports management firm."
          };
          state.lastEvent = evts[(int)random(evts.length)];
          p.addLifeEvent(state.lastEvent, state.currentYear);
        }
        break;

      default: break;
    }
  }

  void simulateSeason() {
    Player p = state.player;
    int   totalPts   = 0;
    float totalPrize = 0;
    int   titles     = 0;
    String bestTournament = "";

    for (Tournament t : calendar.schedule) {
      if (t.tier == TournamentTier.GRAND_SLAM || t.tier == TournamentTier.MASTERS_1000) {
        if (p.health.isAvailable()) {
          TournamentRun run = t.simulate(p, matchEngine, state.currentYear);
          int   rPts   = run.totalPoints();
          float rPrize = run.totalPrize();
          totalPts   += rPts;
          totalPrize += rPrize;
          for (MatchResult r : run.results) p.career.addResult(r);
          MatchResult last = run.results.get(run.results.size() - 1);
          p.form.applyMatchResult(last.playerWon, true);
          if (run.won) {
            titles++;
            p.career.recordTitle(t.tier == TournamentTier.GRAND_SLAM, rPrize);
            if (bestTournament.isEmpty()) bestTournament = t.name;
          }
          state.lastTournamentRun = run;
        }
      }
      p.health.advanceWeek();
    }

    p.career.rankingPoints = (int)(p.career.rankingPoints * 0.65) + totalPts;
    p.career.prizeMoney   += totalPrize;
    p.finances.savings    += totalPrize;

    String summary;
    if (titles > 0) {
      String titleWord = titles == 1 ? "title" : "titles";
      summary = "Won " + titles + " " + titleWord + " this season" +
                (bestTournament.isEmpty() ? "" : " incl. " + bestTournament) +
                " — +" + formatMoney(totalPrize) + " prize money.";
      p.happiness = constrain(p.happiness + 12, 0, 100);
    } else if (totalPts >= 1000) {
      summary = "Strong season — " + totalPts + " points, +" + formatMoney(totalPrize) + ".";
      p.happiness = constrain(p.happiness + 4, 0, 100);
    } else if (totalPts > 0) {
      summary = "Season: " + totalPts + " points, +" + formatMoney(totalPrize) + ".";
    } else {
      summary = "Missed the season due to injury.";
      p.wellbeing  = constrain(p.wellbeing  - 15, 0, 100);
      p.happiness  = constrain(p.happiness  - 10, 0, 100);
    }

    state.lastEvent = summary;
    p.addLifeEvent(summary, state.currentYear);
    p.career.addHistory(state.currentYear + " — " + summary);
  }

  void applyAnnualFinances() {
    Player p = state.player;
    float rankEndorsement = p.finances.endorsementIncomeForRank(p.career.worldRanking);
    float annualIncome    = (rankEndorsement + p.finances.endorsementIncome) * 52;
    float annualExpenses  = p.finances.weeklyExpenses * 52 + p.agent.weeklySalary * 52;

    p.finances.savings += annualIncome - annualExpenses;

    for (OwnedInvestment inv : p.finances.investments) {
      for (int w = 0; w < 52; w++) inv.updateWeek();
    }
    for (OwnedBusiness biz : p.finances.businesses) {
      for (int w = 0; w < 52; w++) biz.updateWeek();
      p.finances.savings += biz.weeklyProfit() * 52;
    }
    p.finances.savings = max(0, p.finances.savings);
  }

  void syncBitLifeStats(Player p) {
    float wbTarget = 100 - p.form.fatigue * 0.4;
    if (p.health.status == InjuryStatus.OUT_MONTHS) wbTarget -= 30;
    else if (p.health.status == InjuryStatus.OUT_WEEKS) wbTarget -= 15;
    p.wellbeing = constrain(lerp(p.wellbeing, wbTarget, 0.25), 0, 100);

    float hapTarget = p.mentalHealth.happiness * 0.7f + 30;
    if (p.family.familyHappiness > 70)         hapTarget += 10;
    if (p.career.worldRanking <= 10)            hapTarget += 12;
    if (p.addiction != AddictionLevel.NONE)     hapTarget -= 15;
    p.happiness = constrain(lerp(p.happiness, hapTarget, 0.3), 0, 100);

    p.smarts = constrain(p.smarts + random(-1, 2), 0, 100);

    if (p.age(state.currentYear) > 28) {
      p.looks = constrain(p.looks - random(0, 1.5), 0, 100);
    }
  }

  void updateRanking() {
    Player p = state.player;
    int pts = p.career.rankingPoints;
    int rank;
    if      (pts >= 10000) rank = (int)random(1, 3);
    else if (pts >=  7000) rank = (int)random(3, 8);
    else if (pts >=  5000) rank = (int)random(8, 20);
    else if (pts >=  3000) rank = (int)random(20, 50);
    else if (pts >=  1500) rank = (int)random(50, 100);
    else if (pts >=   500) rank = (int)random(100, 200);
    else                   rank = (int)random(200, 400);

    int prev = p.career.worldRanking;
    p.career.worldRanking = (int)lerp(prev, rank, 0.4);
  }

  void applyChoice(EventChoice choice) {
    Player p = state.player;
    p.form.confidence       = constrain(p.form.confidence + choice.confidenceEffect, 0, 100);
    p.form.fatigue          = constrain(p.form.fatigue    + choice.fatigueEffect,    0, 100);
    p.baseAttributes.mental = constrain(p.baseAttributes.mental + choice.mentalEffect, 30, 99);
    if (choice.reputationEffect >= 0) p.reputation.applyPositive(choice.reputationEffect);
    else                              p.reputation.applyNegative(-choice.reputationEffect);
    p.career.addPoints(choice.rankingPointsEffect);
    if (choice.moneyEffect != 0) {
      p.finances.savings += choice.moneyEffect * 1000;
      if (p.finances.savings < 0) p.finances.savings = 0;
    }
    if (choice.familyEffect != 0) {
      p.family.familyHappiness = constrain(p.family.familyHappiness + choice.familyEffect, 0, 100);
      if (p.family.spouse != null && choice.familyEffect > 0)
        p.family.spouse.resentment = constrain(p.family.spouse.resentment - choice.familyEffect * 0.5, 0, 100);
    }
    if (choice.happinessEffect != 0)
      p.happiness = constrain(p.happiness + choice.happinessEffect, 0, 100);

    if (choice.label != null && !choice.label.isEmpty())
      p.addLifeEvent("Decision: " + choice.label, state.currentYear);

    if (choice.tag != null && !choice.tag.isEmpty()) {
      handleSpecialChoiceTag(choice.tag, p);
    }

    state.pendingEvent = null;
    ai.hasNewEvent     = false;

    if (!state.pendingMatchAction.isEmpty()) {
      String queuedAction = state.pendingMatchAction;
      state.pendingMatchAction = "";
      handleCareerAction(queuedAction, true);
    }
  }

  // ACTIVITY HANDLERS

  void handleCareerAction(String action) { handleCareerAction(action, false); }

  void handleCareerAction(String action, boolean skipMatchDecision) {
    Player p = state.player;
    switch (action) {
      case "tournament":
        if (!skipMatchDecision && state.lifePhase == LifePhase.PRO && random(1) < 0.40) {
          state.pendingMatchAction = "tournament";
          state.pendingEvent       = buildPreMatchDecisionEvent(p);
          break;
        }
        if (calendar.schedule.isEmpty()) break;
        Tournament t = calendar.schedule.get((int)random(calendar.schedule.size()));
        TournamentRun run = t.simulate(p, matchEngine, state.currentYear);
        MatchResult last = run.results.get(run.results.size() - 1);
        p.career.addPoints(run.totalPoints());
        p.career.prizeMoney  += run.totalPrize();
        p.finances.savings   += run.totalPrize();
        p.form.applyMatchResult(last.playerWon, true);
        for (MatchResult r : run.results) p.career.addResult(r);
        state.lastTournamentRun = run;
        String res = run.won ? "Won " + t.name + "!" : "Exited " + t.name + " in " + last.round;
        state.lastEvent = res + " +" + run.totalPoints() + "pts +" + formatMoney(run.totalPrize());
        p.addLifeEvent(state.lastEvent, state.currentYear);
        checkRivalTracking(run.results, p);
        break;

      case "sponsor":
        float rank01 = map(constrain(p.career.worldRanking, 1, 400), 1, 400, 1, 0);
        float deal   = constrain((rank01 * 400 + random(20, 80)) * 1000, 10000, 2000000);
        state.pendingEvent = buildSponsorEvent((int)(deal / 1000));
        break;

      case "coach":
        p.baseAttributes.serve    = min(99, p.baseAttributes.serve    + (int)random(1, 3));
        p.baseAttributes.forehand = min(99, p.baseAttributes.forehand + (int)random(0, 2));
        p.baseAttributes.mental   = min(99, p.baseAttributes.mental   + (int)random(0, 2));
        p.form.confidence = constrain(p.form.confidence + 8, 0, 100);
        String improvement = "Training camp with new coach — stats improved.";
        state.lastEvent = improvement;
        p.addLifeEvent(improvement, state.currentYear);
        break;

      case "media":
        state.pendingEvent = buildMediaEvent();
        break;
    }
  }

  void handleTrainingAction(String action) {
    Player p = state.player;
    switch (action) {
      case "intensive":
        p.baseAttributes.serve    = min(99, p.baseAttributes.serve    + (int)random(1, 3));
        p.baseAttributes.forehand = min(99, p.baseAttributes.forehand + (int)random(1, 3));
        p.baseAttributes.stamina  = min(99, p.baseAttributes.stamina  + (int)random(1, 3));
        p.form.fatigue = constrain(p.form.fatigue + 22, 0, 100);
        p.wellbeing    = constrain(p.wellbeing - 8, 0, 100);
        state.lastEvent = "Intensive training week — big gains, high fatigue.";
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;

      case "light":
        p.baseAttributes.forehand = min(99, p.baseAttributes.forehand + (int)random(0, 2));
        p.form.fatigue = constrain(p.form.fatigue + 6, 0, 100);
        state.lastEvent = "Light practice session completed.";
        break;

      case "mental":
        p.baseAttributes.mental = min(99, p.baseAttributes.mental + (int)random(1, 3));
        p.form.confidence = constrain(p.form.confidence + 6, 0, 100);
        p.mentalHealth.stressLevel = constrain(p.mentalHealth.stressLevel - 8, 0, 100);
        p.happiness = constrain(p.happiness + 5, 0, 100);
        state.lastEvent = "Mental coaching session — confidence and focus improved.";
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;

      case "recovery":
        p.form.fatigue = constrain(p.form.fatigue - 28, 0, 100);
        p.wellbeing    = constrain(p.wellbeing + 10, 0, 100);
        p.health.advanceWeek();
        state.lastEvent = "Full recovery week — fatigue cleared.";
        break;
    }
  }

  void handleRelationshipAction(String action) {
    Player p = state.player;
    switch (action) {
      case "date":
        if (p.family.spouse == null) {
          state.pendingEvent = buildDateEvent();
        } else {
          p.family.familyHappiness = constrain(p.family.familyHappiness + 10, 0, 100);
          if (p.family.spouse != null)
            p.family.spouse.resentment = constrain(p.family.spouse.resentment - 8, 0, 100);
          p.happiness = constrain(p.happiness + 8, 0, 100);
          state.lastEvent = "Quality time with " + p.family.partnerName() + ".";
          p.addLifeEvent(state.lastEvent, state.currentYear);
        }
        break;

      case "propose":
        if (p.family.status == RelationshipStatus.DATING && p.family.spouse != null) {
          state.pendingEvent = buildProposeEvent(p);
        } else if (p.family.spouse == null) {
          state.lastEvent = "You need to be dating someone first.";
        }
        break;

      case "child":
        if (p.family.status == RelationshipStatus.MARRIED) {
          state.pendingEvent = buildChildEvent(p);
        } else {
          state.lastEvent = "You need to be married first.";
        }
        break;

      case "family":
        p.happiness = constrain(p.happiness + 6, 0, 100);
        if (p.parents != null) {
          p.parents.mom.relationship = constrain(p.parents.mom.relationship + 5, 0, 100);
          p.parents.dad.relationship = constrain(p.parents.dad.relationship + 5, 0, 100);
          state.lastEvent = "Called Mom (" + p.parents.mom.name + ") and Dad (" + p.parents.dad.name + "). They're proud of you.";
        } else {
          state.lastEvent = "Spent quality time with family — feeling connected.";
        }
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;
    }
  }

  void handleHealthAction(String action) {
    Player p = state.player;
    switch (action) {
      case "doctor":
        p.wellbeing = constrain(p.wellbeing + 8, 0, 100);
        if (p.health.status != InjuryStatus.HEALTHY) {
          p.health.weeksRemaining = max(0, p.health.weeksRemaining - 4);
          if (p.health.weeksRemaining == 0) p.health.status = InjuryStatus.HEALTHY;
          state.lastEvent = "Doctor visit — recovery accelerated.";
        } else {
          state.lastEvent = "Annual checkup — all clear.";
        }
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;

      case "therapy":
        p.mentalHealth.startTherapy(8);
        p.happiness = constrain(p.happiness + 10, 0, 100);
        state.lastEvent = "Started therapy — working through stress.";
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;

      case "gym":
        p.baseAttributes.stamina = min(99, p.baseAttributes.stamina + (int)random(1, 3));
        p.wellbeing = constrain(p.wellbeing + 6, 0, 100);
        p.form.fatigue = constrain(p.form.fatigue + 10, 0, 100);
        state.lastEvent = "Gym session — stamina and wellbeing improved.";
        break;

      case "spa":
        float spaCost = 2000;
        if (p.finances.savings >= spaCost) {
          p.finances.savings -= spaCost;
          p.form.fatigue = constrain(p.form.fatigue - 20, 0, 100);
          p.wellbeing    = constrain(p.wellbeing + 12, 0, 100);
          p.happiness    = constrain(p.happiness + 6, 0, 100);
          state.lastEvent = "Spa retreat — fully recovered. -$2K";
          p.addLifeEvent(state.lastEvent, state.currentYear);
        } else {
          state.lastEvent = "Can't afford the spa right now.";
        }
        break;
    }
  }

  void handleAssetAction(String action) {
    Player p = state.player;
    switch (action) {
      case "property":
        state.pendingEvent = buildPropertyEvent(p);
        break;
      case "invest":
        state.pendingEvent = buildInvestEvent(p);
        break;
      case "car":
        state.pendingEvent = buildCarEvent(p);
        break;
      case "business":
        state.pendingEvent = buildBusinessEvent(p);
        break;
    }
  }

  void handleSocialAction(String action) {
    Player p = state.player;
    switch (action) {
      case "post":
        p.socialMedia.updateWeek(p.career.worldRanking, true);
        p.popularity = constrain(p.popularity + 3, 0, 100);
        state.lastEvent = "Posted on social media — follower boost!";
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;

      case "charity":
        if (p.finances.savings >= 10000) {
          p.finances.savings -= 10000;
          p.reputation.applyPositive(15);
          p.happiness = constrain(p.happiness + 8, 0, 100);
          state.lastEvent = "Hosted charity event — reputation up. -$10K";
          p.addLifeEvent(state.lastEvent, state.currentYear);
        } else {
          state.lastEvent = "Need at least $10K for a charity event.";
        }
        break;

      case "interview":
        state.pendingEvent = buildInterviewEvent(p);
        break;

      case "pr":
        if (!p.socialMedia.hasPRManager) {
          float prCost = 50000;
          if (p.finances.savings >= prCost) {
            p.finances.savings -= prCost;
            p.socialMedia.hasPRManager = true;
            p.hasSocialManager = true;
            state.lastEvent = "Hired a PR manager — brand growth accelerated. -$50K";
            p.addLifeEvent(state.lastEvent, state.currentYear);
          } else {
            state.lastEvent = "Need $50K to hire a PR manager.";
          }
        } else {
          state.lastEvent = "You already have a PR manager.";
        }
        break;
    }
  }

  // PROCEDURAL EVENT BUILDERS

  GameEvent buildSponsorEvent(int dealK) {
    GameEvent e = new GameEvent();
    e.headline    = "Sponsorship Offer";
    e.description = "A major brand wants to put their logo on your kit. The deal is worth $" + dealK + "K. Their rep is waiting for an answer.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Sign immediately";
    c1.moneyEffect = dealK; c1.confidenceEffect = 5;
    c1.description = "Secure the deal. Money in the bank."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Negotiate harder";
    c2.moneyEffect = (int)(dealK * 0.7); c2.reputationEffect = 3;
    c2.description = "Push for better terms. Some risk but possible upside."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Turn it down";
    c3.reputationEffect = 5; c3.confidenceEffect = 3;
    c3.description = "Wait for a brand that better fits your image."; e.choices.add(c3);
    return e;
  }

  GameEvent buildMediaEvent() {
    GameEvent e = new GameEvent();
    e.headline    = "Media Opportunity";
    e.description = "A top sports magazine wants an exclusive interview and photoshoot. It could raise your profile significantly — or expose personal details you'd rather keep private.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Full access";
    c1.reputationEffect = 12; c1.moneyEffect = 30; c1.confidenceEffect = -3;
    c1.description = "Open up. Huge profile boost but nothing stays private."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Sport-only interview";
    c2.reputationEffect = 6; c2.moneyEffect = 15;
    c2.description = "Professional and polished. Safe but limited exposure."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Decline";
    c3.confidenceEffect = 5;
    c3.description = "Keep your private life private."; e.choices.add(c3);
    return e;
  }

  GameEvent buildDateEvent() {
    String[] names   = {"Sofia Torres","Emma Laurent","Priya Sharma","Mei Zhang","Anya Petrov",
                        "Isabella Rossi","Fatima Al-Rashid","Hana Kim","Lucia Santos","Zara Ahmed"};
    String name = names[(int)random(names.length)];
    GameEvent e = new GameEvent();
    e.headline    = "Someone Catches Your Eye";
    e.description = name + " — charming, career-driven, and clearly interested. You meet at an industry event. The night is going well.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Ask them out";
    c1.tag = "start_dating_" + name;
    c1.familyEffect = 20; c1.happinessEffect = 15; c1.confidenceEffect = 5;
    c1.description = "Take the leap. Start something new."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Exchange numbers";
    c2.familyEffect = 8; c2.happinessEffect = 8;
    c2.description = "Play it cool. See where it goes."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Stay focused on career";
    c3.confidenceEffect = 3; c3.rankingPointsEffect = 20;
    c3.description = "Your sport comes first right now."; e.choices.add(c3);
    return e;
  }

  GameEvent buildProposeEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Pop the Question?";
    e.description = "You've been with " + p.family.partnerName() + " for a while now. Friends and family keep asking. You have a ring picked out.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Propose!";
    c1.tag = "propose_yes";
    c1.familyEffect = 30; c1.happinessEffect = 25; c1.moneyEffect = -15;
    c1.description = "Get down on one knee. Change your life."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Not yet";
    c2.familyEffect = -5; c2.confidenceEffect = -3;
    c2.description = "Still not sure. Keep things as they are."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "End the relationship";
    c3.familyEffect = -25; c3.happinessEffect = -15; c3.mentalEffect = -5;
    c3.description = "This isn't working. Time to move on."; e.choices.add(c3);
    return e;
  }

  GameEvent buildChildEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Starting a Family";
    e.description = "You and " + p.family.partnerName() + " are talking about having a child. It would change your career schedule and finances significantly.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Have a child";
    c1.tag = "have_child";
    c1.familyEffect = 20; c1.happinessEffect = 20; c1.moneyEffect = -100; c1.fatigueEffect = 15;
    c1.description = "Start a family. Life will never be the same."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Not yet — career first";
    c2.familyEffect = -8; c2.rankingPointsEffect = 50; c2.confidenceEffect = 5;
    c2.description = "Wait until you've achieved more on the field."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Adopt";
    c3.tag = "have_child";
    c3.familyEffect = 18; c3.happinessEffect = 18; c3.moneyEffect = -80; c3.reputationEffect = 8;
    c3.description = "Grow your family differently. A generous and life-changing choice."; e.choices.add(c3);
    return e;
  }

  GameEvent buildPropertyEvent(Player p) {
    GameEvent e = new GameEvent();
    float savings = p.finances.savings;
    e.headline    = "Property Decision";
    e.description = "The real estate market is hot. Your agent says now is the time to invest. You have " + formatMoney(savings) + " saved.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Buy an apartment";
    c1.moneyEffect = savings >= 200000 ? -40 : 0;
    c1.description = savings >= 200000 ? "Secure a city apartment. -$200K down payment." : "Need $200K to buy an apartment."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Buy a house";
    c2.moneyEffect = savings >= 500000 ? -100 : 0;
    c2.description = savings >= 500000 ? "A proper home. -$500K down payment." : "Need $500K for a house."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Not right now";
    c3.confidenceEffect = 2;
    c3.description = "Wait for a better time."; e.choices.add(c3);
    return e;
  }

  GameEvent buildInvestEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Investment Opportunity";
    e.description = "Your financial advisor has three options on the table. You have " + formatMoney(p.finances.savings) + " available.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Stock market";
    c1.moneyEffect = (int)(-min(50, p.finances.savings / 1000));
    c1.description = "Moderate risk. Potential for solid long-term gains."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Crypto";
    c2.moneyEffect = (int)(-min(30, p.finances.savings / 1000));
    c2.description = "High risk, high reward. Could double — or halve."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Bonds (safe)";
    c3.moneyEffect = (int)(-min(20, p.finances.savings / 1000));
    c3.description = "Low risk, steady returns. The safe play."; e.choices.add(c3);
    return e;
  }

  GameEvent buildCarEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "New Wheels";
    e.description = "You're at the dealership. The staff recognise you immediately. Three options are on the lot.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Sports car ($80K)";
    c1.moneyEffect = p.finances.savings >= 80000 ? -80 : 0;
    c1.confidenceEffect = 8; c1.reputationEffect = 5;
    c1.description = p.finances.savings >= 80000 ? "Turn heads everywhere you go." : "Can't afford it yet."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Luxury SUV ($120K)";
    c2.moneyEffect = p.finances.savings >= 120000 ? -120 : 0;
    c2.familyEffect = 5; c2.confidenceEffect = 6;
    c2.description = p.finances.savings >= 120000 ? "Practical and premium." : "Need more savings."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Walk away";
    c3.description = "Save the money for something more important."; e.choices.add(c3);
    return e;
  }

  GameEvent buildBusinessEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Business Venture";
    e.description = "An old friend pitches you a business idea — a sports academy, a fashion brand, or a restaurant. All need capital.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Sports academy ($150K)";
    c1.moneyEffect = p.finances.savings >= 150000 ? -150 : 0;
    c1.reputationEffect = 10;
    c1.description = p.finances.savings >= 150000 ? "Give back to the sport. Long-term brand builder." : "Need $150K."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Fashion brand ($80K)";
    c2.moneyEffect = p.finances.savings >= 80000 ? -80 : 0;
    c2.confidenceEffect = 8; c2.reputationEffect = 6;
    c2.description = p.finances.savings >= 80000 ? "Risky but trending." : "Need $80K."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Skip it";
    c3.rankingPointsEffect = 30;
    c3.description = "Stick to what you know best: your sport."; e.choices.add(c3);
    return e;
  }

  GameEvent buildInterviewEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Hot Take Request";
    e.description = "A journalist asks for your unfiltered opinion on a controversial topic in your sport.";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Speak your mind";
    c1.reputationEffect = -8; c1.confidenceEffect = 12; c1.moneyEffect = 25;
    c1.description = "Bold, viral, and divisive."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Diplomatic answer";
    c2.reputationEffect = 6; c2.moneyEffect = 10;
    c2.description = "Professional and polished."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Refuse to comment";
    c3.reputationEffect = 3; c3.confidenceEffect = 5;
    c3.description = "No comment. Let your game do the talking."; e.choices.add(c3);
    return e;
  }

  GameEvent buildUniversityDecisionEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Your Future Awaits";
    e.description = "You're 17, finishing school in " + p.hometown + ". Scouts are watching — so are university admissions. What path do you choose?";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Go Pro Immediately";
    c1.tag = "go_pro_early"; c1.confidenceEffect = 12; c1.rankingPointsEffect = 100;
    c1.description = "Bet on yourself. Enter the pro circuit at 18."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "University (3 years)";
    c2.tag = "go_university"; c2.mentalEffect = 6; c2.happinessEffect = 12;
    c2.description = "Grow as a person first. Graduate at 21, then go pro."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Balance Both";
    c3.tag = "part_time_study"; c3.confidenceEffect = 5; c3.happinessEffect = 6; c3.mentalEffect = 4;
    c3.description = "Study part-time, train full-time. Hard but rewarding."; e.choices.add(c3);
    return e;
  }

  GameEvent buildPreMatchDecisionEvent(Player p) {
    GameEvent e = new GameEvent();
    String[] scenarios = {
      "Third set, you're down a break. The crowd is quiet. Your coach shouts something from the sideline.",
      "You're up two sets — energy dropping fast. Your knee is slightly sore from the previous round.",
      "Final round. Your opponent just won three in a row. The momentum has shifted.",
      "Rain delay over. The court is slick. Conditions have completely changed.",
      "You can see the opponent is cramping. Time to go for the jugular — or play it safe?"
    };
    e.headline    = "Match Decision";
    e.description = scenarios[(int)random(scenarios.length)];
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Push Harder";
    c1.confidenceEffect = 15; c1.fatigueEffect = 22; c1.rankingPointsEffect = 40;
    c1.description = "Leave everything on the court."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Stay Composed";
    c2.confidenceEffect = 6; c2.mentalEffect = 3; c2.rankingPointsEffect = 15;
    c2.description = "Play smart. Trust your training."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Switch Strategy";
    c3.confidenceEffect = 10; c3.rankingPointsEffect = 25; c3.fatigueEffect = 10;
    c3.description = "Surprise your opponent. Change the rhythm."; e.choices.add(c3);
    return e;
  }

  void handleSpecialChoiceTag(String tag, Player p) {
    if (tag.startsWith("start_dating_")) {
      String partnerName = tag.substring("start_dating_".length());
      if (p.family.spouse == null) {
        p.family.spouse = new Spouse(partnerName);
        p.family.status = RelationshipStatus.DATING;
        p.addLifeEvent("Started dating " + partnerName + ". Something special is growing.", state.currentYear);
      }
      return;
    }
    switch (tag) {
      case "go_university":
        state.inUniversity = true;
        state.lifePhase    = LifePhase.UNIVERSITY;
        p.addLifeEvent("Chose university — will graduate at 21 then turn professional.", state.currentYear);
        break;
      case "go_pro_early":
        state.inUniversity       = false;
        state.proCareerStartYear = state.currentYear;
        state.lifePhase          = LifePhase.PRO;
        p.addLifeEvent("Chose to turn professional immediately. The career begins!", state.currentYear);
        break;
      case "part_time_study":
        state.inUniversity       = false;
        state.proCareerStartYear = state.currentYear;
        state.lifePhase          = LifePhase.PRO;
        p.smarts = constrain(p.smarts + 5, 0, 100);
        p.addLifeEvent("Chose to balance sport and study. Best of both worlds.", state.currentYear);
        break;
      case "propose_yes":
        if (p.family.spouse != null) {
          p.family.status = RelationshipStatus.ENGAGED;
          p.addLifeEvent("Engaged to " + p.family.partnerName() + "! The ring is on the finger.", state.currentYear);
        }
        break;
      case "have_child":
        String[] babyNames = {"Luca","Sofia","Noah","Mia","Mateo","Elena","Lucas","Aria",
                               "Gabriel","Isla","Marco","Zara","Diego","Luna","Oliver","Maya"};
        String babyName = babyNames[(int)random(babyNames.length)];
        p.family.children.add(new Child(babyName, 0));
        p.addLifeEvent("Welcomed " + babyName + " into the world! A new chapter begins.", state.currentYear);
        break;
    }
  }

  void handleEarlyLifeActivity(String key) {
    Player p = state.player;
    state.lastEvent = "";

    if (key.equals("intensive") || key.equals("light") || key.equals("mental") || key.equals("recovery")) {
      handleTrainingAction(key); return;
    }
    if (key.equals("doctor") || key.equals("therapy") || key.equals("gym") || key.equals("spa")) {
      handleHealthAction(key); return;
    }
    if (key.equals("post")) { handleSocialAction("post"); return; }
    if (key.equals("charity")) { handleSocialAction("charity"); return; }
    if (key.equals("interview")) { handleSocialAction("interview"); return; }
    if (key.equals("date")) { handleRelationshipAction("date"); return; }
    if (key.equals("invest")) { handleAssetAction("invest"); return; }
    if (key.equals("sponsor")) { handleCareerAction("sponsor"); return; }

    switch (key) {
      case "play_outside":
        p.wellbeing = constrain(p.wellbeing + 6, 0, 100);
        p.happiness = constrain(p.happiness + 8, 0, 100);
        state.lastEvent = "Spent the afternoon playing outside. Fresh air, scraped knees, pure joy.";
        break;
      case "draw":
        p.smarts    = constrain(p.smarts    + random(1, 3), 0, 100);
        p.happiness = constrain(p.happiness + 5, 0, 100);
        state.lastEvent = "Drew and created all day. Imagination running wild.";
        break;
      case "watch_sport":
        p.baseAttributes.mental = min(99, p.baseAttributes.mental + 1);
        p.happiness = constrain(p.happiness + 5, 0, 100);
        state.lastEvent = "Watched a pro match on TV. Eyes wide, completely inspired.";
        break;
      case "play_sport":
        p.baseAttributes.speed = min(70, p.baseAttributes.speed + (int)random(0, 2));
        p.wellbeing = constrain(p.wellbeing + 6, 0, 100);
        state.lastEvent = "Kicked the ball around all day. Natural talent starting to show.";
        break;
      case "read":
        p.smarts = constrain(p.smarts + random(1, 4), 0, 100);
        state.lastEvent = "Read a book from cover to cover. Mind is growing.";
        break;
      case "homework": case "study_hard":
        p.smarts    = constrain(p.smarts    + random(2, 5), 0, 100);
        p.happiness = constrain(p.happiness - 2, 0, 100);
        state.lastEvent = "Studied hard. Boring but worthwhile — smarts increasing.";
        break;
      case "explore": case "library":
        p.smarts    = constrain(p.smarts    + random(1, 4), 0, 100);
        p.happiness = constrain(p.happiness + 2, 0, 100);
        state.lastEvent = "Explored the world with curiosity. Knowledge is power.";
        break;
      case "build": case "creative":
        p.smarts    = constrain(p.smarts    + random(1, 3), 0, 100);
        p.happiness = constrain(p.happiness + 4, 0, 100);
        state.lastEvent = "Built something from scratch. Problem-solving skills sharpening.";
        break;
      case "talk_mom":
        if (p.parents != null) {
          p.parents.mom.relationship = constrain(p.parents.mom.relationship + 10, 0, 100);
          p.happiness = constrain(p.happiness + 8, 0, 100);
          state.lastEvent = "Long conversation with Mom (" + p.parents.mom.name + "). She always knows what to say.";
        }
        break;
      case "talk_dad":
        if (p.parents != null) {
          p.parents.dad.relationship = constrain(p.parents.dad.relationship + 10, 0, 100);
          p.happiness = constrain(p.happiness + 8, 0, 100);
          state.lastEvent = "Spent quality time with Dad (" + p.parents.dad.name + "). Life lessons and laughter.";
        }
        break;
      case "family_time": case "visit_grandparents": case "family_dinner": case "visit_family": case "family_hug":
        p.happiness = constrain(p.happiness + 9, 0, 100);
        p.wellbeing = constrain(p.wellbeing + 4, 0, 100);
        if (p.parents != null) {
          p.parents.mom.relationship = constrain(p.parents.mom.relationship + 4, 0, 100);
          p.parents.dad.relationship = constrain(p.parents.dad.relationship + 4, 0, 100);
          state.lastEvent = "Family time with Mom (" + p.parents.mom.name + ") and Dad (" + p.parents.dad.name + ").";
        } else {
          state.lastEvent = "Quality family time. Feeling grounded and loved.";
        }
        break;
      case "call_parents":
        if (p.parents != null) {
          p.parents.mom.relationship = constrain(p.parents.mom.relationship + 7, 0, 100);
          p.parents.dad.relationship = constrain(p.parents.dad.relationship + 7, 0, 100);
          p.happiness = constrain(p.happiness + 6, 0, 100);
          state.lastEvent = "Called Mom (" + p.parents.mom.name + ") and Dad (" + p.parents.dad.name + "). They're proud and supportive.";
        }
        break;
      case "hangout": case "date_together":
        p.happiness = constrain(p.happiness + 7, 0, 100);
        if (p.family.spouse != null) {
          p.family.familyHappiness = constrain(p.family.familyHappiness + 10, 0, 100);
          state.lastEvent = "Great time with " + p.family.partnerName() + ".";
        } else {
          state.lastEvent = "Caught up with close friends. Soul recharged.";
        }
        break;
      case "eat_healthy":
        p.wellbeing = constrain(p.wellbeing + 6, 0, 100);
        state.lastEvent = "Ate well — fresh food, balanced diet. Body growing strong.";
        break;
      case "doctor_visit":
        p.wellbeing = constrain(p.wellbeing + 5, 0, 100);
        state.lastEvent = "Checkup — all healthy and growing well.";
        break;
      case "sleep":
        p.wellbeing = constrain(p.wellbeing + 7, 0, 100);
        p.happiness = constrain(p.happiness + 3, 0, 100);
        state.lastEvent = "Solid sleep schedule. Full of energy.";
        break;
      case "rest":
        p.form.fatigue = constrain(p.form.fatigue - 20, 0, 100);
        p.wellbeing    = constrain(p.wellbeing + 8, 0, 100);
        state.lastEvent = "Rest day — body and mind recharged.";
        break;
      case "save_allowance": case "chores":
        p.finances.savings += 200;
        p.smarts = constrain(p.smarts + 1, 0, 100);
        state.lastEvent = "Saved up — $200 added. Financial discipline is forming.";
        break;
      case "buy_toy":
        if (p.finances.savings >= 50) {
          p.finances.savings -= 50;
          p.happiness = constrain(p.happiness + 14, 0, 100);
          state.lastEvent = "Bought a new toy. Pure childhood joy. -$50.";
        } else {
          state.lastEvent = "Not enough pocket money for that right now.";
        }
        break;
      case "help_home": case "part_time_help":
        p.finances.savings += 300;
        if (p.parents != null) {
          p.parents.mom.relationship = constrain(p.parents.mom.relationship + 3, 0, 100);
          p.parents.dad.relationship = constrain(p.parents.dad.relationship + 3, 0, 100);
        }
        state.lastEvent = "Helped around the house — earned $300 and family praise.";
        break;
      case "save_up":
        p.smarts = constrain(p.smarts + 1, 0, 100);
        state.lastEvent = "Committed to saving every penny. Discipline building.";
        break;
      case "buy_equip":
        if (p.finances.savings >= 500) {
          p.finances.savings -= 500;
          p.baseAttributes.serve = min(90, p.baseAttributes.serve + 2);
          state.lastEvent = "Bought better training equipment. Game already feels different. -$500";
          p.addLifeEvent(state.lastEvent, state.currentYear);
        } else {
          state.lastEvent = "Need at least $500 for proper equipment.";
        }
        break;
      case "part_time_job":
        float jobIncome = random(600, 2500);
        p.finances.savings += jobIncome;
        p.happiness = constrain(p.happiness - 3, 0, 100);
        state.lastEvent = "Part-time job income — +" + formatMoney(jobIncome) + ". Building the war chest.";
        break;
      case "first_invest":
        if (p.finances.savings >= 1000) {
          p.finances.savings -= 1000;
          OwnedInvestment firstInv = new OwnedInvestment("First Investment", InvestmentType.STOCKS, 1000);
          p.finances.investments.add(firstInv);
          p.smarts = constrain(p.smarts + 3, 0, 100);
          state.lastEvent = "First investment ever — $1K into stocks. The wealth journey starts.";
          p.addLifeEvent(state.lastEvent, state.currentYear);
        } else {
          state.lastEvent = "Need $1K to make your first investment.";
        }
        break;
      case "scholarship":
        p.smarts = constrain(p.smarts + 3, 0, 100);
        p.finances.savings += 5000;
        state.lastEvent = "Applied for scholarship — received $5K grant! Hard work paying off.";
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;
      case "make_friend":
        p.happiness = constrain(p.happiness + 11, 0, 100);
        state.lastEvent = "Made a new best friend. Life is better with good people.";
        break;
      case "join_club": case "school_club":
        p.smarts    = constrain(p.smarts    + 2, 0, 100);
        p.happiness = constrain(p.happiness + 5, 0, 100);
        state.lastEvent = "Joined an after-school club. Found a tribe of like-minded people.";
        break;
      case "community": case "local_event":
        p.reputation.applyPositive(4);
        p.happiness = constrain(p.happiness + 4, 0, 100);
        state.lastEvent = "Participated in a local community event. Building roots.";
        break;
      case "post_clip":
        p.socialMedia.updateWeek(p.career.worldRanking, true);
        p.happiness = constrain(p.happiness + 3, 0, 100);
        state.lastEvent = "Posted a training clip online — a few hundred people noticed.";
        break;
      case "school_sports": case "phys_train":
        p.baseAttributes.speed   = min(80, p.baseAttributes.speed   + (int)random(1, 3));
        p.baseAttributes.stamina = min(80, p.baseAttributes.stamina + (int)random(0, 2));
        p.happiness = constrain(p.happiness + 5, 0, 100);
        state.lastEvent = "School sports — standing out, talent noticed by coaches.";
        break;
      case "youth_train":
        p.baseAttributes.serve    = min(80, p.baseAttributes.serve    + (int)random(1, 3));
        p.baseAttributes.forehand = min(80, p.baseAttributes.forehand + (int)random(0, 2));
        p.form.fatigue = constrain(p.form.fatigue + 12, 0, 100);
        state.lastEvent = "Youth training session — skill developing fast.";
        break;
      case "watch_pro":
        p.baseAttributes.mental  = min(90, p.baseAttributes.mental  + (int)random(1, 3));
        p.form.confidence        = constrain(p.form.confidence + 5, 0, 100);
        state.lastEvent = "Watched the pros up close — absorbed so much about the game.";
        break;
      case "school_tourney":
        boolean wonSchool = random(1) > 0.45;
        p.form.confidence = constrain(p.form.confidence + (wonSchool ? 12 : -3), 0, 100);
        int sPts = wonSchool ? (int)random(50, 200) : (int)random(10, 50);
        float sPrize = wonSchool ? random(200, 1500) : 0;
        p.career.rankingPoints += sPts;
        p.finances.savings     += sPrize;
        p.career.prizeMoney    += sPrize;
        if (wonSchool) p.career.titlesWon++;
        state.lastEvent = (wonSchool ? "Won the school tournament! " : "Strong run at school tournament. ") + "+" + sPts + " pts" + (wonSchool ? ", +" + formatMoney(sPrize) : "");
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;
      case "youth_tourney":
        boolean wonYouth = random(1) > 0.40;
        p.form.confidence = constrain(p.form.confidence + (wonYouth ? 15 : -4), 0, 100);
        int yPts = wonYouth ? (int)random(150, 600) : (int)random(20, 100);
        float yPrize = wonYouth ? random(500, 8000) : 0;
        p.career.rankingPoints += yPts;
        p.finances.savings     += yPrize;
        p.career.prizeMoney    += yPrize;
        if (wonYouth) p.career.titlesWon++;
        state.lastEvent = (wonYouth ? "Won the youth tournament! " : "Good junior tournament run. ") + "+" + yPts + " pts" + (wonYouth ? ", +" + formatMoney(yPrize) : "");
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;
      case "approach_scout":
        p.form.confidence = constrain(p.form.confidence + 8, 0, 100);
        p.reputation.applyPositive(5);
        state.lastEvent = "Approached a professional scout — left a strong impression.";
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;
      case "hire_first_coach":
        if (p.finances.savings >= 1000) {
          p.finances.savings -= 1000;
          p.baseAttributes.serve  = min(90, p.baseAttributes.serve  + (int)random(2, 5));
          p.baseAttributes.mental = min(90, p.baseAttributes.mental + 3);
          p.form.confidence       = constrain(p.form.confidence + 10, 0, 100);
          state.lastEvent = "Hired first real coach — stats jumped immediately. -$1K";
          p.addLifeEvent(state.lastEvent, state.currentYear);
        } else {
          state.lastEvent = "Need at least $1K to hire a coach.";
        }
        break;
      case "amateur_match":
        boolean wonAmateur = random(1) > 0.45;
        p.form.confidence = constrain(p.form.confidence + (wonAmateur ? 8 : -3), 0, 100);
        int aPts = wonAmateur ? (int)random(30, 100) : 5;
        p.career.rankingPoints += aPts;
        state.lastEvent = (wonAmateur ? "Won the amateur match! " : "Lost the amateur match. ") + "+" + aPts + " pts.";
        break;
      case "hire_coach":
        handleCareerAction("coach"); break;
      case "collegiate_match":
        boolean wonColl = random(1) > 0.40;
        p.form.confidence = constrain(p.form.confidence + (wonColl ? 14 : -5), 0, 100);
        int cPts = wonColl ? (int)random(200, 900) : (int)random(30, 120);
        float cPrize = wonColl ? random(1000, 12000) : 0;
        p.career.rankingPoints += cPts;
        p.finances.savings     += cPrize;
        p.career.prizeMoney    += cPrize;
        if (wonColl) p.career.titlesWon++;
        state.lastEvent = (wonColl ? "Won the collegiate match! " : "Fought hard in collegiate match. ") + "+" + cPts + " pts" + (wonColl ? ", +" + formatMoney(cPrize) : "");
        p.addLifeEvent(state.lastEvent, state.currentYear);
        break;
      case "study_film":
        p.baseAttributes.mental = min(90, p.baseAttributes.mental + (int)random(1, 3));
        p.form.confidence       = constrain(p.form.confidence + 5, 0, 100);
        state.lastEvent = "Studied opponent film — tactical IQ sharpening.";
        break;
      case "tutor":
        p.smarts = constrain(p.smarts + random(2, 5), 0, 100);
        state.lastEvent = "Tutoring session — smarts climbing.";
        break;
      default:
        state.lastEvent = "Spent the year making progress.";
        break;
    }

    if (!state.lastEvent.isEmpty() && !key.equals("school_tourney") && !key.equals("youth_tourney")
        && !key.equals("approach_scout") && !key.equals("hire_first_coach") && !key.equals("collegiate_match")
        && !key.equals("first_invest") && !key.equals("scholarship") && !key.equals("buy_equip")
        && !key.equals("call_parents") && !key.equals("talk_mom") && !key.equals("talk_dad")
        && !key.equals("doctor_visit")) {
      p.addLifeEvent(state.lastEvent, state.currentYear);
    }
  }

  void processKey(char k, int kc) {
    if (k == ' ' && currentScreen == ScreenID.CAREER) ageUp();
    activeScreen.onKey(k, kc);
  }

  void processMouse(int mx, int my) {
    if (state.player != null && my < 52) {
      ScreenID[] navScreens = {ScreenID.CAREER, ScreenID.TRAINING, ScreenID.WORLD_RANKINGS, ScreenID.LIFESTYLE, ScreenID.LEGACY};
      for (int i = 0; i < navScreens.length; i++) {
        float nx = width - 575 + i * 113;
        if (mx > nx && mx < nx + 106 && my > 8 && my < 42) {
          switchTo(navScreens[i]);
          return;
        }
      }
    }
    activeScreen.onClick(mx, my);
  }

  void processMouseReleased(int mx, int my) { activeScreen.onRelease(mx, my); }
  void processMouseMoved(int mx, int my)    { activeScreen.onHover(mx, my); }

  void newGame(String playerName, String nationality, String hometown, PlayStyle style, String hand, Sport sport, int birthYear) {
    state              = new GameState();
    state.currentSport = sport;
    state.sportConfig  = getSportConfig(sport);
    state.player       = new Player(playerName, nationality, birthYear, style, hand);
    state.player.hometown = hometown;
    state.currentYear  = birthYear;
    state.lifePhase    = LifePhase.CHILDHOOD;
    state.player.career.worldRanking  = (int)random(200, 400);
    state.player.career.rankingPoints = 0;
    state.player.finances.savings     = 0;

    calendar = new TournamentCalendar(sport);
    ranking.generateWorldPlayers(50, state.sportConfig);

    String parentStr = (state.player.parents != null)
      ? "Born to " + state.player.parents.mom.name + " and " + state.player.parents.dad.name + "."
      : "";
    state.player.addLifeEvent("Born in " + hometown + ", " + nationality + ". " + parentStr, birthYear);

    initRivals();
    switchTo(ScreenID.CAREER);
  }

  // ── Rival system ──────────────────────────────────────────
  void initRivals() {
    state.rivals = new ArrayList<Rival>();
    int[] indices = {2, 5, 9};
    for (int idx : indices) {
      if (idx < ranking.worldPlayers.size()) {
        WorldPlayer wp = ranking.worldPlayers.get(idx);
        Rival r = new Rival(wp.name, wp.nationality, wp.ranking, state.currentYear);
        state.rivals.add(r);
      }
    }
  }

  void checkRivalTracking(ArrayList<MatchResult> results, Player p) {
    if (state.rivals == null) return;
    for (MatchResult mr : results) {
      for (Rival rival : state.rivals) {
        String oppName = mr.playerWon ? mr.loserName : mr.winnerName;
        if (rival.name.equals(oppName)) {
          if (mr.playerWon) {
            rival.h2hWins++;
            rival.trashTalkLevel = max(0, rival.trashTalkLevel - 15);
          } else {
            rival.h2hLosses++;
            rival.trashTalkLevel = min(100, rival.trashTalkLevel + 20);
            if (rival.trashTalkLevel > 60 && state.pendingEvent == null) {
              state.pendingEvent = buildRivalTrashTalkEvent(rival);
            }
          }
        }
      }
    }
  }

  GameEvent buildRivalTrashTalkEvent(Rival r) {
    GameEvent e = new GameEvent();
    e.headline    = r.name + " Calls You Out";
    e.description = r.name + " told the press the H2H record (" + r.h2hLosses + "-" + r.h2hWins +
                    " against you) doesn't lie. It's all over social media. How do you respond?";
    e.choices     = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Respond publicly";
    c1.confidenceEffect = 12; c1.reputationEffect = -5;
    c1.description = "Fire back. Fans love the drama."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Let racket speak";
    c2.rankingPointsEffect = 30; c2.mentalEffect = 5;
    c2.description = "Train harder. Beat them on the court. Say nothing."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Ignore it";
    c3.confidenceEffect = 5; c3.fatigueEffect = -5;
    c3.description = "Rise above it. Focus on your game."; e.choices.add(c3);
    return e;
  }
}

// Game State
class GameState {
  Player    player;
  int       currentYear = 2018;
  GameEvent pendingEvent        = null;
  TournamentRun lastTournamentRun = null;
  String    lastEvent           = "";
  boolean   retirementEligible  = false;
  ArrayList<String> decisionLog = new ArrayList<String>();
  Sport       currentSport  = Sport.TENNIS;
  SportConfig sportConfig   = null;
  LifePhase   lifePhase     = LifePhase.CHILDHOOD;
  boolean     inUniversity  = false;
  String      pendingMatchAction   = "";
  int         proCareerStartYear   = -1;
  ArrayList<Rival> rivals          = new ArrayList<Rival>();
}

// Ranking System
class RankingSystem {
  ArrayList<WorldPlayer> worldPlayers = new ArrayList<WorldPlayer>();

  void generateWorldPlayers(int count, SportConfig cfg) {
    ArrayList<String> first = new ArrayList<String>();
    ArrayList<String> last  = new ArrayList<String>();
    ArrayList<String> nat   = new ArrayList<String>();

    for (int i = 0; i < cfg.oppFirst.length; i++) {
      first.add(cfg.oppFirst[i]);
      last.add(cfg.oppLast[i]);
      nat.add(cfg.oppNations[i]);
    }
    String[] gFirst = {"Marco","Alexei","Diego","Paulo","Kim","Chen","Ivan","Raj","Omar","Luis",
                       "Andre","Victor","Felix","Emil","Hans","Pablo","Jorge","Finn","Lars","Sven",
                       "Bruno","Gabriel","Carlos","Antonio","Pedro","Nicolas","Adrian","Max","Ben","Jake",
                       "Luca","Tomas","Yuki","Hiro","Wei","Soo","Ali","Amir","Kemal","Thiago",
                       "Damien","Remi","Pierre","Franco","Sergio"};
    String[] gLast  = {"Romano","Petrov","Cruz","Santos","Park","Wang","Novak","Patel","Hassan","Gomez",
                       "Silva","Costa","Weber","Schulz","Hansen","Moreno","Rivera","Berg","Andersen","Larsson",
                       "Ferrari","Oliveira","Rodriguez","Fernandez","Martinez","Torres","Reyes","Fischer","Muller","Wilson",
                       "Rossi","Dvorak","Tanaka","Sato","Chen","Kim","Sahin","Nazari","Yilmaz","Cavalcanti",
                       "Durand","Laurent","Dupont","Russo","Esposito"};
    String[] gNat   = {"Italy","Russia","Mexico","Brazil","Korea","China","Serbia","India","Egypt","Spain",
                       "Brazil","Portugal","Germany","Germany","Denmark","Spain","Mexico","Norway","Denmark","Sweden",
                       "Italy","Brazil","Argentina","Spain","Spain","Spain","Mexico","Germany","Germany","USA",
                       "Italy","Czech","Japan","Japan","China","Korea","Turkey","Iran","Turkey","Brazil",
                       "France","France","France","Italy","Italy"};

    for (int i = 0; i < gFirst.length && first.size() < 50; i++) {
      first.add(gFirst[i]); last.add(gLast[i]); nat.add(gNat[i]);
    }

    worldPlayers.clear();
    int n = min(count, first.size());
    for (int i = 0; i < n; i++) {
      WorldPlayer wp = new WorldPlayer();
      wp.name        = first.get(i) + " " + last.get(i);
      wp.nationality = nat.get(i);
      wp.ranking     = i + 1;
      wp.points      = (int)map(i, 0, n, 11000, 1500) + (int)random(-200, 200);
      worldPlayers.add(wp);
    }
  }
}

class WorldPlayer {
  String name, nationality;
  int    ranking, points;
}
