// ============================================================
// SPORTS CAREER SIMULATOR - Main Sketch
// Paste your Claude API key into CLAUDE_API_KEY below and press Run.
// ============================================================

// !! Replace with your own key. Never commit a real key to source control.
final String CLAUDE_API_KEY = "REDACTED";
final String CLAUDE_MODEL   = "claude-sonnet-4-6";

// ── Global singletons ────────────────────────────────────────
GameEngine engine;
UITheme    theme;

void setup() {
  size(1200, 800);
  smooth(4);
  textFont(createFont("Georgia", 14));

  theme  = new UITheme();
  engine = new GameEngine(CLAUDE_API_KEY, CLAUDE_MODEL);
  engine.start();
}

void draw() {
  background(theme.BG);
  engine.render();
}

void keyPressed()      { engine.processKey(key, keyCode); }
void mousePressed()    { engine.processMouse(mouseX, mouseY); }
void mouseReleased()   { engine.processMouseReleased(mouseX, mouseY); }
void mouseMoved()      { engine.processMouseMoved(mouseX, mouseY); }
void mouseDragged()    { engine.processMouseMoved(mouseX, mouseY); }

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
    apiKey = key;
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
      "Generate a realistic career event/dilemma this week specific to professional " + cfg.name + ". " +
      "Consider the player's age, ranking, fatigue, and form. " +
      "Events should reflect real " + cfg.name + " culture: sponsorships, rivalries, media, injuries, personal life, training decisions.\n\n" +
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
      "        \"rankingPoints\": 0\n" +
      "      }\n" +
      "    }\n" +
      "  ]\n" +
      "}\n\n" +
      "Rules:\n" +
      "- Generate exactly 3 choices\n" +
      "- Effects values must be integers between -20 and +20 (rankingPoints between -50 and +50)\n" +
      "- Make the dilemma contextually appropriate for " + cfg.name + "\n" +
      "- Be creative and specific to this player's situation";
  }

  String buildLegacyPrompt(String playerContext, Player player, Sport sport) {
    SportConfig cfg = getSportConfig(sport);
    return "You are writing the retirement narrative for a professional " + cfg.name + " player.\n\n" +
      "Sport: " + cfg.name + "\n" +
      "Career summary:\n" + playerContext +
      "\nTotal titles: "   + player.career.titlesWon +
      "\nMajor titles: "   + player.career.grandSlamTitles +
      "\nPrize money: $"   + (int)player.career.prizeMoney + "\n\n" +
      "Write a 3-4 sentence legacy summary in the style of a Sports Illustrated retirement piece specific to " + cfg.name + ". " +
      "Be specific to their stats and career phase. Capture what made them unique.";
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

  // ── Fallback event (when API fails) ────────────────────────
  GameEvent fallbackEvent() {
    GameEvent evt = new GameEvent();
    evt.type        = "CAREER_CHOICE";
    evt.headline    = "A New Week Begins";
    evt.description = "Another week on tour. You have a decision to make about how to spend your time.";
    evt.choices     = new ArrayList<EventChoice>();

    EventChoice c1 = new EventChoice();
    c1.label = "Train Hard";
    c1.description = "Push yourself this week.";
    c1.fatigueEffect = 10;
    c1.confidenceEffect = 5;
    evt.choices.add(c1);

    EventChoice c2 = new EventChoice();
    c2.label = "Rest & Recover";
    c2.description = "Take it easy and recover.";
    c2.fatigueEffect = -15;
    c2.confidenceEffect = -2;
    evt.choices.add(c2);

    EventChoice c3 = new EventChoice();
    c3.label = "Light Practice";
    c3.description = "Balanced approach.";
    c3.fatigueEffect = 2;
    c3.confidenceEffect = 2;
    evt.choices.add(c3);

    return evt;
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
    text(p.nationality + " - " + p.dominantHand + " hand", x + 16, y + 40);

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
    int week = engine.state.currentWeek;
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
      engine.advanceWeek();
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
// ENUMS
// ============================================================
enum Sport            { TENNIS, BASKETBALL, SOCCER, GOLF, BOXING }
enum Surface          { HARD, CLAY, GRASS, INDOOR_HARD }
enum PlayStyle        { AGGRESSIVE_BASELINER, COUNTER_PUNCHER, SERVE_VOLLEY, ALL_COURT }
enum TournamentTier   { GRAND_SLAM, MASTERS_1000, ATP_500, ATP_250, CHALLENGER }
enum InjuryStatus     { HEALTHY, DAY_TO_DAY, OUT_WEEKS, OUT_MONTHS }
enum RoundName        { R128, R64, R32, R16, QF, SF, F }
enum CoachType        { HEAD_COACH, FITNESS_TRAINER, MENTAL_COACH, SERVE_COACH }
enum CareerPhase      { JUNIOR, RISING, PRIME, VETERAN, DECLINING }
enum LegacyTier       { JOURNEYMAN, SOLID_PRO, STAR, LEGEND, GOAT }
enum ScreenID         { MAIN_MENU, CAREER, MATCH, TRAINING, WORLD_RANKINGS, LEGACY }
enum EventType        { CAREER_CHOICE, INJURY, RIVAL, MEDIA, FAMILY, TRAINING_RESULT }

// ============================================================
// GAME ENGINE - Core loop, state, screen management
// ============================================================
class GameEngine {
  // Core systems
  GameState           state;
  AIScenarioEngine    ai;
  MatchEngine         matchEngine;
  TournamentCalendar  calendar;
  RankingSystem       ranking;

  // Screen management
  ScreenID    currentScreen = ScreenID.MAIN_MENU;
  BaseScreen  activeScreen;

  // Screens
  MainMenuScreen  menuScreen;
  CareerScreen    careerScreen;
  MatchScreen     matchScreen;
  TrainingScreen  trainingScreen;
  WorldRankScreen worldScreen;
  LegacyScreen    legacyScreen;

  String apiKey, model;

  GameEngine(String key, String mdl) {
    apiKey = key;
    model  = mdl;
  }

  void start() {
    ai          = new AIScenarioEngine(apiKey, model);
    matchEngine = new MatchEngine();
    calendar    = new TournamentCalendar(Sport.TENNIS);
    ranking     = new RankingSystem();
    state       = new GameState();
    state.sportConfig = getSportConfig(Sport.TENNIS);

    menuScreen     = new MainMenuScreen(this);
    careerScreen   = new CareerScreen(this);
    matchScreen    = new MatchScreen(this);
    trainingScreen = new TrainingScreen(this);
    worldScreen    = new WorldRankScreen(this);
    legacyScreen   = new LegacyScreen(this);

    activeScreen = menuScreen;
  }

  // ── Called every draw() frame ────────────────────────────
  void render() {
    activeScreen.render();
    renderGlobalOverlay();
  }

  void renderGlobalOverlay() {
    if (currentScreen != ScreenID.MAIN_MENU && state.player != null) {
      drawStatusBar();
    }
  }

  void drawStatusBar() {
    fill(theme.PANEL);
    noStroke();
    rect(0, 0, width, 50);

    fill(theme.ACCENT);
    textSize(11);
    textAlign(LEFT, CENTER);
    text(state.player.name.toUpperCase(), 20, 25);

    SportConfig sc = state.sportConfig != null ? state.sportConfig : getSportConfig(Sport.TENNIS);
    fill(theme.TEXT_DIM);
    textAlign(LEFT, CENTER);
    text(sc.rankLabel + ": #" + state.player.career.worldRanking +
         " | " + sc.ptsLabel + ": "  + state.player.career.rankingPoints +
         " | Age: "  + state.player.age(state.currentYear) +
         " | Week "  + state.currentWeek + " / " + state.currentYear,
         200, 25);

    String[]   navLabels  = {"CAREER", "TRAINING", "WORLD", "LEGACY"};
    ScreenID[] navScreens = {ScreenID.CAREER, ScreenID.TRAINING, ScreenID.WORLD_RANKINGS, ScreenID.LEGACY};
    for (int i = 0; i < navLabels.length; i++) {
      float nx = width - 500 + i * 120;
      boolean active = currentScreen == navScreens[i];
      fill(active ? theme.ACCENT : theme.PANEL_2);
      rect(nx, 8, 105, 34, 4);
      fill(active ? theme.BG : theme.TEXT);
      textSize(11);
      textAlign(CENTER, CENTER);
      text(navLabels[i], nx + 52, 25);
    }
  }

  // ── Screen switching ─────────────────────────────────────
  void switchTo(ScreenID id) {
    currentScreen = id;
    switch (id) {
      case MAIN_MENU:      activeScreen = menuScreen;     break;
      case CAREER:         activeScreen = careerScreen;   break;
      case MATCH:          activeScreen = matchScreen;    break;
      case TRAINING:       activeScreen = trainingScreen; break;
      case WORLD_RANKINGS: activeScreen = worldScreen;    break;
      case LEGACY:         activeScreen = legacyScreen;   break;
    }
    activeScreen.onEnter();
  }

  // ── Advance one week ─────────────────────────────────────
  void advanceWeek() {
    if (state.player == null) return;
    if (ai.isLoading) return;

    state.currentWeek++;
    if (state.currentWeek > 52) {
      state.currentWeek = 1;
      state.currentYear++;
      endOfYear();
    }

    state.player.career.rankingPoints = (int)(state.player.career.rankingPoints * 0.985);
    state.player.health.advanceWeek();

    Tournament t = calendar.getTournamentForWeek(state.currentWeek);
    if (t != null && state.player.health.isAvailable()) {
      TournamentRun run = t.simulate(state.player, matchEngine, state.currentYear);
      applyTournamentResult(run, t);
      state.weeksPlayedThisYear += run.results.size();   // <-- now actually tracks
    } else if (t == null) {
      ai.generateWeeklyEvent(state.player, state.currentYear, state.currentWeek, state.currentSport);
    }

    if (t == null) state.player.form.applyRestWeek();

    state.player.progression.applyAgingDecay(state.currentYear);
    updateRanking();
  }

  void applyTournamentResult(TournamentRun run, Tournament t) {
    int   totalPts   = run.totalPoints();
    float totalPrize = run.totalPrize();
    state.player.career.addPoints(totalPts);
    state.player.career.prizeMoney += totalPrize;

    MatchResult last = run.results.get(run.results.size() - 1);
    state.player.form.applyMatchResult(last.playerWon, true);

    if (run.won) {
      state.player.career.recordTitle(t.tier == TournamentTier.GRAND_SLAM, totalPrize);
      state.player.career.addHistory("Won " + t.name + " (" + state.currentYear + ")");
      state.lastEvent = "[*] Won " + t.name + "! +" + totalPts + " pts";
    } else {
      state.lastEvent = "Exited " + t.name + " in " + last.round +
                        " | +" + totalPts + " pts";
    }

    for (MatchResult r : run.results) state.player.career.addResult(r);
    state.lastTournamentRun = run;
  }

  void endOfYear() {
    state.player.family.advanceYear(state.weeksPlayedThisYear);
    state.weeksPlayedThisYear = 0;
    state.player.career.addHistory("Year " + (state.currentYear - 1) +
      " ended - Ranking: #" + state.player.career.worldRanking);
    if (state.player.age(state.currentYear) >= 38) state.retirementEligible = true;
  }

  void updateRanking() {
    int pts = state.player.career.rankingPoints;
    int rank;
    if      (pts >= 10000) rank = (int)random(1, 3);
    else if (pts >=  7000) rank = (int)random(3, 8);
    else if (pts >=  5000) rank = (int)random(8, 20);
    else if (pts >=  3000) rank = (int)random(20, 50);
    else if (pts >=  1500) rank = (int)random(50, 100);
    else if (pts >=   500) rank = (int)random(100, 200);
    else                   rank = (int)random(200, 400);

    int prev = state.player.career.worldRanking;
    state.player.career.worldRanking = (int)lerp(prev, rank, 0.4);
  }

  // ── Apply a player's choice from an AI event ─────────────
  void applyChoice(EventChoice choice) {
    Player p = state.player;
    p.form.confidence       = constrain(p.form.confidence + choice.confidenceEffect, 0, 100);
    p.form.fatigue          = constrain(p.form.fatigue    + choice.fatigueEffect,    0, 100);
    p.baseAttributes.mental = constrain(p.baseAttributes.mental + choice.mentalEffect, 30, 99);
    if (choice.reputationEffect >= 0) p.reputation.applyPositive(choice.reputationEffect);
    else                              p.reputation.applyNegative(-choice.reputationEffect);
    p.career.addPoints(choice.rankingPointsEffect);

    state.pendingEvent = null;
    ai.hasNewEvent     = false;
  }

  // ── Input routing ────────────────────────────────────────
  void processKey(char k, int kc) {
    if (k == ' ' && currentScreen == ScreenID.CAREER) advanceWeek();
    activeScreen.onKey(k, kc);
  }

  void processMouse(int mx, int my) {
    if (state.player != null && my < 50) {
      ScreenID[] navScreens = {ScreenID.CAREER, ScreenID.TRAINING, ScreenID.WORLD_RANKINGS, ScreenID.LEGACY};
      for (int i = 0; i < navScreens.length; i++) {
        float nx = width - 500 + i * 120;
        if (mx > nx && mx < nx + 105 && my > 8 && my < 42) {
          switchTo(navScreens[i]);
          return;
        }
      }
    }
    activeScreen.onClick(mx, my);
  }

  void processMouseReleased(int mx, int my) { activeScreen.onRelease(mx, my); }
  void processMouseMoved(int mx, int my)    { activeScreen.onHover(mx, my); }

  // ── New game ─────────────────────────────────────────────
  void newGame(String playerName, String nationality, PlayStyle style, String hand, Sport sport) {
    state               = new GameState();
    state.currentSport  = sport;
    state.sportConfig   = getSportConfig(sport);
    state.player        = new Player(playerName, nationality, 2008, style, hand);
    state.currentYear   = 2024;
    state.currentWeek   = 1;
    state.player.career.worldRanking  = (int)random(200, 400);
    state.player.career.rankingPoints = (int)random(0, 200);

    calendar = new TournamentCalendar(sport);
    ranking.generateWorldPlayers(50, state.sportConfig);
    switchTo(ScreenID.CAREER);
    ai.generateWeeklyEvent(state.player, state.currentYear, state.currentWeek, sport);
  }
}

// ── Game State container ──────────────────────────────────
class GameState {
  Player player;
  int    currentYear  = 2024;
  int    currentWeek  = 1;
  float  weeksPlayedThisYear = 0;
  GameEvent     pendingEvent       = null;
  TournamentRun lastTournamentRun  = null;
  String        lastEvent          = "";
  boolean       retirementEligible = false;
  ArrayList<String> decisionLog = new ArrayList<String>();
  Sport       currentSport  = Sport.TENNIS;
  SportConfig sportConfig   = null;
}

// ── Ranking System ─────────────────────────────────────────
class RankingSystem {
  ArrayList<WorldPlayer> worldPlayers = new ArrayList<WorldPlayer>();

  void generateWorldPlayers(int count, SportConfig cfg) {
    // Build a large pool by repeating + mixing the config names
    ArrayList<String> first = new ArrayList<String>();
    ArrayList<String> last  = new ArrayList<String>();
    ArrayList<String> nat   = new ArrayList<String>();

    // Seed with sport-specific names then fill with generated variants
    for (int i = 0; i < cfg.oppFirst.length; i++) {
      first.add(cfg.oppFirst[i]);
      last.add(cfg.oppLast[i]);
      nat.add(cfg.oppNations[i]);
    }
    // Generic fillers to reach 50
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
// MAIN MENU SCREEN
// ============================================================
class MainMenuScreen extends BaseScreen {
  // Form state
  String    playerName   = "Alex Reyes";
  String    nationality  = "Spain";
  PlayStyle playStyle    = PlayStyle.ALL_COURT;
  String    dominantHand = "Right";
  Sport     selectedSport = Sport.TENNIS;
  int       focusedField = -1;

  // Hover tracking
  boolean hoverStart  = false;
  boolean hoverName   = false;
  boolean hoverNation = false;

  // Animation
  float titleY    = -80;
  float formAlpha = 0;

  MainMenuScreen(GameEngine e) { super(e); }

  void onEnter() {
    titleY    = -80;
    formAlpha = 0;
  }

  SportConfig cfg() { return getSportConfig(selectedSport); }

  void render() {
    drawGrid();

    titleY    = lerp(titleY,    90,  0.06);
    formAlpha = lerp(formAlpha, 255, 0.04);

    fill(theme.ACCENT);
    textSize(48);
    textAlign(CENTER, CENTER);
    text("CAREER LEGACY", width / 2, titleY);

    fill(theme.TEXT_DIM);
    textSize(14);
    text(cfg().subtitle, width / 2, titleY + 46);

    stroke(theme.BORDER);
    strokeWeight(1);
    line(width / 2 - 160, titleY + 62, width / 2 + 160, titleY + 62);
    noStroke();

    float cardW = 520, cardH = 500;
    float cx = width / 2 - cardW / 2;
    float cy = 155;
    tint(255, formAlpha);
    drawFormCard(cx, cy, cardW, cardH);
    noTint();
  }

  void drawGrid() {
    stroke(20, 28, 42);
    strokeWeight(1);
    for (int x = 0; x < width; x += 40) line(x, 0, x, height);
    for (int y = 0; y < height; y += 40) line(0, y, width, y);
    noStroke();

    for (int r = 300; r > 0; r -= 30) {
      fill(theme.ACCENT, map(r, 0, 300, 0, 8));
      ellipse(width / 2, height / 2, r * 2, r * 2);
    }
  }

  void drawFormCard(float cx, float cy, float cw, float ch) {
    fill(theme.PANEL);
    stroke(theme.BORDER);
    strokeWeight(1);
    rect(cx, cy, cw, ch, 8);
    noStroke();

    fill(theme.TEXT_DIM);
    textSize(11);
    textAlign(LEFT, TOP);
    text("CREATE YOUR PLAYER", cx + 20, cy + 16);

    float fy = cy + 40;
    float fw = cw - 40;

    // ── Sport selector ──────────────────────────────────────
    fill(theme.TEXT_DIM);
    textSize(10);
    textAlign(LEFT, TOP);
    text("SELECT SPORT", cx + 20, fy);
    fy += 16;

    Sport[] sports = Sport.values();
    float sw2 = (fw - (sports.length - 1) * 3) / sports.length;
    for (int i = 0; i < sports.length; i++) {
      boolean sel = sports[i] == selectedSport;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER);
      strokeWeight(1);
      rect(cx + 20 + i * (sw2 + 3), fy, sw2, 34, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(11);
      textAlign(CENTER, CENTER);
      text(sportLabel(sports[i]), cx + 20 + i * (sw2 + 3) + sw2 / 2, fy + 17);
    }
    fy += 48;

    // ── Player name + nationality ───────────────────────────
    drawField("PLAYER NAME",  playerName,  cx + 20, fy, fw, focusedField == 0 || hoverName);
    fy += 64;
    drawField("NATIONALITY", nationality, cx + 20, fy, fw, focusedField == 1 || hoverNation);
    fy += 64;

    // ── Role / Play Style ───────────────────────────────────
    fill(theme.TEXT_DIM);
    textSize(10);
    textAlign(LEFT, TOP);
    text(cfg().name.toUpperCase() + " STYLE / ROLE", cx + 20, fy);
    fy += 16;

    PlayStyle[] styles = PlayStyle.values();
    String[]    shorts = cfg().roleShort;
    float sw = (fw - (styles.length - 1) * 3) / styles.length;
    for (int i = 0; i < styles.length; i++) {
      boolean sel = styles[i] == playStyle;
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER);
      strokeWeight(1);
      rect(cx + 20 + i * (sw + 3), fy, sw, 36, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(10);
      textAlign(CENTER, CENTER);
      text(i < shorts.length ? shorts[i] : styles[i].toString(), cx + 20 + i * (sw + 3) + sw / 2, fy + 18);
    }
    fy += 50;

    // ── Dominant hand ───────────────────────────────────────
    fill(theme.TEXT_DIM);
    textSize(10);
    textAlign(LEFT, TOP);
    text("DOMINANT HAND", cx + 20, fy);
    fy += 16;

    String[] hands = {"Right", "Left"};
    for (int i = 0; i < hands.length; i++) {
      boolean sel = hands[i].equals(dominantHand);
      fill(sel ? theme.ACCENT : theme.PANEL_2);
      stroke(sel ? theme.ACCENT : theme.BORDER);
      strokeWeight(1);
      rect(cx + 20 + i * 100, fy, 90, 32, 4);
      noStroke();
      fill(sel ? theme.BG : theme.TEXT_DIM);
      textSize(12);
      textAlign(CENTER, CENTER);
      text(hands[i], cx + 20 + i * 100 + 45, fy + 16);
    }
    fy += 50;

    // ── Begin button ────────────────────────────────────────
    boolean hover = theme.isHover(hoverX, hoverY, cx + 20, fy, fw, 48);
    hoverStart = hover;
    fill(hover ? theme.ACCENT : color(40, 50, 30));
    stroke(theme.ACCENT);
    strokeWeight(hover ? 2 : 1);
    rect(cx + 20, fy, fw, 48, 6);
    noStroke();
    fill(hover ? theme.BG : theme.ACCENT);
    textSize(14);
    textAlign(CENTER, CENTER);
    text("BEGIN CAREER →", cx + 20 + fw / 2, fy + 24);
  }

  void drawField(String label, String value, float x, float y, float w, boolean active) {
    fill(theme.TEXT_DIM);
    textSize(10);
    textAlign(LEFT, TOP);
    text(label, x, y);

    fill(active ? theme.PANEL_2 : color(16, 20, 28));
    stroke(active ? theme.ACCENT2 : theme.BORDER);
    strokeWeight(1);
    rect(x, y + 16, w, 36, 4);
    noStroke();

    fill(theme.TEXT);
    textSize(14);
    textAlign(LEFT, CENTER);
    text(value, x + 12, y + 16 + 18);

    if (active && (frameCount / 30) % 2 == 0) {
      stroke(theme.TEXT);
      strokeWeight(1.5);
      float ccx = x + 12 + textWidth(value) + 2;
      line(ccx, y + 24, ccx, y + 42);
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

  void onClick(int mx, int my) {
    float cx = width / 2 - 260, cy = 155;
    float fw = 480;

    // Sport selector
    Sport[] sports = Sport.values();
    float sw2 = (fw - (sports.length - 1) * 3) / sports.length;
    float sty2 = cy + 56;
    for (int i = 0; i < sports.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (sw2 + 3), sty2, sw2, 34)) {
        selectedSport = sports[i];
        playStyle = PlayStyle.ALL_COURT; // reset role on sport change
        return;
      }
    }

    if (theme.isHover(mx, my, cx + 20, cy + 120, fw, 36)) { focusedField = 0; return; }
    if (theme.isHover(mx, my, cx + 20, cy + 184, fw, 36)) { focusedField = 1; return; }

    PlayStyle[] styles = PlayStyle.values();
    float sw  = (fw - (styles.length - 1) * 3) / styles.length;
    float styY = cy + 248;
    for (int i = 0; i < styles.length; i++) {
      if (theme.isHover(mx, my, cx + 20 + i * (sw + 3), styY, sw, 36)) {
        playStyle = styles[i];
        return;
      }
    }

    float hy = cy + 314;
    if (theme.isHover(mx, my, cx + 20,  hy, 90, 32)) { dominantHand = "Right"; return; }
    if (theme.isHover(mx, my, cx + 120, hy, 90, 32)) { dominantHand = "Left";  return; }

    float by = cy + 364;
    if (theme.isHover(mx, my, cx + 20, by, fw, 48)) {
      if (!playerName.isEmpty()) {
        engine.newGame(playerName, nationality, playStyle, dominantHand, selectedSport);
      }
    }

    focusedField = -1;
  }

  void onHover(int mx, int my) {
    super.onHover(mx, my);
    float cx = width / 2 - 260, cy = 155, fw = 480;
    hoverName   = theme.isHover(mx, my, cx + 20, cy + 120, fw, 36);
    hoverNation = theme.isHover(mx, my, cx + 20, cy + 184, fw, 36);
  }

  void onKey(char k, int kc) {
    if (focusedField == 0) {
      if (kc == BACKSPACE && playerName.length() > 0)
        playerName = playerName.substring(0, playerName.length() - 1);
      else if (k != CODED && k != ENTER && k != RETURN && k != BACKSPACE && playerName.length() < 24)
        playerName += k;
    }
    if (focusedField == 1) {
      if (kc == BACKSPACE && nationality.length() > 0)
        nationality = nationality.substring(0, nationality.length() - 1);
      else if (k != CODED && k != ENTER && k != RETURN && k != BACKSPACE && nationality.length() < 20)
        nationality += k;
    }
  }
}

// ============================================================
// PLAYER DOMAIN
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
  Coach[]           coaches;
  Reputation        reputation;

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
    coaches        = new Coach[0];
    reputation     = new Reputation();
  }

  int age(int currentYear) { return currentYear - birthYear; }

  PlayerAttributes effectiveAttributes() {
    float formMult = map(form.confidence, 0, 100, 0.85, 1.15);
    float fatMult  = map(form.fatigue,    0, 100, 1.05, 0.80);
    float mult     = formMult * fatMult;
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

  CareerPhase currentPhase(int currentYear) {
    int a = age(currentYear);
    if (a < 18) return CareerPhase.JUNIOR;
    if (a < 22) return CareerPhase.RISING;
    if (a < 28) return CareerPhase.PRIME;
    if (a < 32) return CareerPhase.VETERAN;
    return CareerPhase.DECLINING;
  }

  String toPromptString(int currentYear, int week) {
    PlayerAttributes eff = effectiveAttributes();
    return "Player: "      + name +
      " | Age: "           + age(currentYear) +
      " | Nation: "        + nationality +
      " | Style: "         + playStyle +
      " | Ranking: #"      + career.worldRanking +
      " | Phase: "         + currentPhase(currentYear) +
      " | Serve: "         + eff.serve +
      " | Forehand: "      + eff.forehand +
      " | Backhand: "      + eff.backhand +
      " | Speed: "         + eff.speed +
      " | Stamina: "       + eff.stamina +
      " | Mental: "        + eff.mental +
      " | Fatigue: "       + (int)form.fatigue    + "/100" +
      " | Confidence: "    + (int)form.confidence + "/100" +
      " | Injury: "        + health.status +
      " | GrandSlams: "    + career.grandSlamTitles +
      " | Titles: "        + career.titlesWon +
      " | Week: "          + week + " of current year";
  }
}

// ── Attributes (record-style) ──────────────────────────────
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
  Spouse           spouse          = null;
  ArrayList<Child> children        = new ArrayList<Child>();
  float            familyHappiness = 70;
  float            travelTolerance = 80;

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
  }
}

class Spouse {
  String name;
  float  supportLevel       = 70 + random(-20, 20);
  float  resentment         = 10;
  String relationshipStatus = "Together";
  Spouse(String n) { name = n; }
}

class Child {
  String name;
  int    age;
  float  happiness = 80;
  Child(String n, int a) { name = n; age = a; }
}

// ── Coach (minimal - was missing entirely in the original) ─
class Coach {
  String    name;
  CoachType type;
  int       skill = 60;
  float     salary = 50_000;

  Coach(String n, CoachType t, int s) {
    name  = n;
    type  = t;
    skill = s;
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

// ── Reputation (clamped properly) ──────────────────────────
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

    LegacyTier tier   = computeTier(p);
    color      tierCol = legacyColor(tier);
    fill(tierCol, 40);
    rect(x, y, 200, 36, 6);
    fill(tierCol); textSize(18); textAlign(CENTER, CENTER);
    text(tier.toString().replace("_", " "), x + 100, y + 18);
    y += 54;

    theme.drawCard(x, y, 500, 180);
    drawLegacyStat("Grand Slams",  p.career.grandSlamTitles + "",                x +  20, y + 20);
    drawLegacyStat("Total Titles", p.career.titlesWon + "",                      x + 180, y + 20);
    drawLegacyStat("Best Ranking", "#" + p.career.worldRanking,                  x + 340, y + 20);
    drawLegacyStat("Prize Money",  formatMoney(p.career.prizeMoney),             x +  20, y + 80);
    drawLegacyStat("Wks at No.1",  p.career.weeksAtNumberOne + "",               x + 180, y + 80);
    drawLegacyStat("Career Years", (engine.state.currentYear - 2024) + "",       x + 340, y + 80);

    y += 200;
    sectionHeader("CAREER HIGHLIGHTS", x, y); y += 20;
    fill(theme.TEXT_DIM); textSize(12); textAlign(LEFT, TOP);
    ArrayList<String> hist = p.career.careerHistory;
    int start = max(0, hist.size() - 8);
    for (int i = start; i < hist.size(); i++) {
      text("- " + hist.get(i), x, y);
      y += 20;
    }

    y = 70; x = 580;
    theme.drawCard(x, y, 560, 500);
    fill(theme.TEXT_DIM); textSize(10); textAlign(LEFT, TOP);
    text("AI LEGACY NARRATIVE", x + 20, y + 16);

    if (engine.ai.isLoading) {
      fill(theme.ACCENT); textSize(13); textAlign(CENTER, CENTER);
      text(engine.ai.getLoadingText(), x + 280, y + 250);
    } else if (!narrative.isEmpty()) {
      fill(theme.TEXT); textSize(13); textAlign(LEFT, TOP);
      drawWrappedText(narrative, x + 20, y + 40, 520, 20);
    } else {
      hoverGen = theme.isHover(hoverX, hoverY, x + 20, y + 220, 200, 40);
      theme.drawButton(x + 20, y + 220, 200, 40, "GENERATE NARRATIVE", hoverGen);
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
}

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
  // Palette - dark editorial sports aesthetic
  color BG        = color(10, 12, 16);
  color PANEL     = color(18, 22, 30);
  color PANEL_2   = color(25, 30, 42);
  color BORDER    = color(40, 48, 65);
  color ACCENT    = color(220, 160, 40);   // gold
  color ACCENT2   = color(60, 140, 220);   // blue
  color SUCCESS   = color(60, 190, 100);
  color DANGER    = color(210, 70, 70);
  color TEXT      = color(220, 225, 235);
  color TEXT_DIM  = color(130, 140, 160);
  color TEXT_GOLD = color(220, 180, 60);

  // Typography sizes
  int TITLE  = 28;
  int HEADER = 18;
  int BODY   = 13;
  int SMALL  = 11;

  // Card drawing
  void drawCard(float x, float y, float w, float h, boolean highlighted) {
    if (highlighted) { stroke(ACCENT); strokeWeight(1.5); }
    else             { stroke(BORDER); strokeWeight(1); }
    fill(PANEL);
    rect(x, y, w, h, 6);
    noStroke();
  }
  void drawCard(float x, float y, float w, float h) {
    drawCard(x, y, w, h, false);
  }

  // Stat bar
  void drawStatBar(float x, float y, float w, float h, float value, float max, color barColor) {
    fill(PANEL_2);
    rect(x, y, w, h, 2);
    float filled = map(value, 0, max, 0, w);
    fill(barColor);
    rect(x, y, filled, h, 2);
  }

  // Button
  boolean drawButton(float x, float y, float w, float h, String label, boolean hover) {
    color bg  = hover ? ACCENT : PANEL_2;
    color txt = hover ? BG : TEXT;
    fill(bg);
    stroke(hover ? ACCENT : BORDER);
    strokeWeight(1);
    rect(x, y, w, h, 5);
    noStroke();
    fill(txt);
    textSize(BODY);
    textAlign(CENTER, CENTER);
    text(label, x + w/2, y + h/2);
    return hover;
  }

  boolean isHover(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx <= x + w && my >= y && my <= y + h;
  }
}

// ── Base Screen ─────────────────────────────────────────────
abstract class BaseScreen {
  GameEngine engine;
  int hoverX, hoverY;

  BaseScreen(GameEngine e) {
    engine = e;
    // No local theme field - use the global `theme` directly from any method.
  }

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

    fill(theme.ACCENT);
    textSize(16);
    textAlign(LEFT, TOP);
    text(evt.headline == null ? "" : evt.headline, x + 16, y + 14);

    fill(theme.TEXT);
    textSize(theme.BODY);
    drawWrappedText(evt.description == null ? "" : evt.description, x + 16, y + 42, w - 32, 17);

    if (evt.choices != null) {
      float cy = y + 120;
      for (int i = 0; i < evt.choices.size(); i++) {
        EventChoice ch = evt.choices.get(i);
        boolean hover = theme.isHover(hoverX, hoverY, x + 14, cy, w - 28, 52);
        fill(hover ? theme.PANEL_2 : color(22, 28, 40));
        stroke(hover ? theme.ACCENT : theme.BORDER);
        strokeWeight(1);
        rect(x + 14, cy, w - 28, 52, 4);
        noStroke();

        fill(hover ? theme.ACCENT : theme.TEXT);
        textSize(13);
        textAlign(LEFT, TOP);
        text(ch.label == null ? "" : ch.label, x + 26, cy + 8);

        fill(theme.TEXT_DIM);
        textSize(11);
        drawWrappedText(ch.description == null ? "" : ch.description, x + 26, cy + 26, w - 160, 14);

        drawEffectPills(ch, x + w - 130, cy + 16);
        cy += 62;
      }
    }
  }

  void drawEffectPills(EventChoice ch, float x, float y) {
    float px = x;
    if (ch.confidenceEffect != 0)
      px = drawPill("CON " + (ch.confidenceEffect > 0 ? "+" : "") + ch.confidenceEffect, px, y, ch.confidenceEffect > 0);
    if (ch.fatigueEffect != 0)
      px = drawPill("FAT " + (ch.fatigueEffect > 0 ? "+" : "") + ch.fatigueEffect, px, y, ch.fatigueEffect < 0);
    if (ch.mentalEffect != 0)
      px = drawPill("MNT " + (ch.mentalEffect > 0 ? "+" : "") + ch.mentalEffect, px, y, ch.mentalEffect > 0);
  }

  float drawPill(String label, float x, float y, boolean positive) {
    textSize(9);
    float w = textWidth(label) + 10;
    fill(positive ? color(40, 80, 50) : color(80, 40, 40));
    rect(x, y, w, 16, 8);
    fill(positive ? theme.SUCCESS : theme.DANGER);
    textAlign(CENTER, CENTER);
    text(label, x + w/2, y + 8);
    return x + w + 4;
  }

  void drawWrappedText(String txt, float x, float y, float maxW, float lineH) {
    if (txt == null) return;
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
    if (m >= 1_000_000) return "$" + nf(m / 1_000_000, 0, 1) + "M";
    if (m >= 1_000)     return "$" + (int)(m / 1_000) + "K";
    return "$" + (int)m;
  }
}