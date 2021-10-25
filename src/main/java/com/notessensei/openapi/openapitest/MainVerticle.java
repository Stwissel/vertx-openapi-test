package com.notessensei.openapi.openapitest;

import java.util.concurrent.TimeUnit;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.openapi.OpenAPILoaderOptions;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;

/**
 * Simple OpenAPI based server - echo only
 *
 * @author Stephan H. Wissel
 */
public class MainVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    MainVerticle verticle = new MainVerticle();
    VertxOptions options = new VertxOptions().setBlockedThreadCheckInterval(30000)
        .setBlockedThreadCheckIntervalUnit(TimeUnit.MILLISECONDS);
    Vertx vertx = Vertx.vertx(options);
    vertx.deployVerticle(verticle);
  }

  /**
   *
   */
  private static final String OPERATION_MODEL = "operationModel";

  @Override
  public void start(Promise<Void> startPromise) {

    OpenAPILoaderOptions loaderOptions = new OpenAPILoaderOptions();

    RouterBuilder.create(getVertx(), "src/main/resources/webroot/APIStressTest.json", loaderOptions)
        .onFailure(startPromise::fail)
        .onSuccess(rb -> this.routerLoaded(startPromise, rb));
  }

  private void routerLoaded(final Promise<Void> startPromise, final RouterBuilder rb) {
    RouterBuilderOptions builderOptions = new RouterBuilderOptions();
    builderOptions.setOperationModelKey(MainVerticle.OPERATION_MODEL);

    rb.setOptions(builderOptions);
    rb.operation("createAlphaObject")
        .handler(this::createAlphaObject)
        .failureHandler(this::failureHandler);
    rb.operation("getAlphaObjects")
        .handler(this::getAlphaObjects)
        .failureHandler(this::failureHandler);
    rb.operation("getAlphaObject")
        .handler(this::getAlphaObject)
        .failureHandler(this::failureHandler);
    rb.operation("updateAlphaObject")
        .handler(this::updateAlphaObject)
        .failureHandler(this::failureHandler);
    rb.operation("deleteAlphaObject")
        .handler(this::deleteAlphaObject)
        .failureHandler(this::failureHandler);

    rb.rootHandler(this::incoming);
    final Router router = rb.createRouter();
    router.route("/").handler(ctx -> ctx.end("Hi there. Try /alphaobjects or /resources"));
    router.route("/resources/*").handler(StaticHandler.create().setDirectoryListing(true));
    final HttpServer server = getVertx().createHttpServer(
        new HttpServerOptions().setPort(8080).setHost("localhost"));
    server.requestHandler(router).listen()
        .onSuccess(v -> {
          System.out.println("Ready on 8080");
          startPromise.complete();
        })
        .onFailure(startPromise::fail);

  }

  // Global Handler -> no current Route
  void incoming(final RoutingContext ctx) {
    System.out.println("== Global handler ==\n   Data:");
    ctx.data().forEach((k, v) -> System.out.printf("%s=%s%n", k, v));
    System.out.println("== Param:");
    ctx.request().params()
        .forEach(entry -> System.out.printf("%s=%s%n", entry.getKey(), entry.getValue()));
    System.out.println("==");
    ctx.addEndHandler(this::endHandler);
    ctx.next();

  }

  void endHandler(final AsyncResult<Void> result) {
    if (result.succeeded()) {
      System.out.println("==> Success");
    } else {
      // This one gets called when things don't work
      // A failed validation is working code
      result.cause().printStackTrace();
    }

  }

  void createAlphaObject(final RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    this.endCall(ctx, 201, "createAlphaObject", body);
  }

  void getAlphaObject(final RoutingContext ctx) {
    this.endCall(ctx, 200, "getAlphaObject");
  }

  void getAlphaObjects(final RoutingContext ctx) {
    System.out.println(ctx.currentRoute().getPath());
    this.endCall(ctx, 200, "getAlphaObjects");
  }


  void updateAlphaObject(final RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    this.endCall(ctx, 200, "updateAlphaObject", body);
  }

  void deleteAlphaObject(final RoutingContext ctx) {
    this.endCall(ctx, 200, "deleteAlphaObject");
  }

  // When things go wrong
  void failureHandler(final RoutingContext ctx) {
    JsonObject response = new JsonObject().put("Status", "The operation failed");
    response.put("path", ctx.currentRoute().getPath());

    Throwable t = ctx.failure();
    if (t != null) {
      response.put("Message", t instanceof NullPointerException ? "Nullpointer" : t.getMessage());
    }

    ctx.response()
        .setStatusCode(500)
        .putHeader("content-type", "application/json")
        .end(response.encodePrettily());
  }

  void endCall(final RoutingContext ctx, final int statusCode, final String operation) {
    this.endCall(ctx, statusCode, operation, null);
  }

  void endCall(final RoutingContext ctx, final int statusCode, final String operation,
      final JsonObject body) {
    final JsonObject result = new JsonObject().put("operation", operation);
    result.put(MainVerticle.OPERATION_MODEL, ctx.get(MainVerticle.OPERATION_MODEL));
    this.addRequestItems(result, ctx.request());
    if (body != null) {
      result.put("body", body);
    }
    ctx.response().setStatusCode(statusCode)
        .putHeader("content-type", "application/json")
        .end(result.encodePrettily());

  }

  void addRequestItems(final JsonObject result, final HttpServerRequest request) {
    request.params().forEach(entry -> result.put(entry.getKey(), entry.getValue()));
  }


}
