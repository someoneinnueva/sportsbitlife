// ============================================================
// SPORTS CAREER SIMULATOR
// All events generated procedurally — no API key required.
// To re-enable Claude AI events, paste your key into AIEngine.pde
// ============================================================

GameEngine engine;
UITheme    theme;

void setup() {
  size(1200, 800);
  smooth(4);
  textFont(createFont("Georgia", 14));
  theme  = new UITheme();
  engine = new GameEngine();
  engine.start();
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
// AI SCENARIO ENGINE - Claude API Integration
// ============================================================
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;

class AIScenarioEngine {
  String apiKey;
  String model;

  // State flags
  boolean   isLoading     = false;
  boolean   hasNewEvent   = false;
  GameEvent pendingEvent  = null;
  String    loadingMessage = "Simulating...";

  // Loading animation
  int      loadingFrame = 0;
  String[] loadingDots  = {"   ", ".  ", ".. ", "..."};

  AIScenarioEngine(String key, String mdl) {
    apiKey = (key == null || key.isEmpty()) ? "" : key;
    model  = mdl;
  }

  // ── Public triggers ────────────────────────────────────────
  void generateWeeklyEvent(Player player, int currentYear, int week, Sport sport) {
    if (isLoading) return;
    isLoading   = true;
    hasNewEvent = false;
    loadingFrame = 0;

    final String playerContext = player.toPromptString(currentYear, week);
    final Sport  sportFinal    = sport;
    final AIScenarioEngine self = this;

    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          GameEvent evt = self.callClaude(self.buildWeeklyPrompt(playerContext, sportFinal));
          self.pendingEvent = evt;
          self.hasNewEvent  = true;
        } catch (Exception e) {
          println("Claude API error: " + e.getMessage());
          self.pendingEvent = self.fallbackEvent();
          self.hasNewEvent  = true;
        } finally {
          self.isLoading = false;
        }
      }
    });
    t.start();
  }

  void generateLegacyNarrative(Player player, int currentYear, Sport sport, final LegacyCallback cb) {
    isLoading = true;
    final String playerContext = player.toPromptString(currentYear, 0);
    final Player p = player;
    final Sport  sportFinal = sport;
    final AIScenarioEngine self = this;

    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          String narrative = self.callClaudeRaw(self.buildLegacyPrompt(playerContext, p, sportFinal));
          cb.onComplete(narrative);
        } catch (Exception e) {
          println("Claude API legacy error: " + e.getMessage());
          cb.onComplete("A career that left its mark on the sport, defined by dedication, resilience, and unforgettable moments on the court.");
        } finally {
          self.isLoading = false;
        }
      }
    });
    t.start();
  }

  // ── Prompt builders ────────────────────────────────────────
  String buildWeeklyPrompt(String playerContext, Sport sport) {
    SportConfig cfg = getSportConfig(sport);
    return "You are the dynamic event generator for a professional " + cfg.name + " career simulation game.\n\n" +
      "Sport: " + cfg.name + "\n" +
      "Current player state:\n" + playerContext + "\n\n" +
      "Generate a realistic life event or dilemma for this " + cfg.name + " player based on their current age and stage. " +
      "If they are a teenager or university student, focus on youth/student life events. " +
      "If they are professional (age 18+), focus on professional career events. " +
      "Consider the player's age, hometown, family, ranking, and personality.\n\n" +
      "Respond ONLY with valid JSON in exactly this format (no markdown, no extra text):\n" +
      "{\n" +
      "  \"type\": \"CAREER_CHOICE\",\n" +
      "  \"headline\": \"Short dramatic headline (max 8 words)\",\n" +
      "  \"description\": \"2-3 sentence narrative description of the situation\",\n" +
      "  \"choices\": [\n" +
      "    {\n" +
      "      \"label\": \"Choice label (max 5 words)\",\n" +
      "      \"description\": \"One sentence outcome description\",\n" +
      "      \"effects\": {\n" +
      "        \"confidence\": 0,\n" +
      "        \"fatigue\": 0,\n" +
      "        \"mental\": 0,\n" +
      "        \"reputation\": 0,\n" +
      "        \"rankingPoints\": 0,\n" +
      "        \"money\": 0,\n" +
      "        \"family\": 0\n" +
      "      }\n" +
      "    }\n" +
      "  ]\n" +
      "}\n\n" +
      "Rules:\n" +
      "- Generate exactly 3 choices\n" +
      "- confidence/fatigue/mental/reputation between -20 and +20; rankingPoints between -50 and +50\n" +
      "- money is in $1,000s (e.g. 50 = $50K gained, -30 = $30K cost); range -200 to +500\n" +
      "- family is family happiness delta (-20 to +20)\n" +
      "- CRITICAL: Generate events from ALL life domains like BitLife — not just sport:\n" +
      "  ROMANCE: meeting someone, relationship milestones, breakups, jealousy\n" +
      "  MONEY: investments gone wrong, surprise windfalls, expensive temptations\n" +
      "  CRIME/LEGAL: doping rumours, contract disputes, tax issues, bar fights\n" +
      "  HEALTH: mental breakdown, mystery illness, injury decisions\n" +
      "  SOCIAL: viral scandal, cancelled culture moment, unexpected friendship\n" +
      "  FAMILY: parent illness, sibling rivalry, partner ultimatum, child request\n" +
      "  AMBITION: rival offer, retirement temptation, coaching offer, academy\n" +
      "  VICES: gambling, nightlife, substance temptation\n" +
      "- NEVER repeat sport-training as the event type — those are handled separately.\n" +
      "- Write events with drama and consequence. Make the player feel it.\n" +
      "- Tailor deeply to player state: broke players get money crises; famous players get scandals.";
  }

  String buildLegacyPrompt(String playerContext, Player player, Sport sport) {
    SportConfig cfg = getSportConfig(sport);
    String parentStr = (player.parents != null)
      ? "\nMom: " + player.parents.mom.name + " | Dad: " + player.parents.dad.name
      : "";
    String familyStr = (player.family.spouse != null)
      ? "\nPartner: " + player.family.partnerName() + " | Kids: " + player.family.children.size()
      : "\nRelationship: Single";
    return "You are writing the retirement narrative for a professional " + cfg.name + " player.\n\n" +
      "Sport: " + cfg.name + "\n" +
      "Hometown: " + player.hometown + "\n" +
      "Career summary:\n" + playerContext +
      "\nTotal titles: "   + player.career.titlesWon +
      "\nMajor titles: "   + player.career.grandSlamTitles +
      "\nPrize money: $"   + (int)player.career.prizeMoney +
      parentStr + familyStr + "\n\n" +
      "Write a 4-5 sentence legacy story weaving together their sporting achievements, hometown roots, and personal journey. " +
      "Mention their parents, partner if they have one, and what drove them. " +
      "Write it as a Sports Illustrated retirement piece. Be specific and emotional.";
  }

  // ── HTTP call ──────────────────────────────────────────────
  GameEvent callClaude(String prompt) throws Exception {
    String raw = callClaudeRaw(prompt);
    return parseGameEvent(raw);
  }

  String callClaudeRaw(String prompt) throws Exception {
    String body = "{" +
      "\"model\":\""       + model + "\"," +
      "\"max_tokens\":800," +
      "\"messages\":[{\"role\":\"user\",\"content\":" + jsonString(prompt) + "}]" +
      "}";

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder()
      .uri(URI.create("https://api.anthropic.com/v1/messages"))
      .header("Content-Type", "application/json")
      .header("x-api-key", apiKey)
      .header("anthropic-version", "2023-06-01")
      .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
      .build();

    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    String json = resp.body();

    if (resp.statusCode() >= 400) {
      throw new Exception("HTTP " + resp.statusCode() + ": " + json);
    }

    // Locate the "text":"..." field of the first content block.
    int start = json.indexOf("\"text\":\"");
    if (start == -1) throw new Exception("No text field in response: " + json);
    start += 8;

    // Walk forward respecting escape sequences to find the closing quote.
    int end = start;
    while (end < json.length()) {
      char c = json.charAt(end);
      if (c == '\\') { end += 2; continue; }   // skip escape pair
      if (c == '"') break;
      end++;
    }
    if (end >= json.length()) throw new Exception("Unterminated text field");

    String raw = json.substring(start, end);
    // Unescape JSON string content
    raw = raw.replace("\\n", "\n")
             .replace("\\r", "\r")
             .replace("\\t", "\t")
             .replace("\\\"", "\"")
             .replace("\\\\", "\\");
    return raw.trim();
  }

  // ── JSON parser (manual; no external library needed) ───────
  GameEvent parseGameEvent(String raw) {
    try {
      // Strip any markdown fences just in case
      raw = raw.replace("```json", "").replace("```", "").trim();

      GameEvent evt = new GameEvent();
      evt.type        = extractString(raw, "type");
      evt.headline    = extractString(raw, "headline");
      evt.description = extractString(raw, "description");

      // Parse choices array
      int choicesStart = raw.indexOf("\"choices\"");
      if (choicesStart == -1) throw new Exception("No choices");
      String choicesBlock = raw.substring(choicesStart);

      evt.choices = new ArrayList<EventChoice>();
      int pos = choicesBlock.indexOf("{");
      while (pos != -1) {
        int closing = findMatchingBrace(choicesBlock, pos);
        if (closing == -1) break;
        String choiceJson = choicesBlock.substring(pos, closing + 1);

        // Ignore if this looks like the wrapping object
        if (choiceJson.contains("\"choices\"")) break;

        EventChoice ch = new EventChoice();
        ch.label                = extractString(choiceJson, "label");
        ch.description          = extractString(choiceJson, "description");
        ch.confidenceEffect     = extractInt(choiceJson, "confidence");
        ch.fatigueEffect        = extractInt(choiceJson, "fatigue");
        ch.mentalEffect         = extractInt(choiceJson, "mental");
        ch.reputationEffect     = extractInt(choiceJson, "reputation");
        ch.rankingPointsEffect  = extractInt(choiceJson, "rankingPoints");
        ch.moneyEffect          = extractInt(choiceJson, "money");
        ch.familyEffect         = extractInt(choiceJson, "family");
        evt.choices.add(ch);

        pos = choicesBlock.indexOf("{", closing + 1);
        if (evt.choices.size() >= 3) break;
      }

      if (evt.choices.size() == 0) throw new Exception("No choices parsed");
      return evt;
    } catch (Exception e) {
      println("Parse error: " + e.getMessage() + "\nRaw: " + raw);
      return fallbackEvent();
    }
  }

  // ── JSON helpers ───────────────────────────────────────────
  String extractString(String json, String key) {
    String search = "\"" + key + "\":\"";
    int s = json.indexOf(search);
    if (s == -1) return "";
    s += search.length();
    int e = s;
    while (e < json.length()) {
      char c = json.charAt(e);
      if (c == '\\') { e += 2; continue; }
      if (c == '"') break;
      e++;
    }
    if (e > json.length()) e = json.length();
    return json.substring(s, e)
               .replace("\\\"", "\"")
               .replace("\\n", "\n");
  }

  int extractInt(String json, String key) {
    String search = "\"" + key + "\":";
    int s = json.indexOf(search);
    if (s == -1) return 0;
    s += search.length();
    while (s < json.length() && (json.charAt(s) == ' ' || json.charAt(s) == '\n')) s++;
    int e = s;
    if (e < json.length() && json.charAt(e) == '-') e++;
    while (e < json.length() && Character.isDigit(json.charAt(e))) e++;
    if (s == e) return 0;
    try {
      return Integer.parseInt(json.substring(s, e));
    } catch (NumberFormatException nfe) {
      return 0;
    }
  }

  int findMatchingBrace(String src, int startPos) {
    int depth = 0;
    boolean inString = false;
    for (int i = startPos; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '\\') { i++; continue; }    // skip escaped char
      if (c == '"') { inString = !inString; continue; }
      if (inString) continue;
      if (c == '{') depth++;
      else if (c == '}') {
        depth--;
        if (depth == 0) return i;
      }
    }
    return -1;
  }

  String jsonString(String s) {
    s = s.replace("\\", "\\\\")
         .replace("\"", "\\\"")
         .replace("\n", "\\n")
         .replace("\r", "\\r")
         .replace("\t", "\\t");
    return "\"" + s + "\"";
  }

  // ── Varied fallback event pool (when API unavailable) ──────
  GameEvent fallbackEvent() {
    int pick = (int)random(7);
    switch (pick) {
      case 0: return buildFallback_Sponsorship();
      case 1: return buildFallback_RivalTaunt();
      case 2: return buildFallback_Nightlife();
      case 3: return buildFallback_Investment();
      case 4: return buildFallback_FamilyCall();
      case 5: return buildFallback_MediaStorm();
      default: return buildFallback_Burnout();
    }
  }

  GameEvent buildFallback_Sponsorship() {
    GameEvent e = new GameEvent();
    e.type = "CAREER_CHOICE"; e.headline = "Brand Wants You";
    e.description = "A global sportswear giant reached out. They want you as the face of their new campaign. It's flattering — but their rivals sponsor your main rival.";
    e.choices = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Sign the deal"; c1.moneyEffect = 120; c1.reputationEffect = 8; c1.description = "Big payday. Some controversy."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Negotiate terms"; c2.moneyEffect = 70; c2.reputationEffect = 4; c2.description = "Safe middle ground."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Decline"; c3.confidenceEffect = 5; c3.description = "Stay independent."; e.choices.add(c3);
    return e;
  }

  GameEvent buildFallback_RivalTaunt() {
    GameEvent e = new GameEvent();
    e.type = "CAREER_CHOICE"; e.headline = "Rival Calls You Out";
    e.description = "Your biggest rival told a journalist you've 'peaked' and are 'living off past glory.' It's all over social media and your phone won't stop buzzing.";
    e.choices = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Fire back publicly"; c1.confidenceEffect = 15; c1.reputationEffect = -5; c1.description = "Go to war. Fans love drama."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Let your game answer"; c2.rankingPointsEffect = 40; c2.mentalEffect = 5; c2.description = "Train harder. Win. Say nothing."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Reach out privately"; c3.familyEffect = 5; c3.reputationEffect = 6; c3.description = "Rise above. Respect earned."; e.choices.add(c3);
    return e;
  }

  GameEvent buildFallback_Nightlife() {
    GameEvent e = new GameEvent();
    e.type = "CAREER_CHOICE"; e.headline = "Vegas Invite";
    e.description = "A celebrity friend invites you to a VIP weekend in Vegas — private jet, penthouse suite. Season starts in 10 days. This is a decision.";
    e.choices = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Go all in"; c1.happinessEffect = 20; c1.fatigueEffect = 25; c1.moneyEffect = -30; c1.description = "Legendary weekend. Terrible prep."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "One night only"; c2.happinessEffect = 10; c2.fatigueEffect = 10; c2.moneyEffect = -10; c2.description = "Compromise. Good enough."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Pass on it"; c3.confidenceEffect = 8; c3.rankingPointsEffect = 30; c3.description = "Total focus. No regrets."; e.choices.add(c3);
    return e;
  }

  GameEvent buildFallback_Investment() {
    GameEvent e = new GameEvent();
    e.type = "CAREER_CHOICE"; e.headline = "High-Risk Tip";
    e.description = "A teammate swears he has an inside tip on a cryptocurrency that's about to explode. He's putting his life savings in. He wants you in.";
    e.choices = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Go big — $200K"; c1.moneyEffect = (int)random(-200, 400); c1.description = "Huge upside. Huge downside."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Small bet — $20K"; c2.moneyEffect = (int)random(-20, 60); c2.description = "Acceptable risk."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Hard pass"; c3.mentalEffect = 3; c3.description = "If it sounds too good, it usually is."; e.choices.add(c3);
    return e;
  }

  GameEvent buildFallback_FamilyCall() {
    GameEvent e = new GameEvent();
    e.type = "CAREER_CHOICE"; e.headline = "Family Needs You";
    e.description = "Your parent calls — health scare. Nothing life-threatening right now, but they need support. The timing could not be worse: you have a tournament in 5 days.";
    e.choices = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Fly home immediately"; c1.familyEffect = 25; c1.happinessEffect = 15; c1.rankingPointsEffect = -30; c1.description = "Family first. Always."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Video call, fly after"; c2.familyEffect = 10; c2.confidenceEffect = -5; c2.description = "Compromise. Guilt trips included."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Stay — win it for them"; c3.rankingPointsEffect = 60; c3.familyEffect = -10; c3.description = "They'd want you to compete."; e.choices.add(c3);
    return e;
  }

  GameEvent buildFallback_MediaStorm() {
    GameEvent e = new GameEvent();
    e.type = "CAREER_CHOICE"; e.headline = "Caught on Camera";
    e.description = "A paparazzi photo surfaces of you leaving a club at 2 AM three nights before a major tournament. The caption is brutal. Your agent is calling.";
    e.choices = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "Public apology"; c1.reputationEffect = 4; c1.confidenceEffect = -8; c1.description = "Grovel. Boring but effective."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Ignore it completely"; c2.confidenceEffect = 10; c2.reputationEffect = -8; c2.description = "Feed the beast or starve it?"; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Spin it — was charity"; c3.reputationEffect = 8; c3.moneyEffect = -5; c3.description = "Turn a PR crisis into a win."; e.choices.add(c3);
    return e;
  }

  GameEvent buildFallback_Burnout() {
    GameEvent e = new GameEvent();
    e.type = "CAREER_CHOICE"; e.headline = "Running on Empty";
    e.description = "You snap at your coach during practice. You haven't slept properly in weeks. An old friend says 'you've changed.' Something has to give.";
    e.choices = new ArrayList<EventChoice>();
    EventChoice c1 = new EventChoice(); c1.label = "See a therapist"; c1.happinessEffect = 20; c1.mentalEffect = 8; c1.moneyEffect = -5; c1.description = "Invest in your mental health."; e.choices.add(c1);
    EventChoice c2 = new EventChoice(); c2.label = "Take a week off"; c2.fatigueEffect = -30; c2.happinessEffect = 12; c2.rankingPointsEffect = -15; c2.description = "Recharge. Career can wait."; e.choices.add(c2);
    EventChoice c3 = new EventChoice(); c3.label = "Push through"; c3.fatigueEffect = 15; c3.rankingPointsEffect = 25; c3.mentalEffect = -5; c3.description = "Champions aren't built on rest days."; e.choices.add(c3);
    return e;
  }

  // ── Loading animation ──────────────────────────────────────
  String getLoadingText() {
    loadingFrame++;
    return "Generating event" + loadingDots[(loadingFrame / 15) % 4];
  }
}

// ── Data classes for events ─────────────────────────────────
class GameEvent {
  String type;
  String headline;
  String description;
  ArrayList<EventChoice> choices;
  EventType eventType = EventType.CAREER_CHOICE;
}

class EventChoice {
  String label;
  String description;
  int confidenceEffect    = 0;
  int fatigueEffect       = 0;
  int mentalEffect        = 0;
  int reputationEffect    = 0;
  int rankingPointsEffect = 0;
  int moneyEffect         = 0;   // in $1,000s; e.g. 50 = +$50K
  int familyEffect        = 0;   // family happiness delta
  int happinessEffect     = 0;   // player.happiness delta
  String tag              = "";  // special handler tag (e.g. "go_university")
}

interface LegacyCallback {
  void onComplete(String narrative);
}
// ============================================================
// CAREER SCREEN - Main gameplay view
// ============================================================
class CareerScreen extends BaseScreen {
  // Layout constants (NOT static - Processing inner classes don't reliably allow static members)
  final float LEFT_COL  =  20;
  final float MID_COL   = 320;
  final float RIGHT_COL = 780;
  final float TOP_Y     =  62;

  // Hover state
  boolean hoverAdvance = false;

  // Notification toast
  String toastText  = "";
  float  toastAlpha = 0;

  CareerScreen(GameEngine e) { super(e); }

  void onEnter() {}

  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    background(theme.BG);

    // Promote any new AI event from background thread to pendingEvent
    if (engine.ai.hasNewEvent && engine.state.pendingEvent == null) {
      engine.state.pendingEvent = engine.ai.pendingEvent;
      engine.ai.hasNewEvent     = false;
    }

    drawLeftColumn(p);
    drawMiddleColumn(p);
    drawRightColumn(p);
    drawToast();
  }

  // ── LEFT: Player stats card ──────────────────────────────
  void drawLeftColumn(Player p) {
    float x = LEFT_COL, y = TOP_Y;
    theme.drawCard(x, y, 285, 340);

    fill(theme.TEXT);
    textSize(20);
    textAlign(LEFT, TOP);
    text(p.name, x + 16, y + 14);

    fill(theme.TEXT_DIM);
    textSize(11);
    String sideTerm = (engine.state.currentSport == Sport.SOCCER) ? "foot" : "hand";
    text(p.nationality + " - " + p.dominantHand + " " + sideTerm, x + 16, y + 40);

    fill(theme.ACCENT);
    textSize(11);
    text(p.playStyle.toString().replace("_", " "), x + 16, y + 56);

    CareerPhase phase = p.currentPhase(engine.state.currentYear);
    color phaseCol = phaseColor(phase);
    fill(phaseCol, 40);
    rect(x + 16, y + 72, 120, 20, 10);
    fill(phaseCol);
    textSize(10);
    textAlign(CENTER, CENTER);
    text(phase.toString(), x + 76, y + 82);

    y += 104;
    PlayerAttributes eff = p.effectiveAttributes();
    SportConfig sc = engine.state.sportConfig != null ? engine.state.sportConfig : getSportConfig(Sport.TENNIS);
    String[] statNames = sc.statNames;
    int[]    statVals  = {eff.serve, eff.forehand, eff.backhand, eff.volley, eff.speed, eff.stamina, eff.mental};
    color[]  statCols  = {theme.ACCENT, theme.ACCENT2, theme.ACCENT2, theme.SUCCESS, theme.SUCCESS, theme.SUCCESS, theme.DANGER};

    for (int i = 0; i < statNames.length; i++) {
      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
      text(statNames[i], x + 16, y + 8);
      fill(theme.TEXT); textAlign(RIGHT, CENTER);
      text(statVals[i], x + 260, y + 8);
      theme.drawStatBar(x + 16, y + 16, 253, 6, statVals[i], 99, statCols[i]);
      y += 32;
    }

    y += 8;
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
    text("FATIGUE", LEFT_COL + 16, y);
    fill(p.form.fatigue > 70 ? theme.DANGER : theme.TEXT_DIM);
    textAlign(RIGHT, CENTER);
    text((int)p.form.fatigue + "%", LEFT_COL + 260, y);
    theme.drawStatBar(LEFT_COL + 16, y + 8, 253, 6, p.form.fatigue, 100,
                      p.form.fatigue > 70 ? theme.DANGER : theme.ACCENT2);

    y += 24;
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, CENTER);
    text("CONFIDENCE", LEFT_COL + 16, y);
    fill(theme.TEXT); textAlign(RIGHT, CENTER);
    text((int)p.form.confidence + "%", LEFT_COL + 260, y);
    theme.drawStatBar(LEFT_COL + 16, y + 8, 253, 6, p.form.confidence, 100, theme.SUCCESS);

    if (p.health.status != InjuryStatus.HEALTHY) {
      y += 28;
      fill(theme.DANGER, 40);
      rect(LEFT_COL + 16, y, 250, 22, 4);
      fill(theme.DANGER); textSize(11); textAlign(CENTER, CENTER);
      text("! " + p.health.injuredPart + " - " + p.health.weeksRemaining + " wks",
           LEFT_COL + 141, y + 11);
    }
  }

  // ── MIDDLE: Career stats + AI event ──────────────────────
  void drawMiddleColumn(Player p) {
    float x = MID_COL, y = TOP_Y;
    theme.drawCard(x, y, 440, 120);

    float[] vals = {
      p.career.worldRanking,
      p.career.rankingPoints,
      p.career.titlesWon,
      p.career.grandSlamTitles,
      p.career.weeksAtNumberOne,
      p.career.prizeMoney / 1000
    };
    SportConfig sc2 = engine.state.sportConfig != null ? engine.state.sportConfig : getSportConfig(Sport.TENNIS);
    String[] lbls = {sc2.rankLabel, sc2.ptsLabel, sc2.titlesLabel, sc2.majorLabel, "WKS #1", "PRIZE ($K)"};

    for (int i = 0; i < 3; i++) {
      float cx = x + 16 + i * 140;
      fill(theme.ACCENT);
      textSize(24);
      textAlign(LEFT, TOP);
      text(i == 0 ? "#" + (int)vals[i] : (int)vals[i] + "", cx, y + 16);
      fill(theme.TEXT_DIM);
      textSize(10);
      text(lbls[i], cx, y + 50);
    }
    for (int i = 3; i < 6; i++) {
      float cx = x + 16 + (i - 3) * 140;
      fill(theme.TEXT);
      textSize(20);
      textAlign(LEFT, TOP);
      text(i == 5 ? formatMoney(p.career.prizeMoney) : (int)vals[i] + "", cx, y + 66);
      fill(theme.TEXT_DIM);
      textSize(10);
      text(lbls[i], cx, y + 92);
    }

    y += 132;

    if (engine.ai.isLoading) {
      theme.drawCard(x, y, 440, 80, true);
      fill(theme.ACCENT); textSize(14); textAlign(CENTER, CENTER);
      text(engine.ai.getLoadingText(), x + 220, y + 40);
    } else if (engine.state.pendingEvent != null) {
      drawEventPanel(engine.state.pendingEvent, x, y, 440);
    } else {
      theme.drawCard(x, y, 440, 80);
      fill(theme.TEXT_DIM); textSize(12); textAlign(LEFT, CENTER);
      if (!engine.state.lastEvent.isEmpty()) text(engine.state.lastEvent, x + 16, y + 40);
      else                                   text("Press SPACE to advance one week", x + 16, y + 40);
    }

    y += 340;

    if (!p.career.recentResults.isEmpty()) {
      sectionHeader("RECENT RESULTS", x, y);
      y += 16;
      int shown = min(4, p.career.recentResults.size());
      for (int i = 0; i < shown; i++) {
        MatchResult r = p.career.recentResults.get(i);
        drawResultRow(r, x, y, 440);
        y += 36;
      }
    }
  }

  void drawResultRow(MatchResult r, float x, float y, float w) {
    fill(r.playerWon ? color(20, 45, 25) : color(45, 20, 20));
    rect(x, y, w, 32, 4);
    fill(r.playerWon ? theme.SUCCESS : theme.DANGER);
    textSize(11); textAlign(LEFT, CENTER);
    text(r.playerWon ? "W" : "L", x + 12, y + 16);
    fill(theme.TEXT); textAlign(LEFT, CENTER);
    text(r.tournamentName + " " + r.round, x + 30, y + 16);
    fill(theme.TEXT_DIM); textAlign(RIGHT, CENTER);
    text(r.score + " +" + r.rankingPointsAwarded + "pts", x + w - 12, y + 16);
  }

  // ── RIGHT: Calendar + Advance ────────────────────────────
  void drawRightColumn(Player p) {
    float x = RIGHT_COL, y = TOP_Y;
    hoverAdvance = theme.isHover(hoverX, hoverY, x, y, 380, 56);
    fill(hoverAdvance ? theme.ACCENT : color(30, 38, 22));
    stroke(theme.ACCENT);
    strokeWeight(hoverAdvance ? 2 : 1);
    rect(x, y, 380, 56, 6);
    noStroke();
    fill(hoverAdvance ? theme.BG : theme.ACCENT);
    textSize(15);
    textAlign(CENTER, CENTER);
    text("\u25B6 ADVANCE WEEK [SPACE]", x + 190, y + 28);

    y += 70;
    theme.drawCard(x, y, 380, 260);
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("SEASON CALENDAR", x + 16, y + 14);

    float cy = y + 30;
    int week = 0; // CareerScreen replaced by LifeScreen; week unused
    ArrayList<Tournament> all = engine.calendar.schedule;
    int shown = 0;
    for (Tournament t : all) {
      if (shown >= 7) break;
      boolean isCurrent = t.week == week;
      if (t.week >= week || shown == 0) {
        drawCalendarRow(t, x + 12, cy, 356, isCurrent);
        cy += 34;
        shown++;
      }
    }

    y += 274;
    sectionHeader("CAREER LOG", x, y);
    y += 16;
    fill(theme.TEXT_DIM); textSize(11); textAlign(LEFT, TOP);
    ArrayList<String> hist = p.career.careerHistory;
    int start = max(0, hist.size() - 6);
    for (int i = start; i < hist.size(); i++) {
      text("- " + hist.get(i), x, y);
      y += 18;
    }
  }

  void drawCalendarRow(Tournament t, float x, float y, float w, boolean current) {
    if (current) {
      fill(theme.ACCENT, 30);
      rect(x, y, w, 30, 3);
    }
    color tierCol = tierColor(t.tier);
    fill(tierCol);
    ellipse(x + 12, y + 15, 8, 8);

    fill(current ? theme.ACCENT : theme.TEXT_DIM);
    textSize(10); textAlign(LEFT, CENTER);
    text("W" + t.week, x + 24, y + 15);

    fill(current ? theme.ACCENT : theme.TEXT);
    textSize(12); textAlign(LEFT, CENTER);
    text(t.name, x + 50, y + 15);

    String vLabel = t.venueLabel.isEmpty() ? t.surface.toString().replace("_", " ") : t.venueLabel;
    fill(surfaceColor(t.surface), 60);
    rect(x + w - 70, y + 6, 62, 18, 9);
    fill(surfaceColor(t.surface));
    textSize(9); textAlign(CENTER, CENTER);
    text(vLabel, x + w - 39, y + 15);
  }

  color phaseColor(CareerPhase p) {
    switch (p) {
      case JUNIOR:    return color(100, 160, 255);
      case RISING:    return color( 80, 220, 120);
      case PRIME:     return color(220, 160,  40);
      case VETERAN:   return color(200, 100,  60);
      case DECLINING: return color(160,  80,  80);
      default:        return theme.TEXT_DIM;
    }
  }

  color tierColor(TournamentTier t) {
    switch (t) {
      case GRAND_SLAM:   return color(220, 160,  40);
      case MASTERS_1000: return color(180,  80, 220);
      case ATP_500:      return color( 60, 140, 220);
      case ATP_250:      return color( 60, 180, 120);
      default:           return theme.TEXT_DIM;
    }
  }

  color surfaceColor(Surface s) {
    switch (s) {
      case CLAY:        return color(200, 100,  60);
      case GRASS:       return color( 60, 160,  80);
      case INDOOR_HARD: return color(100, 100, 200);
      default:          return color(100, 140, 200);
    }
  }

  void drawToast() {
    if (toastAlpha > 0) {
      toastAlpha -= 3;
      fill(theme.PANEL, toastAlpha);
      stroke(theme.ACCENT, toastAlpha);
      strokeWeight(1);
      rect(width / 2 - 200, height - 80, 400, 44, 6);
      noStroke();
      fill(theme.TEXT, toastAlpha);
      textSize(13); textAlign(CENTER, CENTER);
      text(toastText, width / 2, height - 58);
    }
  }

  void showToast(String msg) {
    toastText  = msg;
    toastAlpha = 255;
  }

  void onClick(int mx, int my) {
    if (theme.isHover(mx, my, RIGHT_COL, TOP_Y, 380, 56)) {
      engine.ageUp();
      return;
    }
    if (engine.state.pendingEvent != null && engine.state.pendingEvent.choices != null) {
      ArrayList<EventChoice> choices = engine.state.pendingEvent.choices;
      float ex = MID_COL, ey = TOP_Y + 132 + 120;
      for (int i = 0; i < choices.size(); i++) {
        if (theme.isHover(mx, my, ex + 14, ey, 412, 52)) {
          engine.applyChoice(choices.get(i));
          showToast("Choice made: " + choices.get(i).label);
          return;
        }
        ey += 62;
      }
    }
  }

  void onHover(int mx, int my) { super.onHover(mx, my); }
}
// ============================================================
// CONFIGURATION
// Paste your Anthropic API key between the quotes below.
// Leave empty to run on built-in procedural events only.
// ============================================================
final String CLAUDE_API_KEY = "";  // ← paste your key here
final String CLAUDE_MODEL   = "claude-sonnet-4-6";
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

// ── Life Sim enums ───────────────────────────────────────────
enum PropertyType  { APARTMENT, CONDO, HOUSE, MANSION, BEACH_HOUSE, PENTHOUSE }
enum VehicleType   { ECONOMY_CAR, SPORTS_CAR, LUXURY_CAR, SUPERCAR, PRIVATE_JET }
enum InvestmentType{ STOCKS, CRYPTO, BONDS, REAL_ESTATE_FUND, BUSINESS_VENTURE }
enum MentalState   { THRIVING, STABLE, STRESSED, ANXIOUS, DEPRESSED, BURNED_OUT }
enum SocialTier    { UNKNOWN, LOCAL_CELEB, NATIONAL_STAR, GLOBAL_ICON, MEGASTAR }
enum AgentTier     { NO_AGENT, BASIC_AGENT, MID_TIER, ELITE, SUPERAGENT }
enum AddictionLevel{ NONE, MILD, MODERATE, SEVERE }
enum LegalStatus   { CLEAN, INVESTIGATED, CHARGED, SETTLED, CONVICTED }
enum AwardType     { PLAYER_OF_YEAR, BEST_NEWCOMER, COMEBACK_PLAYER, MVP, HUMANITARIAN, SPORTSMANSHIP }
enum MarketTab       { PROPERTY, VEHICLES, INVESTMENTS, BUSINESS, AGENT }
enum LifeTab         { OVERVIEW, MENTAL_HEALTH, RELATIONSHIPS, SOCIAL_MEDIA, LEGAL }
enum ActivityCategory{ CAREER, TRAINING, LOVE, HEALTH, ASSETS, SOCIAL }
enum SeasonOutcome   { CHAMPION, STRONG, AVERAGE, DISAPPOINTING, INJURY_RIDDLED }
enum LifePhase       { CHILDHOOD, EARLY_SCHOOL, TEEN, UNIVERSITY, PRO }
// ============================================================
// GAME ENGINE  —  age-based loop, activity system, state
// ============================================================
class GameEngine {
  // Core systems
  GameState           state;
  AIScenarioEngine    ai;
  MatchEngine         matchEngine;
  TournamentCalendar  calendar;
  RankingSystem       ranking;

  // Screens
  ScreenID        currentScreen = ScreenID.MAIN_MENU;
  BaseScreen      activeScreen;

  MainMenuScreen   menuScreen;
  LifeScreen       lifeScreen;      // new BitLife-style main screen
  MatchScreen      matchScreen;
  TrainingScreen   trainingScreen;
  WorldRankScreen  worldScreen;
  LifestyleScreen  lifestyleScreen;
  LegacyScreen     legacyScreen;

  // ── Constructors ─────────────────────────────────────────
  GameEngine() {}   // Config constants used in start()

  // ── Startup ──────────────────────────────────────────────
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

  // ── Render ───────────────────────────────────────────────
  void render() {
    activeScreen.render();
    if (currentScreen != ScreenID.MAIN_MENU && state.player != null) {
      drawStatusBar();
    }
  }

  void drawStatusBar() {
    fill(theme.PANEL); noStroke();
    rect(0, 0, width, 52);
    stroke(theme.BORDER); strokeWeight(1);
    line(0, 52, width, 52);
    noStroke();

    fill(theme.ACCENT); textSize(12); textAlign(LEFT, CENTER);
    String sportIcon = sportIcon(state.currentSport);
    text(sportIcon + "  " + state.player.name.toUpperCase(), 16, 26);

    SportConfig sc = state.sportConfig != null ? state.sportConfig : getSportConfig(Sport.TENNIS);
    fill(theme.TEXT_DIM); textSize(11); textAlign(LEFT, CENTER);
    text(sc.rankLabel + " #" + state.player.career.worldRanking +
         "  |  " + sc.ptsLabel + " " + state.player.career.rankingPoints +
         "  |  Age " + state.player.age(state.currentYear) +
         "  |  " + state.currentYear +
         "  |  " + formatMoney(state.player.finances.savings),
         200, 26);

    if (state.player.family.spouse != null) {
      float fh = state.player.family.familyHappiness;
      fill(fh > 60 ? theme.SUCCESS : (fh > 30 ? theme.ACCENT : theme.DANGER));
      textSize(11); textAlign(LEFT, CENTER);
      text("♥ " + state.player.family.partnerName(), width - 680, 26);
    }

    String[]   navLabels  = {"CAREER", "TRAINING", "WORLD", "LIFE", "LEGACY"};
    ScreenID[] navScreens = {ScreenID.CAREER, ScreenID.TRAINING, ScreenID.WORLD_RANKINGS, ScreenID.LIFESTYLE, ScreenID.LEGACY};
    for (int i = 0; i < navLabels.length; i++) {
      float nx = width - 575 + i * 113;
      boolean active = currentScreen == navScreens[i];
      fill(active ? theme.ACCENT : theme.PANEL_2);
      stroke(active ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(nx, 8, 106, 34, 4);
      noStroke();
      fill(active ? theme.BG : theme.TEXT);
      textSize(11); textAlign(CENTER, CENTER);
      text(navLabels[i], nx + 53, 26);
    }
  }

  String sportIcon(Sport s) {
    switch (s) {
      case BASKETBALL: return "B";
      case SOCCER:     return "S";
      case GOLF:       return "G";
      case BOXING:     return "X";
      default:         return "T";
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

  // ── Screen switching ─────────────────────────────────────
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

  // ════════════════════════════════════════════════════════
  // AGE UP  —  core BitLife-style year progression
  // ════════════════════════════════════════════════════════
  void ageUp() {
    if (state.player == null || ai.isLoading) return;

    state.currentYear++;
    Player p = state.player;
    int    age = p.age(state.currentYear);

    LifePhase prevPhase = state.lifePhase;
    updateLifePhase(p, age);

    // Mark pro career start year the first time we enter PRO
    if (prevPhase != LifePhase.PRO && state.lifePhase == LifePhase.PRO && state.proCareerStartYear < 0) {
      state.proCareerStartYear = state.currentYear;
      p.addLifeEvent("Turned professional — the journey begins.", state.currentYear);
    }

    // Aging and shared systems
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

    // AI events: only for teens and older (younger gets passive story events)
    if (p.age(state.currentYear) >= 13) {
      ai.generateWeeklyEvent(p, state.currentYear, 0, state.currentSport);
    }
  }

  // ── Determine life phase from age ────────────────────────
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

  // ── Passive simulation for childhood / school / uni years ─
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
        // At 17, trigger university decision if not already decided
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

  // ── Season simulation (full year abstracted) ─────────────
  void simulateSeason() {
    Player p = state.player;
    int   totalPts   = 0;
    float totalPrize = 0;
    int   titles     = 0;
    String bestTournament = "";

    // Run every Grand Slam + Masters event in the calendar
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
      // Advance injury recovery through the season
      p.health.advanceWeek();
    }

    // Decay old points (yearly fade), add season total
    p.career.rankingPoints = (int)(p.career.rankingPoints * 0.65) + totalPts;
    p.career.prizeMoney   += totalPrize;
    p.finances.savings    += totalPrize;

    // Build season summary and log it
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

  // ── Annual finances ───────────────────────────────────────
  void applyAnnualFinances() {
    Player p = state.player;
    float rankEndorsement = p.finances.endorsementIncomeForRank(p.career.worldRanking);
    float annualIncome    = (rankEndorsement + p.finances.endorsementIncome) * 52;
    float annualExpenses  = p.finances.weeklyExpenses * 52 + p.agent.weeklySalary * 52;

    p.finances.savings += annualIncome - annualExpenses;

    // Investment returns and business revenue
    for (OwnedInvestment inv : p.finances.investments) {
      for (int w = 0; w < 52; w++) inv.updateWeek();
    }
    for (OwnedBusiness biz : p.finances.businesses) {
      for (int w = 0; w < 52; w++) biz.updateWeek();
      p.finances.savings += biz.weeklyProfit() * 52;
    }
    p.finances.savings = max(0, p.finances.savings);
  }

  // ── Sync BitLife stats from underlying game state ─────────
  void syncBitLifeStats(Player p) {
    // Wellbeing mirrors fatigue and injury
    float wbTarget = 100 - p.form.fatigue * 0.4;
    if (p.health.status == InjuryStatus.OUT_MONTHS) wbTarget -= 30;
    else if (p.health.status == InjuryStatus.OUT_WEEKS) wbTarget -= 15;
    p.wellbeing = constrain(lerp(p.wellbeing, wbTarget, 0.25), 0, 100);

    // Happiness mirrors mental health + family
    float hapTarget = p.mentalHealth.happiness * 0.7f + 30;
    if (p.family.familyHappiness > 70)         hapTarget += 10;
    if (p.career.worldRanking <= 10)            hapTarget += 12;
    if (p.addiction != AddictionLevel.NONE)     hapTarget -= 15;
    p.happiness = constrain(lerp(p.happiness, hapTarget, 0.3), 0, 100);

    // Smarts grows slowly with experience (age/career)
    p.smarts = constrain(p.smarts + random(-1, 2), 0, 100);

    // Looks decline slowly after 28
    if (p.age(state.currentYear) > 28) {
      p.looks = constrain(p.looks - random(0, 1.5), 0, 100);
    }
  }

  // ── Ranking update ────────────────────────────────────────
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

  // ── Apply player choice from event card ───────────────────
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

    // Log the choice
    if (choice.label != null && !choice.label.isEmpty())
      p.addLifeEvent("Decision: " + choice.label, state.currentYear);

    // Handle special tags (university, match decisions, etc.)
    if (choice.tag != null && !choice.tag.isEmpty()) {
      handleSpecialChoiceTag(choice.tag, p);
    }

    state.pendingEvent = null;
    ai.hasNewEvent     = false;

    // If a match was queued behind a decision event, run it now
    if (!state.pendingMatchAction.isEmpty()) {
      String queuedAction = state.pendingMatchAction;
      state.pendingMatchAction = "";
      handleCareerAction(queuedAction, true);
    }
  }

  // ════════════════════════════════════════════════════════
  // ACTIVITY HANDLERS
  // ════════════════════════════════════════════════════════

  // ── Career activities ────────────────────────────────────
  void handleCareerAction(String action) { handleCareerAction(action, false); }

  void handleCareerAction(String action, boolean skipMatchDecision) {
    Player p = state.player;
    switch (action) {
      case "tournament":
        // 40% chance of pre-match decision event (PRO phase only, once per tournament)
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

  // ── Training activities ───────────────────────────────────
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

  // ── Relationship activities ───────────────────────────────
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

  // ── Health activities ─────────────────────────────────────
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

  // ── Asset activities ──────────────────────────────────────
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

  // ── Social activities ─────────────────────────────────────
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

  // ════════════════════════════════════════════════════════
  // PROCEDURAL EVENT BUILDERS
  // ════════════════════════════════════════════════════════

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
    e.description = "You and " + p.family.partnerName() + " are talking about having a child. It would change your career schedule and finances significantly — but also everything else.";
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
    e.description = "The real estate market is hot. Your agent says now is the time to invest in property. You have " + formatMoney(savings) + " saved. What do you do?";
    e.choices     = new ArrayList<EventChoice>();

    EventChoice c1 = new EventChoice(); c1.label = "Buy an apartment";
    c1.moneyEffect = savings >= 200000 ? -40 : 0;
    c1.description = savings >= 200000 ? "Secure a city apartment. -$200K down payment." : "Need $200K to buy an apartment.";
    e.choices.add(c1);

    EventChoice c2 = new EventChoice(); c2.label = "Buy a house";
    c2.moneyEffect = savings >= 500000 ? -100 : 0;
    c2.description = savings >= 500000 ? "A proper home. -$500K down payment." : "Need $500K for a house.";
    e.choices.add(c2);

    EventChoice c3 = new EventChoice(); c3.label = "Not right now";
    c3.confidenceEffect = 2;
    c3.description = "Wait for a better time."; e.choices.add(c3);

    // Apply property purchase in applyChoice via money effect
    return e;
  }

  GameEvent buildInvestEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Investment Opportunity";
    e.description = "Your financial advisor has three options on the table. Each has a different risk profile. You have " + formatMoney(p.finances.savings) + " available.";
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
    c3.moneyEffect = 0;
    c3.description = "Save the money for something more important."; e.choices.add(c3);
    return e;
  }

  GameEvent buildBusinessEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Business Venture";
    e.description = "An old friend pitches you a business idea — a sports academy, a fashion brand, or a restaurant. All need capital. All could change your life.";
    e.choices     = new ArrayList<EventChoice>();

    EventChoice c1 = new EventChoice(); c1.label = "Sports academy ($150K)";
    c1.moneyEffect = p.finances.savings >= 150000 ? -150 : 0;
    c1.reputationEffect = 10;
    c1.description = p.finances.savings >= 150000 ? "Give back to the sport. Long-term brand builder." : "Need $150K."; e.choices.add(c1);

    EventChoice c2 = new EventChoice(); c2.label = "Fashion/lifestyle brand ($80K)";
    c2.moneyEffect = p.finances.savings >= 80000 ? -80 : 0;
    c2.confidenceEffect = 8; c2.reputationEffect = 6;
    c2.description = p.finances.savings >= 80000 ? "Risky but trending. Influencer potential." : "Need $80K."; e.choices.add(c2);

    EventChoice c3 = new EventChoice(); c3.label = "Skip it";
    c3.rankingPointsEffect = 30;
    c3.description = "Stick to what you know best: your sport."; e.choices.add(c3);
    return e;
  }

  GameEvent buildInterviewEvent(Player p) {
    GameEvent e = new GameEvent();
    e.headline    = "Hot Take Request";
    e.description = "A journalist asks for your unfiltered opinion on a controversial topic in your sport — match-fixing allegations, player salary caps, or a rival's behaviour.";
    e.choices     = new ArrayList<EventChoice>();

    EventChoice c1 = new EventChoice(); c1.label = "Speak your mind";
    c1.reputationEffect = -8; c1.confidenceEffect = 12; c1.moneyEffect = 25;
    c1.description = "Bold, viral, and divisive. Some will love you, some won't."; e.choices.add(c1);

    EventChoice c2 = new EventChoice(); c2.label = "Diplomatic answer";
    c2.reputationEffect = 6; c2.moneyEffect = 10;
    c2.description = "Professional and polished. Safe but forgettable."; e.choices.add(c2);

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
    c1.description = "Bet on yourself. Enter the pro circuit at 18. No looking back."; e.choices.add(c1);

    EventChoice c2 = new EventChoice(); c2.label = "University (3 years)";
    c2.tag = "go_university"; c2.mentalEffect = 6; c2.happinessEffect = 12;
    c2.description = "Grow as a person first. Graduate at 21, then go pro with wisdom."; e.choices.add(c2);

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
    c1.description = "Leave everything on the court. High risk, match-winning reward."; e.choices.add(c1);

    EventChoice c2 = new EventChoice(); c2.label = "Stay Composed";
    c2.confidenceEffect = 6; c2.mentalEffect = 3; c2.rankingPointsEffect = 15;
    c2.description = "Play smart. Trust your training. Minimize errors."; e.choices.add(c2);

    EventChoice c3 = new EventChoice(); c3.label = "Switch Strategy";
    c3.confidenceEffect = 10; c3.rankingPointsEffect = 25; c3.fatigueEffect = 10;
    c3.description = "Surprise your opponent. Change the rhythm of the match."; e.choices.add(c3);

    return e;
  }

  // ── Handle special choice tags ────────────────────────────
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

  // ── Handle early-life activity actions ───────────────────
  void handleEarlyLifeActivity(String key) {
    Player p = state.player;
    state.lastEvent = "";

    // Shared with PRO phase
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
      // ── CHILDHOOD ──────────────────────────────────────────
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
      case "homework":
      case "study_hard":
        p.smarts    = constrain(p.smarts    + random(2, 5), 0, 100);
        p.happiness = constrain(p.happiness - 2, 0, 100);
        state.lastEvent = "Studied hard. Boring but worthwhile — smarts increasing.";
        break;
      case "explore":
      case "library":
        p.smarts    = constrain(p.smarts    + random(1, 4), 0, 100);
        p.happiness = constrain(p.happiness + 2, 0, 100);
        state.lastEvent = "Explored the world with curiosity. Knowledge is power.";
        break;
      case "build":
      case "creative":
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
      case "family_time":
      case "visit_grandparents":
      case "family_dinner":
      case "visit_family":
      case "family_hug":
        p.happiness = constrain(p.happiness + 9, 0, 100);
        p.wellbeing = constrain(p.wellbeing + 4, 0, 100);
        if (p.parents != null) {
          p.parents.mom.relationship = constrain(p.parents.mom.relationship + 4, 0, 100);
          p.parents.dad.relationship = constrain(p.parents.dad.relationship + 4, 0, 100);
          state.lastEvent = "Family time with Mom (" + p.parents.mom.name + ") and Dad (" + p.parents.dad.name + "). Love and warmth.";
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
      case "hangout":
      case "date_together":
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

      case "save_allowance":
      case "chores":
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
      case "help_home":
      case "part_time_help":
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
      case "join_club":
      case "school_club":
        p.smarts    = constrain(p.smarts    + 2, 0, 100);
        p.happiness = constrain(p.happiness + 5, 0, 100);
        state.lastEvent = "Joined an after-school club. Found a tribe of like-minded people.";
        break;
      case "community":
      case "local_event":
        p.reputation.applyPositive(4);
        p.happiness = constrain(p.happiness + 4, 0, 100);
        state.lastEvent = "Participated in a local community event. Building roots.";
        break;
      case "post_clip":
        p.socialMedia.updateWeek(p.career.worldRanking, true);
        p.happiness = constrain(p.happiness + 3, 0, 100);
        state.lastEvent = "Posted a training clip online — a few hundred people noticed.";
        break;

      // ── SCHOOL-AGE / TEEN SPORT ────────────────────────────
      case "school_sports":
      case "phys_train":
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
          p.baseAttributes.serve    = min(90, p.baseAttributes.serve    + (int)random(2, 5));
          p.baseAttributes.mental   = min(90, p.baseAttributes.mental   + 3);
          p.form.confidence         = constrain(p.form.confidence + 10, 0, 100);
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

  // ── Input routing ─────────────────────────────────────────
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

  // ── New game ──────────────────────────────────────────────
  void newGame(String playerName, String nationality, String hometown, PlayStyle style, String hand, Sport sport, int birthYear) {
    state              = new GameState();
    state.currentSport = sport;
    state.sportConfig  = getSportConfig(sport);
    state.player       = new Player(playerName, nationality, birthYear, style, hand);
    state.player.hometown = hometown;
    state.currentYear  = birthYear;   // start from birth
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

    switchTo(ScreenID.CAREER);
  }
}

// ── Game State ────────────────────────────────────────────
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
}

// ── Ranking System ────────────────────────────────────────
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
// ============================================================
// LIFE SCREEN  —  BitLife-style main gameplay view
// ============================================================
class LifeScreen extends BaseScreen {

  ActivityCategory selectedCategory = null;
  boolean hoverAgeUp = false;

  // Layout constants
  final float LP_X = 15,  LP_W = 265;
  final float MP_X = 290, MP_W = 520;
  final float RP_X = 820, RP_W = 365;
  final float PY   = 62,  PH   = 700;

  LifeScreen(GameEngine e) { super(e); }

  // ── Phase-aware category labels ───────────────────────────
  String[] getCatLabels() {
    LifePhase phase = engine.state.lifePhase;
    if (phase == LifePhase.CHILDHOOD)    return new String[]{"PLAY","LEARN","FAMILY","HEALTH","POCKET $","SOCIAL"};
    if (phase == LifePhase.EARLY_SCHOOL) return new String[]{"SPORT","STUDY","FAMILY","HEALTH","MONEY","SOCIAL"};
    if (phase == LifePhase.TEEN)         return new String[]{"SPORT","STUDY","LOVE","HEALTH","MONEY","SOCIAL"};
    if (phase == LifePhase.UNIVERSITY)   return new String[]{"SPORT","STUDY","LOVE & FAM.","HEALTH","FINANCES","SOCIAL"};
    return new String[]{"CAREER","TRAINING","LOVE & FAM.","HEALTH","ASSETS","SOCIAL"};
  }

  String[][] getSubLabels() {
    LifePhase phase = engine.state.lifePhase;
    switch (phase) {
      case CHILDHOOD:
        return new String[][]{
          {"Play Outside","Draw & Create","Watch Sports","Play Sport"},
          {"Read Books","Do Homework","Explore Nature","Build Things"},
          {"Family Time","Talk to Mom","Talk to Dad","Visit Family"},
          {"Eat Healthy","Doctor Visit","Sleep Early","Rest Day"},
          {"Save Allowance","Buy a Toy","Help at Home","Yard Sale"},
          {"Make a Friend","Birthday Party","School Show","Join Club"}
        };
      case EARLY_SCHOOL:
        return new String[][]{
          {"School Sports","Youth Training","Watch Pro Match","School Tournament"},
          {"Study Hard","Library Time","Creative Project","Extra Tutoring"},
          {"Hang with Friends","Call Parents","Family Dinner","Visit Family"},
          {"Eat Healthy","Doctor Checkup","Physical Training","Rest Day"},
          {"Do Chores","Save Up","Buy Equipment","Part-time Help"},
          {"Post Clip Online","Local Event","Community Work","School Club"}
        };
      case TEEN:
        return new String[][]{
          {"Youth Tournament","Approach Scout","Hire First Coach","Amateur Match"},
          {"Intensive Drill","Light Practice","Mental Focus","Recovery Week"},
          {"Go on a Date","Call Parents","Family Time","Date Together"},
          {"Doctor Checkup","Start Therapy","Hit the Gym","Rest Day"},
          {"Part-time Job","Buy Equipment","Save Up","First Investment"},
          {"Post on Socials","Charity Work","Give Interview","Seek Sponsor"}
        };
      case UNIVERSITY:
        return new String[][]{
          {"Collegiate Match","Approach Scout","Hire Coach","Study Film"},
          {"Intensive Training","Light Practice","Mental Coaching","Recovery Week"},
          {"Go on a Date","Call Parents","Family Dinner","Date Together"},
          {"Doctor Checkup","Start Therapy","Hit the Gym","Spa Day"},
          {"Part-time Job","Invest Savings","Apply Scholarship","Buy Equipment"},
          {"Post on Socials","Charity Event","Give Interview","Seek Sponsor"}
        };
      default: // PRO
        return new String[][]{
          {"Enter Tournament","Seek Sponsor","Hire Coach","Press Day"},
          {"Intensive Training","Light Practice","Mental Coaching","Recovery Week"},
          {"Go on a Date","Spend Time Together","Have a Child","Call Family"},
          {"Doctor Checkup","Start Therapy","Hit the Gym","Spa Day"},
          {"Buy Property","Invest Money","Buy a Car","Start Business"},
          {"Post on Socials","Charity Event","Give Interview","Hire PR Manager"}
        };
    }
  }

  String[][] getActionKeys() {
    LifePhase phase = engine.state.lifePhase;
    switch (phase) {
      case CHILDHOOD:
        return new String[][]{
          {"play_outside","draw","watch_sport","play_sport"},
          {"read","homework","explore","build"},
          {"family_time","talk_mom","talk_dad","visit_family"},
          {"eat_healthy","doctor_visit","sleep","rest"},
          {"save_allowance","buy_toy","help_home","help_home"},
          {"make_friend","make_friend","make_friend","join_club"}
        };
      case EARLY_SCHOOL:
        return new String[][]{
          {"school_sports","youth_train","watch_pro","school_tourney"},
          {"study_hard","library","creative","tutor"},
          {"hangout","call_parents","family_dinner","family_time"},
          {"eat_healthy","doctor_visit","phys_train","rest"},
          {"chores","save_up","buy_equip","part_time_help"},
          {"post_clip","local_event","community","school_club"}
        };
      case TEEN:
        return new String[][]{
          {"youth_tourney","approach_scout","hire_first_coach","amateur_match"},
          {"intensive","light","mental","recovery"},
          {"date","call_parents","family_time","date_together"},
          {"doctor","therapy","gym","rest"},
          {"part_time_job","buy_equip","save_up","first_invest"},
          {"post","charity","interview","sponsor"}
        };
      case UNIVERSITY:
        return new String[][]{
          {"collegiate_match","approach_scout","hire_coach","study_film"},
          {"intensive","light","mental","recovery"},
          {"date","call_parents","family_dinner","date_together"},
          {"doctor","therapy","gym","spa"},
          {"part_time_job","invest","scholarship","buy_equip"},
          {"post","charity","interview","sponsor"}
        };
      default: // PRO
        return new String[][]{
          {"tournament","sponsor","coach","media"},
          {"intensive","light","mental","recovery"},
          {"date","propose","child","family"},
          {"doctor","therapy","gym","spa"},
          {"property","invest","car","business"},
          {"post","charity","interview","pr"}
        };
    }
  }

  // ── Main render ───────────────────────────────────────────
  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    // Pull any freshly-generated AI event
    if (engine.ai.hasNewEvent && engine.state.pendingEvent == null) {
      engine.state.pendingEvent = engine.ai.pendingEvent;
      engine.ai.hasNewEvent     = false;
    }

    drawLeftPanel(p);
    drawMiddlePanel(p);
    drawRightPanel(p);
  }

  // ════════════════════════════════════════════════════════
  // LEFT PANEL  —  player vitals
  // ════════════════════════════════════════════════════════
  void drawLeftPanel(Player p) {
    float x = LP_X, y = PY, w = LP_W;
    theme.drawCard(x, y, w, PH);

    // Name + age
    fill(theme.TEXT); textSize(17); textAlign(LEFT, TOP);
    text(p.name, x + 14, y + 14);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("Age " + p.age(engine.state.currentYear) + "  ·  " + p.nationality, x + 14, y + 36);
    if (!p.hometown.isEmpty()) {
      fill(theme.TEXT_DIM); textSize(9);
      text(p.hometown, x + 14, y + 48);
    }

    // Phase badge
    LifePhase phase = engine.state.lifePhase;
    SportConfig sc  = engine.state.sportConfig != null ? engine.state.sportConfig : getSportConfig(Sport.TENNIS);
    fill(theme.ACCENT, 35); rect(x + 14, y + 60, w - 28, 20, 10); noStroke();
    fill(theme.ACCENT); textSize(9); textAlign(CENTER, CENTER);
    if (phase == LifePhase.PRO) {
      text(engine.state.currentSport.toString() + "  ·  " + sc.rankLabel + " #" + p.career.worldRanking, x + w/2, y + 70);
    } else {
      text(phaseLabel(phase) + "  ·  Age " + p.age(engine.state.currentYear), x + w/2, y + 70);
    }

    y += 90;

    // BitLife stat bars
    statBar("HEALTH",    p.wellbeing, theme.SUCCESS,   x + 14, y, w - 28); y += 36;
    statBar("HAPPINESS", p.happiness, theme.TEXT_GOLD, x + 14, y, w - 28); y += 36;
    statBar("SMARTS",    p.smarts,    theme.ACCENT2,   x + 14, y, w - 28); y += 36;
    statBar("LOOKS",     p.looks,     theme.PURPLE,    x + 14, y, w - 28); y += 36;

    // Divider
    stroke(theme.BORDER); strokeWeight(1);
    line(x + 14, y + 4, x + w - 14, y + 4); noStroke();
    y += 14;

    if (phase == LifePhase.PRO) {
      // Career stats
      fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
      text("TITLES", x + 14, y); text("GS", x + 80, y); text("PRIZE MONEY", x + 120, y);
      y += 14;
      fill(theme.ACCENT); textSize(20); textAlign(LEFT, TOP); text("" + p.career.titlesWon, x + 14, y);
      fill(theme.TEXT); textSize(20); text("" + p.career.grandSlamTitles, x + 80, y);
      fill(theme.SUCCESS); textSize(14); text(formatMoney(p.career.prizeMoney), x + 120, y);
      y += 34;

      fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP); text("SAVINGS", x + 14, y); y += 13;
      fill(p.finances.savings > 50000 ? theme.SUCCESS : theme.DANGER); textSize(16);
      text(formatMoney(p.finances.savings), x + 14, y); y += 28;

      color mCol = p.mentalHealth.stateColor();
      fill(mCol, 35); rect(x + 14, y, w - 28, 22, 4);
      fill(mCol); textSize(9); textAlign(CENTER, CENTER);
      text("MENTAL: " + p.mentalHealth.stateLabel().toUpperCase(), x + w/2, y + 11); y += 30;

      if (p.health.status != InjuryStatus.HEALTHY) {
        fill(theme.DANGER, 35); rect(x + 14, y, w - 28, 22, 4);
        fill(theme.DANGER); textSize(9); textAlign(CENTER, CENTER);
        text("INJURED: " + p.health.injuredPart + " (" + p.health.weeksRemaining + "wk)", x + w/2, y + 11); y += 30;
      }

      if (p.family.spouse != null) {
        fill(theme.FAMILY, 35); rect(x + 14, y, w - 28, 22, 4);
        fill(theme.FAMILY); textSize(9); textAlign(CENTER, CENTER);
        text("♥  " + p.family.statusDisplay(), x + w/2, y + 11); y += 30;
      }

      // Kids
      if (!p.family.children.isEmpty()) {
        for (Child c : p.family.children) {
          fill(theme.ACCENT2, 30); rect(x + 14, y, w - 28, 18, 4);
          fill(theme.ACCENT2); textSize(8); textAlign(CENTER, CENTER);
          text("Child: " + c.name + " (age " + c.age + ")", x + w/2, y + 9); y += 22;
        }
      }

    } else {
      // EARLY LIFE stats
      fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
      text("SAVINGS", x + 14, y); y += 13;
      fill(theme.SUCCESS); textSize(14);
      text(formatMoney(p.finances.savings), x + 14, y); y += 28;

      // Parents
      if (p.parents != null) {
        fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
        text("PARENTS", x + 14, y); y += 14;

        fill(p.parents.mom.relationshipColor(), 35); rect(x + 14, y, w - 28, 22, 4);
        fill(p.parents.mom.relationshipColor()); textSize(8); textAlign(LEFT, CENTER);
        text("Mom: " + p.parents.mom.name, x + 18, y + 11);
        textAlign(RIGHT, CENTER);
        text(p.parents.mom.relationshipLabel(), x + w - 18, y + 11); y += 26;

        fill(p.parents.dad.relationshipColor(), 35); rect(x + 14, y, w - 28, 22, 4);
        fill(p.parents.dad.relationshipColor()); textSize(8); textAlign(LEFT, CENTER);
        text("Dad: " + p.parents.dad.name, x + 18, y + 11);
        textAlign(RIGHT, CENTER);
        text(p.parents.dad.relationshipLabel(), x + w - 18, y + 11); y += 30;
      }

      if (p.family.spouse != null) {
        fill(theme.FAMILY, 35); rect(x + 14, y, w - 28, 22, 4);
        fill(theme.FAMILY); textSize(9); textAlign(CENTER, CENTER);
        text("♥  " + p.family.statusDisplay(), x + w/2, y + 11); y += 28;
      }
    }

    // Sport attributes (compact) — shown for all phases
    y += 4;
    stroke(theme.BORDER); strokeWeight(1);
    line(x + 14, y, x + w - 14, y); noStroke(); y += 8;
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
    text("SPORT ATTRIBUTES", x + 14, y); y += 14;

    PlayerAttributes eff = p.effectiveAttributes();
    String[] statNames = sc.statNames;
    int[] statVals = {eff.serve, eff.forehand, eff.backhand, eff.volley, eff.speed, eff.stamina, eff.mental};
    for (int i = 0; i < min(statNames.length, 7); i++) {
      if (y > PY + PH - 20) break;
      fill(theme.TEXT_DIM); textSize(8); textAlign(LEFT, TOP); text(statNames[i], x + 14, y);
      fill(theme.TEXT); textAlign(RIGHT, TOP); text("" + statVals[i], x + w - 14, y);
      theme.drawStatBar(x + 14, y + 9, w - 28, 4, statVals[i], 99, theme.ACCENT2);
      y += 20;
    }
  }

  String phaseLabel(LifePhase phase) {
    switch (phase) {
      case CHILDHOOD:    return "CHILDHOOD";
      case EARLY_SCHOOL: return "SCHOOL";
      case TEEN:         return "TEENAGER";
      case UNIVERSITY:   return "UNIVERSITY";
      default:           return "PRO";
    }
  }

  void statBar(String label, float val, color col, float x, float y, float w) {
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP); text(label, x, y);
    fill(col); textAlign(RIGHT, TOP); text((int)val + "%", x + w, y);
    theme.drawStatBar(x, y + 12, w, 11, val, 100, col);
  }

  // ════════════════════════════════════════════════════════
  // MIDDLE PANEL  —  life feed / active event card
  // ════════════════════════════════════════════════════════
  void drawMiddlePanel(Player p) {
    float x = MP_X, y = PY, w = MP_W;

    if (engine.ai.isLoading) {
      theme.drawCard(x, y, w, 52, true);
      fill(theme.ACCENT); textSize(12); textAlign(CENTER, CENTER);
      text(engine.ai.getLoadingText(), x + w/2, y + 26);
      y += 58;
    }

    if (engine.state.pendingEvent != null) {
      drawEventPanel(engine.state.pendingEvent, x, y, w);
    } else {
      float cardH = PH - (engine.ai.isLoading ? 58 : 0);
      theme.drawCard(x, y, w, cardH);

      if (!engine.state.lastEvent.isEmpty()) {
        fill(theme.ACCENT, 28); noStroke(); rect(x + 10, y + 10, w - 20, 32, 4);
        fill(theme.ACCENT); textSize(11); textAlign(LEFT, CENTER);
        text("» " + engine.state.lastEvent, x + 20, y + 26); y += 46;
      } else { y += 10; }

      fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP); text("LIFE FEED", x + 14, y + 8);
      stroke(theme.BORDER); line(x + 14, y + 20, x + w - 14, y + 20); noStroke();
      y += 28;

      ArrayList<String> feed = p.lifeEvents;
      if (feed.isEmpty()) {
        fill(theme.TEXT_DIM); textSize(13); textAlign(CENTER, CENTER);
        text("Your story begins here.", x + w/2, y + 80);
        fill(theme.TEXT_DIM); textSize(10); textAlign(CENTER, CENTER);
        text("Press SPACE to age up, or use the activity panel.", x + w/2, y + 104);
      } else {
        float fy = y;
        for (int i = 0; i < feed.size(); i++) {
          if (fy > PY + PH - 24) break;
          String entry = feed.get(i);
          color dot = feedColor(entry);
          fill(i == 0 ? theme.PANEL_2 : color(0,0,0,0));
          if (i == 0) rect(x + 8, fy - 2, w - 16, 22, 3);
          fill(dot); noStroke(); ellipse(x + 20, fy + 9, 7, 7);
          fill(i == 0 ? theme.TEXT : theme.TEXT_DIM);
          textSize(i == 0 ? 11 : 10); textAlign(LEFT, TOP);
          text(entry, x + 32, fy);
          fy += (i == 0 ? 24 : 20);
        }
      }

      if (engine.state.lifePhase == LifePhase.PRO && !p.career.recentResults.isEmpty()) {
        float ry = PY + PH - 150;
        stroke(theme.BORDER); line(x + 14, ry, x + w - 14, ry); noStroke();
        ry += 8;
        fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP); text("RECENT RESULTS", x + 14, ry); ry += 16;
        int shown = min(3, p.career.recentResults.size());
        for (int i = 0; i < shown; i++) {
          MatchResult r = p.career.recentResults.get(i);
          fill(r.playerWon ? color(20,45,25) : color(45,20,20)); rect(x + 8, ry, w - 16, 28, 3);
          fill(r.playerWon ? theme.SUCCESS : theme.DANGER); textSize(10); textAlign(LEFT, CENTER);
          text(r.playerWon ? "W" : "L", x + 18, ry + 14);
          fill(theme.TEXT); textAlign(LEFT, CENTER); text(r.tournamentName + " " + r.round, x + 34, ry + 14);
          fill(theme.TEXT_DIM); textAlign(RIGHT, CENTER); text(r.score + " +" + r.rankingPointsAwarded + "pts", x + w - 14, ry + 14);
          ry += 32;
        }
      }
    }
  }

  color feedColor(String e) {
    String low = e.toLowerCase();
    if (low.contains("won") || low.contains("title") || low.contains("champion") || low.contains("graduated")) return theme.ACCENT;
    if (low.contains("born") || low.contains("married") || low.contains("partner") || low.contains("child") || low.contains("love") || low.contains("dating") || low.contains("engaged")) return theme.FAMILY;
    if (low.contains("injur") || low.contains("hospital") || low.contains("surgery")) return theme.DANGER;
    if (low.contains("$") || low.contains("deal") || low.contains("money") || low.contains("invest") || low.contains("pts")) return theme.SUCCESS;
    if (low.contains("scandal") || low.contains("legal") || low.contains("court") || low.contains("charged")) return theme.DANGER;
    if (low.contains("mom") || low.contains("dad") || low.contains("parent") || low.contains("family")) return theme.FAMILY;
    return theme.ACCENT2;
  }

  // ════════════════════════════════════════════════════════
  // RIGHT PANEL  —  activity grid + Age Up
  // ════════════════════════════════════════════════════════
  void drawRightPanel(Player p) {
    float x = RP_X, w = RP_W, y = PY;
    theme.drawCard(x, y, w, PH);

    // AGE UP button
    float btnY = PY + PH - 62;
    hoverAgeUp = theme.isHover(hoverX, hoverY, x + 10, btnY, w - 20, 50);
    fill(hoverAgeUp ? theme.ACCENT : color(40, 52, 28));
    stroke(theme.ACCENT); strokeWeight(hoverAgeUp ? 2 : 1);
    rect(x + 10, btnY, w - 20, 50, 6); noStroke();
    fill(hoverAgeUp ? theme.BG : theme.ACCENT);
    textSize(13); textAlign(CENTER, CENTER);
    String btnLabel = engine.state.lifePhase == LifePhase.PRO ? "▶  AGE UP  [SPACE]" : "▶  NEXT YEAR  [SPACE]";
    text(btnLabel, x + w/2, btnY + 25);

    fill(theme.TEXT_DIM); textSize(9); textAlign(CENTER, TOP);
    text(engine.state.currentYear + "  ·  Age " + p.age(engine.state.currentYear), x + w/2, btnY + 56);

    // Activity area
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
    text("ACTIVITIES", x + 14, y + 14);
    float ay = y + 30;

    if (selectedCategory == null) {
      drawActivityGrid(x + 10, ay, w - 20);
    } else {
      drawSubMenu(x + 10, ay, w - 20);
    }

    // Summary box below grid/submenu
    float sumY = y + 30 + 248;
    fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP);
    text("OVERVIEW", x + 14, sumY); sumY += 14;
    fill(theme.PANEL_2); rect(x + 10, sumY, w - 20, 90, 4); sumY += 10;

    if (engine.state.lifePhase == LifePhase.PRO) {
      fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP); text("RANKING POINTS", x + 18, sumY);
      fill(theme.ACCENT); textSize(15); text("" + p.career.rankingPoints, x + 18, sumY + 12);
      fill(theme.TEXT_DIM); textSize(9); text("FATIGUE", x + 18, sumY + 34);
      theme.drawStatBar(x + 18, sumY + 46, w - 36, 8, p.form.fatigue, 100, p.form.fatigue > 70 ? theme.DANGER : theme.ACCENT2);
      fill(theme.TEXT_DIM); textSize(9); text("CONFIDENCE", x + 130, sumY + 34);
      theme.drawStatBar(x + 130, sumY + 46, w - 148, 8, p.form.confidence, 100, theme.SUCCESS);
    } else {
      fill(theme.TEXT_DIM); textSize(9); textAlign(LEFT, TOP); text("SMARTS", x + 18, sumY);
      theme.drawStatBar(x + 18, sumY + 12, w - 36, 8, p.smarts, 100, theme.ACCENT2);
      fill(theme.TEXT_DIM); textSize(9); text("SAVINGS", x + 18, sumY + 34);
      fill(p.finances.savings > 500 ? theme.SUCCESS : theme.TEXT_DIM); textSize(12); textAlign(LEFT, TOP);
      text(formatMoney(p.finances.savings), x + 18, sumY + 46);
      if (p.parents != null) {
        fill(theme.TEXT_DIM); textSize(8); textAlign(RIGHT, TOP);
        text("Mom: " + (int)p.parents.mom.relationship + "  Dad: " + (int)p.parents.dad.relationship, x + w - 18, sumY + 70);
      }
    }
  }

  void drawActivityGrid(float x, float y, float w) {
    String[] labels = getCatLabels();
    color[] cols = {theme.ACCENT, theme.ACCENT2, theme.FAMILY, theme.SUCCESS, theme.TEXT_GOLD, theme.PURPLE};
    float bw = (w - 6) / 2f, bh = 68;
    for (int i = 0; i < 6; i++) {
      int col = i % 2, row = i / 2;
      float bx = x + col * (bw + 6);
      float by = y + row * (bh + 6);
      boolean hov = theme.isHover(hoverX, hoverY, bx, by, bw, bh);
      fill(hov ? cols[i] : theme.PANEL_2);
      stroke(hov ? cols[i] : theme.BORDER); strokeWeight(1);
      rect(bx, by, bw, bh, 6); noStroke();
      fill(hov ? theme.BG : cols[i]);
      textSize(10); textAlign(CENTER, CENTER);
      text(labels[i], bx + bw/2, by + bh/2 - 6);
      fill(hov ? theme.BG : color(red(cols[i]), green(cols[i]), blue(cols[i]), 120));
      textSize(9); text("▸ tap", bx + bw/2, by + bh/2 + 10);
    }
  }

  void drawSubMenu(float x, float y, float w) {
    int catIdx = catIndex();
    String[][] subLabels = getSubLabels();
    color col  = catColor(catIdx);
    String[] labels = subLabels[catIdx];

    boolean hBack = theme.isHover(hoverX, hoverY, x, y, 70, 26);
    fill(hBack ? theme.PANEL_2 : theme.PANEL);
    stroke(theme.BORDER); strokeWeight(1); rect(x, y, 70, 26, 4); noStroke();
    fill(theme.TEXT_DIM); textSize(9); textAlign(CENTER, CENTER); text("← BACK", x + 35, y + 13);

    String[] catLabels = getCatLabels();
    fill(col); textSize(12); textAlign(LEFT, CENTER); text(catLabels[catIdx], x + 80, y + 13);
    y += 34;

    for (int i = 0; i < labels.length; i++) {
      boolean hov = theme.isHover(hoverX, hoverY, x, y, w, 48);
      fill(hov ? col : theme.PANEL_2); stroke(hov ? col : theme.BORDER); strokeWeight(1);
      rect(x, y, w, 48, 4); noStroke();
      fill(hov ? theme.BG : theme.TEXT); textSize(12); textAlign(LEFT, CENTER);
      text(labels[i], x + 14, y + 24); y += 54;
    }
  }

  int catIndex() {
    ActivityCategory[] cats = ActivityCategory.values();
    for (int i = 0; i < cats.length; i++) {
      if (cats[i] == selectedCategory) return i;
    }
    return 0;
  }

  color catColor(int i) {
    color[] cols = {theme.ACCENT, theme.ACCENT2, theme.FAMILY, theme.SUCCESS, theme.TEXT_GOLD, theme.PURPLE};
    return cols[constrain(i, 0, cols.length - 1)];
  }

  // ════════════════════════════════════════════════════════
  // INPUT
  // ════════════════════════════════════════════════════════
  void onClick(int mx, int my) {
    float btnY = PY + PH - 62;
    if (theme.isHover(mx, my, RP_X + 10, btnY, RP_W - 20, 50)) {
      engine.ageUp(); return;
    }

    if (engine.state.pendingEvent != null && engine.state.pendingEvent.choices != null) {
      ArrayList<EventChoice> choices = engine.state.pendingEvent.choices;
      float ex = MP_X, ey = PY + 118;
      for (int i = 0; i < choices.size(); i++) {
        if (theme.isHover(mx, my, ex + 14, ey, MP_W - 28, 54)) {
          engine.applyChoice(choices.get(i)); return;
        }
        ey += 64;
      }
      return;
    }

    float ax = RP_X + 10, aw = RP_W - 20, ay = PY + 30;
    if (selectedCategory == null) {
      float bw = (aw - 6) / 2f, bh = 68;
      ActivityCategory[] cats = ActivityCategory.values();
      for (int i = 0; i < 6; i++) {
        int c = i % 2, r = i / 2;
        float bx = ax + c * (bw + 6);
        float by = ay + r * (bh + 6);
        if (theme.isHover(mx, my, bx, by, bw, bh)) {
          selectedCategory = cats[i]; return;
        }
      }
    } else {
      if (theme.isHover(mx, my, ax, ay, 70, 26)) { selectedCategory = null; return; }
      ay += 34;
      String[][] subLabels = getSubLabels();
      int catIdx = catIndex();
      for (int i = 0; i < subLabels[catIdx].length; i++) {
        if (theme.isHover(mx, my, ax, ay, aw, 48)) {
          dispatchActivity(catIdx, i); selectedCategory = null; return;
        }
        ay += 54;
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

    // Tab bar
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
    theme.drawCard(x, y, 560, 120);

    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("RELATIONSHIP STATUS", x + 20, y + 14);
    fill(theme.TEXT); textSize(16);
    text(fam.statusDisplay(), x + 20, y + 30);

    if (fam.spouse != null) {
      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
      text("FAMILY HAPPINESS", x + 300, y + 14);
      theme.drawStatBar(x + 300, y + 28, 240, 10, fam.familyHappiness, 100,
        fam.familyHappiness > 60 ? theme.SUCCESS : (fam.familyHappiness > 30 ? theme.ACCENT : theme.DANGER));

      fill(theme.TEXT_DIM); textSize(10);
      text("PARTNER RESENTMENT", x + 300, y + 56);
      theme.drawStatBar(x + 300, y + 70, 240, 10, fam.spouse.resentment, 100, theme.DANGER);
    }

    if (!fam.children.isEmpty()) {
      y += 136;
      sectionHeader("CHILDREN", x, y); y += 20;
      for (Child c : fam.children) {
        theme.drawCard(x, y, 560, 50);
        fill(theme.TEXT); textSize(13); textAlign(LEFT, CENTER);
        text(c.name + "  (age " + c.age + ")", x + 16, y + 25);
        fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
        text("Happiness:", x + 320, y + 14);
        theme.drawStatBar(x + 320, y + 28, 220, 8, c.happiness, 100, theme.SUCCESS);
        y += 56;
      }
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
// ============================================================
// MAIN MENU SCREEN
// ============================================================
class MainMenuScreen extends BaseScreen {
  // Form state
  String    playerName    = "Alex Reyes";
  String    nationality   = "Spain";
  PlayStyle playStyle     = PlayStyle.ALL_COURT;
  String    dominantSide  = "Right";
  Sport     selectedSport = Sport.TENNIS;
  int       focusedField  = -1;

  // Nationality dropdown
  final String[] natOptions = {
    "Algeria","Argentina","Australia","Austria","Belgium","Bolivia",
    "Brazil","Cameroon","Canada","Chile","China","Colombia",
    "Costa Rica","Croatia","Cuba","Czech Republic","Denmark","Ecuador",
    "Egypt","England","Finland","France","Germany","Ghana",
    "Greece","Honduras","Hungary","India","Ireland","Ivory Coast",
    "Jamaica","Japan","Kazakhstan","Kenya","Latvia","Lithuania",
    "Mexico","Morocco","Netherlands","New Zealand","N. Ireland","Nigeria",
    "Norway","Panama","Paraguay","Peru","Philippines","Poland",
    "Portugal","Puerto Rico","Romania","Russia","Scotland","Senegal",
    "Serbia","Slovakia","Slovenia","South Africa","South Korea","Spain",
    "Sweden","Switzerland","Trinidad & Tobago","Tunisia","Turkey",
    "Ukraine","Uruguay","USA","Venezuela","Wales"
  };
  boolean dropdownOpen   = false;
  int     dropdownScroll = 0;
  float   dropListX, dropListY, dropListW; // set each render frame
  final int VISIBLE_ITEMS = 8;
  final float ITEM_H      = 30;

  // Hometown + birth year
  String  hometown      = "Madrid";
  int     birthYear     = 2000;
  boolean hoverHometown = false;

  // Hover tracking
  boolean hoverStart = false;
  boolean hoverName  = false;

  // Animation
  float titleY    = -80;
  float formAlpha = 0;

  MainMenuScreen(GameEngine e) { super(e); }

  void onEnter() { titleY = -80; formAlpha = 0; }

  SportConfig cfg() { return getSportConfig(selectedSport); }

  // ── Render ───────────────────────────────────────────────
  void render() {
    drawGrid();

    titleY    = lerp(titleY,    90,  0.06);
    formAlpha = lerp(formAlpha, 255, 0.04);

    fill(theme.ACCENT);
    textSize(46); textAlign(CENTER, CENTER);
    text("CAREER LEGACY", width / 2, titleY);

    fill(theme.TEXT_DIM);
    textSize(12);
    text(cfg().subtitle + "  ·  POWERED BY AI", width / 2, titleY + 46);
    fill(CLAUDE_API_KEY.isEmpty() ? theme.DANGER : theme.SUCCESS);
    textSize(10);
    text(CLAUDE_API_KEY.isEmpty() ? "● AI KEY NOT SET — procedural events only" : "● AI ACTIVE", width/2, titleY + 62);

    stroke(theme.BORDER); strokeWeight(1);
    line(width / 2 - 160, titleY + 76, width / 2 + 160, titleY + 76);
    noStroke();

    float cardW = 520, cardH = 570;
    float cx = width / 2 - cardW / 2;
    float cy = 155;
    tint(255, formAlpha);
    drawFormCard(cx, cy, cardW, cardH);
    noTint();
  }

  void drawGrid() {
    stroke(20, 28, 42); strokeWeight(1);
    for (int x = 0; x < width; x += 40) line(x, 0, x, height);
    for (int y = 0; y < height; y += 40) line(0, y, width, y);
    noStroke();
    for (int r = 300; r > 0; r -= 30) {
      fill(theme.ACCENT, map(r, 0, 300, 0, 8));
      ellipse(width / 2, height / 2, r * 2, r * 2);
    }
  }

  void drawFormCard(float cx, float cy, float cw, float ch) {
    fill(theme.PANEL); stroke(theme.BORDER); strokeWeight(1);
    rect(cx, cy, cw, ch, 8);
    noStroke();

    fill(theme.TEXT_DIM); textSize(11); textAlign(LEFT, TOP);
    text("CREATE YOUR PLAYER", cx + 20, cy + 16);

    float fy = cy + 40;
    float fw = cw - 40;

    // ── Sport selector ──────────────────────────────────────
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("SELECT SPORT", cx + 20, fy);
    fy += 16;

    Sport[] sports = Sport.values();
    float sw2 = (fw - (sports.length - 1) * 3) / sports.length;
    for (int i = 0; i < sports.length; i++) {
      boolean sel = sports[i] == selectedSport;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * (sw2 + 3), fy, sw2, 34, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(11); textAlign(CENTER, CENTER);
      text(sportLabel(sports[i]), cx + 20 + i * (sw2 + 3) + sw2 / 2, fy + 17);
    }
    fy += 48;

    // ── Player name ─────────────────────────────────────────
    drawField("PLAYER NAME", playerName, cx + 20, fy, fw, focusedField == 0 || hoverName);
    fy += 64;

    // ── Nationality dropdown (closed view) ──────────────────
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("NATIONALITY", cx + 20, fy);

    boolean natHover = !dropdownOpen && theme.isHover(hoverX, hoverY, cx + 20, fy + 16, fw, 36);
    fill(dropdownOpen ? theme.PANEL_2 : (natHover ? theme.PANEL_2 : color(16, 20, 28)));
    stroke(dropdownOpen ? theme.ACCENT : (natHover ? theme.ACCENT2 : theme.BORDER));
    strokeWeight(1);
    rect(cx + 20, fy + 16, fw, 36, dropdownOpen ? 4 : 4);
    noStroke();

    fill(theme.TEXT); textSize(14); textAlign(LEFT, CENTER);
    text(nationality, cx + 20 + 12, fy + 16 + 18);

    fill(theme.TEXT_DIM); textSize(11); textAlign(RIGHT, CENTER);
    text(dropdownOpen ? "▲" : "▼", cx + 20 + fw - 14, fy + 16 + 18);

    // Store list position for click detection and rendering
    dropListX = cx + 20;
    dropListY = fy + 16 + 36 + 2;
    dropListW = fw;
    fy += 64;

    // ── Hometown ────────────────────────────────────────────
    drawField("HOMETOWN / CITY", hometown, cx + 20, fy, fw, focusedField == 1 || hoverHometown);
    fy += 64;

    // ── Birth year ──────────────────────────────────────────
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("BIRTH YEAR", cx + 20, fy);
    fy += 16;
    int[] birthYears   = {1990, 1993, 1995, 1998, 2000, 2002, 2005};
    String[] yearShort = {"'90","'93","'95","'98","'00","'02","'05"};
    float byw = (fw - (birthYears.length - 1) * 3) / birthYears.length;
    for (int i = 0; i < birthYears.length; i++) {
      boolean sel = birthYears[i] == birthYear;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * (byw + 3), fy, byw, 32, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(10); textAlign(CENTER, CENTER);
      text(yearShort[i], cx + 20 + i * (byw + 3) + byw / 2, fy + 16);
    }
    fy += 48;

    // ── Role / Play Style ───────────────────────────────────
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text(cfg().name.toUpperCase() + " STYLE / ROLE", cx + 20, fy);
    fy += 16;

    PlayStyle[] styles = PlayStyle.values();
    String[] shorts = cfg().roleShort;
    float sw = (fw - (styles.length - 1) * 3) / styles.length;
    for (int i = 0; i < styles.length; i++) {
      boolean sel = styles[i] == playStyle;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * (sw + 3), fy, sw, 36, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(10); textAlign(CENTER, CENTER);
      text(i < shorts.length ? shorts[i] : styles[i].toString(),
           cx + 20 + i * (sw + 3) + sw / 2, fy + 18);
    }
    fy += 50;

    // ── Dominant hand / foot (sport-specific label) ─────────
    String sideLabel = (selectedSport == Sport.SOCCER) ? "DOMINANT FOOT" : "DOMINANT HAND";
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text(sideLabel, cx + 20, fy);
    fy += 16;

    String[] sides = {"Right", "Left"};
    for (int i = 0; i < sides.length; i++) {
      boolean sel = sides[i].equals(dominantSide);
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER); strokeWeight(1);
      rect(cx + 20 + i * 100, fy, 90, 32, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(12); textAlign(CENTER, CENTER);
      text(sides[i], cx + 20 + i * 100 + 45, fy + 16);
    }
    fy += 50;

    // ── Begin button ────────────────────────────────────────
    boolean hover = theme.isHover(hoverX, hoverY, cx + 20, fy, fw, 48);
    hoverStart = hover;
    fill(hover ? theme.ACCENT : color(40, 50, 30));
    stroke(theme.ACCENT); strokeWeight(hover ? 2 : 1);
    rect(cx + 20, fy, fw, 48, 6);
    noStroke();
    fill(hover ? theme.BG : theme.ACCENT);
    textSize(14); textAlign(CENTER, CENTER);
    text("BEGIN YOUR LEGACY  →", cx + 20 + fw / 2, fy + 24);

    // ── Dropdown list (rendered last so it appears on top) ──
    if (dropdownOpen) {
      float listH = VISIBLE_ITEMS * ITEM_H;

      fill(color(18, 24, 36));
      stroke(theme.ACCENT); strokeWeight(1);
      rect(dropListX, dropListY, dropListW, listH, 4);
      noStroke();

      for (int i = 0; i < VISIBLE_ITEMS; i++) {
        int idx = dropdownScroll + i;
        if (idx >= natOptions.length) break;

        boolean sel = natOptions[idx].equals(nationality);
        boolean hov = theme.isHover(hoverX, hoverY, dropListX,
                                    dropListY + i * ITEM_H, dropListW, ITEM_H);

        if (sel) {
          fill(theme.ACCENT, 50);
          rect(dropListX, dropListY + i * ITEM_H, dropListW, ITEM_H);
        } else if (hov) {
          fill(theme.PANEL_2);
          rect(dropListX, dropListY + i * ITEM_H, dropListW, ITEM_H);
        }

        fill(sel ? theme.ACCENT : (hov ? theme.TEXT : theme.TEXT_DIM));
        textSize(13); textAlign(LEFT, CENTER);
        text(natOptions[idx], dropListX + 14, dropListY + i * ITEM_H + ITEM_H / 2);

        // Scroll hints on the top/bottom rows
        if (i == 0 && dropdownScroll > 0) {
          fill(theme.TEXT_DIM); textSize(9); textAlign(RIGHT, CENTER);
          text("▲ " + dropdownScroll + " above",
               dropListX + dropListW - 10, dropListY + ITEM_H / 2);
        }
        if (i == VISIBLE_ITEMS - 1 && idx < natOptions.length - 1) {
          fill(theme.TEXT_DIM); textSize(9); textAlign(RIGHT, CENTER);
          text((natOptions.length - idx - 1) + " more ▼",
               dropListX + dropListW - 10, dropListY + i * ITEM_H + ITEM_H / 2);
        }
      }

      // Redraw border on top of items
      noFill(); stroke(theme.ACCENT); strokeWeight(1);
      rect(dropListX, dropListY, dropListW, listH, 4);
      noStroke();
    }
  }

  void drawField(String label, String value, float x, float y, float w, boolean active) {
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text(label, x, y);

    fill(active ? theme.PANEL_2 : color(16, 20, 28));
    stroke(active ? theme.ACCENT2 : theme.BORDER); strokeWeight(1);
    rect(x, y + 16, w, 36, 4);
    noStroke();

    fill(theme.TEXT); textSize(14); textAlign(LEFT, CENTER);
    text(value, x + 12, y + 16 + 18);

    if (active && (frameCount / 30) % 2 == 0) {
      stroke(theme.TEXT); strokeWeight(1.5);
      float cx = x + 12 + textWidth(value) + 2;
      line(cx, y + 24, cx, y + 42);
      noStroke();
    }
  }

  String sportLabel(Sport s) {
    switch (s) {
      case TENNIS:     return "Tennis";
      case BASKETBALL: return "Basketball";
      case SOCCER:     return "Soccer";
      case GOLF:       return "Golf";
      case BOXING:     return "Boxing";
      default:         return s.toString();
    }
  }

  // ── Input handling ───────────────────────────────────────
  void onClick(int mx, int my) {
    float cx = width / 2 - 260, cy = 155;
    float fw = 480;

    // ── Dropdown open: consume all clicks ──────────────────
    if (dropdownOpen) {
      float listH = VISIBLE_ITEMS * ITEM_H;
      if (theme.isHover(mx, my, dropListX, dropListY, dropListW, listH)) {
        int clickedRow = (int)((my - dropListY) / ITEM_H);
        int selected   = dropdownScroll + clickedRow;
        if (selected >= 0 && selected < natOptions.length) {
          nationality = natOptions[selected];
        }
      }
      dropdownOpen = false;
      return;
    }

    // ── Sport buttons ──────────────────────────────────────
    Sport[] sports = Sport.values();
    float sw2 = (fw - (sports.length - 1) * 3) / sports.length;
    float sty2 = cy + 56;
    for (int i = 0; i < sports.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (sw2 + 3), sty2, sw2, 34)) {
        selectedSport = sports[i];
        playStyle     = PlayStyle.ALL_COURT;
        return;
      }
    }

    // ── Player name field ──────────────────────────────────
    if (theme.isHover(mx, my, cx + 20, cy + 120, fw, 36)) { focusedField = 0; return; }

    // ── Nationality dropdown field ─────────────────────────
    if (theme.isHover(mx, my, cx + 20, cy + 184, fw, 36)) {
      dropdownOpen = !dropdownOpen;
      focusedField = -1;
      if (dropdownOpen) scrollToSelected();
      return;
    }

    // ── Hometown field ─────────────────────────────────────
    if (theme.isHover(mx, my, cx + 20, cy + 248, fw, 36)) { focusedField = 1; return; }

    // ── Birth year buttons ─────────────────────────────────
    int[] birthYears = {1990, 1993, 1995, 1998, 2000, 2002, 2005};
    float byw = (fw - (birthYears.length - 1) * 3) / birthYears.length;
    float yy  = cy + 312;
    for (int i = 0; i < birthYears.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (byw + 3), yy, byw, 32)) {
        birthYear = birthYears[i]; return;
      }
    }

    // ── Role buttons ───────────────────────────────────────
    PlayStyle[] styles = PlayStyle.values();
    float sw  = (fw - (styles.length - 1) * 3) / styles.length;
    float styY = cy + 376;
    for (int i = 0; i < styles.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (sw + 3), styY, sw, 36)) {
        playStyle = styles[i]; return;
      }
    }

    // ── Dominant side buttons ──────────────────────────────
    float hy = cy + 442;
    if (theme.isHover(mx, my, cx + 20,  hy, 90, 32)) { dominantSide = "Right"; return; }
    if (theme.isHover(mx, my, cx + 120, hy, 90, 32)) { dominantSide = "Left";  return; }

    // ── Begin button ───────────────────────────────────────
    float by = cy + 492;
    if (theme.isHover(mx, my, cx + 20, by, fw, 48)) {
      if (!playerName.isEmpty()) {
        engine.newGame(playerName, nationality, hometown, playStyle, dominantSide, selectedSport, birthYear);
      }
    }

    focusedField = -1;
  }

  void onHover(int mx, int my) {
    super.onHover(mx, my);
    float cx = width / 2 - 260, cy = 155, fw = 480;
    hoverName     = theme.isHover(mx, my, cx + 20, cy + 120, fw, 36);
    hoverHometown = theme.isHover(mx, my, cx + 20, cy + 248, fw, 36);
  }

  void onKey(char k, int kc) {
    // Dropdown navigation
    if (dropdownOpen) {
      if (kc == UP)     { dropdownScroll = max(0, dropdownScroll - 1); return; }
      if (kc == DOWN)   { dropdownScroll = min(natOptions.length - VISIBLE_ITEMS, dropdownScroll + 1); return; }
      if (k == ESC) { dropdownOpen = false; return; }
      // Type to jump: find first match
      if (k != CODED && Character.isLetter(k)) {
        char ch = Character.toUpperCase(k);
        for (int i = 0; i < natOptions.length; i++) {
          if (natOptions[i].charAt(0) == ch) {
            dropdownScroll = max(0, min(i, natOptions.length - VISIBLE_ITEMS));
            break;
          }
        }
      }
      return;
    }

    // Player name / hometown typing
    if (focusedField == 0) {
      if (kc == BACKSPACE && playerName.length() > 0)
        playerName = playerName.substring(0, playerName.length() - 1);
      else if (k != CODED && k != ENTER && k != RETURN && k != BACKSPACE && playerName.length() < 24)
        playerName += k;
    } else if (focusedField == 1) {
      if (kc == BACKSPACE && hometown.length() > 0)
        hometown = hometown.substring(0, hometown.length() - 1);
      else if (k != CODED && k != ENTER && k != RETURN && k != BACKSPACE && hometown.length() < 30)
        hometown += k;
    }
  }

  void scrollToSelected() {
    for (int i = 0; i < natOptions.length; i++) {
      if (natOptions[i].equals(nationality)) {
        dropdownScroll = constrain(i - VISIBLE_ITEMS / 2, 0, natOptions.length - VISIBLE_ITEMS);
        return;
      }
    }
    dropdownScroll = 0;
  }
}
// ============================================================
// PLAYER DOMAIN — core + all life-sim subsystems
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

  // ── Life-sim systems ──────────────────────────────────────
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
  float              popularity          = 10;   // 0-100 general fame
  int                weeksInRelationship = 0;
  boolean            hasSocialManager    = false;
  String             hometown            = "";
  Parents            parents;

  // BitLife-style core life stats (0–100)
  float wellbeing  = 90;   // displayed as "HEALTH"
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

    // Randomise starting life stats (genes)
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
    lifeEvents.add(0, "[" + year + "] " + entry);   // most-recent first
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

// ── Attributes ─────────────────────────────────────────────
class PlayerAttributes {
  int serve, forehand, backhand, volley, speed, stamina, mental;
  PlayerAttributes(int sv, int fh, int bh, int vo, int sp, int st, int mn) {
    serve = sv; forehand = fh; backhand = bh; volley = vo;
    speed = sp; stamina = st; mental = mn;
  }
}

// ── Form ───────────────────────────────────────────────────
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

// ── Health ─────────────────────────────────────────────────
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

// ── Career stats ───────────────────────────────────────────
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

// ── Personality ────────────────────────────────────────────
class PlayerPersonality {
  float clutchFactor  = 50 + random(-15, 15);
  float aggression    = 50 + random(-15, 15);
  float consistency   = 50 + random(-15, 15);
  float adaptability  = 50 + random(-15, 15);
  float determination = 50 + random(-15, 15);
}

// ── Progression ────────────────────────────────────────────
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

// ── Family ─────────────────────────────────────────────────
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

// ── Coach ──────────────────────────────────────────────────
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

// ── Finances (extended) ────────────────────────────────────
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

// ── Rivalry ────────────────────────────────────────────────
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

// ── Reputation ─────────────────────────────────────────────
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

// ══════════════════════════════════════════════════════════
// LIFE-SIM SUBSYSTEMS
// ══════════════════════════════════════════════════════════

// ── Owned Property ──────────────────────────────────────────
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
    mortgageLeft  = price * 0.80; // 20% down

    float annualRate = 0.055 / 52.0; // ~5.5% mortgage
    switch (t) {
      case APARTMENT:  weeklyUpkeep = 200;  weeklyMortgage = mortgageLeft * annualRate; break;
      case CONDO:      weeklyUpkeep = 400;  weeklyMortgage = mortgageLeft * annualRate; break;
      case HOUSE:      weeklyUpkeep = 800;  weeklyMortgage = mortgageLeft * annualRate; break;
      case MANSION:    weeklyUpkeep = 3000; weeklyMortgage = mortgageLeft * annualRate; break;
      case BEACH_HOUSE:weeklyUpkeep = 2000; weeklyMortgage = mortgageLeft * annualRate; break;
      case PENTHOUSE:  weeklyUpkeep = 5000; weeklyMortgage = mortgageLeft * annualRate; break;
      default:         weeklyUpkeep = 500;  weeklyMortgage = mortgageLeft * annualRate; break;
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

// ── Owned Vehicle ──────────────────────────────────────────
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

// ── Owned Investment ────────────────────────────────────────
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

// ── Owned Business ──────────────────────────────────────────
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
    weeklyRevenue  = inv * 0.0022; // ~11.4% annual gross
    weeklyExpenses = inv * 0.0016; // ~8.3% overhead
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

// ── Mental Health ──────────────────────────────────────────
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
    stressLevel += random(-2, 3); // daily noise
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

// ── Social Media ───────────────────────────────────────────
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

// ── Agent Contract ─────────────────────────────────────────
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

// ── Award ──────────────────────────────────────────────────
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

// ── Parents ────────────────────────────────────────────────
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
// TRAINING SCREEN
// ============================================================
class TrainingScreen extends BaseScreen {
  String[] trainingTypes = {"SERVE", "FITNESS", "MENTAL", "TACTICAL"};
  float[]  intensities   = {60, 60, 60, 60};
  int      selectedType  = 0;

  boolean hoverTrain   = false;
  String  lastResult   = "";
  float   resultAlpha  = 0;

  TrainingScreen(GameEngine e) { super(e); }

  void render() {
    Player p = engine.state.player;
    if (p == null) return;

    float x = 30, y = 70;
    fill(theme.ACCENT);
    textSize(22);
    textAlign(LEFT, TOP);
    text("TRAINING CAMP", x, y);

    fill(theme.TEXT_DIM);
    textSize(12);
    text("Design your training week. Higher intensity = more gains, more fatigue.", x, y + 30);

    y += 70;
    for (int i = 0; i < trainingTypes.length; i++) {
      boolean sel = i == selectedType;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER);
      strokeWeight(1);
      rect(x + i * 160, y, 148, 40, 5);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(13);
      textAlign(CENTER, CENTER);
      text(trainingTypes[i], x + i * 160 + 74, y + 20);
    }

    y += 60;
    theme.drawCard(x, y, 700, 200);
    fill(theme.TEXT_DIM);
    textSize(11);
    textAlign(LEFT, TOP);
    text("INTENSITY - " + (int)intensities[selectedType] + "%", x + 20, y + 16);

    float sliderX = x + 20;
    float sliderY = y + 50;
    float sliderW = 660;
    float sliderH = 12;
    float fillW   = map(intensities[selectedType], 0, 100, 0, sliderW);

    fill(theme.PANEL_2);
    rect(sliderX, sliderY, sliderW, sliderH, 6);
    fill(theme.ACCENT);
    rect(sliderX, sliderY, fillW, sliderH, 6);

    float handleX = sliderX + fillW;
    fill(theme.ACCENT);
    ellipse(handleX, sliderY + 6, 20, 20);

    TrainingSession sess = new TrainingSession(trainingTypes[selectedType], intensities[selectedType]);
    TrainingOutcome out  = sess.projectedGains();

    y += 80;
    fill(theme.TEXT_DIM); textSize(11); textAlign(LEFT, TOP);
    text("PROJECTED GAINS THIS WEEK:", x + 20, y);

    y += 20;
    drawGainRow("Serve",    out.serveGain,    x + 20,  y);
    drawGainRow("Forehand", out.forehandGain, x + 180, y);
    drawGainRow("Backhand", out.backhandGain, x + 340, y);
    y += 24;
    drawGainRow("Speed",    out.speedGain,    x + 20,  y);
    drawGainRow("Stamina",  out.staminaGain,  x + 180, y);
    drawGainRow("Mental",   out.mentalGain,   x + 340, y);
    fill(theme.DANGER);
    text("Fatigue added: +" + (int)out.fatigueAdded + "%", x + 500, y);

    float risk = p.health.injuryRisk(p.form, intensities[selectedType] / 100.0);
    y += 30;
    fill(risk > 0.1 ? theme.DANGER : theme.TEXT_DIM);
    text("Injury risk: " + (int)(risk * 100) + "%", x + 20, y);

    y += 60;
    hoverTrain = theme.isHover(hoverX, hoverY, x, y, 320, 48);
    fill(hoverTrain ? theme.ACCENT : color(30, 38, 22));
    stroke(theme.ACCENT); strokeWeight(1);
    rect(x, y, 320, 48, 6);
    noStroke();
    fill(hoverTrain ? theme.BG : theme.ACCENT);
    textSize(14); textAlign(CENTER, CENTER);
    text("RUN TRAINING SESSION", x + 160, y + 24);

    if (resultAlpha > 0) {
      resultAlpha -= 2;
      fill(theme.TEXT, resultAlpha);
      textSize(13); textAlign(LEFT, TOP);
      text(lastResult, x, y + 60);
    }

    if (mousePressed && theme.isHover(mouseX, mouseY, sliderX - 10, sliderY - 6, sliderW + 20, 30)) {
      intensities[selectedType] = constrain(map(mouseX, sliderX, sliderX + sliderW, 0, 100), 10, 100);
    }
  }

  void drawGainRow(String label, int gain, float x, float y) {
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text(label + ":", x, y);
    fill(gain > 0 ? theme.SUCCESS : theme.TEXT_DIM);
    text("+" + gain, x + 70, y);
  }

  void onClick(int mx, int my) {
    float x = 30, y = 140;
    for (int i = 0; i < trainingTypes.length; i++) {
      if (theme.isHover(mx, my, x + i * 160, y, 148, 40)) { selectedType = i; return; }
    }

    if (theme.isHover(mx, my, 30, 360, 320, 48)) {
      Player p = engine.state.player;
      TrainingSession sess = new TrainingSession(trainingTypes[selectedType], intensities[selectedType]);
      p.progression.applyTrainingGains(sess);

      float risk = p.health.injuryRisk(p.form, intensities[selectedType] / 100.0);
      if (random(1) < risk) {
        String[] parts = {"wrist", "knee", "shoulder", "back", "ankle"};
        String part = parts[(int)random(parts.length)];
        int weeks = (int)random(1, 6);
        p.health.applyInjury(part, weeks);
        lastResult = "[!] Injured " + part + " - out " + weeks + " weeks";
      } else {
        lastResult = "[OK] Training complete. Fatigue +" + (int)(intensities[selectedType] * 0.3) + "%";
      }
      resultAlpha = 255;
    }
  }
}

// ============================================================
// WORLD RANKINGS SCREEN
// ============================================================
class WorldRankScreen extends BaseScreen {
  WorldRankScreen(GameEngine e) { super(e); }

  void render() {
    Player p = engine.state.player;
    float x = 30, y = 70;
    fill(theme.ACCENT); textSize(22); textAlign(LEFT, TOP);
    text("WORLD RANKINGS", x, y);

    y += 50;
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("RANK",   x,        y);
    text("PLAYER", x +  70,  y);
    text("NATION", x + 300,  y);
    text("POINTS", x + 450,  y);

    stroke(theme.BORDER);
    line(x, y + 16, x + 600, y + 16);
    noStroke();
    y += 24;

    ArrayList<WorldPlayer> wp = engine.ranking.worldPlayers;
    for (int i = 0; i < min(20, wp.size()); i++) {
      WorldPlayer w = wp.get(i);
      if (i % 2 == 0) {
        fill(theme.PANEL);
        rect(x - 8, y - 2, 620, 28, 2);
      }
      fill(i < 3 ? theme.ACCENT : theme.TEXT);
      textSize(13); textAlign(LEFT, CENTER);
      text("#" + w.ranking, x, y + 12);
      text(w.name, x + 70, y + 12);
      fill(theme.TEXT_DIM);
      text(w.nationality, x + 300, y + 12);
      fill(theme.TEXT);
      text(w.points, x + 450, y + 12);
      y += 30;
    }

    y += 10;
    fill(theme.ACCENT, 40);
    rect(x - 8, y - 2, 620, 28, 2);
    fill(theme.ACCENT); textSize(13); textAlign(LEFT, CENTER);
    if (p != null) {
      text("-> #" + p.career.worldRanking + " " + p.name, x, y + 12);
      fill(theme.TEXT_DIM); text(p.nationality, x + 300, y + 12);
      fill(theme.TEXT);     text(p.career.rankingPoints, x + 450, y + 12);
    }
  }
}

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

    // Hometown badge
    if (!p.hometown.isEmpty()) {
      fill(theme.ACCENT2, 30); rect(x + 210, y, 160, 36, 6);
      fill(theme.ACCENT2); textSize(11); textAlign(CENTER, CENTER);
      text(p.hometown + ", " + p.nationality, x + 290, y + 18);
    }
    y += 54;

    // Career stats
    theme.drawCard(x, y, 500, 180);
    int proYears = engine.state.proCareerStartYear > 0 ? engine.state.currentYear - engine.state.proCareerStartYear : 0;
    drawLegacyStat("Grand Slams",  p.career.grandSlamTitles + "",    x +  20, y + 20);
    drawLegacyStat("Total Titles", p.career.titlesWon + "",          x + 180, y + 20);
    drawLegacyStat("Best Ranking", "#" + p.career.worldRanking,      x + 340, y + 20);
    drawLegacyStat("Prize Money",  formatMoney(p.career.prizeMoney), x +  20, y + 80);
    drawLegacyStat("Wks at No.1",  p.career.weeksAtNumberOne + "",   x + 180, y + 80);
    drawLegacyStat("Pro Years",    proYears + "",                     x + 340, y + 80);
    y += 200;

    // Family section
    sectionHeader("FAMILY & RELATIONSHIPS", x, y); y += 20;
    theme.drawCard(x, y, 500, 120); float fy = y + 12;

    // Parents
    if (p.parents != null) {
      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
      text("Mom: " + p.parents.mom.name + " (" + p.parents.mom.relationshipLabel() + ")", x + 14, fy);
      text("Dad: " + p.parents.dad.name + " (" + p.parents.dad.relationshipLabel() + ")", x + 240, fy);
      fy += 20;
    }
    // Relationship
    if (p.family.spouse != null) {
      fill(theme.FAMILY); textSize(10); textAlign(LEFT, TOP);
      text("♥ " + p.family.statusDisplay(), x + 14, fy); fy += 20;
    }
    // Kids
    if (!p.family.children.isEmpty()) {
      fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
      String kidsStr = "Children: ";
      for (int i = 0; i < p.family.children.size(); i++) {
        Child c = p.family.children.get(i);
        kidsStr += c.name + " (age " + c.age + ")";
        if (i < p.family.children.size() - 1) kidsStr += ", ";
      }
      text(kidsStr, x + 14, fy); fy += 20;
    }
    // Coach
    if (p.coaches != null && p.coaches.length > 0) {
      fill(theme.ACCENT2); textSize(10); textAlign(LEFT, TOP);
      text("Coach: " + p.coaches[0].name + " (bond: " + (int)p.coaches[0].coachBond + "/100)", x + 14, fy);
    }
    y += 128;

    // Career highlights
    sectionHeader("CAREER HIGHLIGHTS", x, y); y += 20;
    fill(theme.TEXT_DIM); textSize(12); textAlign(LEFT, TOP);
    ArrayList<String> hist = p.career.careerHistory;
    int start = max(0, hist.size() - 7);
    for (int i = start; i < hist.size(); i++) {
      text("- " + hist.get(i), x, y); y += 20;
    }

    y = 70; x = 580;
    theme.drawCard(x, y, 560, 540);
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("AI LEGACY NARRATIVE", x + 20, y + 16);

    if (engine.ai.isLoading) {
      fill(theme.ACCENT); textSize(13); textAlign(CENTER, CENTER);
      text(engine.ai.getLoadingText(), x + 280, y + 270);
    } else if (!narrative.isEmpty()) {
      fill(theme.TEXT); textSize(13); textAlign(LEFT, TOP);
      drawWrappedText(narrative, x + 20, y + 40, 520, 20);
    } else {
      hoverGen = theme.isHover(hoverX, hoverY, x + 20, y + 240, 200, 40);
      theme.drawButton(x + 20, y + 240, 200, 40, "GENERATE NARRATIVE", hoverGen);
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
    if (theme.isHover(mx, my, x + 20, y + 220, 200, 40)) {
      generated = false;
      narrative = "";
      onEnter();
    }
  }
}


// ============================================================
// MATCH SCREEN (tournament run summary)
// ============================================================
class MatchScreen extends BaseScreen {
  MatchScreen(GameEngine e) { super(e); }

  void render() {
    TournamentRun run = engine.state.lastTournamentRun;
    if (run == null) {
      fill(theme.TEXT_DIM); textSize(14); textAlign(CENTER, CENTER);
      text("No recent tournament", width / 2, height / 2);
      return;
    }

    float x = 40, y = 70;
    fill(run.won ? theme.ACCENT : theme.TEXT_DIM);
    textSize(24); textAlign(LEFT, TOP);
    text((run.won ? "WON - " : "EXITED - ") + run.tournament.name, x, y);
    y += 50;

    fill(theme.TEXT_DIM); textSize(11);
    text(run.tournament.surface + " - " + run.tournament.tier, x, y);
    y += 40;

    sectionHeader("MATCH RESULTS", x, y); y += 20;
    for (MatchResult r : run.results) {
      fill(r.playerWon ? color(20, 45, 25) : color(45, 20, 20));
      rect(x, y, 600, 40, 4);
      fill(r.playerWon ? theme.SUCCESS : theme.DANGER);
      textSize(12); textAlign(LEFT, CENTER);
      text(r.playerWon ? "WIN" : "LOSS", x + 12, y + 20);

      fill(theme.TEXT); textAlign(LEFT, CENTER);
      text(r.round + " vs " + (r.playerWon ? r.loserName : r.winnerName), x + 65, y + 20);

      fill(theme.TEXT_DIM); textAlign(RIGHT, CENTER);
      text(r.score + " +" + r.rankingPointsAwarded + " pts", x + 588, y + 20);
      y += 46;
    }

    y += 20;
    fill(theme.TEXT_DIM); textSize(12); textAlign(LEFT, TOP);
    text("Total points earned: " + run.totalPoints() +
         " | Prize: " + formatMoney(run.totalPrize()), x, y);
  }
}// ============================================================
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

// ── Opponent Profile ───────────────────────────────────────
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

// ── Tournament ─────────────────────────────────────────────
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

// ── Tournament Calendar ────────────────────────────────────
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

// ── Training ───────────────────────────────────────────────
class TrainingSession {
  String type;       // "SERVE", "FITNESS", "MENTAL", "TACTICAL"
  float  intensity;  // 0..100
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
// ============================================================
// SPORT CONFIGURATION — per-sport display data
// ============================================================
class SportConfig {
  String   name;           // "Tennis", "Basketball", etc.
  String   subtitle;       // shown on main menu
  String[] statNames;      // 7 stat display names
  String[] roleShort;      // abbreviated role labels for buttons (max 4 chars)
  String[] roleFull;       // full role/position names
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
// ============================================================
// UI THEME & BASE SCREEN
// ============================================================
class UITheme {
  // Palette
  color BG        = color(10, 12, 18);
  color PANEL     = color(16, 20, 30);
  color PANEL_2   = color(24, 30, 45);
  color BORDER    = color(38, 48, 68);
  color ACCENT    = color(220, 160, 40);    // gold
  color ACCENT2   = color(55, 135, 215);   // blue
  color SUCCESS   = color(55, 185, 95);
  color DANGER    = color(210, 65, 65);
  color TEXT      = color(218, 224, 235);
  color TEXT_DIM  = color(118, 132, 155);
  color TEXT_GOLD = color(220, 178, 58);
  color PURPLE    = color(170, 80, 220);
  color FAMILY    = color(220, 80, 130);   // pink for family

  // Typography
  int TITLE  = 28;
  int HEADER = 18;
  int BODY   = 13;
  int SMALL  = 11;

  // ── Card ────────────────────────────────────────────────
  void drawCard(float x, float y, float w, float h, boolean highlighted) {
    // Shadow
    fill(0, 0, 0, 40);
    rect(x + 3, y + 3, w, h, 7);

    if (highlighted) { stroke(ACCENT); strokeWeight(1.5); }
    else             { stroke(BORDER); strokeWeight(1); }
    fill(PANEL);
    rect(x, y, w, h, 6);
    noStroke();

    // Subtle top-edge highlight
    fill(255, 255, 255, 10);
    rect(x + 6, y + 1, w - 12, 2, 1);
    fill(255, 255, 255, 5);
    rect(x + 6, y + 3, w - 12, 2, 1);
  }

  void drawCard(float x, float y, float w, float h) {
    drawCard(x, y, w, h, false);
  }

  // Colored accent card (left border stripe)
  void drawAccentCard(float x, float y, float w, float h, color accentColor) {
    drawCard(x, y, w, h, false);
    fill(accentColor);
    rect(x, y + 10, 3, h - 20, 2);
  }

  // ── Stat bar with end-cap glow ───────────────────────────
  void drawStatBar(float x, float y, float w, float h, float value, float max, color barColor) {
    fill(PANEL_2);
    rect(x, y, w, h, 2);
    float filled = map(constrain(value, 0, max), 0, max, 0, w);
    if (filled > 1) {
      fill(barColor);
      rect(x, y, filled, h, 2);
      // end-cap glow
      fill(barColor, 60);
      ellipse(x + filled, y + h / 2, h * 3, h * 3);
      fill(barColor, 180);
      ellipse(x + filled, y + h / 2, h + 2, h + 2);
    }
  }

  // ── Button ───────────────────────────────────────────────
  boolean drawButton(float x, float y, float w, float h, String label, boolean hover) {
    color bg  = hover ? ACCENT : PANEL_2;
    color txt = hover ? BG : TEXT;
    // shadow
    fill(0, 0, 0, 30);
    rect(x + 2, y + 2, w, h, 5);
    fill(bg);
    stroke(hover ? ACCENT : BORDER);
    strokeWeight(1);
    rect(x, y, w, h, 5);
    noStroke();
    if (hover) {
      fill(255, 255, 255, 20);
      rect(x + 2, y + 2, w - 4, h / 3, 3);
    }
    fill(txt);
    textSize(BODY);
    textAlign(CENTER, CENTER);
    text(label, x + w / 2, y + h / 2);
    return hover;
  }

  // Danger/secondary button
  boolean drawDangerButton(float x, float y, float w, float h, String label, boolean hover) {
    color bg  = hover ? DANGER : color(40, 16, 16);
    color bdr = hover ? DANGER : color(80, 30, 30);
    fill(bg);
    stroke(bdr);
    strokeWeight(1);
    rect(x, y, w, h, 5);
    noStroke();
    fill(hover ? color(255, 240, 240) : color(200, 100, 100));
    textSize(BODY);
    textAlign(CENTER, CENTER);
    text(label, x + w / 2, y + h / 2);
    return hover;
  }

  // ── Pill badge ───────────────────────────────────────────
  void drawBadge(String label, float x, float y, color bg, color fg) {
    textSize(9);
    float w = textWidth(label) + 12;
    fill(bg);
    rect(x, y, w, 18, 9);
    fill(fg);
    textAlign(CENTER, CENTER);
    text(label, x + w / 2, y + 9);
  }

  // ── Background dot grid (for all game screens) ───────────
  void drawDotGrid() {
    int spacing = 32;
    for (int gx = spacing; gx < width; gx += spacing) {
      for (int gy = 56; gy < height; gy += spacing) {
        fill(35, 45, 65, 90);
        noStroke();
        ellipse(gx, gy, 2, 2);
      }
    }
  }

  // ── Gradient rect (top→bottom) ───────────────────────────
  void drawGradientRect(float x, float y, float w, float h, color top, color bot) {
    int strips = 10;
    float sh = h / strips;
    noStroke();
    for (int i = 0; i < strips; i++) {
      fill(lerpColor(top, bot, (float)i / strips));
      rect(x, y + i * sh, w, sh + 1);
    }
  }

  // ── Section header ───────────────────────────────────────
  boolean isHover(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx <= x + w && my >= y && my <= y + h;
  }
}

// ── Base Screen ─────────────────────────────────────────────
abstract class BaseScreen {
  GameEngine engine;
  int hoverX, hoverY;

  BaseScreen(GameEngine e) { engine = e; }

  abstract void render();
  void onEnter() {}
  void onKey(char k, int kc) {}
  void onClick(int mx, int my)   {}
  void onRelease(int mx, int my) {}
  void onHover(int mx, int my)   { hoverX = mx; hoverY = my; }

  // ── Shared helpers ────────────────────────────────────────
  void sectionHeader(String label, float x, float y) {
    fill(theme.TEXT_DIM);
    textSize(theme.SMALL);
    textAlign(LEFT, CENTER);
    text(label.toUpperCase(), x, y);
    stroke(theme.BORDER);
    strokeWeight(1);
    line(x + textWidth(label.toUpperCase()) + 8, y, x + 300, y);
    noStroke();
  }

  void drawEventPanel(GameEvent evt, float x, float y, float w) {
    if (evt == null) return;
    float h = 320;
    theme.drawCard(x, y, w, h, true);

    // Left accent stripe by event type
    color stripe = theme.ACCENT;
    if (evt.eventType == EventType.FAMILY)  stripe = theme.FAMILY;
    if (evt.eventType == EventType.INJURY)  stripe = theme.DANGER;
    if (evt.eventType == EventType.MEDIA)   stripe = theme.ACCENT2;
    fill(stripe);
    rect(x, y + 10, 4, h - 20, 2);

    fill(stripe);
    textSize(16);
    textAlign(LEFT, TOP);
    text(evt.headline == null ? "" : evt.headline, x + 20, y + 14);

    fill(theme.TEXT);
    textSize(theme.BODY);
    drawWrappedText(evt.description == null ? "" : evt.description, x + 20, y + 42, w - 36, 17);

    if (evt.choices != null) {
      float cy = y + 118;
      for (int i = 0; i < evt.choices.size(); i++) {
        EventChoice ch = evt.choices.get(i);
        boolean hover = theme.isHover(hoverX, hoverY, x + 14, cy, w - 28, 54);
        fill(hover ? theme.PANEL_2 : color(20, 26, 40));
        stroke(hover ? stripe : theme.BORDER);
        strokeWeight(1);
        rect(x + 14, cy, w - 28, 54, 4);
        noStroke();

        fill(hover ? stripe : theme.TEXT);
        textSize(13);
        textAlign(LEFT, TOP);
        text(ch.label == null ? "" : ch.label, x + 28, cy + 8);

        fill(theme.TEXT_DIM);
        textSize(11);
        drawWrappedText(ch.description == null ? "" : ch.description, x + 28, cy + 26, w - 160, 14);

        drawEffectPills(ch, x + w - 135, cy + 18);
        cy += 64;
      }
    }
  }

  void drawEffectPills(EventChoice ch, float x, float y) {
    float px = x;
    if (ch.confidenceEffect != 0)
      px = drawPill("CON" + (ch.confidenceEffect > 0 ? "+" : "") + ch.confidenceEffect, px, y, ch.confidenceEffect > 0);
    if (ch.fatigueEffect != 0)
      px = drawPill("FAT" + (ch.fatigueEffect > 0 ? "+" : "") + ch.fatigueEffect, px, y, ch.fatigueEffect < 0);
    if (ch.mentalEffect != 0)
      px = drawPill("MNT" + (ch.mentalEffect > 0 ? "+" : "") + ch.mentalEffect, px, y, ch.mentalEffect > 0);
    if (ch.moneyEffect != 0) {
      String mLabel = "$" + (ch.moneyEffect > 0 ? "+" : "") + ch.moneyEffect + "K";
      px = drawPill(mLabel, px, y, ch.moneyEffect > 0);
    }
    if (ch.familyEffect != 0) {
      px = drawPill("FAM" + (ch.familyEffect > 0 ? "+" : "") + ch.familyEffect, px, y, ch.familyEffect > 0);
    }
  }

  float drawPill(String label, float x, float y, boolean positive) {
    textSize(9);
    float w = textWidth(label) + 10;
    fill(positive ? color(35, 75, 45) : color(75, 35, 35));
    rect(x, y, w, 16, 8);
    fill(positive ? theme.SUCCESS : theme.DANGER);
    textAlign(CENTER, CENTER);
    text(label, x + w / 2, y + 8);
    return x + w + 4;
  }

  void drawWrappedText(String txt, float x, float y, float maxW, float lineH) {
    if (txt == null || txt.isEmpty()) return;
    String[] words = txt.split(" ");
    String line = "";
    float cy = y;
    textAlign(LEFT, TOP);
    for (String word : words) {
      String test = line.isEmpty() ? word : line + " " + word;
      if (textWidth(test) > maxW) {
        text(line, x, cy);
        cy += lineH;
        line = word;
      } else {
        line = test;
      }
    }
    if (!line.isEmpty()) text(line, x, cy);
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
}
