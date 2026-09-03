package ua.mytnyk.qrbot.telegram.handler;

import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.menu.MainMenuCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.feedback.BeginFeedbackCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.donation.DonationAmountCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.donation.DonationMenuCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.ChoosePasswordCaseCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.CreateQrCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.FinishContentQrCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.SelectQrTypeCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.SkipCreationPasswordCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.ChangeQrPasswordCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.ChooseChangedPasswordCaseCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.DeleteQrCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.FilterQrsCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.ListQrsCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.QrInfoCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.ShowQrCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.ViewQrCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.open.RedeemOneTimeQrCallbackHandler;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.CallbackQuery;
import ua.mytnyk.telegram.common.model.common.webhook.Chat;
import ua.mytnyk.telegram.common.model.common.webhook.Message;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;
import ua.mytnyk.telegram.common.model.common.webhook.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallbackHandlerContractTest {
    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";

    @ParameterizedTest(name = "{0}")
    @MethodSource("handlers")
    void supportsOnlyItsExactCallback(String name,
                                       BiFunction<QrWorkflow, TelegramClient, UpdateHandler> factory,
                                       String validData) {
        var handler = factory.apply(mock(QrWorkflow.class), mock(TelegramClient.class));
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
        assertThat(handler.supports(callback(null))).isFalse();
        assertThat(handler.supports(callback("invalid:value"))).isFalse();
        assertThat(handler.supports(callback(validData))).isTrue();
    }

    @Test
    void filterHandlerAcceptsEveryFilterFamilyAndRejectsMalformedPages() {
        var handler = new FilterQrsCallbackHandler(mock(QrWorkflow.class), mock(TelegramClient.class));
        assertThat(handler.supports(callback("list:type:CONTENT"))).isTrue();
        assertThat(handler.supports(callback("list:status:ACTIVE"))).isTrue();
        assertThat(handler.supports(callback("list:sort:OLDEST"))).isTrue();
        assertThat(handler.supports(callback("list:page:0"))).isTrue();
        assertThat(handler.supports(callback("list:page:-1"))).isFalse();
        assertThat(handler.supports(callback("list:page:x"))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("textBranchHandlers")
    void handlesCallbackPanelsThatArriveAsMediaMessages(String data,
                                                          BiFunction<QrWorkflow, TelegramClient, UpdateHandler> factory) {
        var workflow = mock(QrWorkflow.class);
        var telegram = mock(TelegramClient.class);
        when(workflow.isCurrentNavigation(77L, 9)).thenReturn(true);
        factory.apply(workflow, telegram).handle(callbackWithoutText(data));
    }

    static Stream<Arguments> textBranchHandlers() {
        return Stream.of(
                Arguments.of("menu:create", factory(CreateQrCallbackHandler::new)),
                Arguments.of("menu:feedback", factory(BeginFeedbackCallbackHandler::new)),
                Arguments.of("menu:donate", factory(DonationMenuCallbackHandler::new)),
                Arguments.of("donate:amount:10", factory(DonationAmountCallbackHandler::new)),
                Arguments.of("create:type:CONTENT", factory(SelectQrTypeCallbackHandler::new)),
                Arguments.of("menu:list", factory(ListQrsCallbackHandler::new)),
                Arguments.of("list:page:1", factory(FilterQrsCallbackHandler::new)),
                Arguments.of("qr:password:123e4567-e89b-12d3-a456-426614174000", factory(ChangeQrPasswordCallbackHandler::new)),
                Arguments.of("create:content:done", factory(FinishContentQrCallbackHandler::new))
        );
    }

    static Stream<Arguments> handlers() {
        return Stream.of(
                Arguments.of("main menu", factory(MainMenuCallbackHandler::new), "menu:home"),
                Arguments.of("create", factory(CreateQrCallbackHandler::new), "menu:create"),
                Arguments.of("feedback", factory(BeginFeedbackCallbackHandler::new), "menu:feedback"),
                Arguments.of("donation menu", factory(DonationMenuCallbackHandler::new), "menu:donate"),
                Arguments.of("donation amount", factory(DonationAmountCallbackHandler::new), "donate:amount:100"),
                Arguments.of("select type", factory(SelectQrTypeCallbackHandler::new), "create:type:CONTENT"),
                Arguments.of("finish content", factory(FinishContentQrCallbackHandler::new), "create:content:done"),
                Arguments.of("skip password", factory(SkipCreationPasswordCallbackHandler::new), "create:protection:skip"),
                Arguments.of("creation case", factory(ChoosePasswordCaseCallbackHandler::new), "create:password-case:ignore"),
                Arguments.of("list", factory(ListQrsCallbackHandler::new), "menu:list"),
                Arguments.of("filter", factory(FilterQrsCallbackHandler::new), "list:page:2"),
                Arguments.of("view", factory(ViewQrCallbackHandler::new), "qr:view:" + ID),
                Arguments.of("show", factory(ShowQrCallbackHandler::new), "qr:image:" + ID),
                Arguments.of("delete", factory(DeleteQrCallbackHandler::new), "qr:delete:" + ID),
                Arguments.of("change password", factory(ChangeQrPasswordCallbackHandler::new), "qr:password:" + ID),
                Arguments.of("changed case", factory(ChooseChangedPasswordCaseCallbackHandler::new), "list:password-case:exact"),
                Arguments.of("redeem", factory(RedeemOneTimeQrCallbackHandler::new), "qr:redeem:" + ID),
                Arguments.of("info", factory((workflow, telegram) -> new QrInfoCallbackHandler(telegram)), "qr:noop")
        );
    }

    private static BiFunction<QrWorkflow, TelegramClient, UpdateHandler> factory(
            BiFunction<QrWorkflow, TelegramClient, UpdateHandler> value) {
        return value;
    }

    private static UpdateWebhook callback(String data) {
        var user = User.builder().id(77).username("alice").build();
        var message = Message.builder().messageId(9).from(user)
                .chat(Chat.builder().id(88).type("private").build()).text("panel").build();
        return UpdateWebhook.builder().callbackQuery(CallbackQuery.builder().id("callback-1")
                .from(user).message(message).data(data).build()).build();
    }

    private static UpdateWebhook callbackWithoutText(String data) {
        var user = User.builder().id(77).username("alice").build();
        var message = Message.builder().messageId(9).from(user)
                .chat(Chat.builder().id(88).type("private").build()).build();
        return UpdateWebhook.builder().callbackQuery(CallbackQuery.builder().id("callback-1")
                .from(user).message(message).data(data).build()).build();
    }
}
