package org.sample.jetty.client.http2;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Date: 2021/4/1
 */
public class HttpClientWithHttp2Transport {
    private static final Logger log = LoggerFactory.getLogger(HttpClientWithHttp2Transport.class);

    public static void main(String[] args) {

        System.getProperties().entrySet().stream()
            .filter(entry -> entry.getKey().toString().matches("^(java|jdk|sun|user|file|os)\\..*"))
            .sorted(Comparator.comparing(e -> e.getKey().toString()))
            .forEach(entry ->
                log.info("System Property [" + entry.getKey() + "] = " + entry.getValue()));

        String url = "http://127.0.0.1:8081/tomcat/post";
        String requestBody = "{ \"age\": 0, \"name\": \"string\"}";
        int headerCount = 10;
        int headerSize = 200;
        int threadCount = 100;

        HttpClient httpClient = builderHttpClient();
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    Request request = buildRequest(httpClient, url, requestBody, headerCount, headerSize);
                    ContentResponse contentResponse = null;
                    try {
                        contentResponse = request.send();
                        printResponse(contentResponse);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e);
                    } catch (TimeoutException e) {
                        log.error(e.getMessage(), e);
                    } catch (ExecutionException e) {
                        log.error(e.getMessage(), e);
                    }
                }
                countDownLatch.countDown();
            });
            thread.setDaemon(true);
            thread.setName("jetty-http-client-" + i);
            thread.start();
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        try {
            httpClient.stop();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static Request buildRequest(HttpClient httpClient, String url, String content, int headerCount, int headerSize) {
        ContentProvider contentProvider = new StringContentProvider("application/json", content, StandardCharsets.UTF_8);
        Request request = httpClient.POST(url).content(contentProvider, "application/json");
        request.timeout(6, TimeUnit.SECONDS);
        request.idleTimeout(5, TimeUnit.SECONDS);
        for (int i = 0; i < headerCount; i++) {
            request.header("x-customer-" + i, randomValue(headerSize));
        }
        return request;
    }

    private static String randomValue(int headerSize) {
        StringBuilder headerBuilder = new StringBuilder(headerSize);
        for (int i = 0; i < headerSize; i++) {
            headerBuilder.append((char)(65 + new Random().nextInt(57)));
        }
        return headerBuilder.toString();
    }

    private static void printResponse(ContentResponse contentResponse) {
        try {
            int status = contentResponse.getStatus();
            HttpVersion version = contentResponse.getVersion();
            String contentAsString = contentResponse.getContentAsString();
            String contentEncoding = contentResponse.getHeaders().get(HttpHeader.CONTENT_ENCODING);
            log.info("status: {}, version:{}, contentEncoding:{}, contentAsString:{}", status, version, contentEncoding, contentAsString);
        } catch (Exception e) {
            log.error("print response error");
        }
    }

    private static HttpClient builderHttpClient() {
        HTTP2Client http2Client = new HTTP2Client();
        http2Client.setSelectors(1);
        HttpClientTransport transport = new HttpClientTransportOverHTTP2(http2Client);

        HttpClient httpClient = new HttpClient(transport, null);
        httpClient.setConnectTimeout(1000);
        httpClient.setIdleTimeout(30000);
        // Increase header size
        httpClient.setRequestBufferSize(16384);
        httpClient.setFollowRedirects(true);
        try {
            httpClient.start();
            return httpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
