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

  // Public triggers
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
    final String playerContext  = player.toPromptString(currentYear, 0);
    final Player p              = player;
    final Sport  sportFinal     = sport;
    final AIScenarioEngine self = this;

    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          String result = self.callClaudeRaw(self.buildLegacyPrompt(playerContext, p, sportFinal));
          cb.onComplete(result);
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

  // Prompt builders
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
      "  LEGAL: doping rumours, contract disputes, tax issues, bar fights, criminal trouble\n" +
      "  HEALTH: mental breakdown, mystery illness, injury decisions, addiction\n" +
      "  SOCIAL: viral scandal, cancelled culture moment, unexpected friendship, media crisis\n" +
      "  FAMILY: parent illness, sibling rivalry, partner ultimatum, child request\n" +
      "  VICES: gambling, nightlife, substance temptation, reckless spending\n" +
      "  RIVALS: trash talk incidents, rivalry escalation, public callouts, grudge matches\n" +
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
      "Write it as a Sports Illustrated retirement piece. Be specific and emotional." +
      (rivalNarCtx.isEmpty() ? "" : "\n" + rivalNarCtx);
  }

  // HTTP call
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

    int start = json.indexOf("\"text\":\"");
    if (start == -1) throw new Exception("No text field in response: " + json);
    start += 8;

    int end = start;
    while (end < json.length()) {
      char c = json.charAt(end);
      if (c == '\\') { end += 2; continue; }
      if (c == '"') break;
      end++;
    }
    if (end >= json.length()) throw new Exception("Unterminated text field");

    String raw = json.substring(start, end);
    raw = raw.replace("\\n", "\n")
             .replace("\\r", "\r")
             .replace("\\t", "\t")
             .replace("\\\"", "\"")
             .replace("\\\\", "\\");
    return raw.trim();
  }

  // JSON parser (manual; no external library needed)
  GameEvent parseGameEvent(String raw) {
    try {
      raw = raw.replace("```json", "").replace("```", "").trim();

      GameEvent evt = new GameEvent();
      evt.type        = extractString(raw, "type");
      evt.headline    = extractString(raw, "headline");
      evt.description = extractString(raw, "description");

      int choicesStart = raw.indexOf("\"choices\"");
      if (choicesStart == -1) throw new Exception("No choices");
      String choicesBlock = raw.substring(choicesStart);

      evt.choices = new ArrayList<EventChoice>();
      int pos = choicesBlock.indexOf("{");
      while (pos != -1) {
        int closing = findMatchingBrace(choicesBlock, pos);
        if (closing == -1) break;
        String choiceJson = choicesBlock.substring(pos, closing + 1);

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

  // JSON helpers
String extractString(String json, String key) {
  String search = "\"" + key + "\"";
  int s = json.indexOf(search);
  if (s == -1) return "";
  s += search.length();
  
  // Skip spaces, colons, and newlines
  while (s < json.length() && (json.charAt(s) == ' ' || json.charAt(s) == ':' || json.charAt(s) == '\n' || json.charAt(s) == '\t')) {
    s++;
  }
  
  // Ensure we are at the starting quote
  if (s < json.length() && json.charAt(s) == '"') {
    s++;
  } else {
    return "";
  }
  
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
      if (c == '\\') { i++; continue; }
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

  // Varied fallback event pool (when API unavailable)
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

  // Loading animation
  String getLoadingText() {
    loadingFrame++;
    return "Generating event" + loadingDots[(loadingFrame / 15) % 4];
  }
}

// Data classes for events
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
  int moneyEffect         = 0;   // in $1,000s
  int familyEffect        = 0;
  int happinessEffect     = 0;
  String tag              = "";
}

interface LegacyCallback {
  void onComplete(String narrative);
}
