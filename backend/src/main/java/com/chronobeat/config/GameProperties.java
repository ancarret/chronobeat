package com.chronobeat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chronobeat.game")
public class GameProperties {

    /** Default number of lives before {@code maxLives} is explicitly requested. */
    private int defaultMaxLives = 3;

    /** Base points awarded for a correct placement. */
    private int basePointsPerCorrect = 100;

    /** Extra points per consecutive correct answer, added on top of the base points. */
    private int streakBonusPerLevel = 10;

    /** Streak level at which the bonus stops increasing. */
    private int maxStreakBonusLevel = 10;

    /** How many seconds of the (up to ~30s) provider preview the frontend should play. */
    private int previewPlaySeconds = 12;

    /** How many recent distinct artists the selection engine tries to avoid repeating. */
    private int recentArtistAvoidanceWindow = 3;

    /**
     * Time limit per round for online rooms that didn't pick one. Online games must run on a clock,
     * otherwise one player who closes the tab would stall the whole table forever.
     */
    private int onlineAnswerSeconds = 45;

    public int getDefaultMaxLives() {
        return defaultMaxLives;
    }

    public void setDefaultMaxLives(int defaultMaxLives) {
        this.defaultMaxLives = defaultMaxLives;
    }

    public int getBasePointsPerCorrect() {
        return basePointsPerCorrect;
    }

    public void setBasePointsPerCorrect(int basePointsPerCorrect) {
        this.basePointsPerCorrect = basePointsPerCorrect;
    }

    public int getStreakBonusPerLevel() {
        return streakBonusPerLevel;
    }

    public void setStreakBonusPerLevel(int streakBonusPerLevel) {
        this.streakBonusPerLevel = streakBonusPerLevel;
    }

    public int getMaxStreakBonusLevel() {
        return maxStreakBonusLevel;
    }

    public void setMaxStreakBonusLevel(int maxStreakBonusLevel) {
        this.maxStreakBonusLevel = maxStreakBonusLevel;
    }

    public int getPreviewPlaySeconds() {
        return previewPlaySeconds;
    }

    public void setPreviewPlaySeconds(int previewPlaySeconds) {
        this.previewPlaySeconds = previewPlaySeconds;
    }

    public int getOnlineAnswerSeconds() {
        return onlineAnswerSeconds;
    }

    public void setOnlineAnswerSeconds(int onlineAnswerSeconds) {
        this.onlineAnswerSeconds = onlineAnswerSeconds;
    }

    public int getRecentArtistAvoidanceWindow() {
        return recentArtistAvoidanceWindow;
    }

    public void setRecentArtistAvoidanceWindow(int recentArtistAvoidanceWindow) {
        this.recentArtistAvoidanceWindow = recentArtistAvoidanceWindow;
    }
}
