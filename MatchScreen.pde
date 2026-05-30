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
