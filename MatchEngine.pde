// ============================================================
// MATCH ENGINE & TOURNAMENT
// ============================================================

class MatchResult {
  String winnerName, loserName;
  String score;
  int   rankingPointsAwarded;
  float prizeMoneyAwarded;
  boolean playerWon;
  String tournamentName;
  RoundName round;

  MatchResult(String w, String l, String sc, int pts, float prize,
              boolean pw, String tn, RoundName r) {
    winnerName           = w;
    loserName            = l;
    score                = sc;
    rankingPointsAwarded = pts;
    prizeMoneyAwarded    = prize;
    playerWon            = pw;
    tournamentName       = tn;
    round                = r;
  }
}

class MatchEngine {
  MatchResult simulate(Player player, OpponentProfile opp,
                       Surface surface, TournamentTier tier,
                       RoundName round, int currentYear, Sport sport) {
    PlayerAttributes eff = player.effectiveAttributes();

    float surfBonus = (sport == Sport.TENNIS) ? getSurfaceBonus(player.playStyle, surface) : 0;
    float playerRating = (eff.serve    * 0.20 + eff.forehand * 0.18 +
                          eff.backhand * 0.15 + eff.speed    * 0.15 +
                          eff.stamina  * 0.12 + eff.mental   * 0.20) + surfBonus;

    float clutch = player.personality.clutchFactor;
    playerRating += map(clutch, 0, 100, -5, 5);
    playerRating += player.career.worldRanking < opp.ranking ? 2 : -1;

    float diff    = playerRating - opp.rating;
    float winProb = 1.0 / (1.0 + exp(-diff / 15.0));
    winProb       = constrain(winProb + random(-0.08, 0.08), 0.05, 0.95);

    boolean playerWon = random(1) < winProb;
    String score = generateScore(playerWon, sport);

    int   pts   = getPoints(tier, round, playerWon);
    float prize = getPrize(tier, round, playerWon);

    String winner = playerWon ? player.name : opp.name;
    String loser  = playerWon ? opp.name    : player.name;

    return new MatchResult(winner, loser, score, pts, prize, playerWon, "", round);
  }

  float getSurfaceBonus(PlayStyle style, Surface surf) {
    if (style == PlayStyle.SERVE_VOLLEY         && surf == Surface.GRASS) return 6;
    if (style == PlayStyle.AGGRESSIVE_BASELINER && surf == Surface.HARD)  return 4;
    if (style == PlayStyle.COUNTER_PUNCHER      && surf == Surface.CLAY)  return 5;
    if (style == PlayStyle.ALL_COURT) return 2;
    return 0;
  }

  String generateScore(boolean won, Sport sport) {
    switch (sport) {
      case BASKETBALL: {
        int myPts  = won ? (int)random(95, 128) : (int)random(82, 108);
        int oppPts = won ? myPts - (int)random(3, 22) : myPts + (int)random(3, 22);
        return myPts + "-" + oppPts;
      }
      case SOCCER: {
        int myG  = won ? (int)random(1, 5) : (int)random(0, 2);
        int oppG = won ? max(0, myG - (int)random(1, 3)) : myG + (int)random(1, 3);
        return myG + "-" + oppG;
      }
      case GOLF: {
        int score = won ? -(int)random(5, 18) : (int)random(0, 8);
        return (score <= 0 ? "" : "+") + score;
      }
      case BOXING: {
        if (won) {
          float r = random(1);
          if (r < 0.25) return "KO R" + (int)random(1, 8);
          if (r < 0.45) return "TKO R" + (int)random(3, 10);
          if (r < 0.75) return "UD 12";
          return "MD 12";
        } else {
          float r = random(1);
          if (r < 0.20) return "KO'd R" + (int)random(2, 9);
          if (r < 0.35) return "TKO'd R" + (int)random(4, 11);
          return "UD Loss 12";
        }
      }
      default: { // TENNIS
        ArrayList<String> sets = new ArrayList<String>();
        if (won) {
          sets.add(random(1) > 0.4 ? "6-" + (int)random(0, 5) : "7-6");
          sets.add(random(1) > 0.4 ? "6-" + (int)random(0, 5) : "7-6");
          if (random(1) > 0.6) sets.add(0, (int)random(3, 6) + "-6");
        } else {
          sets.add(random(1) > 0.4 ? (int)random(0, 5) + "-6" : "6-7");
          sets.add(random(1) > 0.4 ? (int)random(0, 5) + "-6" : "6-7");
          if (random(1) > 0.6) sets.add(0, "6-" + (int)random(3, 6));
        }
        String out = "";
        for (int i = 0; i < sets.size(); i++) {
          out += sets.get(i);
          if (i < sets.size() - 1) out += " ";
        }
        return out;
      }
    }
  }

  int getPoints(TournamentTier tier, RoundName round, boolean won) {
    int base = 0;
    switch (tier) {
      case GRAND_SLAM:   base = won ? 2000 : 0; break;
      case MASTERS_1000: base = won ? 1000 : 0; break;
      case ATP_500:      base = won ? 500  : 0; break;
      case ATP_250:      base = won ? 250  : 0; break;
      case CHALLENGER:   base = won ? 125  : 0; break;
    }
    switch (round) {
      case F:   return (int)(base * 1.0);
      case SF:  return (int)(base * 0.6);
      case QF:  return (int)(base * 0.36);
      case R16: return (int)(base * 0.18);
      case R32: return (int)(base * 0.09);
      case R64: return (int)(base * 0.045);
      default:  return (int)(base * 0.02);
    }
  }

  float getPrize(TournamentTier tier, RoundName round, boolean won) {
    float base = 0;
    switch (tier) {
      case GRAND_SLAM:   base = won ? 2_000_000 : 0; break;
      case MASTERS_1000: base = won ? 1_000_000 : 0; break;
      case ATP_500:      base = won ?   500_000 : 0; break;
      case ATP_250:      base = won ?   250_000 : 0; break;
      case CHALLENGER:   base = won ?    50_000 : 0; break;
    }
    switch (round) {
      case F:   return base * 1.0;
      case SF:  return base * 0.5;
      case QF:  return base * 0.25;
      case R16: return base * 0.12;
      default:  return base * 0.05;
    }
  }
}

// Opponent Profile
class OpponentProfile {
  String name;
  String nationality;
  int    ranking;
  float  rating;
  PlayStyle style;

  OpponentProfile(String n, String nat, int rank) {
    name        = n;
    nationality = nat;
    ranking     = rank;
    rating      = map(rank, 1, 500, 88, 42) + random(-5, 5);
    PlayStyle[] styles = PlayStyle.values();
    style = styles[(int)random(styles.length)];
  }
}

// Tournament
class Tournament {
  String name;
  TournamentTier tier;
  Surface        surface;
  int            drawSize;
  float          prizeMoney;
  int            week;
  String         venueLabel = "";
  Sport          sport      = Sport.TENNIS;

  Tournament(String n, TournamentTier t, Surface s, int draw, float prize, int wk) {
    name = n; tier = t; surface = s; drawSize = draw; prizeMoney = prize; week = wk;
  }

  TournamentRun simulate(Player player, MatchEngine eng, int currentYear) {
    RoundName[] rounds = getRounds();
    TournamentRun run = new TournamentRun(this);
    SportConfig cfg = getSportConfig(sport);

    for (RoundName round : rounds) {
      int oppRank = generateOpponentRank(round);
      OpponentProfile opp = generateOpponent(oppRank, cfg);
      MatchResult result = eng.simulate(player, opp, surface, tier, round, currentYear, sport);
      result.tournamentName = name;
      run.addResult(result);
      if (!result.playerWon) break;
    }
    run.finalRound = run.results.get(run.results.size() - 1).round;
    run.won        = run.results.get(run.results.size() - 1).playerWon &&
                     run.finalRound == RoundName.F;
    return run;
  }

  RoundName[] getRounds() {
    if (drawSize >= 128) return new RoundName[]{ RoundName.R128, RoundName.R64, RoundName.R32, RoundName.R16, RoundName.QF, RoundName.SF, RoundName.F };
    if (drawSize >=  64) return new RoundName[]{               RoundName.R64, RoundName.R32, RoundName.R16, RoundName.QF, RoundName.SF, RoundName.F };
    if (drawSize >=  32) return new RoundName[]{                              RoundName.R32, RoundName.R16, RoundName.QF, RoundName.SF, RoundName.F };
    return                       new RoundName[]{                                             RoundName.R16, RoundName.QF, RoundName.SF, RoundName.F };
  }

  int generateOpponentRank(RoundName round) {
    switch (round) {
      case F:   return (int)random(1, 5);
      case SF:  return (int)random(3, 15);
      case QF:  return (int)random(8, 30);
      case R16: return (int)random(20, 60);
      case R32: return (int)random(40, 100);
      default:  return (int)random(80, 200);
    }
  }

  OpponentProfile generateOpponent(int rank, SportConfig cfg) {
    int idx = (int)random(cfg.oppFirst.length);
    return new OpponentProfile(cfg.oppFirst[idx] + " " + cfg.oppLast[idx], cfg.oppNations[idx], rank);
  }
}

class TournamentRun {
  Tournament tournament;
  ArrayList<MatchResult> results = new ArrayList<MatchResult>();
  RoundName finalRound;
  boolean won = false;

  TournamentRun(Tournament t) { tournament = t; }

  void addResult(MatchResult r) { results.add(r); }

  int totalPoints() {
    int sum = 0;
    for (MatchResult r : results) sum += r.rankingPointsAwarded;
    return sum;
  }

  float totalPrize() {
    float sum = 0;
    for (MatchResult r : results) sum += r.prizeMoneyAwarded;
    return sum;
  }
}

// Tournament Calendar
class TournamentCalendar {
  ArrayList<Tournament> schedule = new ArrayList<Tournament>();
  Sport sport;

  TournamentCalendar(Sport sport) { this.sport = sport; buildSchedule(sport); }

  void buildSchedule(Sport s) {
    schedule.clear();
    switch (s) {
      case BASKETBALL: buildBasketball(); break;
      case SOCCER:     buildSoccer();     break;
      case GOLF:       buildGolf();       break;
      case BOXING:     buildBoxing();     break;
      default:         buildTennis();     break;
    }
  }

  void buildTennis() {
    add("Australian Open",      TournamentTier.GRAND_SLAM,   Surface.HARD,         128, 2_000_000,  2, "Hard");
    add("Dubai Championships",  TournamentTier.ATP_500,      Surface.HARD,          32,   500_000,  5, "Hard");
    add("Indian Wells Masters", TournamentTier.MASTERS_1000, Surface.HARD,          96, 1_000_000,  9, "Hard");
    add("Miami Open",           TournamentTier.MASTERS_1000, Surface.HARD,          96, 1_000_000, 12, "Hard");
    add("Monte-Carlo Masters",  TournamentTier.MASTERS_1000, Surface.CLAY,          64, 1_000_000, 15, "Clay");
    add("Barcelona Open",       TournamentTier.ATP_500,      Surface.CLAY,          48,   500_000, 17, "Clay");
    add("Madrid Open",          TournamentTier.MASTERS_1000, Surface.CLAY,          96, 1_000_000, 19, "Clay");
    add("Italian Open",         TournamentTier.MASTERS_1000, Surface.CLAY,          96, 1_000_000, 21, "Clay");
    add("Roland Garros",        TournamentTier.GRAND_SLAM,   Surface.CLAY,         128, 2_000_000, 23, "Clay");
    add("Queen's Club",         TournamentTier.ATP_500,      Surface.GRASS,         48,   500_000, 25, "Grass");
    add("Wimbledon",            TournamentTier.GRAND_SLAM,   Surface.GRASS,        128, 2_000_000, 27, "Grass");
    add("US Open Series",       TournamentTier.MASTERS_1000, Surface.HARD,          64, 1_000_000, 33, "Hard");
    add("US Open",              TournamentTier.GRAND_SLAM,   Surface.HARD,         128, 2_000_000, 35, "Hard");
    add("Shanghai Masters",     TournamentTier.MASTERS_1000, Surface.HARD,          96, 1_000_000, 41, "Hard");
    add("Paris Masters",        TournamentTier.MASTERS_1000, Surface.INDOOR_HARD,   64, 1_000_000, 46, "Indoor");
    add("ATP Finals",           TournamentTier.MASTERS_1000, Surface.INDOOR_HARD,    8, 1_500_000, 49, "Indoor");
  }

  void buildBasketball() {
    add("NBA Finals",             TournamentTier.GRAND_SLAM,   Surface.HARD,  8, 2_000_000, 22, "Finals");
    add("Conference Finals",      TournamentTier.MASTERS_1000, Surface.HARD,  8, 1_000_000, 19, "Conf. Finals");
    add("Conference Semis",       TournamentTier.ATP_500,      Surface.HARD, 16,   600_000, 16, "Conf. Semis");
    add("First Round Playoffs",   TournamentTier.ATP_250,      Surface.HARD, 16,   300_000, 14, "Playoffs R1");
    add("All-Star Weekend",       TournamentTier.ATP_500,      Surface.HARD, 32,   400_000,  5, "All-Star");
    add("Opening Week",           TournamentTier.CHALLENGER,   Surface.HARD, 32,    50_000,  1, "Regular Season");
    add("Winter Classic",         TournamentTier.ATP_250,      Surface.HARD, 32,   200_000,  8, "Regular Season");
    add("February Stretch",       TournamentTier.CHALLENGER,   Surface.HARD, 32,    50_000, 11, "Regular Season");
    add("March Run",              TournamentTier.ATP_250,      Surface.HARD, 32,   150_000, 28, "Regular Season");
    add("Play-In Tournament",     TournamentTier.ATP_500,      Surface.HARD, 16,   300_000, 35, "Play-In");
    add("Summer League",          TournamentTier.CHALLENGER,   Surface.HARD, 32,    30_000, 40, "Summer League");
    add("Pre-Season",             TournamentTier.CHALLENGER,   Surface.HARD, 32,    10_000, 45, "Pre-Season");
  }

  void buildSoccer() {
    add("UEFA Champions League",  TournamentTier.GRAND_SLAM,   Surface.GRASS, 32, 2_500_000, 21, "European");
    add("FIFA World Cup",         TournamentTier.GRAND_SLAM,   Surface.GRASS, 32, 3_000_000, 27, "International");
    add("Premier League Title",   TournamentTier.MASTERS_1000, Surface.GRASS, 20, 1_200_000, 38, "League");
    add("FA Cup Final",           TournamentTier.ATP_500,      Surface.GRASS, 32,   500_000, 18, "Cup");
    add("Europa League",          TournamentTier.ATP_500,      Surface.GRASS, 32,   600_000, 23, "European");
    add("Carabao Cup",            TournamentTier.ATP_250,      Surface.GRASS, 16,   200_000,  6, "Cup");
    add("Community Shield",       TournamentTier.ATP_250,      Surface.GRASS,  2,   100_000,  2, "Shield");
    add("Winter Break Return",    TournamentTier.CHALLENGER,   Surface.GRASS,  8,    50_000,  1, "League");
    add("Boxing Day Fixtures",    TournamentTier.CHALLENGER,   Surface.GRASS,  8,    50_000, 12, "League");
    add("Spring Fixtures",        TournamentTier.CHALLENGER,   Surface.GRASS,  8,    80_000, 30, "League");
    add("Euro Championships",     TournamentTier.MASTERS_1000, Surface.GRASS, 24, 1_000_000, 44, "International");
    add("Nations League Final",   TournamentTier.ATP_500,      Surface.GRASS,  4,   400_000, 48, "International");
  }

  void buildGolf() {
    add("The Masters",            TournamentTier.GRAND_SLAM,   Surface.GRASS, 96, 3_000_000, 14, "Augusta");
    add("US Open",                TournamentTier.GRAND_SLAM,   Surface.HARD,  96, 2_500_000, 23, "Parkland");
    add("The Open Championship",  TournamentTier.GRAND_SLAM,   Surface.GRASS, 96, 2_500_000, 29, "Links");
    add("PGA Championship",       TournamentTier.GRAND_SLAM,   Surface.HARD,  96, 2_500_000, 19, "Parkland");
    add("The Players Championship",TournamentTier.MASTERS_1000,Surface.GRASS, 96, 2_000_000, 11, "Stadium");
    add("Genesis Invitational",   TournamentTier.ATP_500,      Surface.GRASS, 64, 1_000_000,  7, "Links");
    add("WGC Match Play",         TournamentTier.ATP_500,      Surface.GRASS, 64, 1_000_000, 11, "Desert");
    add("Arnold Palmer Invit.",   TournamentTier.ATP_500,      Surface.GRASS, 64, 1_000_000,  9, "Parkland");
    add("Memorial Tournament",    TournamentTier.ATP_500,      Surface.GRASS, 64, 1_000_000, 22, "Parkland");
    add("Tour Championship",      TournamentTier.MASTERS_1000, Surface.GRASS, 30, 1_500_000, 36, "Parkland");
    add("Sentry TOC",             TournamentTier.ATP_250,      Surface.GRASS, 48,   400_000,  1, "Mountain");
    add("Farmers Insurance Open", TournamentTier.ATP_250,      Surface.GRASS, 64,   400_000,  4, "Links");
    add("Honda Classic",          TournamentTier.ATP_250,      Surface.GRASS, 64,   300_000, 37, "Parkland");
    add("Shriners Open",          TournamentTier.CHALLENGER,   Surface.GRASS, 64,   200_000, 41, "Desert");
    add("RSM Classic",            TournamentTier.CHALLENGER,   Surface.GRASS, 64,   200_000, 45, "Links");
    add("Hero World Challenge",   TournamentTier.ATP_250,      Surface.GRASS, 20,   350_000, 49, "Island");
  }

  void buildBoxing() {
    add("World Title Fight",      TournamentTier.GRAND_SLAM,   Surface.HARD,   2, 5_000_000,  6, "Arena");
    add("Interim Title Fight",    TournamentTier.MASTERS_1000, Surface.HARD,   2, 2_000_000, 14, "Arena");
    add("Mandatory Defense",      TournamentTier.ATP_500,      Surface.HARD,   2, 1_000_000, 22, "Stadium");
    add("Ranked Contender Bout",  TournamentTier.ATP_250,      Surface.HARD,   2,   400_000, 30, "Casino");
    add("Tune-Up Fight",          TournamentTier.CHALLENGER,   Surface.HARD,   2,   100_000,  3, "Arena");
    add("Spring Main Event",      TournamentTier.ATP_500,      Surface.HARD,   2,   800_000, 10, "Stadium");
    add("Summer Showdown",        TournamentTier.MASTERS_1000, Surface.HARD,   2, 1_500_000, 18, "Stadium");
    add("Fall Classic",           TournamentTier.ATP_250,      Surface.HARD,   2,   500_000, 37, "Casino");
    add("Year-End Spectacular",   TournamentTier.MASTERS_1000, Surface.HARD,   2, 2_000_000, 47, "Arena");
    add("International Bout",     TournamentTier.ATP_500,      Surface.HARD,   2,   700_000, 41, "International");
  }

  void add(String name, TournamentTier tier, Surface surf, int draw, float prize, int week, String venueLabel) {
    Tournament t = new Tournament(name, tier, surf, draw, prize, week);
    t.venueLabel = venueLabel;
    t.sport = sport;
    schedule.add(t);
  }

  Tournament getTournamentForWeek(int week) {
    for (Tournament t : schedule) {
      if (t.week == week) return t;
    }
    return null;
  }

  ArrayList<Tournament> getGrandSlams() {
    ArrayList<Tournament> gs = new ArrayList<Tournament>();
    for (Tournament t : schedule) if (t.tier == TournamentTier.GRAND_SLAM) gs.add(t);
    return gs;
  }
}

// Training
class TrainingSession {
  String type;
  float  intensity;
  TrainingSession(String t, float i) { type = t; intensity = i; }

  TrainingOutcome projectedGains() {
    TrainingOutcome o = new TrainingOutcome();
    float base = intensity * 0.02;
    switch (type) {
      case "SERVE":    o.serveGain    = (int)(base * 2); break;
      case "FITNESS":  o.staminaGain  = (int)(base * 2); o.speedGain = (int)base; break;
      case "MENTAL":   o.mentalGain   = (int)(base * 2); break;
      case "TACTICAL": o.forehandGain = (int) base;       o.backhandGain = (int) base; break;
    }
    o.fatigueAdded = intensity * 0.3;
    return o;
  }
}

class TrainingOutcome {
  int   serveGain    = 0, forehandGain = 0, backhandGain = 0;
  int   speedGain    = 0, staminaGain  = 0, mentalGain   = 0;
  float fatigueAdded = 0;
  boolean injuryOccurred = false;
}
