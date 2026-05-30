// ============================================================
// LIFE-SIM SUBSYSTEMS
// ============================================================

// Owned Property
class OwnedProperty {
  String       name;
  PropertyType type;
  float        purchasePrice;
  float        currentValue;
  float        weeklyUpkeep;
  float        weeklyMortgage;
  float        mortgageLeft;
  int          yearPurchased;

  OwnedProperty(String n, PropertyType t, float price, int year) {
    name          = n;
    type          = t;
    purchasePrice = price;
    currentValue  = price;
    yearPurchased = year;
    mortgageLeft  = price * 0.80;

    float annualRate = 0.055 / 52.0;
    switch (t) {
      case APARTMENT:   weeklyUpkeep = 200;  weeklyMortgage = mortgageLeft * annualRate; break;
      case CONDO:       weeklyUpkeep = 400;  weeklyMortgage = mortgageLeft * annualRate; break;
      case HOUSE:       weeklyUpkeep = 800;  weeklyMortgage = mortgageLeft * annualRate; break;
      case MANSION:     weeklyUpkeep = 3000; weeklyMortgage = mortgageLeft * annualRate; break;
      case BEACH_HOUSE: weeklyUpkeep = 2000; weeklyMortgage = mortgageLeft * annualRate; break;
      case PENTHOUSE:   weeklyUpkeep = 5000; weeklyMortgage = mortgageLeft * annualRate; break;
      default:          weeklyUpkeep = 500;  weeklyMortgage = mortgageLeft * annualRate; break;
    }
  }

  float weeklyTotal()   { return weeklyUpkeep + weeklyMortgage; }
  float downPayment()   { return purchasePrice * 0.20; }

  String typeName() {
    switch (type) {
      case APARTMENT:   return "Apartment";
      case CONDO:       return "Condo";
      case HOUSE:       return "House";
      case MANSION:     return "Mansion";
      case BEACH_HOUSE: return "Beach House";
      case PENTHOUSE:   return "Penthouse";
      default:          return "Property";
    }
  }
}

// Owned Vehicle
class OwnedVehicle {
  String      name;
  VehicleType type;
  float       purchasePrice;
  float       weeklyUpkeep;

  OwnedVehicle(String n, VehicleType t, float price) {
    name          = n;
    type          = t;
    purchasePrice = price;
    switch (t) {
      case ECONOMY_CAR: weeklyUpkeep = 50;   break;
      case SPORTS_CAR:  weeklyUpkeep = 200;  break;
      case LUXURY_CAR:  weeklyUpkeep = 500;  break;
      case SUPERCAR:    weeklyUpkeep = 1500; break;
      case PRIVATE_JET: weeklyUpkeep = 8000; break;
      default:          weeklyUpkeep = 100;  break;
    }
  }

  String typeName() {
    switch (type) {
      case ECONOMY_CAR: return "Economy Car";
      case SPORTS_CAR:  return "Sports Car";
      case LUXURY_CAR:  return "Luxury Car";
      case SUPERCAR:    return "Supercar";
      case PRIVATE_JET: return "Private Jet";
      default:          return "Vehicle";
    }
  }
}

// Owned Investment
class OwnedInvestment {
  String         name;
  InvestmentType type;
  float          invested;
  float          currentValue;
  float          weeklyReturnRate;
  float          volatility;

  OwnedInvestment(String n, InvestmentType t, float amt) {
    name         = n;
    type         = t;
    invested     = amt;
    currentValue = amt;
    switch (t) {
      case STOCKS:           weeklyReturnRate = 0.0020; volatility = 0.030; break;
      case CRYPTO:           weeklyReturnRate = 0.0040; volatility = 0.100; break;
      case BONDS:            weeklyReturnRate = 0.0010; volatility = 0.002; break;
      case REAL_ESTATE_FUND: weeklyReturnRate = 0.0015; volatility = 0.010; break;
      case BUSINESS_VENTURE: weeklyReturnRate = 0.0030; volatility = 0.050; break;
      default:               weeklyReturnRate = 0.0015; volatility = 0.020; break;
    }
  }

  void updateWeek() {
    float r = weeklyReturnRate + randomGaussian() * volatility;
    currentValue *= (1 + r);
    if (currentValue < 0) currentValue = 0;
  }

  float unrealizedGain() { return currentValue - invested; }
  float gainPct()        { return invested > 0 ? (unrealizedGain() / invested) * 100 : 0; }

  String typeName() {
    switch (type) {
      case STOCKS:           return "Stock Market";
      case CRYPTO:           return "Crypto";
      case BONDS:            return "Bonds";
      case REAL_ESTATE_FUND: return "Real Estate Fund";
      case BUSINESS_VENTURE: return "Venture Capital";
      default:               return "Investment";
    }
  }
}

// Owned Business
class OwnedBusiness {
  String name;
  float  invested;
  float  currentValue;
  float  weeklyRevenue;
  float  weeklyExpenses;
  int    weeksOwned;
  boolean isProfit;

  OwnedBusiness(String n, float inv) {
    name         = n;
    invested     = inv;
    currentValue = inv;
    weeksOwned   = 0;
    weeklyRevenue  = inv * 0.0022;
    weeklyExpenses = inv * 0.0016;
  }

  float weeklyProfit() { return weeklyRevenue - weeklyExpenses; }

  void updateWeek() {
    weeksOwned++;
    if (weeksOwned > 52 && random(1) < 0.06) weeklyRevenue  *= 1.04;
    if (random(1) < 0.02)                    weeklyExpenses *= 1.08;
    weeklyRevenue  = max(0, weeklyRevenue);
    weeklyExpenses = max(0, weeklyExpenses);
    currentValue   = invested + weeklyProfit() * weeksOwned * 0.9;
    currentValue   = max(0, currentValue);
    isProfit       = weeklyProfit() > 0;
  }
}

// Mental Health
class MentalHealthSystem {
  MentalState state         = MentalState.STABLE;
  float       stressLevel   = 30;
  float       happiness     = 70;
  boolean     inTherapy     = false;
  int         therapyWeeks  = 0;

  void updateWeek(boolean hadTournamentWin, boolean hadTournamentLoss, boolean rested) {
    if (hadTournamentWin)  { stressLevel = max(0, stressLevel - 6); happiness = min(100, happiness + 10); }
    if (hadTournamentLoss) { stressLevel = min(100, stressLevel + 9); happiness = max(0, happiness - 7); }
    if (rested)            { stressLevel = max(0, stressLevel - 4); }
    if (inTherapy) {
      therapyWeeks--;
      stressLevel = max(0, stressLevel - 7);
      happiness   = min(100, happiness + 5);
      if (therapyWeeks <= 0) { inTherapy = false; therapyWeeks = 0; }
    }
    stressLevel += random(-2, 3);
    stressLevel  = constrain(stressLevel, 0, 100);
    happiness    = constrain(happiness,   0, 100);
    recalcState();
  }

  void recalcState() {
    if      (stressLevel >= 80 || happiness <= 15) state = MentalState.BURNED_OUT;
    else if (stressLevel >= 65 || happiness <= 25) state = MentalState.DEPRESSED;
    else if (stressLevel >= 50 || happiness <= 40) state = MentalState.ANXIOUS;
    else if (stressLevel >= 35 || happiness <= 55) state = MentalState.STRESSED;
    else if (stressLevel >= 20 || happiness <= 70) state = MentalState.STABLE;
    else state = MentalState.THRIVING;
  }

  float performanceModifier() {
    switch (state) {
      case THRIVING:   return 1.10;
      case STABLE:     return 1.00;
      case STRESSED:   return 0.95;
      case ANXIOUS:    return 0.88;
      case DEPRESSED:  return 0.80;
      case BURNED_OUT: return 0.70;
      default:         return 1.00;
    }
  }

  void startTherapy(int weeks) { inTherapy = true; therapyWeeks = weeks; }

  color stateColor() {
    switch (state) {
      case THRIVING:   return color(55, 185, 95);
      case STABLE:     return color(80, 140, 220);
      case STRESSED:   return color(220, 160, 40);
      case ANXIOUS:    return color(200, 120, 40);
      case DEPRESSED:  return color(160, 80, 160);
      case BURNED_OUT: return color(210, 65, 65);
      default:         return color(120, 130, 150);
    }
  }

  String stateLabel() {
    switch (state) {
      case THRIVING:   return "Thriving";
      case STABLE:     return "Stable";
      case STRESSED:   return "Stressed";
      case ANXIOUS:    return "Anxious";
      case DEPRESSED:  return "Depressed";
      case BURNED_OUT: return "Burned Out";
      default:         return "Unknown";
    }
  }
}

// Social Media
class SocialMediaProfile {
  float      followers      = 5000;
  SocialTier tier           = SocialTier.UNKNOWN;
  float      engagementRate = 0.05;
  float      controversyScore = 0;
  boolean    hasPRManager   = false;

  void updateWeek(int ranking, boolean viralEvent) {
    float growthRate = 0.003;
    growthRate += map(constrain(500 - ranking, 0, 500), 0, 500, 0, 0.018);
    if (viralEvent) growthRate += 0.05;
    if (hasPRManager) growthRate += 0.005;
    controversyScore = max(0, controversyScore - 2);
    followers *= (1 + growthRate);
    followers  = max(followers, 100);
    updateTier();
  }

  void updateTier() {
    if      (followers >= 10_000_000) tier = SocialTier.MEGASTAR;
    else if (followers >=  1_000_000) tier = SocialTier.GLOBAL_ICON;
    else if (followers >=    100_000) tier = SocialTier.NATIONAL_STAR;
    else if (followers >=     10_000) tier = SocialTier.LOCAL_CELEB;
    else                               tier = SocialTier.UNKNOWN;
  }

  float weeklyBrandValue() {
    float base = followers * 0.0005;
    base *= (1 + engagementRate * 4);
    if (hasPRManager) base *= 1.3;
    return base;
  }

  String tierLabel() {
    switch (tier) {
      case MEGASTAR:      return "Megastar";
      case GLOBAL_ICON:   return "Global Icon";
      case NATIONAL_STAR: return "National Star";
      case LOCAL_CELEB:   return "Local Celeb";
      default:            return "Unknown";
    }
  }

  String followersLabel() {
    if (followers >= 1_000_000) return nf(followers / 1_000_000, 0, 1) + "M";
    if (followers >= 1_000)     return (int)(followers / 1000) + "K";
    return "" + (int)followers;
  }
}

// Agent Contract
class AgentContract {
  AgentTier tier;
  String    agentName;
  float     commissionRate;
  float     weeklySalary;
  float     bonusEndorsementMultiplier;

  AgentContract(AgentTier t) {
    tier = t;
    switch (t) {
      case NO_AGENT:
        agentName = "No Agent"; commissionRate = 0;    weeklySalary = 0;     bonusEndorsementMultiplier = 0;    break;
      case BASIC_AGENT:
        agentName = "Local Agent"; commissionRate = 0.05; weeklySalary = 500;   bonusEndorsementMultiplier = 0.10; break;
      case MID_TIER:
        agentName = "IMG Sports";  commissionRate = 0.08; weeklySalary = 1500;  bonusEndorsementMultiplier = 0.30; break;
      case ELITE:
        agentName = "WME Agency";  commissionRate = 0.10; weeklySalary = 3500;  bonusEndorsementMultiplier = 0.60; break;
      case SUPERAGENT:
        agentName = "Roc Nation";  commissionRate = 0.12; weeklySalary = 8000;  bonusEndorsementMultiplier = 1.10; break;
      default:
        agentName = "None"; commissionRate = 0; weeklySalary = 0; bonusEndorsementMultiplier = 0; break;
    }
  }

  String tierLabel() {
    switch (tier) {
      case NO_AGENT:    return "No Agent";
      case BASIC_AGENT: return "Local Agent";
      case MID_TIER:    return "Mid-Tier Agency";
      case ELITE:       return "Elite Agency";
      case SUPERAGENT:  return "Superagent";
      default:          return "Unknown";
    }
  }

  color tierColor() {
    switch (tier) {
      case SUPERAGENT:  return color(220, 160, 40);
      case ELITE:       return color(180, 80, 220);
      case MID_TIER:    return color(60, 140, 220);
      case BASIC_AGENT: return color(60, 180, 100);
      default:          return color(120, 130, 150);
    }
  }
}

// Award
class Award {
  AwardType type;
  int       year;
  String    sport;

  Award(AwardType t, int y, String s) { type = t; year = y; sport = s; }

  String label() {
    switch (type) {
      case PLAYER_OF_YEAR:   return "Player of the Year " + year;
      case BEST_NEWCOMER:    return "Best Newcomer " + year;
      case COMEBACK_PLAYER:  return "Comeback Player of the Year " + year;
      case MVP:              return "MVP " + year;
      case HUMANITARIAN:     return "Humanitarian Award " + year;
      case SPORTSMANSHIP:    return "Sportsmanship Award " + year;
      default:               return "Award " + year;
    }
  }
}
