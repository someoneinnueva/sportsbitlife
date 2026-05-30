// ============================================================
// SPORTS CAREER SIMULATOR
// All events generated procedurally — no API key required.
// To re-enable Claude AI events, paste your key into AIEngine.pde
// ============================================================

GameEngine engine;
UITheme    theme;
String     rivalNarCtx = "";  // rivals context injected into legacy AI prompt


// ============================================================
// CONFIGURATION
// Paste your Anthropic API key between the quotes below.
// Leave empty to run on built-in procedural events only.
// ============================================================
String CLAUDE_API_KEY = "";  //
final String CLAUDE_MODEL   = "claude-sonnet-4-6";

void setup() {
  size(1200, 800);
  smooth(4);
  textFont(createFont("Arial", 13));
  theme  = new UITheme();
  engine = new GameEngine();
  engine.start();
  String[] lines = loadStrings("secrets.txt");
  
  if (lines != null && lines.length > 0) {
    // trim() removes any accidental spaces or hidden newline characters
    CLAUDE_API_KEY = lines[0].trim(); 
    println("API Key loaded securely.");
  } else {
    println("CRITICAL ERROR: secrets.txt is missing or empty!");
    exit(); // Stops the program so it doesn't crash later when calling Claude
  }
}

void draw() {
  background(theme.BG);
  engine.render();
}

void keyPressed()    { engine.processKey(key, keyCode); }
void mousePressed()  { engine.processMouse(mouseX, mouseY); }
void mouseReleased() { engine.processMouseReleased(mouseX, mouseY); }
void mouseMoved()    { engine.processMouseMoved(mouseX, mouseY); }
void mouseDragged()  { engine.processMouseMoved(mouseX, mouseY); }



// ============================================================
// ENUMS
// ============================================================
enum Sport              { TENNIS, BASKETBALL, SOCCER, GOLF, BOXING }
enum Surface            { HARD, CLAY, GRASS, INDOOR_HARD }
enum PlayStyle          { AGGRESSIVE_BASELINER, COUNTER_PUNCHER, SERVE_VOLLEY, ALL_COURT }
enum TournamentTier     { GRAND_SLAM, MASTERS_1000, ATP_500, ATP_250, CHALLENGER }
enum InjuryStatus       { HEALTHY, DAY_TO_DAY, OUT_WEEKS, OUT_MONTHS }
enum RoundName          { R128, R64, R32, R16, QF, SF, F }
enum CoachType          { HEAD_COACH, FITNESS_TRAINER, MENTAL_COACH, SERVE_COACH }
enum CareerPhase        { JUNIOR, RISING, PRIME, VETERAN, DECLINING }
enum LegacyTier         { JOURNEYMAN, SOLID_PRO, STAR, LEGEND, GOAT }
enum ScreenID           { MAIN_MENU, CAREER, MATCH, TRAINING, WORLD_RANKINGS, LIFESTYLE, LEGACY, MARKET }
enum EventType          { CAREER_CHOICE, INJURY, RIVAL, MEDIA, FAMILY, TRAINING_RESULT, FINANCIAL, LEGAL, SOCIAL, RANDOM_LIFE }
enum LifestyleTier      { FRUGAL, COMFORTABLE, LUXURY, LAVISH }
enum RelationshipStatus { SINGLE, DATING, ENGAGED, MARRIED, SEPARATED, DIVORCED }

// Life Sim enums
enum PropertyType   { APARTMENT, CONDO, HOUSE, MANSION, BEACH_HOUSE, PENTHOUSE }
enum VehicleType    { ECONOMY_CAR, SPORTS_CAR, LUXURY_CAR, SUPERCAR, PRIVATE_JET }
enum InvestmentType { STOCKS, CRYPTO, BONDS, REAL_ESTATE_FUND, BUSINESS_VENTURE }
enum MentalState    { THRIVING, STABLE, STRESSED, ANXIOUS, DEPRESSED, BURNED_OUT }
enum SocialTier     { UNKNOWN, LOCAL_CELEB, NATIONAL_STAR, GLOBAL_ICON, MEGASTAR }
enum AgentTier      { NO_AGENT, BASIC_AGENT, MID_TIER, ELITE, SUPERAGENT }
enum AddictionLevel { NONE, MILD, MODERATE, SEVERE }
enum LegalStatus    { CLEAN, INVESTIGATED, CHARGED, SETTLED, CONVICTED }
enum AwardType      { PLAYER_OF_YEAR, BEST_NEWCOMER, COMEBACK_PLAYER, MVP, HUMANITARIAN, SPORTSMANSHIP }
enum MarketTab        { PROPERTY, VEHICLES, INVESTMENTS, BUSINESS, AGENT }
enum LifeTab          { OVERVIEW, MENTAL_HEALTH, RELATIONSHIPS, SOCIAL_MEDIA, LEGAL }
enum ActivityCategory { CAREER, TRAINING, LOVE, HEALTH, ASSETS, SOCIAL }
enum SeasonOutcome    { CHAMPION, STRONG, AVERAGE, DISAPPOINTING, INJURY_RIDDLED }
enum LifePhase        { CHILDHOOD, EARLY_SCHOOL, TEEN, UNIVERSITY, PRO }
