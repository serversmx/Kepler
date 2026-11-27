package org.alexdev.kepler.game.polls;

import java.util.List;

/**
 * One question of a poll as the hh_poll cast understands it.
 *
 * Types 1 and 2 are both selection questions for the client (radio buttons
 * when maxSelections is 1, checkboxes otherwise); types 3 and 4 are both
 * free-text. HabboP uses 1 = single choice, 2 = multiple choice, 3 = text.
 */
public class PollQuestion {
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_MULTIPLE = 2;
    public static final int TYPE_TEXT = 3;

    private final int id;
    private final int type;
    private final String text;
    private final int minSelections;
    private final int maxSelections;
    private final List<Integer> optionIds;
    private final List<String> options;

    public PollQuestion(int id, int type, String text, int minSelections, int maxSelections, List<Integer> optionIds, List<String> options) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
        this.optionIds = List.copyOf(optionIds);
        this.options = List.copyOf(options);
    }

    public boolean isSelection() {
        return this.type == 1 || this.type == 2;
    }

    public boolean isText() {
        return this.type == 3 || this.type == 4;
    }

    /**
     * Selection limits clamped to what the client accepts (validateQuestion
     * drops a question with maxSelect < 1 or minSelect > maxSelect).
     */
    public int getEffectiveMax() {
        int max = Math.min(Math.max(this.maxSelections, 1), Math.max(this.options.size(), 1));
        return this.type == TYPE_SINGLE ? 1 : max;
    }

    public int getEffectiveMin() {
        return Math.max(0, Math.min(this.minSelections, this.getEffectiveMax()));
    }

    public int getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getMinSelections() {
        return minSelections;
    }

    public int getMaxSelections() {
        return maxSelections;
    }

    public List<Integer> getOptionIds() {
        return optionIds;
    }

    public List<String> getOptions() {
        return options;
    }
}
