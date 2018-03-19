package fr.ybonnel.vertx.livereload;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class LiveReload implements DirectoryChangeListener, Handler<ServerWebSocket> {

    private final AtomicBoolean firstInfo;
    private final ConcurrentHashSet<ServerWebSocket> wsServers;
    private final List<Entry<Path, String>> reloadResources;
    private final DirectoryWatcher directoryWatcher;
    private final HttpServer httpServer;

    public LiveReload(Vertx vertx, Map<String, String> reloadResources) {
        this.reloadResources = reloadResources.entrySet().stream()
                .sorted(Comparator.<Entry<String, String>, Integer>comparing(entry ->
                        entry.getKey().length()
                ).reversed())
                .map(realPathAndResourcePath ->
                        new SimpleEntry<>(
                                FileSystems.getDefault().getPath(realPathAndResourcePath.getKey()).toAbsolutePath(),
                                realPathAndResourcePath.getValue()
                        )
                ).collect(Collectors.toList());
        firstInfo = new AtomicBoolean(true);
        wsServers = new ConcurrentHashSet<>();
        try {
            directoryWatcher = DirectoryWatcher.create(
                    this.reloadResources.stream().map(Entry::getKey).collect(Collectors.toList()),
                    this
            );
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
        httpServer = vertx.createHttpServer()
                .requestHandler(req -> {
                    if (req.path().equals("/livereload.js")) {
                        firstInfo.set(false);
                        req.response().sendFile("livereload.js");
                    }
                })
                .websocketHandler(this);
    }

    public Future<Void> start() {
        return start(35729);
    }

    public Future<Void> start(int port) {
        Future<Void> future = Future.future();
        directoryWatcher.watchAsync();
        httpServer.listen(port, result -> {
            if (result.succeeded()) {
                future.complete();
            } else {
                future.fail(result.cause());
            }
        });
        return future;
    }

    public Future<Void> stop() {
        Future<Void> future = Future.future();
        try {
            directoryWatcher.close();
        } catch (IOException ignore) {
        }
        httpServer.close(result -> {
            if (result.succeeded()) {
                future.complete();
            } else {
                future.fail(result.cause());
            }
        });
        return future;
    }

    @Override
    public void onEvent(DirectoryChangeEvent event) {
        if (event.path() == null || !event.path().toFile().isFile()) {
            return;
        }

        String eventPath = event.path().toAbsolutePath().toString();
        reloadResources.stream().filter(realPathAndResourcePath ->
                eventPath.startsWith(realPathAndResourcePath.getKey().toString())
        ).findFirst().ifPresent(reloadResource ->
                wsServers.forEach(ws ->
                        ws.writeTextMessage(new JsonObject()
                                .put("command", "reload")
                                .put("path", eventPath.replace(reloadResource.getKey().toString(), reloadResource.getValue()))
                                .put("liveCSS", true)
                                .toString()
                        )
                ));

    }

    @Override
    public void handle(ServerWebSocket wsServer) {
        wsServers.add(wsServer);
        wsServer.handler(req -> {
            if (req.toJsonObject().getString("command").equals("hello")) {
                wsServer.writeTextMessage(new JsonObject()
                        .put("command", "hello")
                        .put("serverName", "vertx-livereload")
                        .put("protocols", req.toJsonObject().getJsonArray("protocols"))
                        .toString()
                );
            } else if (req.toJsonObject().getString("command").equals("info")
                    && firstInfo.getAndSet(false)
                    ) {
                wsServer.writeTextMessage(new JsonObject()
                        .put("command", "reload")
                        .put("path", "/")
                        .put("liveCSS", true)
                        .toString()
                );
            }
        }).closeHandler(event -> wsServers.remove(wsServer));
    }
}
