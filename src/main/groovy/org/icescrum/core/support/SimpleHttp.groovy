/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Minimal HTTP helper replacing the dead http-builder 0.7.2 (Groovy 2 only).
 * Only what the iceScrum call sites need: GET/POST/DELETE with timeouts,
 * custom headers and an optional trust-everything SSL mode for webhooks
 * targeting self-signed endpoints.
 */
package org.icescrum.core.support

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration

class SimpleHttp {

    static class Response {
        int status
        String body

        boolean isSuccess() {
            return status >= 200 && status < 300
        }

        String getStatusLine() {
            return "HTTP ${status}"
        }

        String getData() {
            return body
        }
    }

    static Response get(String url, Map<String, String> headers = [:], int connectTimeoutMs = 5000, int readTimeoutMs = 5000, boolean ignoreSsl = false) {
        return execute('GET', url, null, headers, connectTimeoutMs, readTimeoutMs, ignoreSsl)
    }

    static Response post(String url, String body, Map<String, String> headers = [:], int connectTimeoutMs = 5000, int readTimeoutMs = 5000, boolean ignoreSsl = false) {
        return execute('POST', url, body, headers, connectTimeoutMs, readTimeoutMs, ignoreSsl)
    }

    static Response delete(String url, Map<String, String> headers = [:], int connectTimeoutMs = 5000, int readTimeoutMs = 5000, boolean ignoreSsl = false) {
        return execute('DELETE', url, null, headers, connectTimeoutMs, readTimeoutMs, ignoreSsl)
    }

    private static Response execute(String method, String url, String body, Map<String, String> headers, int connectTimeoutMs, int readTimeoutMs, boolean ignoreSsl) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
        if (ignoreSsl) {
            clientBuilder.sslContext(trustEverythingContext())
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .method(method, body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody())
        headers?.each { k, v ->
            if (v != null) {
                requestBuilder.header(k, v.toString())
            }
        }
        HttpResponse<String> response = clientBuilder.build().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        return new Response(status: response.statusCode(), body: response.body())
    }

    private static SSLContext trustEverythingContext() {
        def trustAll = [
                getAcceptedIssuers: { -> new X509Certificate[0] },
                checkClientTrusted: { X509Certificate[] certs, String authType -> },
                checkServerTrusted: { X509Certificate[] certs, String authType -> }
        ] as X509TrustManager
        SSLContext context = SSLContext.getInstance('TLS')
        context.init(null, [trustAll] as TrustManager[], new SecureRandom())
        return context
    }
}
