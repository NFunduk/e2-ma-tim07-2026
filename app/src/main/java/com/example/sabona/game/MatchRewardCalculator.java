package com.example.sabona.game;

public final class MatchRewardCalculator {

    public static final int STARS_PER_TOKEN = 50;

    private MatchRewardCalculator() {}

    public static int regularStarsDelta(boolean won, int score) {
        int bonus = Math.max(score, 0) / 40;
        return won ? 10 + bonus : -10 + bonus;
    }

    public static Reward applyRegularReward(long oldStars,
                                            long oldTokens,
                                            int previousTokenRewardLevel,
                                            boolean won,
                                            int score) {
        int rawStarsDelta = regularStarsDelta(won, score);
        long newStars = Math.max(0, oldStars + rawStarsDelta);
        int starsDelta = (int) (newStars - oldStars);

        int newTokenRewardLevel = (int) (newStars / STARS_PER_TOKEN);
        int tokensGained = Math.max(0, newTokenRewardLevel - previousTokenRewardLevel);

        return new Reward(
                starsDelta,
                tokensGained,
                newStars,
                oldTokens + tokensGained,
                Math.max(previousTokenRewardLevel, newTokenRewardLevel)
        );
    }

    public static final class Reward {
        public final int starsDelta;
        public final int tokensGained;
        public final long newStars;
        public final long newTokens;
        public final int tokenRewardLevel;

        private Reward(int starsDelta,
                       int tokensGained,
                       long newStars,
                       long newTokens,
                       int tokenRewardLevel) {
            this.starsDelta = starsDelta;
            this.tokensGained = tokensGained;
            this.newStars = newStars;
            this.newTokens = newTokens;
            this.tokenRewardLevel = tokenRewardLevel;
        }
    }
}
