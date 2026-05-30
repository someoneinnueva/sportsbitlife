// ============================================================
// PLAYER DOMAIN — core player and sub-classes
// ============================================================
class Player {
  String name;
  String nationality;
  int    birthYear;
  PlayStyle playStyle;
  String dominantHand;

  PlayerAttributes  baseAttributes;
  PlayerForm        form;
  PlayerHealth      health;
  PlayerCareer      career;
  PlayerPersonality personality;
  PlayerProgression progression;
  Family            family;
  PlayerFinances    finances;
  Coach[]           coaches;
  Reputation        reputation;

  // Life-sim systems
  MentalHealthSystem mentalHealth;
  SocialMediaProfile socialMedia;
  AgentContract      agent;
  AddictionLevel     addiction           = AddictionLevel.NONE;
  int                addictionWeeks      = 0;
  LegalStatus        legalStatus         = LegalStatus.CLEAN;
  String             activeLegalCase     = "";
  int                legalWeeksRemaining = 0;
  float              legalFineAmount     = 0;
  ArrayList<Award>   awards;
  ArrayList<String>  lifeEvents;
  float              popularity          = 10;
  int                weeksInRelationship = 0;
  boolean            hasSocialManager    = false;
  String             hometown            = "";
  Parents            parents;

  // BitLife-style core life stats (0-100)
  float wellbeing  = 90;
  float happiness  = 75;
  float smarts     = 50;
  float looks      = 50;

  Player(String name, String nationality, int birthYear,
         PlayStyle style, String hand) {
    this.name         = name;
    this.nationality  = nationality;
    this.birthYear    = birthYear;
    this.playStyle    = style;
    this.dominantHand = hand;

    baseAttributes = new PlayerAttributes(60, 58, 55, 45, 65, 70, 55);
    form           = new PlayerForm();
    health         = new PlayerHealth();
    career         = new PlayerCareer();
    personality    = new PlayerPersonality();
    progression    = new PlayerProgression(this);
    family         = new Family();
    finances       = new PlayerFinances();
    coaches        = new Coach[0];
    reputation     = new Reputation();
    mentalHealth   = new MentalHealthSystem();
    socialMedia    = new SocialMediaProfile();
    agent          = new AgentContract(AgentTier.NO_AGENT);
    awards         = new ArrayList<Award>();
    lifeEvents     = new ArrayList<String>();
    parents        = new Parents();

    wellbeing = constrain(75 + random(-10, 20), 0, 100);
    happiness = constrain(65 + random(-10, 25), 0, 100);
    smarts    = constrain(random(30, 90),        0, 100);
    looks     = constrain(random(30, 90),        0, 100);
  }

  int age(int currentYear) { return currentYear - birthYear; }

  PlayerAttributes effectiveAttributes() {
    float formMult   = map(form.confidence, 0, 100, 0.85, 1.15);
    float fatMult    = map(form.fatigue,    0, 100, 1.05, 0.80);
    float mentalMult = mentalHealth.performanceModifier();
    float addMult    = addictionPenalty();
    float mult       = formMult * fatMult * mentalMult * addMult;
    return new PlayerAttributes(
      (int)(baseAttributes.serve    * mult),
      (int)(baseAttributes.forehand * mult),
      (int)(baseAttributes.backhand * mult),
      (int)(baseAttributes.volley   * mult),
      (int)(baseAttributes.speed    * mult),
      (int)(baseAttributes.stamina  * mult),
      (int)(baseAttributes.mental   * mult * map(form.confidence, 0, 100, 0.9, 1.1))
    );
  }

  float addictionPenalty() {
    switch (addiction) {
      case MILD:     return 0.96;
      case MODERATE: return 0.88;
      case SEVERE:   return 0.75;
      default:       return 1.0;
    }
  }

  CareerPhase currentPhase(int currentYear) {
    int a = age(currentYear);
    if (a < 18) return CareerPhase.JUNIOR;
    if (a < 22) return CareerPhase.RISING;
    if (a < 28) return CareerPhase.PRIME;
    if (a < 32) return CareerPhase.VETERAN;
    return CareerPhase.DECLINING;
  }

  void addLifeEvent(String entry, int year) {
    lifeEvents.add(0, "[" + year + "] " + entry);
    if (lifeEvents.size() > 60) lifeEvents.remove(lifeEvents.size() - 1);
  }

  String toPromptString(int currentYear, int week) {
    PlayerAttributes eff = effectiveAttributes();
    return "Player: " + name +
      " | Age: "              + age(currentYear) +
      " | Nation: "           + nationality +
      " | Style: "            + playStyle +
      " | Ranking: #"         + career.worldRanking +
      " | Phase: "            + currentPhase(currentYear) +
      " | Serve: "            + eff.serve +
      " | Forehand: "         + eff.forehand +
      " | Backhand: "         + eff.backhand +
      " | Speed: "            + eff.speed +
      " | Stamina: "          + eff.stamina +
      " | Mental: "           + eff.mental +
      " | Fatigue: "          + (int)form.fatigue    + "/100" +
      " | Confidence: "       + (int)form.confidence + "/100" +
      " | MentalState: "      + mentalHealth.stateLabel() +
      " | Injury: "           + health.status +
      " | GrandSlams: "       + career.grandSlamTitles +
      " | Titles: "           + career.titlesWon +
      " | Week: "             + week +
      " | Savings: $"         + (int)finances.savings +
      " | NetWorth: $"        + (int)finances.netWorth() +
      " | Lifestyle: "        + finances.lifestyleLabel() +
      " | Agent: "            + agent.tierLabel() +
      " | Followers: "        + socialMedia.followersLabel() +
      " | Addiction: "        + addiction +
      " | LegalStatus: "      + legalStatus +
      " | RelStatus: "        + family.statusDisplay() +
      " | Kids: "             + family.children.size() +
      " | Hometown: "          + hometown +
      " | Mom: "              + (parents != null ? parents.mom.name + "(" + (int)parents.mom.relationship + ")" : "N/A") +
      " | Dad: "              + (parents != null ? parents.dad.name + "(" + (int)parents.dad.relationship + ")" : "N/A") +
      " | FamilyHappiness: "  + (int)family.familyHappiness + "/100" +
      " | Wellbeing: "        + (int)wellbeing + "/100" +
      " | Happiness: "        + (int)happiness + "/100" +
      " | Smarts: "           + (int)smarts    + "/100" +
      " | Looks: "            + (int)looks     + "/100";
  }
}

// Attributes
class PlayerAttributes {
  int serve, forehand, backhand, volley, speed, stamina, mental;
  PlayerAttributes(int sv, int fh, int bh, int vo, int sp, int st, int mn) {
    serve = sv; forehand = fh; backhand = bh; volley = vo;
    speed = sp; stamina = st; mental = mn;
  }
}

// Form
class PlayerForm {
  float confidence     = 60;
  float fatigue        = 20;
  float momentum       = 50;
  float matchSharpness = 50;

  void applyMatchResult(boolean won, boolean wasClose) {
    if (won) {
      confidence     = min(100, confidence + (wasClose ? 8 : 5));
      momentum       = min(100, momentum + 6);
      matchSharpness = min(100, matchSharpness + 4);
    } else {
      confidence = max(0, confidence - (wasClose ? 3 : 8));
      momentum   = max(0, momentum - 5);
    }
    fatigue = min(100, fatigue + random(8, 18));
  }

  void applyRestWeek() {
    fatigue        = max(0, fatigue - random(15, 25));
    matchSharpness = max(0, matchSharpness - 5);
    momentum       = max(0, momentum - 3);
  }

  void applyTrainingWeek(float intensity) {
    fatigue    = min(100, fatigue + intensity * 0.4);
    confidence = min(100, confidence + 2);
  }
}

// Health
class PlayerHealth {
  InjuryStatus status         = InjuryStatus.HEALTHY;
  String       injuredPart    = "";
  int          weeksRemaining = 0;

  float injuryRisk(PlayerForm form, float intensity) {
    float base = 0.03;
    base += map(form.fatigue, 0, 100, 0, 0.12);
    base += intensity * 0.05;
    return base;
  }

  void advanceWeek() {
    if (weeksRemaining > 0) {
      weeksRemaining--;
      if (weeksRemaining == 0) {
        status      = InjuryStatus.HEALTHY;
        injuredPart = "";
      }
    }
  }

  boolean isAvailable() {
    return status == InjuryStatus.HEALTHY || status == InjuryStatus.DAY_TO_DAY;
  }

  void applyInjury(String part, int weeks) {
    injuredPart    = part;
    weeksRemaining = weeks;
    if (weeks <= 1)      status = InjuryStatus.DAY_TO_DAY;
    else if (weeks <= 6) status = InjuryStatus.OUT_WEEKS;
    else                 status = InjuryStatus.OUT_MONTHS;
  }
}

// Career stats
class PlayerCareer {
  int   worldRanking      = 250;
  int   rankingPoints     = 0;
  int   titlesWon         = 0;
  int   grandSlamTitles   = 0;
  int   weeksAtNumberOne  = 0;
  float prizeMoney        = 0;
  ArrayList<String>      careerHistory = new ArrayList<String>();
  ArrayList<MatchResult> recentResults = new ArrayList<MatchResult>();

  void addPoints(int pts) {
    rankingPoints += pts;
    rankingPoints = max(0, rankingPoints);
  }

  void recordTitle(boolean isGrandSlam, float prize) {
    titlesWon++;
    prizeMoney += prize;
    if (isGrandSlam) grandSlamTitles++;
  }

  void addHistory(String entry) { careerHistory.add(entry); }

  void addResult(MatchResult r) {
    recentResults.add(0, r);
    if (recentResults.size() > 10) recentResults.remove(recentResults.size() - 1);
  }
}

// Personality
class PlayerPersonality {
  float clutchFactor  = 50 + random(-15, 15);
  float aggression    = 50 + random(-15, 15);
  float consistency   = 50 + random(-15, 15);
  float adaptability  = 50 + random(-15, 15);
  float determination = 50 + random(-15, 15);
}

// Progression
class PlayerProgression {
  Player player;
  PlayerProgression(Player p) { player = p; }

  int peakAge() {
    switch (player.playStyle) {
      case SERVE_VOLLEY:         return 25;
      case AGGRESSIVE_BASELINER: return 26;
      case COUNTER_PUNCHER:      return 28;
      default:                   return 27;
    }
  }

  void applyTrainingGains(TrainingSession session) {
    PlayerAttributes a = player.baseAttributes;
    TrainingOutcome  o = session.projectedGains();
    a.serve    = min(99, a.serve    + o.serveGain);
    a.forehand = min(99, a.forehand + o.forehandGain);
    a.backhand = min(99, a.backhand + o.backhandGain);
    a.speed    = min(99, a.speed    + o.speedGain);
    a.stamina  = min(99, a.stamina  + o.staminaGain);
    a.mental   = min(99, a.mental   + o.mentalGain);
    player.form.fatigue = min(100, player.form.fatigue + o.fatigueAdded);
  }

  void applyAgingDecay(int currentYear) {
    int age  = player.age(currentYear);
    int peak = peakAge();
    if (age > peak) {
      float decay = 0.003 * (age - peak);
      PlayerAttributes a = player.baseAttributes;
      a.speed   = max(30, (int)(a.speed   - decay * 100));
      a.stamina = max(30, (int)(a.stamina - decay * 80));
      a.serve   = max(30, (int)(a.serve   - decay * 40));
    }
  }
}

// Family
class Family {
  Spouse               spouse          = null;
  ArrayList<Child>     children        = new ArrayList<Child>();
  float                familyHappiness = 70;
  float                travelTolerance = 80;
  RelationshipStatus   status          = RelationshipStatus.SINGLE;

  void advanceYear(float weeksAway) {
    if (spouse != null) {
      if (weeksAway > 30) {
        familyHappiness   = max(0, familyHappiness - 10);
        spouse.resentment = min(100, spouse.resentment + 8);
      } else {
        familyHappiness   = min(100, familyHappiness + 5);
        spouse.resentment = max(0, spouse.resentment - 3);
      }
    }
    for (Child c : children) {
      c.age++;
      c.happiness = constrain(c.happiness + (weeksAway > 30 ? -5 : 3), 0, 100);
    }
  }

  String partnerName()   { return spouse != null ? spouse.name : ""; }

  String statusDisplay() {
    if (spouse != null) {
      switch (status) {
        case MARRIED:   return "Married to " + spouse.name;
        case ENGAGED:   return "Engaged to " + spouse.name;
        case DATING:    return "Dating " + spouse.name;
        case SEPARATED: return "Separated";
        case DIVORCED:  return "Divorced";
        default:        return "With " + spouse.name;
      }
    }
    return "Single";
  }
}

class Spouse {
  String name;
  float  supportLevel = 70 + random(-20, 20);
  float  resentment   = 10;
  Spouse(String n) { name = n; }
}

class Child {
  String name;
  int    age;
  float  happiness = 80;
  Child(String n, int a) { name = n; age = a; }
}

// Coach
class Coach {
  String    name;
  CoachType type;
  int       skill      = 60;
  float     salary     = 50_000;
  float     coachBond  = 50;

  Coach(String n, CoachType t, int s) {
    name  = n;
    type  = t;
    skill = s;
  }
}

// Finances (extended)
class PlayerFinances {
  float         savings         = 10_000;
  float         weeklyExpenses  = 500;
  LifestyleTier lifestyle       = LifestyleTier.FRUGAL;
  ArrayList<String> endorsements     = new ArrayList<String>();
  float endorsementIncome       = 0;

  ArrayList<OwnedProperty>   properties  = new ArrayList<OwnedProperty>();
  ArrayList<OwnedVehicle>    vehicles    = new ArrayList<OwnedVehicle>();
  ArrayList<OwnedInvestment> investments = new ArrayList<OwnedInvestment>();
  ArrayList<OwnedBusiness>   businesses  = new ArrayList<OwnedBusiness>();
  float totalDebt = 0;

  float weeklyExpensesForTier(LifestyleTier t) {
    switch (t) {
      case FRUGAL:      return 500;
      case COMFORTABLE: return 2_500;
      case LUXURY:      return 7_500;
      case LAVISH:      return 20_000;
      default:          return 2_500;
    }
  }

  void setLifestyle(LifestyleTier t) {
    lifestyle      = t;
    weeklyExpenses = weeklyExpensesForTier(t);
  }

  void addEndorsement(String name, float weeklyValue) {
    endorsements.add(name + " ($" + (int)weeklyValue + "/wk)");
    endorsementIncome += weeklyValue;
  }

  float endorsementIncomeForRank(int rank) {
    if (rank <=   5) return 18_000;
    if (rank <=  15) return  8_000;
    if (rank <=  50) return  3_000;
    if (rank <= 100) return  1_200;
    if (rank <= 200) return    300;
    return 80;
  }

  String lifestyleLabel() {
    switch (lifestyle) {
      case FRUGAL:      return "Frugal";
      case COMFORTABLE: return "Comfortable";
      case LUXURY:      return "Luxury";
      case LAVISH:      return "Lavish";
      default:          return "Comfortable";
    }
  }

  float totalInvestmentValue() {
    float v = 0;
    for (OwnedInvestment i : investments) v += i.currentValue;
    return v;
  }

  float totalPropertyValue() {
    float v = 0;
    for (OwnedProperty p : properties) v += p.currentValue;
    return v;
  }

  float totalWeeklyPropertyCost() {
    float c = 0;
    for (OwnedProperty p : properties) c += p.weeklyTotal();
    return c;
  }

  float totalWeeklyVehicleCost() {
    float c = 0;
    for (OwnedVehicle v : vehicles) c += v.weeklyUpkeep;
    return c;
  }

  float totalWeeklyBusinessRevenue() {
    float r = 0;
    for (OwnedBusiness b : businesses) r += b.weeklyProfit();
    return r;
  }

  float netWorth() {
    return savings + totalInvestmentValue() + totalPropertyValue();
  }
}

// Rivalry
class Rivalry {
  Player playerOne, playerTwo;
  int wins = 0, losses = 0;
  ArrayList<String> matchHistory = new ArrayList<String>();

  Rivalry(Player p1, Player p2) { playerOne = p1; playerTwo = p2; }

  float psychologicalEdge() {
    int total = wins + losses;
    if (total == 0) return 0;
    return map(wins, 0, total, -10, 10);
  }
}

// Reputation
class Reputation {
  float publicImage       = 60;
  float mediaRelationship = 60;
  float leagueReputation  = 60;

  void applyPositive(float amount) {
    publicImage       = constrain(publicImage       + amount,        0, 100);
    mediaRelationship = constrain(mediaRelationship + amount * 0.7,  0, 100);
    leagueReputation  = constrain(leagueReputation  + amount * 0.5,  0, 100);
  }

  void applyNegative(float amount) {
    publicImage       = constrain(publicImage       - amount,        0, 100);
    mediaRelationship = constrain(mediaRelationship - amount * 1.2,  0, 100);
  }
}

// Parents
class Parents {
  Parent mom;
  Parent dad;

  Parents() {
    String[] momNames = {"Maria","Elena","Sofia","Ana","Isabel","Carmen","Laura","Rosa","Julia","Paula",
                         "Sarah","Jennifer","Emma","Olivia","Fatima","Yuki","Mei","Priya","Aisha","Linda"};
    String[] dadNames = {"Carlos","Miguel","Antonio","David","Jorge","Luis","Roberto","Pedro","Fernando","Ricardo",
                         "James","Robert","William","Michael","Omar","Hiroshi","Wei","Raj","Ahmed","John"};
    mom = new Parent(momNames[(int)random(momNames.length)]);
    dad = new Parent(dadNames[(int)random(dadNames.length)]);
  }
}

class Parent {
  String  name;
  float   relationship;
  boolean alive = true;

  Parent(String n) {
    name         = n;
    relationship = constrain(70 + random(-20, 20), 30, 100);
  }

  String relationshipLabel() {
    if (relationship >= 85) return "Very Close";
    if (relationship >= 65) return "Good";
    if (relationship >= 45) return "Distant";
    return "Estranged";
  }

  color relationshipColor() {
    if (relationship >= 75) return color(55, 185, 95);
    if (relationship >= 50) return color(80, 140, 220);
    if (relationship >= 30) return color(220, 160, 40);
    return color(210, 65, 65);
  }
}

// ============================================================
// RIVAL SYSTEM
// ============================================================
interface Trackable {
  String getSummaryLine();
  boolean isActive();
}

class Rival implements Trackable {
  String name, nationality;
  int    ranking, h2hWins, h2hLosses, yearDebuted;
  float  trashTalkLevel; // 0-100
  boolean active;        // renamed from isActive to avoid method-name conflict

  Rival(String n, String nat, int rank, int year) {
    name = n; nationality = nat; ranking = rank; yearDebuted = year;
    trashTalkLevel = 30; active = true;
  }

  String getSummaryLine() { return name + " H2H: " + h2hWins + "-" + h2hLosses; }
  boolean isActive() { return active; }

  String verdict() {
    if (h2hWins > h2hLosses)   return "Dominated";
    if (h2hLosses > h2hWins)   return "They had the edge";
    return "Even";
  }
}
