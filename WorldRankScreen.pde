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

      // Check rival match
      Rival matchRival = null;
      if (engine.state.rivals != null) {
        for (Rival rv : engine.state.rivals) {
          if (rv.name.equals(w.name)) { matchRival = rv; break; }
        }
      }

      if (i % 2 == 0) {
        fill(theme.PANEL);
        rect(x - 8, y - 2, 620, 28, 2);
      }
      // Rival flame indicator
      if (matchRival != null) {
        fill(theme.DANGER);
        ellipse(x + 60, y + 12, 9, 9);
      }
      fill(i < 3 ? theme.ACCENT : theme.TEXT);
      textSize(13); textAlign(LEFT, CENTER);
      text("#" + w.ranking, x, y + 12);
      text(w.name, x + 70, y + 12);
      fill(theme.TEXT_DIM);
      text(w.nationality, x + 300, y + 12);
      fill(theme.TEXT);
      text(w.points, x + 450, y + 12);
      if (matchRival != null) {
        fill(theme.DANGER); textSize(9); textAlign(LEFT, CENTER);
        text("H2H: W" + matchRival.h2hWins + "-L" + matchRival.h2hLosses, x + 530, y + 12);
      }
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
