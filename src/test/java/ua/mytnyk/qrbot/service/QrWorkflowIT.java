package ua.mytnyk.qrbot.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ua.mytnyk.qrbot.domain.BotUser;
import ua.mytnyk.qrbot.domain.CustomerFeedback;
import ua.mytnyk.qrbot.domain.PendingPasswordOptions;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrContentItem;
import ua.mytnyk.qrbot.domain.QrListPreferences;
import ua.mytnyk.qrbot.domain.QrListSort;
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.repository.BotUserRepository;
import ua.mytnyk.qrbot.repository.CustomerFeedbackRepository;
import ua.mytnyk.qrbot.repository.DonationRepository;
import ua.mytnyk.qrbot.repository.QrAccessRepository;
import ua.mytnyk.qrbot.repository.QrCodeRepository;
import ua.mytnyk.qrbot.telegram.handler.menu.MainMenuCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.SelectQrTypeCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.DeleteQrCallbackHandler;
import ua.mytnyk.qrbot.web.QrRedirectController;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.config.TelegramProperties;
import ua.mytnyk.telegram.common.model.common.api.BotCommand;
import ua.mytnyk.telegram.common.model.common.api.TelegramMedia;
import ua.mytnyk.telegram.common.model.common.api.markup.keyboard.inline.InlineKeyboard;
import ua.mytnyk.telegram.common.model.common.webhook.Chat;
import ua.mytnyk.telegram.common.model.common.webhook.Message;
import ua.mytnyk.telegram.common.model.common.webhook.User;
import ua.mytnyk.telegram.common.model.common.webhook.CallbackQuery;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;
import ua.mytnyk.telegram.common.model.common.webhook.PreCheckoutQuery;
import ua.mytnyk.telegram.common.model.common.webhook.SuccessfulPayment;
import ua.mytnyk.telegram.common.model.common.api.invoice.InvoiceCurrency;
import ua.mytnyk.telegram.common.model.common.api.invoice.SendInvoiceRequest;
import ua.mytnyk.telegram.common.model.common.api.precheckout.PreCheckoutAnswerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(QrWorkflowIT.FakeTelegramConfiguration.class)
class QrWorkflowIT {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @Autowired QrWorkflow workflow;
    @Autowired BotUserRepository users;
    @Autowired QrCodeRepository qrs;
    @Autowired QrAccessRepository accesses;
    @Autowired CustomerFeedbackRepository feedback;
    @Autowired DonationRepository donations;
    @Autowired MongoTemplate mongo;
    @Autowired QrLinkService links;
    @Autowired RecordingTelegramClient telegram;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("qr_workflow_it"));
        registry.add("telegram.token", () -> "isolated-test-token");
        registry.add("telegram.bot-username", () -> "@isolated_test_bot");
        registry.add("telegram.content-channel-id", () -> "-100123");
        registry.add("telegram.startup-notifier.enabled", () -> "false");
        registry.add("telegram.polling.enabled", () -> "false");
        registry.add("telegram.webhook.enabled", () -> "false");
    }

    @BeforeEach
    void reset() {
        mongo.getDb().drop();
        telegram.reset();
    }

    @Test
    void menuResetsNewAndExistingUsersAndReplacesBothNavigationShapes() {
        var actor = actor(77, "alice");
        workflow.showMainMenu(actor, 88);
        assertThat(users.findById(77L)).hasValueSatisfying(user -> {
            assertThat(user.state()).isEqualTo(BotUser.State.IDLE);
            assertThat(user.navigationMessageId()).isEqualTo(1);
            assertThat(user.displayedMessageIds()).containsExactly(1);
        });

        users.save(botUser(actor, BotUser.State.IDLE, null, null, null, 5, List.of(4, 5), null));
        workflow.showMainMenu(actor, 88);
        assertThat(telegram.deleted()).containsExactly("88:4", "88:5");

        var view = workflow.beginCreation(actor, 88);
        workflow.replaceNavigation(actor, 88, 9, true, view);
        assertThat(telegram.edited()).singleElement().satisfies(value -> assertThat(value).contains("88:9"));
        workflow.replaceNavigation(actor, 88, 10, false, view);
        assertThat(telegram.inline()).hasSize(3);
    }

    @Test
    void feedbackFlowPersistsTrimmedTextAndReturnsToMainMenu() {
        var actor = actor(77, "alice");
        var view = workflow.beginFeedback(actor);
        assertThat(view.text()).contains("скаргу або пропозицію");
        assertThat(users.findById(77L).orElseThrow().state()).isEqualTo(BotUser.State.WAITING_FOR_FEEDBACK);
        assertThat(workflow.isWaitingForFeedback(77L)).isTrue();

        workflow.acceptFeedback(message(actor, 88, 10, "  Додайте темну тему  "));

        assertThat(feedback.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isNotBlank();
            assertThat(saved.customerId()).isEqualTo(77L);
            assertThat(saved.username()).isEqualTo("alice");
            assertThat(saved.text()).isEqualTo("Додайте темну тему");
            assertThat(saved.type()).isEqualTo(CustomerFeedback.Type.GENERAL);
            assertThat(saved.createdAt()).isNotNull();
        });
        assertThat(users.findById(77L).orElseThrow().state()).isEqualTo(BotUser.State.IDLE);
        assertThat(telegram.inline()).singleElement().satisfies(sent -> assertThat(sent).contains("Дякуємо"));
    }

    @Test
    void paymentSupportFlowUsesDedicatedStateAndFeedbackType() {
        var actor = actor(77, "alice");
        workflow.showPaymentSupport(message(actor, 88, 10, "/paysupport"));
        assertThat(users.findById(77L).orElseThrow().state())
                .isEqualTo(BotUser.State.WAITING_FOR_PAYMENT_SUPPORT);
        assertThat(workflow.isWaitingForPaymentSupport(77L)).isTrue();

        workflow.acceptPaymentSupport(message(actor, 88, 11, "  50 Stars, прошу повернення  "));

        assertThat(feedback.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.text()).isEqualTo("50 Stars, прошу повернення");
            assertThat(saved.type()).isEqualTo(CustomerFeedback.Type.PAYMENT_SUPPORT);
        });
        assertThat(users.findById(77L).orElseThrow().state()).isEqualTo(BotUser.State.IDLE);
        assertThat(telegram.inline()).hasSize(2).last().satisfies(sent ->
                assertThat(sent).contains("Запит щодо платежу отримано"));
    }

    @Test
    void feedbackSubmissionRejectsBlankTextAndWrongState() {
        var actor = actor(77, "alice");
        users.save(botUser(actor, BotUser.State.WAITING_FOR_FEEDBACK, null, null, null, null, null, null));
        assertThatThrownBy(() -> workflow.acceptFeedback(message(actor, 88, 10, "   ")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
        assertThat(feedback.findAll()).isEmpty();

        users.save(botUser(actor, BotUser.State.IDLE, null, null, null, null, null, null));
        assertThatThrownBy(() -> workflow.acceptFeedback(message(actor, 88, 11, "Suggestion")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected user state");
        assertThat(feedback.findAll()).isEmpty();
    }

    @Test
    void classicQrValidatesUrlCreatesShortLinkRedirectsAndCountsEveryOpening() {
        var actor = actor(77, "alice");
        var selection = workflow.selectType(actor, 88, QrType.CLASSIC);
        assertThat(selection.text()).contains("http://").contains("https://");
        assertThat(users.findById(77L).orElseThrow().state()).isEqualTo(BotUser.State.WAITING_FOR_CLASSIC_URL);
        assertThat(workflow.isWaitingForClassicUrl(77L)).isTrue();

        workflow.acceptClassicUrl(message(actor, 88, 1, "javascript:alert(1)"));
        assertThat(qrs.findAll()).isEmpty();
        assertThat(telegram.text()).singleElement().satisfies(value -> assertThat(value).contains("http/https"));
        assertThat(users.findById(77L).orElseThrow().state()).isEqualTo(BotUser.State.WAITING_FOR_CLASSIC_URL);

        workflow.acceptClassicUrl(message(actor, 88, 2, " https://example.com/menu?q=lunch "));
        var created = qrs.findAll().get(0);
        assertThat(created.type()).isEqualTo(QrType.CLASSIC);
        assertThat(created.targetUrl()).isEqualTo("https://example.com/menu?q=lunch");
        assertThat(created.token()).matches("[A-Za-z0-9]{8}");
        assertThat(created.openCount()).isZero();
        assertThat(users.findById(77L).orElseThrow().state()).isEqualTo(BotUser.State.IDLE);
        assertThat(telegram.photos()).singleElement().satisfies(value ->
                assertThat(value).contains("qr-").contains(".png").contains("/" + created.token()));
        assertThat(telegram.inline()).singleElement().satisfies(value ->
                assertThat(value).contains("Ваш QR готовий ⬆️"));

        var redirect = new QrRedirectController(qrs, links, mongo);
        assertThat(redirect.redirect(created.token()).getHeaders().getLocation())
                .hasToString("https://example.com/menu?q=lunch");
        assertThat(redirect.redirect(created.token()).getHeaders().getLocation())
                .hasToString("https://example.com/menu?q=lunch");
        assertThat(qrs.findById(created.id()).orElseThrow().openCount()).isEqualTo(2);
        assertThat(accesses.findAll()).isEmpty();
    }

    @Test
    void donationFlowSendsInvoiceValidatesCheckoutAndStoresSuccessfulPaymentOnce() {
        var actor = actor(77, "alice");
        var menu = workflow.donationMenu(actor);
        assertThat(menu.text()).contains("Stars");

        workflow.sendDonationInvoice(actor, 88, 50);
        assertThat(telegram.invoices()).singleElement().satisfies(invoice -> {
            assertThat(invoice.getChatId()).isEqualTo(88L);
            assertThat(invoice.getCurrency()).isEqualTo(InvoiceCurrency.XTR);
            assertThat(invoice.getPrices()).singleElement().satisfies(price -> assertThat(price.getAmount()).isEqualTo(50));
            assertThat(invoice.getPayload()).startsWith("donation:77:50:");
        });
        var payload = telegram.invoices().get(0).getPayload();
        workflow.handleDonationPreCheckout(PreCheckoutQuery.builder().id("checkout").from(actor)
                .currency(InvoiceCurrency.XTR).totalAmount(50).invoicePayload(payload).build());
        assertThat(telegram.checkoutAnswers()).singleElement().satisfies(answer -> {
            assertThat(answer.getPreCheckoutQueryId()).isEqualTo("checkout");
            assertThat(answer.isOk()).isTrue();
            assertThat(answer.getErrorMessage()).isNull();
        });

        var payment = SuccessfulPayment.builder().currency(InvoiceCurrency.XTR).totalAmount(50)
                .invoicePayload(payload).telegramPaymentChargeId("charge-1").build();
        var paymentMessage = Message.builder().from(actor).chat(Chat.builder().id(88).build())
                .successfulPayment(payment).build();
        workflow.acceptSuccessfulDonation(paymentMessage);
        workflow.acceptSuccessfulDonation(paymentMessage);
        assertThat(donations.findAll()).singleElement().satisfies(donation -> {
            assertThat(donation.telegramPaymentChargeId()).isEqualTo("charge-1");
            assertThat(donation.customerId()).isEqualTo(77L);
            assertThat(donation.username()).isEqualTo("alice");
            assertThat(donation.amount()).isEqualTo(50);
            assertThat(donation.paidAt()).isNotNull();
        });
    }

    @Test
    void customDonationAndPaymentValidationCoverInvalidBranches() {
        var actor = actor(77, "alice");
        workflow.beginCustomDonation(actor);
        workflow.acceptCustomDonationAmount(message(actor, 88, 1, "not-a-number"));
        workflow.acceptCustomDonationAmount(message(actor, 88, 2, "10001"));
        assertThat(telegram.invoices()).isEmpty();
        assertThat(telegram.text()).hasSize(2);

        workflow.acceptCustomDonationAmount(message(actor, 88, 3, "10"));
        assertThat(telegram.invoices()).singleElement().satisfies(invoice ->
                assertThat(invoice.getPrices().get(0).getAmount()).isEqualTo(10));
        assertThat(users.findById(77L).orElseThrow().state()).isEqualTo(BotUser.State.IDLE);

        workflow.handleDonationPreCheckout(PreCheckoutQuery.builder().id("bad").from(actor)
                .currency(InvoiceCurrency.XTR).totalAmount(50).invoicePayload("invalid").build());
        assertThat(telegram.checkoutAnswers()).singleElement().satisfies(answer -> {
            assertThat(answer.isOk()).isFalse();
            assertThat(answer.getErrorMessage()).isNotBlank();
        });
        workflow.acceptSuccessfulDonation(Message.builder().from(actor).chat(Chat.builder().id(88).build())
                .successfulPayment(SuccessfulPayment.builder().currency(InvoiceCurrency.XTR).totalAmount(1)
                        .invoicePayload("invalid").telegramPaymentChargeId("bad-charge").build()).build());
        assertThat(donations.findAll()).isEmpty();
    }

    @Test
    void contentCreationCoversEmptyCouponTextMediaAndPasswordChoices() {
        var actor = actor(77, "alice");
        users.save(botUser(actor, BotUser.State.WAITING_FOR_CONTENT, QrType.CONTENT, null, null, null, null, null));
        assertThat(workflow.finishContentSelection(actor, 88).text()).contains("0");

        users.save(botUser(actor, BotUser.State.WAITING_FOR_CONTENT, QrType.COUPON, 30,
                List.of(30), 8, List.of(8), List.of(textItem("coupon", 10))));
        workflow.finishContentSelection(actor, 88);
        assertThat(qrs.findAll()).singleElement().satisfies(qr -> assertThat(qr.type()).isEqualTo(QrType.COUPON));

        mongo.getDb().drop();
        telegram.reset();
        users.save(botUser(actor, BotUser.State.WAITING_FOR_CONTENT, QrType.CONTENT, null, null, null, null, null));
        workflow.acceptContent(message(actor, 88, 10, "hello"));
        assertThat(qrs.findAll()).isEmpty();
        assertThat(users.findById(77L).orElseThrow().state())
                .isEqualTo(BotUser.State.WAITING_FOR_CREATION_PASSWORD);
        workflow.skipCreationPassword(actor, 88);
        assertThat(qrs.findAll()).singleElement().satisfies(qr -> {
            assertThat(qr.type()).isEqualTo(QrType.CONTENT);
            assertThat(qr.passwordHash()).isNull();
            assertThat(qr.previewText()).isEqualTo("hello");
        });

        mongo.getDb().drop();
        telegram.reset();
        users.save(botUser(actor, BotUser.State.WAITING_FOR_CREATION_PASSWORD, QrType.CONTENT, 40,
                List.of(40), null, null, List.of(textItem("secret content", 10))));
        workflow.acceptCreationPassword(message(actor, 88, 11, "  SeCrEt  "));
        assertThat(users.findById(77L).orElseThrow().state())
                .isEqualTo(BotUser.State.WAITING_FOR_CREATION_CASE_CHOICE);
        workflow.chooseCreationPasswordCase(actor, 88, true);
        assertThat(qrs.findAll()).singleElement().satisfies(qr -> {
            assertThat(qr.passwordHash()).isNotNull();
            assertThat(qr.ignorePasswordCase()).isTrue();
        });
    }

    @Test
    void contentSubmissionRejectsDuplicatesOtherGroupsUnsupportedAndOversizedText() {
        var actor = actor(77, "alice");
        var existing = List.of(new QrContentItem(QrContentItem.Kind.PHOTO, null, null, "p1", "u1", 10));
        users.save(botUser(actor, BotUser.State.WAITING_FOR_CONTENT, QrType.CONTENT, 20,
                List.of(20), 5, List.of(5), existing, "group-1", null));
        workflow.acceptContent(photoMessage(actor, 88, 10, "group-1"));
        workflow.acceptContent(photoMessage(actor, 88, 11, "group-2"));
        assertThat(users.findById(77L).orElseThrow().pendingContentItems()).hasSize(1);

        users.save(botUser(actor, BotUser.State.WAITING_FOR_CONTENT, QrType.CONTENT, null,
                null, null, null, null));
        workflow.acceptContent(message(actor, 88, 12, "x".repeat(4097)));
        assertThat(users.findById(77L).orElseThrow().pendingContentItems()).isNull();
        assertThat(telegram.text()).singleElement().satisfies(value -> assertThat(value).contains("88:"));

        users.save(botUser(actor, BotUser.State.WAITING_FOR_CONTENT, QrType.CONTENT, null,
                null, null, null, null));
        assertThatThrownBy(() -> workflow.acceptContent(Message.builder().messageId(13).from(actor)
                .chat(Chat.builder().id(88).build()).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
    }

    @Test
    void passwordInputValidationCoversNullBlankCommandAndMissingOptions() {
        var actor = actor(77, "alice");
        for (String invalid : new String[]{null, "   ", "/command"}) {
            users.save(botUser(actor, BotUser.State.WAITING_FOR_CREATION_PASSWORD, QrType.CONTENT, 20,
                    List.of(20), null, null, List.of(textItem("content", 1))));
            assertThatThrownBy(() -> workflow.acceptCreationPassword(message(actor, 88, 20, invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        users.save(botUser(actor, BotUser.State.WAITING_FOR_CREATION_CASE_CHOICE, QrType.CONTENT, 20,
                List.of(20), null, null, List.of(textItem("content", 1))));
        assertThatThrownBy(() -> workflow.chooseCreationPasswordCase(actor, 88, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("options");
    }

    @Test
    void listFlowCoversDefaultsEveryFilterSortPageAndEmptyResults() {
        var actor = actor(77, "alice");
        for (int i = 0; i < 7; i++) {
            qrs.insert(qr("00000000-0000-0000-0000-00000000000" + i, QrType.values()[i % 3],
                    i % 2 == 0 ? QrStatus.ACTIVE : QrStatus.REDEEMED, 77, null, null));
        }
        var first = workflow.listCreatedQrs(actor, 88);
        assertThat(first.text()).contains("7");
        workflow.updateListPreferences(actor, 88, "list:type:CONTENT");
        workflow.updateListPreferences(actor, 88, "list:type:CONTENT");
        workflow.updateListPreferences(actor, 88, "list:status:ACTIVE");
        workflow.updateListPreferences(actor, 88, "list:status:ACTIVE");
        workflow.updateListPreferences(actor, 88, "list:sort:OLDEST");
        workflow.updateListPreferences(actor, 88, "list:page:99");
        var stored = users.findById(77L).orElseThrow().listPreferences();
        assertThat(stored.sort()).isEqualTo(QrListSort.OLDEST);
        assertThat(stored.page()).isEqualTo(99);
    }

    @Test
    void detailsAndImageCoverMissingDeletedActiveProtectedAndRedeemedQrs() {
        var actor = actor(77, "alice");
        workflow.showQrDetails("missing", actor, 88);
        workflow.showQrImage("missing", actor, 88);

        var deleted = qr("10000000-0000-0000-0000-000000000001", QrType.CONTENT,
                QrStatus.DELETED, 77, null, null);
        qrs.insert(deleted);
        workflow.showQrDetails(deleted.id(), actor, 88);

        var protectedQr = qr("10000000-0000-0000-0000-000000000002", QrType.CONTENT,
                QrStatus.ACTIVE, 77, new byte[]{1}, new byte[]{2});
        qrs.insert(protectedQr);
        workflow.showQrDetails(protectedQr.id(), actor, 88);
        workflow.showQrImage(protectedQr.id(), actor, 88);
        assertThat(telegram.photos()).singleElement()
                .satisfies(value -> assertThat(value).contains("https://qr.twob.cc/")
                        .matches(".*https://qr\\.twob\\.cc/[A-Za-z0-9]{8}.*"));

        var redeemed = new QrCode("10000000-0000-0000-0000-000000000003", null, QrType.COUPON,
                QrStatus.REDEEMED, 77, -100123, 20, null, null, Instant.now(), 2, List.of(20),
                null, 77L, null, " ", null, null, "preview");
        qrs.insert(redeemed);
        workflow.showQrDetails(redeemed.id(), actor, 88);
        assertThat(telegram.inline()).isNotEmpty();
    }

    @Test
    void deletionAndPasswordChangeCoverInvalidAndSuccessfulPaths() {
        var actor = actor(77, "alice");
        assertThat(workflow.softDelete("missing", actor, 88).deleted()).isFalse();
        var plain = qr("20000000-0000-0000-0000-000000000001", QrType.CONTENT,
                QrStatus.ACTIVE, 77, null, null);
        qrs.insert(plain);
        assertThat(workflow.beginPasswordChange(plain.id(), actor, 88).text()).contains("QR");

        var protectedQr = qr("20000000-0000-0000-0000-000000000002", QrType.CONTENT,
                QrStatus.ACTIVE, 77, new byte[]{1}, new byte[]{2});
        qrs.insert(protectedQr);
        workflow.beginPasswordChange(protectedQr.id(), actor, 88);
        workflow.acceptPasswordChange(message(actor, 88, 20, "New Password"));
        workflow.chooseChangedPasswordCase(actor, 88, false);
        assertThat(qrs.findById(protectedQr.id()).orElseThrow().passwordHash()).isNotEqualTo(new byte[]{2});

        assertThat(workflow.softDelete(plain.id(), actor, 88).deleted()).isTrue();
        assertThat(qrs.findById(plain.id()).orElseThrow().status()).isEqualTo(QrStatus.DELETED);
    }

    @Test
    void openingCoversMissingDeletedRedeemedCouponOwnershipAndUnprotectedContent() {
        var actor = actor(77, "alice");
        assertThat(workflow.open("missing", message(actor, 88, 1, "/start missing")))
                .isEqualTo(QrWorkflow.OpenResult.NOT_FOUND);

        var deleted = qr("30000000-0000-0000-0000-000000000001", QrType.CONTENT,
                QrStatus.DELETED, 77, null, null);
        var redeemed = qr("30000000-0000-0000-0000-000000000002", QrType.CONTENT,
                QrStatus.REDEEMED, 77, null, null);
        var foreignCoupon = qr("30000000-0000-0000-0000-000000000003", QrType.COUPON,
                QrStatus.ACTIVE, 99, null, null);
        qrs.insert(List.of(deleted, redeemed, foreignCoupon));
        assertThat(workflow.open(deleted.id(), message(actor, 88, 2, "open"))).isEqualTo(QrWorkflow.OpenResult.NOT_FOUND);
        assertThat(workflow.open(redeemed.id(), message(actor, 88, 3, "open"))).isEqualTo(QrWorkflow.OpenResult.NOT_FOUND);
        assertThat(workflow.open(foreignCoupon.id(), message(actor, 88, 4, "open"))).isEqualTo(QrWorkflow.OpenResult.NOT_FOUND);

        var content = qr("30000000-0000-0000-0000-000000000004", QrType.CONTENT,
                QrStatus.ACTIVE, 99, null, null);
        qrs.insert(content);
        var navigationCountBeforeDelivery = telegram.inline().size();
        assertThat(workflow.open(content.id(), message(actor, 88, 5, "open")))
                .isEqualTo(QrWorkflow.OpenResult.DELIVERED);
        assertThat(telegram.inline()).hasSize(navigationCountBeforeDelivery);
        assertThat(accesses.findAll()).singleElement().satisfies(access -> {
            assertThat(access.qrId()).isEqualTo(content.id());
            assertThat(access.userId()).isEqualTo(77L);
        });
    }

    @Test
    void protectedOpeningCoversWrongCorrectCaseInsensitiveAndInactivePasswords() {
        var actor = actor(77, "alice");
        var hasher = new PasswordHasher();
        var password = hasher.hash("secret");
        var qr = qr("40000000-0000-0000-0000-000000000001", QrType.CONTENT,
                QrStatus.ACTIVE, 99, password.salt(), password.hash());
        qrs.insert(new QrCode(qr.id(), qr.token(), qr.type(), qr.status(), qr.ownerId(), qr.channelId(),
                qr.messageId(), qr.passwordSalt(), qr.passwordHash(), qr.createdAt(), qr.openCount(),
                qr.messageIds(), qr.contentItems(), null, null, null, null, true, qr.previewText()));
        assertThat(workflow.open(qr.id(), message(actor, 88, 1, "open")))
                .isEqualTo(QrWorkflow.OpenResult.PASSWORD_REQUIRED);
        assertThat(workflow.acceptOpeningPassword(message(actor, 88, 2, "wrong"))).isFalse();
        assertThat(workflow.acceptOpeningPassword(message(actor, 88, 3, "  SeCrEt "))).isTrue();

        mongo.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("id").is(qr.id())),
                new org.springframework.data.mongodb.core.query.Update().set("status", QrStatus.DELETED), QrCode.class);
        users.save(new BotUser(actor.getId(), actor.getUsername(), BotUser.State.WAITING_FOR_OPEN_PASSWORD,
                null, null, qr.id(), QrListPreferences.defaults(), null, Instant.now(), null, null,
                null, null, null));
        assertThat(workflow.acceptOpeningPassword(message(actor, 88, 4, "secret"))).isFalse();
    }

    @Test
    void couponRedemptionCoversConfirmationMismatchMissingAndSuccess() {
        var actor = actor(77, "alice");
        var coupon = qr("50000000-0000-0000-0000-000000000001", QrType.COUPON,
                QrStatus.ACTIVE, 77, null, null);
        qrs.insert(coupon);
        assertThat(workflow.open(coupon.id(), message(actor, 88, 1, "open")))
                .isEqualTo(QrWorkflow.OpenResult.CONFIRMATION_REQUIRED);
        assertThat(workflow.confirmRedemption("different", actor, 88)).isFalse();

        users.save(new BotUser(77L, "alice", BotUser.State.WAITING_FOR_REDEEM_CONFIRMATION,
                null, null, "missing", QrListPreferences.defaults(), null, Instant.now(), null,
                null, null, null, null));
        assertThat(workflow.confirmRedemption("missing", actor, 88)).isFalse();

        workflow.open(coupon.id(), message(actor, 88, 2, "open"));
        assertThat(workflow.confirmRedemption(coupon.id(), actor, 88)).isTrue();
        assertThat(qrs.findById(coupon.id()).orElseThrow().status()).isEqualTo(QrStatus.REDEEMED);
        assertThat(accesses.findAll()).singleElement()
                .satisfies(access -> assertThat(access.qrId()).isEqualTo(coupon.id()));
    }

    @Test
    void singleUseGiftCoversImmediateAndPasswordProtectedRedemption() {
        var recipient = actor(77, "alice");
        var plain = qr("60000000-0000-0000-0000-000000000001", QrType.SINGLE_USE,
                QrStatus.ACTIVE, 99, null, null);
        qrs.insert(plain);
        assertThat(workflow.open(plain.id(), message(recipient, 88, 1, "open")))
                .isEqualTo(QrWorkflow.OpenResult.DELIVERED);
        assertThat(qrs.findById(plain.id()).orElseThrow().status()).isEqualTo(QrStatus.REDEEMED);
        assertThat(workflow.open(plain.id(), message(recipient, 88, 2, "open")))
                .isEqualTo(QrWorkflow.OpenResult.NOT_FOUND);

        var password = new PasswordHasher().hash("gift");
        var protectedGift = qr("60000000-0000-0000-0000-000000000002", QrType.SINGLE_USE,
                QrStatus.ACTIVE, 99, password.salt(), password.hash());
        qrs.insert(protectedGift);
        assertThat(workflow.open(protectedGift.id(), message(recipient, 88, 3, "open")))
                .isEqualTo(QrWorkflow.OpenResult.PASSWORD_REQUIRED);
        assertThat(workflow.acceptOpeningPassword(message(recipient, 88, 4, "gift"))).isTrue();
        assertThat(qrs.findById(protectedGift.id()).orElseThrow().status()).isEqualTo(QrStatus.REDEEMED);
    }

    @Test
    void coversStartupNotifierAndNullablePreferenceNormalizationInIsolatedContext() {
        var telegramClient = org.mockito.Mockito.mock(TelegramClient.class);
        new ua.mytnyk.qrbot.telegram.StartupNotifier(telegramClient, 0, true).notifyRestart();
        new ua.mytnyk.qrbot.telegram.StartupNotifier(telegramClient, 123, true).notifyRestart();
        assertThat(new QrListPreferences(EnumSet.allOf(QrType.class),
                EnumSet.allOf(QrStatus.class), QrListSort.NEWEST, null).page()).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new QrImageGenerator().generatePng(null))
                .isInstanceOf(RuntimeException.class);
        assertThat(workflow.isWaitingForContent(999L)).isFalse();
        assertThat(workflow.isCurrentNavigation(999L, 1)).isFalse();
        var mockWorkflow = org.mockito.Mockito.mock(QrWorkflow.class);
        var handlerTelegram = org.mockito.Mockito.mock(TelegramClient.class);
        var callback = callbackWithoutText(QrWorkflow.MENU_HOME);
        org.mockito.Mockito.when(mockWorkflow.isCurrentNavigation(77L, 9)).thenReturn(true);
        new MainMenuCallbackHandler(mockWorkflow, handlerTelegram).handle(callback);
        org.mockito.Mockito.when(mockWorkflow.softDelete("123e4567-e89b-12d3-a456-426614174000", actor(77, "alice"), 88))
                .thenReturn(new QrWorkflow.DeleteResult(false, new BotView("x", null)));
        new DeleteQrCallbackHandler(mockWorkflow, handlerTelegram).handle(
                callbackWithoutText("qr:delete:123e4567-e89b-12d3-a456-426614174000"));
        org.mockito.Mockito.when(mockWorkflow.selectType(actor(77, "alice"), 88, QrType.CONTENT)).thenReturn(null);
        new SelectQrTypeCallbackHandler(mockWorkflow, handlerTelegram).handle(
                callbackWithoutText("create:type:CONTENT"));
    }

    private static UpdateWebhook callbackWithoutText(String data) {
        var user = actor(77, "alice");
        return UpdateWebhook.builder().callbackQuery(CallbackQuery.builder().id("cb").from(user)
                .message(Message.builder().messageId(9).from(user).chat(Chat.builder().id(88).build()).build())
                .data(data).build()).build();
    }

    private static User actor(long id, String username) {
        return User.builder().id(id).username(username).firstName("Alice").lastName("Tester").build();
    }

    private static Message message(User actor, long chatId, int id, String text) {
        return Message.builder().messageId(id).from(actor).chat(Chat.builder().id(chatId).type("private").build())
                .text(text).build();
    }

    private static Message photoMessage(User actor, long chatId, int id, String group) {
        var photo = new ua.mytnyk.telegram.common.model.common.webhook.PhotoSize();
        photo.setFileId("photo-" + id);
        photo.setFileUniqueId("unique-" + id);
        return Message.builder().messageId(id).from(actor).chat(Chat.builder().id(chatId).build())
                .photo(List.of(photo)).mediaGroupId(group).build();
    }

    private static QrContentItem textItem(String text, int order) {
        return new QrContentItem(QrContentItem.Kind.TEXT, text, null, null, null, order);
    }

    private static BotUser botUser(User actor, BotUser.State state, QrType type, Integer channelId,
                                   List<Integer> pendingIds, Integer navigationId,
                                   List<Integer> displayedIds, List<QrContentItem> content) {
        return botUser(actor, state, type, channelId, pendingIds, navigationId, displayedIds, content,
                null, null);
    }

    private static BotUser botUser(User actor, BotUser.State state, QrType type, Integer channelId,
                                   List<Integer> pendingIds, Integer navigationId,
                                   List<Integer> displayedIds, List<QrContentItem> content,
                                   String mediaGroup, PendingPasswordOptions options) {
        return new BotUser(actor.getId(), actor.getUsername(), state, type, channelId, null,
                QrListPreferences.defaults(), navigationId, Instant.now(), pendingIds, mediaGroup,
                displayedIds, content, options);
    }

    private static QrCode qr(String id, QrType type, QrStatus status, long owner,
                             byte[] salt, byte[] hash) {
        var compactId = id.replace("-", "");
        var token = compactId.substring(compactId.length() - 8);
        return new QrCode(id, token, type, status, owner, -100123, 20, salt, hash, Instant.now(), 0,
                List.of(20), List.of(textItem("preview content", 20)), null, null, null, null,
                false, "preview content");
    }

    static class FakeTelegramConfiguration {
        @Bean
        @Primary
        RecordingTelegramClient recordingTelegramClient() {
            return new RecordingTelegramClient();
        }
    }

    static final class RecordingTelegramClient extends TelegramClient {
        private int nextId = 1;
        private final List<String> inline = new ArrayList<>();
        private final List<String> text = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private final List<String> edited = new ArrayList<>();
        private final List<String> photos = new ArrayList<>();
        private final List<SendInvoiceRequest> invoices = new ArrayList<>();
        private final List<PreCheckoutAnswerRequest> checkoutAnswers = new ArrayList<>();

        RecordingTelegramClient() {
            super(org.springframework.web.client.RestClient.create(), properties());
        }

        @Override public int sendInline(long chatId, String value, InlineKeyboard keyboard) {
            inline.add(chatId + ":" + value); return nextId++;
        }
        @Override public int sendText(long chatId, String value) { text.add(chatId + ":" + value); return nextId++; }
        @Override public void deleteMessage(long chatId, int messageId) { deleted.add(chatId + ":" + messageId); }
        @Override public void editInline(long chatId, int messageId, String value, InlineKeyboard keyboard) {
            edited.add(chatId + ":" + messageId + ":" + value);
        }
        @Override public int sendPhoto(long chatId, String filename, byte[] content, String caption) {
            photos.add(chatId + ":" + filename + ":" + caption); return nextId++;
        }
        @Override public int sendPhoto(long chatId, String filename, byte[] content, String caption,
                                       InlineKeyboard keyboard) {
            photos.add(chatId + ":" + filename + ":" + caption); return nextId++;
        }
        @Override public List<Integer> copyMessages(long chatId, long fromChatId, List<Integer> ids) {
            return ids.stream().map(ignored -> nextId++).toList();
        }
        @Override public int sendMedia(long chatId, TelegramMedia media) { return nextId++; }
        @Override public List<Integer> sendMediaGroup(long chatId, List<TelegramMedia> media) {
            return media.stream().map(ignored -> nextId++).toList();
        }
        @Override public void publishCommands(List<BotCommand> commands) { throw new AssertionError("disabled notifier ran"); }
        @Override public void sendInvoice(SendInvoiceRequest request) { invoices.add(request); }
        @Override public void answerPreCheckoutQuery(PreCheckoutAnswerRequest request) { checkoutAnswers.add(request); }
        void reset() { nextId = 1; inline.clear(); text.clear(); deleted.clear(); edited.clear(); photos.clear();
            invoices.clear(); checkoutAnswers.clear(); }
        List<String> inline() { return List.copyOf(inline); }
        List<String> text() { return List.copyOf(text); }
        List<String> deleted() { return List.copyOf(deleted); }
        List<String> edited() { return List.copyOf(edited); }
        List<String> photos() { return List.copyOf(photos); }
        List<SendInvoiceRequest> invoices() { return List.copyOf(invoices); }
        List<PreCheckoutAnswerRequest> checkoutAnswers() { return List.copyOf(checkoutAnswers); }
        private static TelegramProperties properties() {
            var properties = new TelegramProperties(); properties.setToken("isolated-test-token"); return properties;
        }
    }
}
