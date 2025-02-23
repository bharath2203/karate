/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.http;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.cors.CorsService;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class HttpServer {

    protected static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    private final Server server;
    private final CompletableFuture<Void> future;
    private final int port;

    public static class Builder { // TODO fix code duplication with MockServer

        int port;
        boolean ssl;
        boolean local = true;
        File certFile;
        File keyFile;
        boolean corsEnabled;
        ServerHandler handler;
        
        public Builder local(boolean value) {
            local = value;
            return this;
        }

        public Builder http(int value) {
            port = value;
            return this;
        }

        public Builder https(int value) {
            ssl = true;
            port = value;
            return this;
        }

        public Builder certFile(File value) {
            certFile = value;
            return this;
        }

        public Builder keyFile(File value) {
            keyFile = value;
            return this;
        }

        public Builder corsEnabled(boolean value) {
            corsEnabled = value;
            return this;
        }

        public Builder handler(ServerHandler value) {
            handler = value;
            return this;
        }

        public HttpServer build() {
            ServerBuilder sb = Server.builder();
            sb.requestTimeoutMillis(0);
            if (ssl) {
                if (local) {
                    sb.localPort(port, SessionProtocol.HTTPS);
                } else {
                    sb.https(port);
                }
                SslContextFactory factory = new SslContextFactory();
                factory.setCertFile(certFile);
                factory.setKeyFile(keyFile);
                factory.build();
                sb.tls(factory.getCertFile(), factory.getKeyFile());
            } else {
                if (local) {
                    sb.localPort(port, SessionProtocol.HTTP);
                } else {
                    sb.http(port);
                }
            }
            HttpService service = new HttpServerHandler(handler);
            if (corsEnabled) {
                service = service.decorate(CorsService.builderForAnyOrigin().allowAllRequestHeaders(true).newDecorator());
            }
            sb.service("prefix:/", service);
            return new HttpServer(sb);
        }

    }

    public void waitSync() {
        try {
            Thread.currentThread().join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getPort() {
        return port;
    }

    public CompletableFuture stop() {
        return server.stop();
    }

    public static Builder handler(ServerHandler handler) {
        return new Builder().handler(handler);
    }

    public static Builder root(String root) {
        return config(new ServerConfig(root));
    }

    public static Builder config(ServerConfig config) {
        return handler(new RequestHandler(config));
    }

    public HttpServer(ServerBuilder sb) {
        HttpService httpStopService = (ctx, req) -> {
            logger.debug("received command to stop server: {}", req.path());
            this.stop();
            return HttpResponse.of(HttpStatus.ACCEPTED);
        };
        sb.service("/__admin/stop", httpStopService);
        server = sb.build();
        future = server.start();
        future.join();
        this.port = server.activePort().localAddress().getPort();
        logger.debug("server started: {}:{}", server.defaultHostname(), this.port);
    }

}
