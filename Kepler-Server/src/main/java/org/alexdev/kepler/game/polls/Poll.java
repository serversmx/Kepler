package org.alexdev.kepler.game.polls;

import java.util.List;

public class Poll {
    private final int id;
    private final int roomId;
    private final String description;
    private final String headline;
    private final String thanks;
    private final List<PollQuestion> questions;

    public Poll(int id, int roomId, String description, String headline, String thanks, List<PollQuestion> questions) {
        this.id = id;
        this.roomId = roomId;
        this.description = description;
        this.headline = headline;
        this.thanks = thanks;
        this.questions = List.copyOf(questions);
    }

    public PollQuestion getQuestion(int questionId) {
        for (PollQuestion question : this.questions) {
            if (question.getId() == questionId) {
                return question;
            }
        }

        return null;
    }

    public int getId() {
        return id;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getDescription() {
        return description;
    }

    public String getHeadline() {
        return headline;
    }

    public String getThanks() {
        return thanks;
    }

    public List<PollQuestion> getQuestions() {
        return questions;
    }
}
