package ua.mytnyk.qrbot.service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import ua.mytnyk.qrbot.config.QrBotProperties;
import ua.mytnyk.qrbot.domain.AnalyticsAction;
import ua.mytnyk.qrbot.domain.BotUser;
import ua.mytnyk.qrbot.domain.QrAccess;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrContentItem;
import ua.mytnyk.qrbot.domain.QrListPreferences;
import ua.mytnyk.qrbot.domain.QrListSort;
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.domain.PendingPasswordOptions;
import ua.mytnyk.qrbot.repository.BotUserRepository;
import ua.mytnyk.qrbot.repository.QrAccessRepository;
import ua.mytnyk.qrbot.repository.QrCodeRepository;
import ua.mytnyk.qrbot.telegram.TelegramGateway;
import ua.mytnyk.telegram.common.model.common.api.markup.keyboard.inline.InlineKeyboard;
import ua.mytnyk.telegram.common.model.common.api.markup.keyboard.inline.InlineKeyboardButton;
import ua.mytnyk.telegram.common.model.common.webhook.Message;
import ua.mytnyk.telegram.common.model.common.webhook.User;

@Service
public class QrWorkflow {
    public static final String MENU_HOME = "menu:home";
    public static final String MENU_CREATE = "menu:create";
    public static final String MENU_LIST = "menu:list";
    public static final String TYPE_PREFIX = "create:type:";
    public static final String VIEW_PREFIX = "qr:view:";
    public static final String SHOW_QR_PREFIX = "qr:image:";
    public static final String REDEEM_PREFIX = "qr:redeem:";
    public static final String NOOP = "qr:noop";
    public static final String CONTENT_DONE = "create:content:done";
    public static final String DELETE_PREFIX = "qr:delete:";
    public static final String CHANGE_PASSWORD_PREFIX = "qr:password:";
    public static final String FILTER_TYPE_PREFIX = "list:type:";
    public static final String FILTER_STATUS_PREFIX = "list:status:";
    public static final String SORT_PREFIX = "list:sort:";
    public static final String PAGE_PREFIX = "list:page:";
    public static final String CREATION_CASE_PREFIX = "create:password-case:";
    public static final String CHANGE_CASE_PREFIX = "list:password-case:";
    public static final String PROTECTION_PREFIX = "create:protection:";
    private static final Logger log = LoggerFactory.getLogger(QrWorkflow.class);
    private static final int MAX_CONTENT_ITEMS = 10;
    private static final int QR_LIST_PAGE_SIZE = 5;
    private static final int MAX_MEDIA_ITEMS = 10;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_MEDIA_CAPTION_LENGTH = 1024;
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("uk-UA"))
            .withZone(ZoneId.of("Europe/Kyiv"));
    private final BotUserRepository users;
    private final QrCodeRepository qrCodes;
    private final QrAccessRepository accesses;
    private final PasswordHasher passwords;
    private final QrImageGenerator imageGenerator;
    private final TelegramGateway telegram;
    private final QrBotProperties properties;
    private final List<ContentDeliveryStrategy> deliveryStrategies;
    private final MongoTemplate mongo;
    private final QrAnalytics analytics;
    private final Clock clock = Clock.systemUTC();

    public QrWorkflow(BotUserRepository users, QrCodeRepository qrCodes, QrAccessRepository accesses,
                      PasswordHasher passwords, QrImageGenerator imageGenerator, TelegramGateway telegram,
                      QrBotProperties properties, List<ContentDeliveryStrategy> deliveryStrategies,
                      MongoTemplate mongo, QrAnalytics analytics) {
        this.users = users;
        this.qrCodes = qrCodes;
        this.accesses = accesses;
        this.passwords = passwords;
        this.imageGenerator = imageGenerator;
        this.telegram = telegram;
        this.properties = properties;
        this.deliveryStrategies = deliveryStrategies;
        this.mongo = mongo;
        this.analytics = analytics;
    }

    public void showMainMenu(Message message) {
        showMainMenu(message.getFrom(), message.getChat().getId());
    }

    public void showMainMenu(User actor, long chatId) {
        deleteNavigation(actor.getId(), chatId);
        resetUser(actor);
        var view = mainMenuView();
        var navigationMessageId = telegram.sendInline(chatId, view.text(), view.keyboard());
        setNavigationMessage(actor, navigationMessageId);
        setDisplayedMessages(actor, List.of(navigationMessageId));
        analytics.track(AnalyticsAction.MAIN_MENU_VIEWED, actor.getId(), chatId);
        log.info("Main menu shown and user state reset userId={} chatId={}", actor.getId(), chatId);
    }

    public BotView mainMenu(User actor, long chatId) {
        saveUser(actor, BotUser.State.IDLE, null, null, null);
        analytics.track(AnalyticsAction.MAIN_MENU_VIEWED, actor.getId(), chatId);
        return mainMenuView();
    }

    public void replaceNavigation(User actor, long chatId, int messageId, boolean textMessage, BotView view) {
        if (textMessage) {
            deleteDisplayedMessagesExcept(actor.getId(), chatId, messageId);
            telegram.editInline(chatId, messageId, view.text(), view.keyboard());
            setNavigationMessage(actor, messageId);
            setDisplayedMessages(actor, List.of(messageId));
        } else {
            deleteNavigation(actor.getId(), chatId);
            var navigationMessageId = telegram.sendInline(chatId, view.text(), view.keyboard());
            setNavigationMessage(actor, navigationMessageId);
            setDisplayedMessages(actor, List.of(navigationMessageId));
        }
    }

    public BotView beginCreation(User actor, long chatId) {
        saveUser(actor, BotUser.State.IDLE, null, null, null);
        analytics.track(AnalyticsAction.CREATION_STARTED, actor.getId(), chatId);
        log.info("QR creation started userId={}", actor.getId());
        return new BotView("🧩 Оберіть тип QR-коду.", keyboard(List.of(
                row(button("📄 Контент", TYPE_PREFIX + QrType.CONTENT)),
                row(button("1️⃣ Одноразовий QR-код", TYPE_PREFIX + QrType.SINGLE_USE)),
                row(button("🎟️ Купон", TYPE_PREFIX + QrType.COUPON)),
                row(button("🏠 Головне меню", MENU_HOME)))));
    }

    public BotView selectType(User actor, long chatId, QrType type) {
        saveUser(actor, BotUser.State.WAITING_FOR_CONTENT, type, null, null);
        users.save(users.findById(actor.getId()).orElseThrow().withPendingContentItems(List.of()));
        analytics.track(AnalyticsAction.QR_TYPE_SELECTED, actor.getId(), chatId, null,
                Map.of("qrType", type.name()));
        log.info("QR type selected userId={} type={}", actor.getId(), type);
        return new BotView("⏳ Очікую на контент…\n\n⚠️ Надішліть увесь контент ОДНИМ повідомленням,"
                + " додавши не більше 10 файлів. Додаткові повідомлення або окремі завантаження можуть не обробитися.",
                keyboard(List.of(row(button("❌ Скасувати", MENU_HOME)))));
    }

    public boolean isWaitingForContent(long userId) {
        return hasState(userId, BotUser.State.WAITING_FOR_CONTENT);
    }

    public boolean isCurrentNavigation(long userId, int messageId) {
        return users.findById(userId).map(BotUser::navigationMessageId)
                .map(currentMessageId -> currentMessageId == messageId).orElse(false);
    }

    public void acceptContent(Message message) {
        var user = requiredUser(message.getFrom().getId(), BotUser.State.WAITING_FOR_CONTENT);
        var chatId = message.getChat().getId();
        var contentItems = new ArrayList<QrContentItem>();
        if (user.pendingContentItems() != null) {
            contentItems.addAll(user.pendingContentItems());
        }
        var firstContentItem = contentItems.isEmpty();
        if (contentItems.stream().anyMatch(item -> item.order() == message.getMessageId())) {
            log.info("Duplicate content update ignored userId={} messageId={} mediaGroupId={}",
                    message.getFrom().getId(), message.getMessageId(), message.getMediaGroupId());
            return;
        }
        var mediaGroupId = contentItems.isEmpty() ? message.getMediaGroupId() : user.pendingMediaGroupId();
        if (!contentItems.isEmpty()
                && (mediaGroupId == null || !mediaGroupId.equals(message.getMediaGroupId()))) {
            log.info("Additional content submission ignored userId={} messageId={} mediaGroupId={}"
                            + " acceptedMediaGroupId={}",
                    message.getFrom().getId(), message.getMessageId(), message.getMediaGroupId(), mediaGroupId);
            return;
        }
        var contentItem = contentItem(message, message.getMessageId());
        contentItems.add(contentItem);
        contentItems.sort(java.util.Comparator.comparingInt(QrContentItem::order));
        var limitViolation = contentLimitViolation(contentItems);
        if (limitViolation != null) {
            telegram.sendText(chatId, "⚠️ " + limitViolation + " Наявну чернетку не змінено.");
            refreshUploadControl(message.getFrom(), chatId, contentItems.size() - 1,
                    previewText(user.pendingContentItems()), false);
            log.info("Draft content limit reached userId={} messageId={} mediaGroupId={} reason={}",
                    message.getFrom().getId(), message.getMessageId(), message.getMediaGroupId(), limitViolation);
            return;
        }
        var newStoredMessageIds = telegram.sendContent(properties.getContentChannelId(), List.of(contentItem));
        var storedMessageIds = new ArrayList<Integer>();
        if (user.pendingMessageIds() != null) {
            storedMessageIds.addAll(user.pendingMessageIds());
        }
        storedMessageIds.addAll(newStoredMessageIds);
        var storedMessageId = storedMessageIds.get(0);
        analytics.track(AnalyticsAction.CONTENT_SELECTED, message.getFrom().getId(), chatId, null,
                Map.of("qrType", user.selectedType().name()));
        saveUser(message.getFrom(), BotUser.State.WAITING_FOR_CONTENT, user.selectedType(), storedMessageId,
                null, storedMessageIds, mediaGroupId);
        users.save(users.findById(message.getFrom().getId()).orElseThrow().withPendingContentItems(contentItems));
        log.info("Draft content updated userId={} messageId={} mediaGroupId={} itemCount={}",
                message.getFrom().getId(), message.getMessageId(), message.getMediaGroupId(), contentItems.size());
        if (contentItem.kind() == QrContentItem.Kind.TEXT) {
            finishContentSelection(message.getFrom(), chatId);
            return;
        }
        refreshUploadControl(message.getFrom(), chatId, contentItems.size(), previewText(contentItems),
                firstContentItem);
    }

    public BotView finishContentSelection(User actor, long chatId) {
        var user = requiredUser(actor.getId(), BotUser.State.WAITING_FOR_CONTENT);
        var type = user.selectedType();
        var storedMessageIds = user.pendingMessageIds();
        if (storedMessageIds == null || storedMessageIds.isEmpty()) {
            return contentReadyView(0, null);
        }
        var storedMessageId = storedMessageIds.get(0);
        if (type == QrType.COUPON) {
            finishCreation(actor, chatId, type, storedMessageIds, null, null);
            return null;
        }
        saveUser(actor, BotUser.State.WAITING_FOR_CREATION_PASSWORD,
                type, storedMessageId, null, storedMessageIds, null);
        var navigationMessageId = user.navigationMessageId();
        var hasAttachments = user.pendingContentItems() != null && user.pendingContentItems().stream()
                .anyMatch(item -> item.kind() != QrContentItem.Kind.TEXT);
        var text = "🔐 Надішліть пароль/код для цього QR-коду. Ваше повідомлення буде одразу видалено.";
        var keyboard = keyboard(List.of(row(button("⏭️ Пропустити пароль", PROTECTION_PREFIX + "skip")),
                row(button("❌ Скасувати", MENU_HOME))));
        if (!hasAttachments || navigationMessageId == null) {
            navigationMessageId = telegram.sendInline(chatId, text, keyboard);
        } else {
            telegram.editInline(chatId, navigationMessageId, text, keyboard);
        }
        setNavigationMessage(actor, navigationMessageId);
        return null;
    }

    public void skipCreationPassword(User actor, long chatId) {
        var user = requiredUser(actor.getId(), BotUser.State.WAITING_FOR_CREATION_PASSWORD);
        finishCreation(actor, chatId, user.selectedType(), user.pendingMessageIds(), null, null);
    }

    public boolean isWaitingForCreationPassword(long userId) {
        return hasState(userId, BotUser.State.WAITING_FOR_CREATION_PASSWORD);
    }

    public void acceptCreationPassword(Message message) {
        var user = requiredUser(message.getFrom().getId(), BotUser.State.WAITING_FOR_CREATION_PASSWORD);
        telegram.deleteMessage(message.getChat().getId(), message.getMessageId());
        var password = requirePassword(message);
        setPendingPasswordOptions(message.getFrom(), passwordOptions(password));
        saveUser(message.getFrom(), BotUser.State.WAITING_FOR_CREATION_CASE_CHOICE, user.selectedType(),
                user.channelMessageId(), null, user.pendingMessageIds(), null);
        var navigationMessageId = user.navigationMessageId();
        var text = "🔤 Ігнорувати регістр літер у відповідях?\n\nПриклад: Подарунок, ПОДАРУНОК і подарунок вважатимуться однаковими.";
        var keyboard = caseChoiceKeyboard(CREATION_CASE_PREFIX);
        if (navigationMessageId == null) {
            navigationMessageId = telegram.sendInline(message.getChat().getId(), text, keyboard);
        } else {
            telegram.editInline(message.getChat().getId(), navigationMessageId, text, keyboard);
        }
        setNavigationMessage(message.getFrom(), navigationMessageId);
    }

    public void chooseCreationPasswordCase(User actor, long chatId, boolean ignoreCase) {
        var user = requiredUser(actor.getId(), BotUser.State.WAITING_FOR_CREATION_CASE_CHOICE);
        var password = selectedPassword(user.pendingPasswordOptions(), ignoreCase);
        analytics.track(AnalyticsAction.CREATION_PASSWORD_SET, actor.getId(), chatId);
        finishCreation(actor, chatId, user.selectedType(), user.pendingMessageIds(), password, ignoreCase);
    }

    public BotView listCreatedQrs(User actor, long chatId) {
        touchUser(actor);
        var user = users.findById(actor.getId()).orElseThrow();
        var preferences = preferences(user);
        var totalCount = filteredQrCount(actor.getId(), preferences);
        var totalPages = Math.max(1, (int) Math.ceil((double) totalCount / QR_LIST_PAGE_SIZE));
        var page = Math.min(preferences.page(), totalPages - 1);
        if (page != preferences.page()) {
            preferences = new QrListPreferences(preferences.types(), preferences.statuses(), preferences.sort(), page);
        }
        var all = filteredQrs(actor.getId(), preferences);
        var text = new StringBuilder("📚 Ваші QR-коди\n\n🔎 Знайдено: ").append(totalCount)
                .append("\n\n📄 Контент · 1️⃣ Одноразовий · 🎟️ Купон")
                .append("\n✅ Активний · 🏁 Погашений · 🔒 Захищений · 📎 Вкладення");
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        rows.add(row(
                groupedTypeCheckboxButton(QrType.CONTENT, preferences.types()),
                groupedTypeCheckboxButton(QrType.SINGLE_USE, preferences.types()),
                groupedTypeCheckboxButton(QrType.COUPON, preferences.types())));
        rows.add(row(statusCheckboxButton(QrStatus.ACTIVE, preferences.statuses()),
                statusCheckboxButton(QrStatus.REDEEMED, preferences.statuses())));
        rows.add(row(button(preferences.sort() == QrListSort.NEWEST
                        ? "📅 Спочатку новіші ↓" : "📅 Спочатку старіші ↑",
                SORT_PREFIX + (preferences.sort() == QrListSort.NEWEST
                        ? QrListSort.OLDEST : QrListSort.NEWEST))));
        var index = page * QR_LIST_PAGE_SIZE + 1;
        for (var qrCode : all) {
            var callback = VIEW_PREFIX + qrCode.id();
            rows.add(row(button(index + ". " + itemMetadata(qrCode), callback),
                    button(itemPreview(qrCode), callback)));
            index++;
        }
        if (all.isEmpty()) {
            text.append("\n\n📭 Жоден QR-код не відповідає цим фільтрам.");
        }
        rows.add(row(button(page > 0 ? "⬅️" : "·", page > 0 ? PAGE_PREFIX + (page - 1) : NOOP),
                button((page + 1) + "/" + totalPages, NOOP),
                button(page + 1 < totalPages ? "➡️" : "·",
                        page + 1 < totalPages ? PAGE_PREFIX + (page + 1) : NOOP)));
        rows.add(row(button("🏠 Головне меню", MENU_HOME)));
        analytics.track(AnalyticsAction.QR_LIST_VIEWED, actor.getId(), chatId, null,
                Map.of("filteredCount", Long.toString(totalCount)));
        return new BotView(text.toString(), keyboard(rows));
    }

    public BotView updateListPreferences(User actor, long chatId, String callbackData) {
        touchUser(actor);
        var user = users.findById(actor.getId()).orElseThrow();
        var current = preferences(user);
        var types = EnumSet.noneOf(QrType.class);
        types.addAll(current.types());
        var statuses = EnumSet.noneOf(QrStatus.class);
        statuses.addAll(current.statuses());
        var sort = current.sort();
        var page = current.page();
        if (callbackData.startsWith(FILTER_TYPE_PREFIX)) {
            var type = QrType.valueOf(callbackData.substring(FILTER_TYPE_PREFIX.length()));
            var group = typeGroup(type);
            if (types.containsAll(group)) {
                types.removeAll(group);
            } else {
                types.addAll(group);
            }
            page = 0;
        } else if (callbackData.startsWith(SORT_PREFIX)) {
            sort = QrListSort.valueOf(callbackData.substring(SORT_PREFIX.length()));
            page = 0;
        } else if (callbackData.startsWith(FILTER_STATUS_PREFIX)) {
            var status = QrStatus.valueOf(callbackData.substring(FILTER_STATUS_PREFIX.length()));
            if (!statuses.remove(status)) {
                statuses.add(status);
            }
            page = 0;
        } else if (callbackData.startsWith(PAGE_PREFIX)) {
            page = Math.max(0, Integer.parseInt(callbackData.substring(PAGE_PREFIX.length())));
        }
        users.save(new BotUser(user.id(), actor.getUsername(), user.state(), user.selectedType(),
                user.channelMessageId(), user.pendingQrId(), new QrListPreferences(types, statuses, sort, page),
                user.navigationMessageId(), clock.instant(), user.pendingMessageIds(), user.pendingMediaGroupId(),
                user.displayedMessageIds(), user.pendingContentItems(), user.pendingPasswordOptions()));
        return listCreatedQrs(actor, chatId);
    }

    public void showQrDetails(String qrId, User actor, long chatId) {
        touchUser(actor);
        var qrCode = qrCodes.findByIdAndOwnerId(qrId, actor.getId()).orElse(null);
        deleteNavigation(actor.getId(), chatId);
        if (qrCode == null || effectiveStatus(qrCode) == QrStatus.DELETED) {
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR-код не знайдено.\n\n");
            return;
        }
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        rows.add(row(button("📷 Показати QR-код і посилання", SHOW_QR_PREFIX + qrCode.id())));
        if (effectiveStatus(qrCode) == QrStatus.ACTIVE) {
            if (isPasswordProtected(qrCode)) {
                rows.add(row(button("🔑 Змінити пароль", CHANGE_PASSWORD_PREFIX + qrCode.id())));
            }
            rows.add(row(button("🗑️ Видалити QR-код", DELETE_PREFIX + qrCode.id())));
        }
        rows.add(row(button("⬅️ Назад до моїх QR-кодів", MENU_LIST), button("🏠 Головне меню", MENU_HOME)));
        var displayedMessageIds = previewContent(qrCode, chatId);
        var details = "🔎 QR-код\n\n"
                + "🆔 " + qrCode.id()
                + "\n" + typeEmoji(qrCode.type()) + " Тип: " + typeLabel(qrCode.type())
                + "\n✅ Статус: " + statusLabel(effectiveStatus(qrCode))
                + "\n👁 Успішних відкриттів: " + qrCode.openCount()
                + "\n📅 Створено: " + DISPLAY_DATE.format(qrCode.createdAt());
        if (qrCode.previewText() != null && !qrCode.previewText().isBlank()) {
            details += "\n📝 Перегляд: " + qrCode.previewText();
        }
        if (effectiveStatus(qrCode) == QrStatus.REDEEMED) {
            details += "\n\n👤 Погашено користувачем: " + redeemedBy(qrCode)
                    + "\n🕐 Погашено: " + (qrCode.redeemedAt() == null
                    ? "Невідомо" : DISPLAY_DATE.format(qrCode.redeemedAt()));
        }
        var navigationMessageId = telegram.sendInline(chatId, details, keyboard(rows));
        displayedMessageIds.add(navigationMessageId);
        setNavigationMessage(actor, navigationMessageId);
        setDisplayedMessages(actor, displayedMessageIds);
        analytics.track(AnalyticsAction.QR_PREVIEWED, actor.getId(), chatId, qrCode);
    }

    public void showQrImage(String qrId, User actor, long chatId) {
        touchUser(actor);
        var qrCode = qrCodes.findByIdAndOwnerId(qrId, actor.getId()).orElse(null);
        deleteNavigation(actor.getId(), chatId);
        if (qrCode == null || effectiveStatus(qrCode) == QrStatus.DELETED) {
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR-код не знайдено.\n\n");
            return;
        }
        var link = deepLink(qrCode.id());
        var qrMessageId = telegram.sendPhoto(chatId, "qr-" + qrCode.id() + ".png", imageGenerator.generatePng(link),
                "🔗 " + link, keyboard(List.of(row(button("⬅️ Назад до QR-коду", VIEW_PREFIX + qrCode.id())),
                        row(button("📚 Мої QR-коди", MENU_LIST), button("🏠 Головне меню", MENU_HOME)))));
        setNavigationMessage(actor, qrMessageId);
        setDisplayedMessages(actor, List.of(qrMessageId));
    }

    public DeleteResult softDelete(String qrId, User actor, long chatId) {
        touchUser(actor);
        var qrCode = qrCodes.findByIdAndOwnerId(qrId, actor.getId()).orElse(null);
        if (qrCode == null || effectiveStatus(qrCode) != QrStatus.ACTIVE) {
            return new DeleteResult(false, listCreatedQrs(actor, chatId));
        }
        mongo.updateFirst(Query.query(Criteria.where("id").is(qrId).and("ownerId").is(actor.getId())
                        .and("status").is(QrStatus.ACTIVE)),
                new Update().set("status", QrStatus.DELETED), QrCode.class);
        analytics.track(AnalyticsAction.QR_DELETED, actor.getId(), chatId, qrCode);
        log.info("QR soft-deleted qrId={} ownerId={}", qrId, actor.getId());
        return new DeleteResult(true, listCreatedQrs(actor, chatId));
    }

    public BotView beginPasswordChange(String qrId, User actor, long chatId) {
        var qrCode = qrCodes.findByIdAndOwnerId(qrId, actor.getId()).orElse(null);
        if (qrCode == null || effectiveStatus(qrCode) != QrStatus.ACTIVE
                || !isPasswordProtected(qrCode)) {
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            return new BotView("🔍 QR-код не знайдено.\n\n" + mainMenuView().text(), mainMenuView().keyboard());
        }
        saveUser(actor, BotUser.State.WAITING_FOR_PASSWORD_CHANGE, null, null, qrId);
        return new BotView("🔑 Надішліть новий пароль/код. Ваше повідомлення буде одразу видалено.",
                keyboard(List.of(row(button("❌ Скасувати", MENU_HOME)))));
    }

    public boolean isWaitingForPasswordChange(long userId) {
        return hasState(userId, BotUser.State.WAITING_FOR_PASSWORD_CHANGE);
    }

    public void acceptPasswordChange(Message message) {
        var user = requiredUser(message.getFrom().getId(), BotUser.State.WAITING_FOR_PASSWORD_CHANGE);
        telegram.deleteMessage(message.getChat().getId(), message.getMessageId());
        var qrCode = qrCodes.findByIdAndOwnerId(user.pendingQrId(), message.getFrom().getId()).orElse(null);
        if (qrCode == null || effectiveStatus(qrCode) != QrStatus.ACTIVE
                || !isPasswordProtected(qrCode)) {
            deleteNavigation(message.getFrom().getId(), message.getChat().getId());
            saveUser(message.getFrom(), BotUser.State.IDLE, null, null, null);
            sendMainNavigation(message.getFrom(), message.getChat().getId(), "🔍 QR-код не знайдено.\n\n");
            return;
        }
        var password = requirePassword(message);
        setPendingPasswordOptions(message.getFrom(), passwordOptions(password));
        saveUser(message.getFrom(), BotUser.State.WAITING_FOR_CHANGE_CASE_CHOICE, null, null, qrCode.id());
        deleteNavigation(message.getFrom().getId(), message.getChat().getId());
        var navigationMessageId = telegram.sendInline(message.getChat().getId(),
                "🔤 Ігнорувати регістр літер у відповідях?\n\nПриклад: Подарунок, ПОДАРУНОК і подарунок вважатимуться однаковими.",
                caseChoiceKeyboard(CHANGE_CASE_PREFIX));
        setNavigationMessage(message.getFrom(), navigationMessageId);
    }

    public void chooseChangedPasswordCase(User actor, long chatId, boolean ignoreCase) {
        var user = requiredUser(actor.getId(), BotUser.State.WAITING_FOR_CHANGE_CASE_CHOICE);
        var qrCode = qrCodes.findByIdAndOwnerId(user.pendingQrId(), actor.getId()).orElse(null);
        if (qrCode == null || effectiveStatus(qrCode) != QrStatus.ACTIVE) {
            deleteNavigation(actor.getId(), chatId);
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR-код не знайдено.\n\n");
            return;
        }
        var password = selectedPassword(user.pendingPasswordOptions(), ignoreCase);
        mongo.updateFirst(Query.query(Criteria.where("id").is(qrCode.id()).and("ownerId").is(actor.getId())),
                new Update().set("passwordSalt", password.salt()).set("passwordHash", password.hash())
                        .set("ignorePasswordCase", ignoreCase), QrCode.class);
        saveUser(actor, BotUser.State.IDLE, null, null, null);
        telegram.sendText(chatId, "✅ Пароль оновлено");
        showQrDetails(qrCode.id(), actor, chatId);
        log.info("QR password updated qrId={} ownerId={} ignoreCase={}", qrCode.id(), actor.getId(), ignoreCase);
    }

    public OpenResult open(String payload, Message message) {
        saveUser(message.getFrom(), BotUser.State.IDLE, null, null, null);
        var qrCode = findByPayload(payload);
        analytics.track(AnalyticsAction.QR_SCANNED, message.getFrom().getId(), message.getChat().getId(), qrCode);
        if (qrCode != null && effectiveStatus(qrCode) == QrStatus.REDEEMED) {
            sendMainNavigation(message.getFrom(), message.getChat().getId(),
                    "ℹ️ Цей QR-код уже погашено.\n\n");
            return OpenResult.NOT_FOUND;
        }
        if (qrCode == null || effectiveStatus(qrCode) != QrStatus.ACTIVE) {
            trackNotFound(message, qrCode);
            sendMainNavigation(message.getFrom(), message.getChat().getId(), "🔍 QR-код не знайдено.\n\n");
            return OpenResult.NOT_FOUND;
        }
        if (qrCode.type() == QrType.SINGLE_USE && !isPasswordProtected(qrCode)) {
            var redeemed = redeemOneTimeGift(qrCode, message.getFrom(), message.getChat().getId());
            saveUser(message.getFrom(), BotUser.State.IDLE, null, null, null);
            if (!redeemed) {
                sendMainNavigation(message.getFrom(), message.getChat().getId(),
                        "ℹ️ Цей QR-код уже погашено.\n\n");
                return OpenResult.NOT_FOUND;
            }
            sendMainNavigation(message.getFrom(), message.getChat().getId(), "");
            return OpenResult.DELIVERED;
        }
        if (qrCode.type() == QrType.COUPON) {
            if (qrCode.ownerId() != message.getFrom().getId()) {
                trackNotFound(message, qrCode);
                sendMainNavigation(message.getFrom(), message.getChat().getId(), "🔍 QR-код не знайдено.\n\n");
                return OpenResult.NOT_FOUND;
            }
            saveUser(message.getFrom(), BotUser.State.WAITING_FOR_REDEEM_CONFIRMATION, null, null, qrCode.id());
            var displayedMessageIds = previewContent(qrCode, message.getChat().getId());
            var navigationMessageId = telegram.sendInline(message.getChat().getId(),
                    "🎟️ Погасити цей купон зараз?",
                    keyboard(List.of(row(button("✅ Погасити", REDEEM_PREFIX + qrCode.id())),
                            row(button("❌ Скасувати", MENU_HOME)))));
            setNavigationMessage(message.getFrom(), navigationMessageId);
            displayedMessageIds.add(navigationMessageId);
            setDisplayedMessages(message.getFrom(), displayedMessageIds);
            return OpenResult.CONFIRMATION_REQUIRED;
        }
        if (isPasswordProtected(qrCode)) {
            saveUser(message.getFrom(), BotUser.State.WAITING_FOR_OPEN_PASSWORD, null, null, qrCode.id());
            var navigationMessageId = telegram.sendInline(message.getChat().getId(),
                    "🔐 Введіть пароль/код. Ваше повідомлення буде одразу видалено.",
                    keyboard(List.of(row(button("❌ Скасувати", MENU_HOME)))));
            setNavigationMessage(message.getFrom(), navigationMessageId);
            analytics.track(AnalyticsAction.PASSWORD_REQUESTED, message.getFrom().getId(),
                    message.getChat().getId(), qrCode);
            return OpenResult.PASSWORD_REQUIRED;
        }
        completeDelivery(qrCode, message.getFrom(), message.getChat().getId());
        return OpenResult.DELIVERED;
    }

    public boolean isWaitingForOpeningPassword(long userId) {
        return hasState(userId, BotUser.State.WAITING_FOR_OPEN_PASSWORD);
    }

    public boolean acceptOpeningPassword(Message message) {
        var user = requiredUser(message.getFrom().getId(), BotUser.State.WAITING_FOR_OPEN_PASSWORD);
        telegram.deleteMessage(message.getChat().getId(), message.getMessageId());
        var qrCode = qrCodes.findById(user.pendingQrId())
                .orElseThrow(() -> new IllegalStateException("QR code not found"));
        if (effectiveStatus(qrCode) != QrStatus.ACTIVE) {
            deleteNavigation(message.getFrom().getId(), message.getChat().getId());
            saveUser(message.getFrom(), BotUser.State.IDLE, null, null, null);
            var prefix = effectiveStatus(qrCode) == QrStatus.REDEEMED
                    ? "ℹ️ Цей QR-код уже погашено.\n\n" : "🔍 QR-код не знайдено.\n\n";
            sendMainNavigation(message.getFrom(), message.getChat().getId(), prefix);
            return false;
        }
        var password = normalizePassword(requirePassword(message), Boolean.TRUE.equals(qrCode.ignorePasswordCase()));
        if (!passwords.matches(password, qrCode.passwordSalt(), qrCode.passwordHash())) {
            deleteNavigation(message.getFrom().getId(), message.getChat().getId());
            var navigationMessageId = telegram.sendInline(message.getChat().getId(),
                    "❌ Неправильний пароль/код. Спробуйте ще раз.",
                    keyboard(List.of(row(button("❌ Скасувати", MENU_HOME)))));
            setNavigationMessage(message.getFrom(), navigationMessageId);
            analytics.track(AnalyticsAction.PASSWORD_REJECTED, message.getFrom().getId(),
                    message.getChat().getId(), qrCode);
            return false;
        }
        if (qrCode.type() == QrType.SINGLE_USE) {
            var redeemed = redeemOneTimeGift(qrCode, message.getFrom(), message.getChat().getId());
            saveUser(message.getFrom(), BotUser.State.IDLE, null, null, null);
            if (!redeemed) {
                sendMainNavigation(message.getFrom(), message.getChat().getId(), "🔍 QR-код не знайдено.\n\n");
                return false;
            }
            sendMainNavigation(message.getFrom(), message.getChat().getId(), "");
            return true;
        }
        completeDelivery(qrCode, message.getFrom(), message.getChat().getId());
        return true;
    }

    public boolean confirmRedemption(String qrId, User actor, long chatId) {
        var user = requiredUser(actor.getId(), BotUser.State.WAITING_FOR_REDEEM_CONFIRMATION);
        if (!qrId.equals(user.pendingQrId())) {
            deleteNavigation(actor.getId(), chatId);
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR-код не знайдено.\n\n");
            return false;
        }
        var qrCode = qrCodes.findById(qrId).orElse(null);
        if (qrCode == null || qrCode.ownerId() != actor.getId() || effectiveStatus(qrCode) != QrStatus.ACTIVE) {
            deleteNavigation(actor.getId(), chatId);
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR-код не знайдено.\n\n");
            return false;
        }
        if (user.navigationMessageId() != null) {
            telegram.deleteMessage(chatId, user.navigationMessageId());
        }
        var redeemed = redeemOneTime(qrCode, actor, chatId);
        if (redeemed) {
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            setDisplayedMessages(actor, List.of());
            telegram.sendText(chatId, "✅ Успішно погашено");
            sendMainNavigation(actor, chatId, "");
        }
        return redeemed;
    }

    private void finishCreation(User actor, long chatId, QrType type, List<Integer> storedMessageIds,
                                PasswordHasher.PasswordValue passwordValue, Boolean ignorePasswordCase) {
        var id = UUID.randomUUID().toString();
        var salt = passwordValue == null ? null : passwordValue.salt();
        var hash = passwordValue == null ? null : passwordValue.hash();
        var finalizedMessageIds = List.copyOf(storedMessageIds);
        var storedMessageId = finalizedMessageIds.get(0);
        var contentItems = users.findById(actor.getId()).map(BotUser::pendingContentItems)
                .filter(items -> !items.isEmpty()).map(List::copyOf).orElse(null);
        var previewText = previewText(contentItems);
        var qrCode = qrCodes.insert(new QrCode(id, null, type, QrStatus.ACTIVE, actor.getId(),
                properties.getContentChannelId(), storedMessageId, salt, hash, clock.instant(), 0,
                List.copyOf(finalizedMessageIds), contentItems, null, null, null, null,
                ignorePasswordCase, previewText));
        saveUser(actor, BotUser.State.IDLE, null, null, null);
        var link = deepLink(qrCode.id());
        telegram.sendPhoto(chatId, "qr-" + qrCode.id() + ".png",
                imageGenerator.generatePng(link), "🔗 " + link);
        analytics.track(AnalyticsAction.QR_CREATED, actor.getId(), chatId, qrCode);
        var view = mainMenuView();
        var navigationMessageId = telegram.sendInline(chatId,
                "✨ Можна створити ще один QR-код.\n\n" + view.text(), view.keyboard());
        setNavigationMessage(actor, navigationMessageId);
    }

    private boolean redeemOneTime(QrCode qrCode, User actor, long chatId) {
        var activeStatus = new Criteria().orOperator(Criteria.where("status").is(QrStatus.ACTIVE),
                Criteria.where("status").exists(false), Criteria.where("status").is(null));
        var query = Query.query(Criteria.where("id").is(qrCode.id()).and("ownerId").is(actor.getId())
                .and("type").is(QrType.COUPON).andOperator(activeStatus));
        var redeemed = mongo.findAndModify(query, redemptionUpdate(actor),
                FindAndModifyOptions.options().returnNew(true), QrCode.class);
        if (redeemed == null) {
            return false;
        }
        try {
            recordDelivery(redeemed, actor, chatId);
            analytics.track(AnalyticsAction.ONE_TIME_REDEEMED, actor.getId(), chatId, redeemed);
            return true;
        } catch (RuntimeException exception) {
            mongo.updateFirst(Query.query(Criteria.where("id").is(qrCode.id()).and("status").is(QrStatus.REDEEMED)),
                    new Update().set("status", QrStatus.ACTIVE), QrCode.class);
            throw exception;
        }
    }

    private boolean redeemOneTimeGift(QrCode qrCode, User actor, long chatId) {
        var activeStatus = new Criteria().orOperator(Criteria.where("status").is(QrStatus.ACTIVE),
                Criteria.where("status").exists(false), Criteria.where("status").is(null));
        var query = Query.query(Criteria.where("id").is(qrCode.id())
                .and("type").is(QrType.SINGLE_USE)
                .andOperator(activeStatus));
        var redeemed = mongo.findAndModify(query, redemptionUpdate(actor),
                FindAndModifyOptions.options().returnNew(true), QrCode.class);
        if (redeemed == null) {
            return false;
        }
        try {
            deliverAndRecord(redeemed, actor, chatId);
            analytics.track(AnalyticsAction.ONE_TIME_REDEEMED, actor.getId(), chatId, redeemed);
            return true;
        } catch (RuntimeException exception) {
            mongo.updateFirst(Query.query(Criteria.where("id").is(qrCode.id()).and("status").is(QrStatus.REDEEMED)),
                    new Update().set("status", QrStatus.ACTIVE), QrCode.class);
            throw exception;
        }
    }

    private void completeDelivery(QrCode qrCode, User actor, long chatId) {
        deliverAndRecord(qrCode, actor, chatId);
        saveUser(actor, BotUser.State.IDLE, null, null, null);
        sendMainNavigation(actor, chatId, "");
    }

    private void deliverAndRecord(QrCode qrCode, User actor, long chatId) {
        var strategy = deliveryStrategies.stream().filter(candidate -> candidate.supports(qrCode))
                .findFirst().orElseThrow(() -> new IllegalStateException("No delivery strategy for " + qrCode.type()));
        strategy.deliver(qrCode, chatId);
        recordDelivery(qrCode, actor, chatId);
    }

    private void recordDelivery(QrCode qrCode, User actor, long chatId) {
        var now = clock.instant();
        accesses.save(new QrAccess(UUID.randomUUID().toString(), qrCode.id(), actor.getId(), actor.getUsername(), now));
        mongo.updateFirst(Query.query(Criteria.where("id").is(qrCode.id())),
                new Update().inc("openCount", 1), QrCode.class);
        analytics.track(AnalyticsAction.CONTENT_DELIVERED, actor.getId(), chatId, qrCode);
    }

    private BotView mainMenuView() {
        return new BotView("👋 Оберіть дію.", keyboard(List.of(
                row(button("➕ Створити QR-код", MENU_CREATE)),
                row(button("📚 Мої QR-коди", MENU_LIST)))));
    }

    private BotView contentReadyView(int count, String previewText) {
        var preview = previewText == null || previewText.isBlank() ? "" : "\n📝 " + previewText;
        return new BotView("⏳ Обробка завантажень…" + preview
                + "\n\n📦 Отримано вкладень: " + count,
                keyboard(List.of(row(button("✅ Створити з " + count + " вкладеннями", CONTENT_DONE)),
                        row(button("❌ Скасувати", MENU_HOME)))));
    }

    private void refreshUploadControl(User actor, long chatId, int count, String previewText,
                                      boolean firstContentItem) {
        var view = contentReadyView(count, previewText);
        var navigationMessageId = users.findById(actor.getId()).map(BotUser::navigationMessageId).orElse(null);
        if (firstContentItem || navigationMessageId == null) {
            navigationMessageId = telegram.sendInline(chatId, view.text(), view.keyboard());
        } else {
            telegram.editInline(chatId, navigationMessageId, view.text(), view.keyboard());
        }
        setNavigationMessage(actor, navigationMessageId);
        setDisplayedMessages(actor, List.of(navigationMessageId));
    }

    private void sendMainNavigation(User actor, long chatId, String prefix) {
        var view = mainMenuView();
        var navigationMessageId = telegram.sendInline(chatId, prefix + view.text(), view.keyboard());
        setNavigationMessage(actor, navigationMessageId);
    }

    private QrCode findByPayload(String payload) {
        return qrCodes.findById(payload).orElseGet(() -> qrCodes.findByToken(payload).orElse(null));
    }

    private List<QrCode> filteredQrs(long ownerId, QrListPreferences preferences) {
        var criteria = filteredQrCriteria(ownerId, preferences);
        var direction = preferences.sort() == QrListSort.NEWEST ? Sort.Direction.DESC : Sort.Direction.ASC;
        var query = Query.query(criteria).with(Sort.by(direction, "createdAt"))
                .skip((long) preferences.page() * QR_LIST_PAGE_SIZE).limit(QR_LIST_PAGE_SIZE);
        return mongo.find(query, QrCode.class);
    }

    private long filteredQrCount(long ownerId, QrListPreferences preferences) {
        return mongo.count(Query.query(filteredQrCriteria(ownerId, preferences)), QrCode.class);
    }

    private Criteria filteredQrCriteria(long ownerId, QrListPreferences preferences) {
        var criteria = Criteria.where("ownerId").is(ownerId);
        if (preferences.types().size() < QrType.values().length) {
            criteria = criteria.and("type").in(preferences.types());
        }
        if (preferences.statuses().isEmpty()) {
            criteria = criteria.and("status").in(List.of("__NONE__"));
        } else if (preferences.statuses().contains(QrStatus.ACTIVE)) {
            var statuses = new ArrayList<QrStatus>(preferences.statuses());
            criteria = criteria.andOperator(new Criteria().orOperator(Criteria.where("status").in(statuses),
                    Criteria.where("status").exists(false), Criteria.where("status").is(null)));
        } else {
            criteria = criteria.and("status").in(preferences.statuses());
        }
        return criteria;
    }

    private QrListPreferences preferences(BotUser user) {
        if (user.listPreferences() == null || user.listPreferences().types() == null
                || user.listPreferences().statuses() == null) {
            return QrListPreferences.defaults();
        }
        return user.listPreferences();
    }

    private String typeEmoji(QrType type) {
        return switch (type) {
            case CONTENT -> "📄";
            case SINGLE_USE -> "1️⃣";
            case COUPON -> "🎟️";
        };
    }

    private InlineKeyboardButton checkboxButton(QrType type, Set<QrType> selectedTypes) {
        var check = selectedTypes.contains(type) ? "☑️" : "⬜";
        return button(check + " " + typeEmoji(type) + " " + typeLabel(type), FILTER_TYPE_PREFIX + type);
    }

    private InlineKeyboardButton groupedTypeCheckboxButton(QrType type, Set<QrType> selectedTypes) {
        var group = typeGroup(type);
        var check = selectedTypes.containsAll(group) ? "☑️" : "⬜";
        return button(check + " " + typeEmoji(type) + " " + typeLabel(type), FILTER_TYPE_PREFIX + type);
    }

    private Set<QrType> typeGroup(QrType type) {
        return EnumSet.of(type);
    }

    private InlineKeyboardButton statusCheckboxButton(QrStatus status, Set<QrStatus> selectedStatuses) {
        var check = selectedStatuses.contains(status) ? "☑️" : "⬜";
        return button(check + " " + statusEmoji(status) + " " + statusLabel(status), FILTER_STATUS_PREFIX + status);
    }

    private String statusEmoji(QrStatus status) {
        return switch (status) {
            case ACTIVE -> "✅";
            case REDEEMED -> "🏁";
            case DELETED -> "🗑️";
        };
    }

    private String itemMetadata(QrCode qrCode) {
        var metadata = new StringBuilder(typeEmoji(qrCode.type()) + " " + statusEmoji(effectiveStatus(qrCode)));
        if (isPasswordProtected(qrCode)) {
            metadata.append(" 🔒");
        }
        return metadata.toString();
    }

    private String itemPreview(QrCode qrCode) {
        var preview = shortPreview(qrCode.previewText());
        var fileCount = qrCode.contentItems() == null ? qrCode.contentMessageIds().size()
                : qrCode.contentItems().stream().filter(item -> item.kind() != QrContentItem.Kind.TEXT).count();
        var content = new StringBuilder(preview);
        if (!preview.isBlank()) {
            content.append(fileCount > 0 ? " " : "");
        }
        if (fileCount > 0) {
            content.append("📎 ").append(fileCount);
        }
        return content.isEmpty() ? "Відкрити" : truncate(content.toString(), 64);
    }

    private String shortPreview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var normalized = value.strip();
        var characterCount = normalized.codePointCount(0, normalized.length());
        if (characterCount <= 5) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, 5)) + "...";
    }

    private Update redemptionUpdate(User actor) {
        return new Update().set("status", QrStatus.REDEEMED)
                .set("redeemedByUserId", actor.getId())
                .set("redeemedByUsername", actor.getUsername())
                .set("redeemedByName", displayName(actor))
                .set("redeemedAt", clock.instant());
    }

    private String redeemedBy(QrCode qrCode) {
        var username = qrCode.redeemedByUsername() == null ? "без імені користувача" : "@" + qrCode.redeemedByUsername();
        var name = qrCode.redeemedByName() == null || qrCode.redeemedByName().isBlank()
                ? "Ім’я невідоме" : qrCode.redeemedByName();
        return name + " · " + username + " · ID " + qrCode.redeemedByUserId();
    }

    private String displayName(User actor) {
        return java.util.stream.Stream.of(actor.getFirstName(), actor.getLastName())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private QrStatus effectiveStatus(QrCode qrCode) {
        return qrCode.status() == null ? QrStatus.ACTIVE : qrCode.status();
    }

    private boolean isPasswordProtected(QrCode qrCode) {
        return qrCode.passwordSalt() != null && qrCode.passwordHash() != null;
    }

    private String typeLabel(QrType type) {
        return switch (type) {
            case CONTENT -> "Контент";
            case SINGLE_USE -> "Одноразовий QR-код";
            case COUPON -> "Купон";
        };
    }

    private String statusLabel(QrStatus status) {
        return switch (status) {
            case ACTIVE -> "Активний";
            case REDEEMED -> "Погашений";
            case DELETED -> "Видалений";
        };
    }

    private void trackNotFound(Message message, QrCode qrCode) {
        analytics.track(AnalyticsAction.QR_NOT_FOUND, message.getFrom().getId(), message.getChat().getId(), qrCode);
    }

    private boolean hasState(long userId, BotUser.State state) {
        return users.findById(userId).map(user -> user.state() == state).orElse(false);
    }

    private BotUser requiredUser(long userId, BotUser.State state) {
        var user = users.findById(userId).orElseThrow(() -> new IllegalStateException("Bot user not found"));
        if (user.state() != state) {
            throw new IllegalStateException("Unexpected user state " + user.state());
        }
        return user;
    }

    private void touchUser(User actor) {
        var current = users.findById(actor.getId()).orElse(null);
        if (current == null) {
            saveUser(actor, BotUser.State.IDLE, null, null, null);
        } else if (!java.util.Objects.equals(current.username(), actor.getUsername())) {
            users.save(new BotUser(current.id(), actor.getUsername(), current.state(), current.selectedType(),
                    current.channelMessageId(), current.pendingQrId(), preferences(current),
                    current.navigationMessageId(), clock.instant(), current.pendingMessageIds(),
                    current.pendingMediaGroupId(), current.displayedMessageIds(), current.pendingContentItems(),
                    current.pendingPasswordOptions()));
        }
    }

    private void saveUser(User actor, BotUser.State state, QrType type, Integer messageId, String qrId) {
        saveUser(actor, state, type, messageId, qrId, null, null);
    }

    private void saveUser(User actor, BotUser.State state, QrType type, Integer messageId, String qrId,
                          List<Integer> pendingMessageIds, String pendingMediaGroupId) {
        var listPreferences = users.findById(actor.getId()).map(this::preferences)
                .orElseGet(QrListPreferences::defaults);
        users.save(new BotUser(actor.getId(), actor.getUsername(), state, type, messageId, qrId,
                listPreferences, users.findById(actor.getId()).map(BotUser::navigationMessageId).orElse(null),
                clock.instant(), pendingMessageIds, pendingMediaGroupId,
                users.findById(actor.getId()).map(BotUser::displayedMessageIds).orElse(null),
                users.findById(actor.getId()).map(BotUser::pendingContentItems).orElse(null),
                users.findById(actor.getId()).map(BotUser::pendingPasswordOptions).orElse(null)));
    }

    private void resetUser(User actor) {
        var listPreferences = users.findById(actor.getId()).map(this::preferences)
                .orElseGet(QrListPreferences::defaults);
        users.save(new BotUser(actor.getId(), actor.getUsername(), BotUser.State.IDLE,
                null, null, null, listPreferences, null, clock.instant(), null, null, null, null, null));
    }

    private void setNavigationMessage(User actor, int messageId) {
        var user = users.findById(actor.getId()).orElseThrow();
        users.save(new BotUser(user.id(), actor.getUsername(), user.state(), user.selectedType(),
                user.channelMessageId(), user.pendingQrId(), preferences(user), messageId, clock.instant(),
                user.pendingMessageIds(), user.pendingMediaGroupId(), user.displayedMessageIds(),
                user.pendingContentItems(), user.pendingPasswordOptions()));
    }

    private void setDisplayedMessages(User actor, List<Integer> messageIds) {
        var user = users.findById(actor.getId()).orElseThrow();
        users.save(new BotUser(user.id(), actor.getUsername(), user.state(), user.selectedType(),
                user.channelMessageId(), user.pendingQrId(), preferences(user), user.navigationMessageId(),
                clock.instant(), user.pendingMessageIds(), user.pendingMediaGroupId(), List.copyOf(messageIds),
                user.pendingContentItems(), user.pendingPasswordOptions()));
    }

    private void setPendingPasswordOptions(User actor, PendingPasswordOptions options) {
        var user = users.findById(actor.getId()).orElseThrow();
        users.save(new BotUser(user.id(), actor.getUsername(), user.state(), user.selectedType(),
                user.channelMessageId(), user.pendingQrId(), preferences(user), user.navigationMessageId(),
                clock.instant(), user.pendingMessageIds(), user.pendingMediaGroupId(), user.displayedMessageIds(),
                user.pendingContentItems(), options));
    }

    private void deleteNavigation(long userId, long chatId) {
        users.findById(userId).ifPresent(user -> {
            var messageIds = new java.util.LinkedHashSet<Integer>();
            if (user.displayedMessageIds() != null) {
                messageIds.addAll(user.displayedMessageIds());
            }
            if (user.navigationMessageId() != null) {
                messageIds.add(user.navigationMessageId());
            }
            for (var messageId : messageIds) {
                telegram.deleteMessage(chatId, messageId);
            }
        });
    }

    private void deleteDisplayedMessagesExcept(long userId, long chatId, int retainedMessageId) {
        users.findById(userId).ifPresent(user -> {
            if (user.displayedMessageIds() == null) {
                return;
            }
            for (var messageId : user.displayedMessageIds()) {
                if (messageId != retainedMessageId) {
                    telegram.deleteMessage(chatId, messageId);
                }
            }
        });
    }

    private String requirePassword(Message message) {
        var password = message.getText() == null ? null : message.getText().strip();
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Пароль/код має бути текстом");
        }
        if (password.startsWith("/")) {
            throw new IllegalArgumentException("Пароль/код не може починатися з косої риски");
        }
        return password;
    }

    private PendingPasswordOptions passwordOptions(String password) {
        var exact = passwords.hash(normalizePassword(password, false));
        var normalized = passwords.hash(normalizePassword(password, true));
        return new PendingPasswordOptions(exact.salt(), exact.hash(), normalized.salt(), normalized.hash());
    }

    private PasswordHasher.PasswordValue selectedPassword(PendingPasswordOptions options, boolean ignoreCase) {
        if (options == null) {
            throw new IllegalStateException("Pending password options are missing");
        }
        return ignoreCase
                ? new PasswordHasher.PasswordValue(options.normalizedSalt(), options.normalizedHash())
                : new PasswordHasher.PasswordValue(options.exactSalt(), options.exactHash());
    }

    private String normalizePassword(String password, boolean ignoreCase) {
        var normalized = password.strip();
        return ignoreCase ? normalized.toLowerCase(java.util.Locale.ROOT) : normalized;
    }

    private InlineKeyboard caseChoiceKeyboard(String prefix) {
        return keyboard(List.of(row(button("✅ Так, ігнорувати регістр", prefix + "ignore")),
                row(button("🔠 Ні, враховувати регістр", prefix + "exact")),
                row(button("❌ Скасувати", MENU_HOME))));
    }

    private QrContentItem contentItem(Message message, int order) {
        if (message.getPhoto() != null && !message.getPhoto().isEmpty()) {
            var photo = message.getPhoto().get(message.getPhoto().size() - 1);
            return new QrContentItem(QrContentItem.Kind.PHOTO, null, message.getCaption(), photo.getFileId(),
                    photo.getFileUniqueId(), order);
        }
        if (message.getVideo() != null) {
            return new QrContentItem(QrContentItem.Kind.VIDEO, null, message.getCaption(),
                    message.getVideo().getFileId(), message.getVideo().getFileUniqueId(), order);
        }
        if (message.getDocument() != null) {
            return new QrContentItem(QrContentItem.Kind.DOCUMENT, null, message.getCaption(),
                    message.getDocument().getFileId(), message.getDocument().getFileUniqueId(), order);
        }
        if (message.getText() != null) {
            return new QrContentItem(QrContentItem.Kind.TEXT, message.getText(), null, null, null, order);
        }
        throw new IllegalArgumentException("Unsupported QR content message type");
    }

    private String contentLimitViolation(List<QrContentItem> items) {
        if (items.size() > MAX_CONTENT_ITEMS) {
            return "QR-код може містити не більше " + MAX_CONTENT_ITEMS + " повідомлень/елементів.";
        }
        var photoAndVideoCount = items.stream().filter(item -> item.kind() == QrContentItem.Kind.PHOTO
                || item.kind() == QrContentItem.Kind.VIDEO).count();
        if (photoAndVideoCount > MAX_MEDIA_ITEMS) {
            return "QR-код може містити не більше 10 фото/відео.";
        }
        var documentCount = items.stream().filter(item -> item.kind() == QrContentItem.Kind.DOCUMENT).count();
        if (documentCount > MAX_MEDIA_ITEMS) {
            return "QR-код може містити не більше 10 документів.";
        }
        var oversizedText = items.stream().filter(item -> item.kind() == QrContentItem.Kind.TEXT)
                .map(QrContentItem::text).filter(java.util.Objects::nonNull)
                .anyMatch(text -> text.length() > MAX_TEXT_LENGTH);
        if (oversizedText) {
            return "Кожне текстове повідомлення може містити не більше 4 096 символів.";
        }
        var oversizedCaption = items.stream().filter(item -> item.kind() != QrContentItem.Kind.TEXT)
                .map(QrContentItem::caption).filter(java.util.Objects::nonNull)
                .anyMatch(caption -> caption.length() > MAX_MEDIA_CAPTION_LENGTH);
        if (oversizedCaption) {
            return "Кожен підпис до медіафайлу може містити не більше 1 024 символи.";
        }
        return null;
    }

    private String previewText(List<QrContentItem> items) {
        if (items == null) {
            return null;
        }
        var value = items.stream().map(item -> item.kind() == QrContentItem.Kind.TEXT
                        ? item.text() : item.caption())
                .filter(text -> text != null && !text.isBlank()).findFirst().orElse(null);
        if (value == null) {
            return null;
        }
        return truncate(value.replaceAll("\\s+", " ").strip(), 30);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private ArrayList<Integer> previewContent(QrCode qrCode, long chatId) {
        var messageIds = new ArrayList<Integer>();
        if (qrCode.contentItems() != null && !qrCode.contentItems().isEmpty()) {
            messageIds.addAll(telegram.sendContent(chatId, qrCode.contentItems()));
            return messageIds;
        }
        messageIds.addAll(telegram.copyMessages(chatId, qrCode.channelId(), qrCode.contentMessageIds()));
        return messageIds;
    }

    private String deepLink(String id) {
        var username = properties.getBotUsername().replaceFirst("^@", "");
        return "https://t.me/" + username + "?start=" + id;
    }

    private InlineKeyboard keyboard(List<List<InlineKeyboardButton>> rows) {
        return InlineKeyboard.builder().inlineKeyboard(rows).build();
    }

    private List<InlineKeyboardButton> row(InlineKeyboardButton... buttons) {
        return List.of(buttons);
    }

    private InlineKeyboardButton button(String text, String callback) {
        return InlineKeyboardButton.builder().text(text).callbackData(callback).build();
    }

    public record DeleteResult(boolean deleted, BotView view) {
    }

    public enum OpenResult {
        DELIVERED,
        PASSWORD_REQUIRED,
        CONFIRMATION_REQUIRED,
        NOT_FOUND
    }
}
