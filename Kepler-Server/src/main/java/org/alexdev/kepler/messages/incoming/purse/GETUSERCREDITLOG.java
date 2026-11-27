package org.alexdev.kepler.messages.incoming.purse;

import org.alexdev.kepler.dao.mysql.TransactionDao;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.outgoing.purse.CREDIT_LOG;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

public class GETUSERCREDITLOG implements MessageEvent {

    @Override
    public void handle(Player player, NettyRequest reader) {
        // The purse "Transactions" view waits for 209 and stays on "Loading..." without it.
        // No CREDIT_BALANCE here: header 6 plays the cash sound on every refresh.
        player.send(new CREDIT_LOG(TransactionDao.getCreditLog(player.getDetails().getId(), TransactionDao.CREDIT_LOG_LIMIT)));
    }
}
