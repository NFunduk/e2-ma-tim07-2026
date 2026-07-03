package com.example.sabona.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MatchRewardCalculatorTest {

    @Test
    public void winnerGetsTenStarsPlusOnePerFortyPoints() {
        MatchRewardCalculator.Reward reward =
                MatchRewardCalculator.applyRegularReward(20, 3, 0, true, 150);

        assertEquals(13, reward.starsDelta);
        assertEquals(33, reward.newStars);
        assertEquals(0, reward.tokensGained);
        assertEquals(3, reward.newTokens);
    }

    @Test
    public void loserLosesTenStarsPlusPointBonusWithoutGoingBelowZero() {
        MatchRewardCalculator.Reward reward =
                MatchRewardCalculator.applyRegularReward(5, 1, 0, false, 80);

        assertEquals(-5, reward.starsDelta);
        assertEquals(0, reward.newStars);
        assertEquals(0, reward.tokensGained);
        assertEquals(1, reward.newTokens);
    }

    @Test
    public void crossingFiftyStarsGivesOneTokenAfterStarsAreApplied() {
        MatchRewardCalculator.Reward reward =
                MatchRewardCalculator.applyRegularReward(49, 2, 0, true, 0);

        assertEquals(10, reward.starsDelta);
        assertEquals(59, reward.newStars);
        assertEquals(1, reward.tokensGained);
        assertEquals(3, reward.newTokens);
        assertEquals(1, reward.tokenRewardLevel);
    }

    @Test
    public void crossingMultipleNewFiftyStarThresholdsGivesMultipleTokens() {
        MatchRewardCalculator.Reward reward =
                MatchRewardCalculator.applyRegularReward(45, 0, 0, true, 400);

        assertEquals(20, reward.starsDelta);
        assertEquals(65, reward.newStars);
        assertEquals(1, reward.tokensGained);
        assertEquals(1, reward.newTokens);
    }

    @Test
    public void alreadyRewardedThresholdIsNotPaidAgainAfterDroppingBelowIt() {
        MatchRewardCalculator.Reward reward =
                MatchRewardCalculator.applyRegularReward(49, 5, 1, true, 0);

        assertEquals(59, reward.newStars);
        assertEquals(0, reward.tokensGained);
        assertEquals(5, reward.newTokens);
        assertEquals(1, reward.tokenRewardLevel);
    }
}
