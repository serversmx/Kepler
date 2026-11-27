package org.alexdev.kepler.messages.outgoing.polls;

import org.alexdev.kepler.game.polls.Poll;
import org.alexdev.kepler.game.polls.PollManager;
import org.alexdev.kepler.game.polls.PollQuestion;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * 317: hh_poll handle_poll_contents reads
 * int pollId, str headline, str thanks, int questionCount, then per question
 * int questionId, int questionNumber, int type, str text and, for types 1/2,
 * int optionCount, int minSelect, int maxSelect, str option * optionCount.
 */
public class POLL_CONTENTS extends MessageComposer {
    private final Poll poll;

    public POLL_CONTENTS(Poll poll) {
        this.poll = poll;
    }

    @Override
    public void compose(NettyResponse response) {
        response.writeInt(this.poll.getId());
        response.writeString(PollManager.clientText(this.poll.getHeadline()));
        response.writeString(PollManager.clientText(this.poll.getThanks()));
        response.writeInt(this.poll.getQuestions().size());

        int number = 1;

        for (PollQuestion question : this.poll.getQuestions()) {
            response.writeInt(question.getId());
            response.writeInt(number++);
            response.writeInt(question.getType());
            response.writeString(PollManager.clientText(question.getText()));

            if (question.isSelection()) {
                response.writeInt(question.getOptions().size());
                response.writeInt(question.getEffectiveMin());
                response.writeInt(question.getEffectiveMax());

                for (String option : question.getOptions()) {
                    response.writeString(PollManager.clientText(option));
                }
            }
        }
    }

    @Override
    public short getHeader() {
        return 317;
    }
}
