package de.upb.codingpirates.battleships.server.handler;

import com.google.inject.Inject;
import de.upb.codingpirates.battleships.network.id.Id;
import de.upb.codingpirates.battleships.network.message.Message;
import de.upb.codingpirates.battleships.network.message.MessageHandler;
import de.upb.codingpirates.battleships.network.message.request.RemainingTimeRequest;
import de.upb.codingpirates.battleships.server.network.ClientManager;

public class RemainingTimeRequestHandler implements MessageHandler<RemainingTimeRequest> {
    @Inject
    private ClientManager clientManager;

    @Override
    public void handle(RemainingTimeRequest message, Id connectionId) {

    }

    @Override
    public boolean canHandle(Message message) {
        return message instanceof RemainingTimeRequest;
    }
}
