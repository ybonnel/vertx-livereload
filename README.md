# Vertx-livereload

A very simple helper to have a [livereload](http://livereload.com/) server on a vertx application for your static resources.

## Motivation

Refresh a web page can be so long :), so just save your js/css/html file and when you arrived in you browser the change is already there.

## Implementation

All samples are in kotlin, but it's very simple, so the conversion to java must be easy.

Image you avec some static resources :
```kotlin
get("/public/*").handler(
    StaticHandler.create("src/main/resources/public")
        .setCachingEnabled(false)
)
```
this is just the dev example, in real life you may have something like that :
```kotlin
if (getMode() == "dev") {
    get("/public/*").handler(
        StaticHandler.create("src/main/resources/public")
            .setCachingEnabled(false)    
    )
} else {
    get("/public/*").handler(StaticHandler.create("public"))
} 
```

Why not just the "prod mode" code? Because I don't want to wait the compile process to have my update :)

So, as you guess, I have some static files in ```src/main/resources/public``` which are served for url ```/public/*```
Now to add my livereload, I just add this code to my verticle :
```kotlin
import fr.ybonnel.vertx.livereload.LiveReload

class MainVerticle: AbstractVerticle() {
    private lateinit var liveReloadServer: LiveReload

    override fun start(startFuture: Future<Void>) {
        /* ... */
        liveReloadServer = LiveReload(this.vertx, mapOf(
            "src/main/resources/public/" to "/public/"
        ))
        liveReloadServer?.start()?.setHandler(startFuture)
    }

    override fun stop(stopFuture: Future<Void>) {
        /* ... */
        liveReloadServer?.stop()?.setHandler(stopFuture)
    }
}
```