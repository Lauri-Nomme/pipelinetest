package io.github.unapplicable.hangman.api;

import io.github.unapplicable.hangman.service.*;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestParameter;
import io.vertx.ext.web.RequestParameters;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.designdriven.OpenAPI3RouterFactory;
import rx.Observable;
import rx.Single;
import rx.observables.SyncOnSubscribe;

import java.util.NoSuchElementException;

public class HangmanVerticle extends io.vertx.rxjava.core.AbstractVerticle {
    private PlayerService playerService;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        PlayerRepository playerRepository = new MemoryPlayerRepository();
        playerRepository.create(new Player("player1", 1));
        playerService = new PlayerServiceImpl(playerRepository);

        createRouterFactory().subscribe(
            rf -> {
                Router hangmanApiRouter = registerHandlers(rf).getRouter();
                Router router = Router
                    .router(vertx)
                    .mountSubRouter("/hangman/v1", hangmanApiRouter);
                vertx
                    .createHttpServer()
                    .requestHandler(router::accept)
                    .rxListen(8080)
                    .toCompletable()
                    .subscribe(RxHelper.toSubscriber(startFuture));
            }, startFuture::fail);
    }

    private Single<OpenAPI3RouterFactory> createRouterFactory() {
        try {
            String filename = HangmanVerticle.class.getResource("/hangmanapi.yaml").toURI().toURL().toString();

            return OpenAPI3RouterFactory.rxCreateRouterFactoryFromURL(vertx, filename);
        } catch (Exception ex) {
            return Single.error(ex);
        }
    }

    private OpenAPI3RouterFactory registerHandlers(OpenAPI3RouterFactory rf) {
        rf.addHandlerByOperationId("fetchPlayerInfo", this::fetchPlayerInfo, this::handleFailure);
        rf.addHandlerByOperationId("createPlayer", this::createPlayer, this::handleFailure);
        rf.addHandlerByOperationId("listPlayers", this::listPlayers, this::handleFailure);

        rf.addHandlerByOperationId("startGame", this::startGame, this::handleFailure);
        return rf;
    }

    private void startGame(RoutingContext ctx) {

    }

    private void listPlayers(RoutingContext ctx) {
        Observable<Player> playersO = playerService.list();
        HttpServerResponse response = ctx.response();

        streamJsonObjectArray(playersO, response);
    }

    private <O> void streamJsonObjectArray(Observable<O> objectsO, HttpServerResponse response) {
        response.setChunked(true);
        Observable<String> joinerO = Observable.create(SyncOnSubscribe.createStateful(() -> "[", (s, o) -> {
            o.onNext(s);
            return ",";
        }));
        joinerO
            .zipWith(objectsO, (joiner, object) -> joiner.concat(JsonObject.mapFrom(object).encodePrettily()))
            .subscribe(
                response::write,
                error -> {
                    response.setStatusCode(500).end(error.getMessage()); // @todo Error & serialization. ctx.fail + failureHandler
                },
                () -> response.end("]"));
    }

    private void fetchPlayerInfo(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        String playerId = params.pathParameter("playerId").getString();
        Single<Player> playerS = playerService.fetch(playerId);
        playerS.subscribe(player -> {
                ctx.response().end(JsonObject.mapFrom(player).encodePrettily());
            },
            error -> {
                if (error instanceof NoSuchElementException) {
                    ctx.response().setStatusCode(404).end();
                    return;
                }

                ctx.response().setStatusCode(500).end(error.getMessage()); // @todo Error & serialization. ctx.fail + failureHandler
            });
    }

    private void createPlayer(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        RequestParameter body = params.body();
        JsonObject jsonBody = body.getJsonObject();
        Player requestPlayer = new Player(jsonBody.getString("name"), jsonBody.getInteger("age"));

        Single<Player> playerS = playerService.create(requestPlayer);
        playerS.subscribe(player -> {
                ctx.response().end(JsonObject.mapFrom(player).encodePrettily());
            },
            error -> {
                if (error instanceof RuntimeException) {
                    ctx.response().setStatusCode(400).end();
                    return;
                }

                ctx.response().setStatusCode(500).end(error.getMessage()); // @todo Error & serialization
            });
    }

    private void handleFailure(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        ctx.response().setStatusCode(400).end(failure.getMessage());
    }
}
