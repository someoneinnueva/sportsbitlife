// ============================================================
// SPORT CONFIGURATION — per-sport display data
// ============================================================
class SportConfig {
  String   name;
  String   subtitle;
  String[] statNames;
  String[] roleShort;
  String[] roleFull;
  String   rankLabel;
  String   ptsLabel;
  String   titlesLabel;
  String   majorLabel;
  String[] oppFirst;
  String[] oppLast;
  String[] oppNations;

  SportConfig(String name, String subtitle,
              String[] statNames, String[] roleShort, String[] roleFull,
              String rankLabel, String ptsLabel, String titlesLabel, String majorLabel,
              String[] oppFirst, String[] oppLast, String[] oppNations) {
    this.name      = name;
    this.subtitle  = subtitle;
    this.statNames = statNames;
    this.roleShort = roleShort;
    this.roleFull  = roleFull;
    this.rankLabel  = rankLabel;
    this.ptsLabel   = ptsLabel;
    this.titlesLabel = titlesLabel;
    this.majorLabel  = majorLabel;
    this.oppFirst   = oppFirst;
    this.oppLast    = oppLast;
    this.oppNations = oppNations;
  }
}

SportConfig getSportConfig(Sport sport) {
  switch (sport) {

    case BASKETBALL:
      return new SportConfig("Basketball", "NBA CAREER SIMULATOR",
        new String[]{"SHOOTING","DRIBBLING","PASSING","DEFENSE","REBOUNDING","ATHLETICISM","IQ"},
        new String[]{"PG","SG","SF","PF"},
        new String[]{"Point Guard","Shooting Guard","Small Forward","Power Forward"},
        "NBA RANK","CAREER WINS","TITLES","RINGS",
        new String[]{"LeBron","Stephen","Kevin","Giannis","Luka","Nikola","Joel","Jayson","Damian","Kawhi"},
        new String[]{"James","Curry","Durant","Antetokounmpo","Doncic","Jokic","Embiid","Tatum","Lillard","Leonard"},
        new String[]{"USA","USA","USA","Greece","Slovenia","Serbia","Cameroon","USA","USA","Canada"});

    case SOCCER:
      return new SportConfig("Soccer", "FOOTBALL CAREER SIMULATOR",
        new String[]{"PACE","SHOOTING","PASSING","DRIBBLING","PHYSICALITY","STAMINA","VISION"},
        new String[]{"STR","MID","DEF","GK"},
        new String[]{"Striker","Midfielder","Defender","Goalkeeper"},
        "WORLD RANK","CAREER GOALS","TITLES","WORLD CUPS",
        new String[]{"Kylian","Erling","Vinicius","Rodri","Bukayo","Phil","Jude","Pedri","Florian","Leroy"},
        new String[]{"Mbappe","Haaland","Junior","Rodriguez","Saka","Foden","Bellingham","Gonzalez","Wirtz","Sane"},
        new String[]{"France","Norway","Brazil","Spain","England","England","England","Spain","Germany","Germany"});

    case GOLF:
      return new SportConfig("Golf", "PGA TOUR CAREER SIMULATOR",
        new String[]{"DRIVING","IRON PLAY","CHIPPING","PUTTING","MENTAL","ACCURACY","DISTANCE"},
        new String[]{"PWR","PREC","SHT","ALL"},
        new String[]{"Power Hitter","Precision Player","Short Game Spec.","All-Round"},
        "WORLD RANK","TOUR WINS","TITLES","MAJOR WINS",
        new String[]{"Scottie","Rory","Jon","Xander","Viktor","Collin","Cameron","Patrick","Max","Brooks"},
        new String[]{"Scheffler","McIlroy","Rahm","Schauffele","Hovland","Morikawa","Smith","Cantlay","Homa","Koepka"},
        new String[]{"USA","N.Ireland","Spain","USA","Norway","USA","Australia","USA","USA","USA"});

    case BOXING:
      return new SportConfig("Boxing", "PROFESSIONAL BOXING CAREER SIMULATOR",
        new String[]{"JAB","POWER","DEFENSE","FOOTWORK","CHIN","STAMINA","RING IQ"},
        new String[]{"OUT","BRAW","CNTR","B-P"},
        new String[]{"Outboxer","Brawler","Counter Puncher","Boxer-Puncher"},
        "WORLD RANK","CAREER WINS","TITLES","WORLD TITLES",
        new String[]{"Oleksandr","Tyson","Anthony","Deontay","Naoya","Canelo","Terence","Vasyl","Ryan","Errol"},
        new String[]{"Usyk","Fury","Joshua","Wilder","Inoue","Alvarez","Crawford","Lomachenko","Garcia","Spence"},
        new String[]{"Ukraine","UK","UK","USA","Japan","Mexico","USA","Ukraine","USA","USA"});

    default: // TENNIS
      return new SportConfig("Tennis", "PROFESSIONAL TENNIS CAREER SIMULATOR",
        new String[]{"SERVE","FOREHAND","BACKHAND","VOLLEY","SPEED","STAMINA","MENTAL"},
        new String[]{"AGGR","CNTR","S&V","ALL"},
        new String[]{"Aggressive Baseliner","Counter Puncher","Serve & Volley","All Court"},
        "WORLD RANKING","RANKING PTS","TITLES","GS TITLES",
        new String[]{"Carlos","Novak","Roger","Rafael","Jannik","Daniil","Alexander","Andrey","Holger","Taylor"},
        new String[]{"Alcaraz","Djokovic","Federer","Nadal","Sinner","Medvedev","Zverev","Rublev","Rune","Fritz"},
        new String[]{"Spain","Serbia","Switzerland","Spain","Italy","Russia","Germany","Russia","Denmark","USA"});
  }
}
