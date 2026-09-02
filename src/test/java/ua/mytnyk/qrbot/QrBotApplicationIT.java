package ua.mytnyk.qrbot;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ua.mytnyk.analyticscommon.AnalyticsEvent;
import ua.mytnyk.qrbot.domain.BotUser;
import ua.mytnyk.qrbot.repository.BotUserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QrBotApplicationIT {
    private static final String SECRET = "e2e-secret";
    private static final MockWebServer TELEGRAM = startTelegramStub();

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private BotUserRepository users;
    @Autowired
    private MongoTemplate mongo;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("qr_bot_e2e"));
        registry.add("telegram.api-base-url", () -> TELEGRAM.url("/").toString());
        registry.add("telegram.token", () -> "test-token");
        registry.add("telegram.bot-username", () -> "test_bot");
        registry.add("telegram.content-channel-id", () -> "-100123");
        registry.add("telegram.restart-notification-chat-id", () -> "0");
        registry.add("telegram.polling.enabled", () -> "false");
        registry.add("telegram.webhook.enabled", () -> "true");
        registry.add("telegram.webhook.path", () -> "/telegram/webhook");
        registry.add("telegram.webhook.secret", () -> SECRET);
        registry.add("analytics.enabled", () -> "true");
        registry.add("analytics.collection", () -> "analytics_events");
    }

    @BeforeEach
    void resetState() throws InterruptedException {
        users.deleteAll();
        mongo.dropCollection("analytics_events");
        while (TELEGRAM.takeRequest(25, TimeUnit.MILLISECONDS) != null) {
            // Discard StartupNotifier and requests from the preceding test.
        }
    }

    @AfterAll
    static void stopTelegramStub() throws IOException {
        TELEGRAM.shutdown();
    }

    @Test
    void rejectsWebhookWithoutTheTelegramSecretBeforeDispatch() throws Exception {
        var response = post(startUpdate(100), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(users.count()).isZero();
        assertThat(mongo.collectionExists("analytics_events")).isFalse();
        assertThat(TELEGRAM.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void processesStartCommandEndToEndAndPersistsStateAndAnalytics() throws Exception {
        TELEGRAM.enqueue(json(200, "{\"ok\":true,\"result\":{\"message_id\":901}}"));

        var response = post(startUpdate(101), SECRET);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(77L)).hasValueSatisfying(user -> {
            assertThat(user.username()).isEqualTo("alice");
            assertThat(user.state()).isEqualTo(BotUser.State.IDLE);
            assertThat(user.navigationMessageId()).isEqualTo(901);
            assertThat(user.displayedMessageIds()).containsExactly(901);
        });
        assertThat(mongo.findAll(AnalyticsEvent.class, "analytics_events"))
                .singleElement().satisfies(event -> {
                    assertThat(event.action()).isEqualTo("MAIN_MENU_VIEWED");
                    assertThat(event.actorId()).isEqualTo(77L);
                    assertThat(event.contextId()).isEqualTo(88L);
                });
        RecordedRequest telegramRequest = TELEGRAM.takeRequest(2, TimeUnit.SECONDS);
        assertThat(telegramRequest).isNotNull();
        assertThat(telegramRequest.getPath()).isEqualTo("/bottest-token/sendMessage");
        assertThat(telegramRequest.getBody().readUtf8())
                .contains("\"chat_id\":88", "\"inline_keyboard\"");
    }

    @Test
    void acknowledgesUnsupportedAuthenticatedUpdateWithoutSideEffects() throws Exception {
        var response = post("{\"update_id\":102}", SECRET);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.count()).isZero();
        assertThat(TELEGRAM.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void returnsServerErrorWhenTelegramDeliveryFails() {
        TELEGRAM.enqueue(json(500, "{\"ok\":false,\"description\":\"stub failure\"}"));
        var response = post(startUpdate(103), SECRET);
        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }

    private org.springframework.http.ResponseEntity<Void> post(String body, String secret) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (secret != null) {
            headers.set("X-Telegram-Bot-Api-Secret-Token", secret);
        }
        return http.postForEntity("/telegram/webhook", new HttpEntity<>(body, headers), Void.class);
    }

    private static String startUpdate(long updateId) {
        return """
                {"update_id":%d,"message":{"message_id":10,
                "from":{"id":77,"username":"alice","first_name":"Alice"},
                "chat":{"id":88,"type":"private"},"text":"/start"}}
                """.formatted(updateId);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockWebServer startTelegramStub() {
        var server = new MockWebServer();
        try {
            server.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        server.enqueue(json(200, "{\"ok\":true,\"result\":true}"));
        return server;
    }
}
