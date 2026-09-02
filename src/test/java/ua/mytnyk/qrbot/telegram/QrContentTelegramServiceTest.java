package ua.mytnyk.qrbot.telegram;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import ua.mytnyk.qrbot.domain.QrContentItem;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.model.common.api.TelegramMedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QrContentTelegramServiceTest {
    private TelegramClient telegram;
    private QrContentTelegramService service;

    @BeforeEach
    void setUp() {
        telegram = mock(TelegramClient.class);
        service = new QrContentTelegramService(telegram);
    }

    @Test
    void emptyContentDoesNothing() {
        assertThat(service.sendContent(10L, List.of())).isEmpty();
        verifyNoInteractions(telegram);
    }

    @Test
    void sendsTextDirectly() {
        when(telegram.sendText(10L, "hello")).thenReturn(11);
        assertThat(service.sendContent(10L, List.of(item(QrContentItem.Kind.TEXT, "hello", null, null))))
                .containsExactly(11);
    }

    @ParameterizedTest
    @EnumSource(value = QrContentItem.Kind.class, names = {"PHOTO", "VIDEO", "DOCUMENT"})
    void sendsSingleMediaWithCorrectMapping(QrContentItem.Kind kind) {
        var expected = new TelegramMedia(TelegramMedia.Type.valueOf(kind.name()), "file", "caption");
        when(telegram.sendMedia(10L, expected)).thenReturn(12);
        service.sendContent(10L, List.of(item(kind, null, "caption", "file")));
        var media = ArgumentCaptor.forClass(TelegramMedia.class);
        verify(telegram).sendMedia(org.mockito.ArgumentMatchers.eq(10L), media.capture());
        assertThat(media.getValue()).isEqualTo(expected);
    }

    @Test
    void groupsPhotosAndVideosButSeparatesDocumentsAndText() {
        var album = List.of(new TelegramMedia(TelegramMedia.Type.PHOTO, "p", null),
                new TelegramMedia(TelegramMedia.Type.VIDEO, "v", null));
        var document = new TelegramMedia(TelegramMedia.Type.DOCUMENT, "d", null);
        when(telegram.sendMediaGroup(10L, album)).thenReturn(List.of(1, 2));
        when(telegram.sendMedia(10L, document)).thenReturn(3);
        when(telegram.sendText(10L, "separator")).thenReturn(4);

        var result = service.sendContent(10L, List.of(
                item(QrContentItem.Kind.PHOTO, null, null, "p"),
                item(QrContentItem.Kind.VIDEO, null, null, "v"),
                item(QrContentItem.Kind.DOCUMENT, null, null, "d"),
                item(QrContentItem.Kind.TEXT, "separator", null, null)));

        assertThat(result).containsExactly(1, 2, 3, 4);
        verify(telegram).sendMediaGroup(10L, album);
        verify(telegram).sendMedia(10L, document);
        verify(telegram).sendText(10L, "separator");
    }

    @Test
    void splitsTelegramAlbumsAtTenItems() {
        var items = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> item(QrContentItem.Kind.PHOTO, null, null, "p" + i)).toList();
        var album = items.subList(0, 10).stream().map(value ->
                new TelegramMedia(TelegramMedia.Type.PHOTO, value.fileId(), null)).toList();
        var finalPhoto = new TelegramMedia(TelegramMedia.Type.PHOTO, "p10", null);
        when(telegram.sendMediaGroup(10L, album)).thenReturn(
                java.util.stream.IntStream.range(1, 11).boxed().toList());
        when(telegram.sendMedia(10L, finalPhoto)).thenReturn(11);

        assertThat(service.sendContent(10L, items)).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 11).boxed().toList());
        verify(telegram).sendMediaGroup(10L, album);
        verify(telegram).sendMedia(10L, finalPhoto);
    }

    private static QrContentItem item(QrContentItem.Kind kind, String text, String caption, String fileId) {
        return new QrContentItem(kind, text, caption, fileId, null, 0);
    }
}
