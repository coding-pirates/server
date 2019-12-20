package de.upb.codingpirates.battleships.server;

import com.google.common.collect.Maps;
import de.upb.codingpirates.battleships.logic.*;
import de.upb.codingpirates.battleships.network.ConnectionHandler;
import de.upb.codingpirates.battleships.network.exceptions.game.GameException;
import de.upb.codingpirates.battleships.network.exceptions.game.InvalidActionException;
import de.upb.codingpirates.battleships.network.exceptions.game.NotAllowedException;
import de.upb.codingpirates.battleships.network.id.IdManager;
import de.upb.codingpirates.battleships.network.message.notification.NotificationBuilder;
import de.upb.codingpirates.battleships.server.exceptions.InvalidGameSizeException;
import de.upb.codingpirates.battleships.server.game.GameHandler;
import de.upb.codingpirates.battleships.server.util.ConfigurationChecker;
import de.upb.codingpirates.battleships.server.util.ServerMarker;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * Handles all {@link Game}-related functionality.
 *
 * @author Paul Becker
 */
public class GameManager implements ConfigurationChecker {
    private static final Logger LOGGER = LogManager.getLogger();

    @Nonnull
    private final ClientManager clientManager;
    @Nonnull
    private final IdManager idManager;

    /**
     * maps game id to gamehandler
     */
    private final ObservableMap<Integer, GameHandler> gameHandlersById =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap());
    /**
     * maps client id to gameid
     */
    private final Map<Integer, Integer> clientToGame = Collections.synchronizedMap(Maps.newHashMap());

    @Inject
    public GameManager(@Nonnull ConnectionHandler handler, @Nonnull IdManager idManager) {
        this.clientManager = (ClientManager) handler;
        this.idManager = idManager;
        new Timer("Server Main").schedule(new TimerTask() {
            @Override
            public void run() {
                GameManager.this.run();
            }
        }, 1L, 1L);
    }

    /**
     * creates game based on parameter
     *
     * @param configuration
     * @param name
     * @param tournament
     * @return {@code -1} if game was created successful, {@code > 0} if the selected field size of the Configuration is too small
     */
    public GameHandler createGame(@Nonnull Configuration configuration, @Nonnull String name, boolean tournament) throws InvalidGameSizeException {
        checkField(configuration);
        int id = this.idManager.generate().getInt();
        LOGGER.debug(ServerMarker.GAME, "Create game: {} with id: {}", name, id);
        GameHandler gameHandler = new GameHandler(name, id, configuration, tournament, clientManager);
        this.gameHandlersById.put(id, gameHandler);
        return gameHandler;
    }

    /**
     * adds client with clientType to the specific game
     *
     * @param gameId
     * @param client
     * @param clientType
     * @throws InvalidActionException if game does not exist
     */
    public void addClientToGame(int gameId, @Nonnull Client client, @Nonnull ClientType clientType) throws GameException {
        LOGGER.debug(ServerMarker.GAME, "Adding client {}, with type {}, to game {}", client.getId(), clientType, gameId);
        if(this.clientToGame.containsKey(client.getId())){
            if(clientType.equals(ClientType.PLAYER)) {
                GameHandler handler = this.gameHandlersById.get(this.clientToGame.get(client.getId()));
                if(handler.getGame().getState().equals(GameState.FINISHED)){
                    this.clientToGame.remove(client.getId());
                }
                throw new NotAllowedException("game.gameManager.alreadyIngame");
            } else {
                this.clientToGame.remove(client.getId());
            }
        }
        if (this.gameHandlersById.containsKey(gameId)) {
            this.gameHandlersById.get(gameId).addClient(clientType, client);
            this.clientToGame.put(client.getId(), gameId);
        } else {
            LOGGER.error(ServerMarker.GAME, "Can't find game {}", gameId);
            throw new InvalidActionException("game.gameManager.noGame");
        }
    }

    /**
     * removes client from participating games
     *
     * @param client
     * @throws InvalidActionException if client does not participate
     */
    public void removeClientFromGame(int client) throws InvalidActionException {
        LOGGER.debug(ServerMarker.CLIENT, "Remove client {} from active game", client);
        if (clientToGame.containsKey(client)) {
            this.gameHandlersById.get(this.clientToGame.remove(client)).removeClient(client);
        } else {
            LOGGER.warn(ServerMarker.CLIENT, "Client {} does not participate in a game", client);
            throw new InvalidActionException("game.gameManager.noGameForClient");
        }
    }

    /**
     * launches game with id
     *
     * @param gameId gameId
     * @return {@code false} if player count is under 2
     */
    public boolean launchGame(int gameId) {
        LOGGER.debug(ServerMarker.GAME, "launched game {}, {}", gameId, this.gameHandlersById.get(gameId).getGame().getName());
        return this.gameHandlersById.get(gameId).launchGame();
    }

    /**
     * pauses game with id
     *
     * @param gameId gameId
     */
    public void pauseGame(int gameId) {
        LOGGER.debug(ServerMarker.GAME, "paused game {}, {}", gameId, this.gameHandlersById.get(gameId).getGame().getName());
        this.gameHandlersById.get(gameId).pauseGame();
        clientManager.sendMessageToClients(NotificationBuilder.pauseNotification(), this.gameHandlersById.get(gameId).getAllClients());
    }

    /**
     * continue game with id
     *
     * @param gameId gameId
     */
    public void continueGame(int gameId) {
        LOGGER.debug(ServerMarker.GAME, "continued game {}, {}", gameId, this.gameHandlersById.get(gameId).getGame().getName());
        this.gameHandlersById.get(gameId).continueGame();
        clientManager.sendMessageToClients(NotificationBuilder.continueNotification(), this.gameHandlersById.get(gameId).getAllClients());
    }

    /**
     * continue game with id
     *
     * @param gameId gameId
     * @param points if {@code false} all points will be set to 0
     */
    public void abortGame(int gameId, boolean points) {
        LOGGER.debug(ServerMarker.GAME, "abort game {}, {}", gameId, this.gameHandlersById.get(gameId).getGame().getName());
        this.gameHandlersById.get(gameId).abortGame(points);
    }

    /**
     * @return all existing games
     */
    public Collection<GameHandler> getGameHandlers() {
        return this.gameHandlersById.values();
    }

    /**
     * @param clientId for which a game should be found
     * @return a game where the client participate
     * @throws InvalidActionException if the client does not participate in a game
     */
    @Nonnull
    public GameHandler getGameHandlerForClientId(int clientId) throws InvalidActionException {
        if (!this.clientToGame.containsKey(clientId)) {
            LOGGER.warn(ServerMarker.CLIENT, "Could not get game for client {}", clientId);
            throw new InvalidActionException("game.gameManager.noGameForClient");
        }
        return this.gameHandlersById.get(this.clientToGame.get(clientId));
    }

    /**
     * @param id id of the game
     * @return the game with the id
     * @throws InvalidActionException if the game does not exist
     */
    @Nonnull
    public GameHandler getGameHandler(int id) throws InvalidActionException {
        if (!this.gameHandlersById.containsKey(id)) {
            LOGGER.warn(ServerMarker.GAME, "The game with id: {} does not exist", id);
            throw new InvalidActionException("game.gameManager.gameNotExist");
        }
        return this.gameHandlersById.get(id);
    }

    /**
     * run method for every game
     */
    private void run() {
        this.gameHandlersById.values().forEach(GameHandler::run);
    }

    public ObservableMap<Integer, GameHandler> getGameMappings() {
        return gameHandlersById;
    }
}
