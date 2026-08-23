package ua.mytnyk.qrbot.service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    public static final String FILTER_TYPE_PREFIX = "list:type:";
    public static final String FILTER_STATUS_PREFIX = "list:status:";
    public static final String SORT_PREFIX = "list:sort:";
    private static final Logger log = LoggerFactory.getLogger(QrWorkflow.class);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("Europe/Kyiv"));
    private final BotUserRepository users;
    private final QrCodeRepository qrCodes;
    private final QrAccessRepository accesses;
    private final PasswordHasher passwords;
    private final QrImageGenerator imageGenerator;
    private final TelegramGateway telegram;
    private final QrBotProperties properties;
    private final List<ContentDeliveryStrategy> deliveryStrategies;
    private final MongoTemplate mongo;
    private final AnalyticsService analytics;
    private final Clock clock = Clock.systemUTC();

    public QrWorkflow(BotUserRepository users, QrCodeRepository qrCodes, QrAccessRepository accesses,
                      PasswordHasher passwords, QrImageGenerator imageGenerator, TelegramGateway telegram,
                      QrBotProperties properties, List<ContentDeliveryStrategy> deliveryStrategies,
                      MongoTemplate mongo, AnalyticsService analytics) {
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
        deleteNavigation(message.getFrom().getId(), message.getChat().getId());
        resetUser(message.getFrom());
        var view = mainMenuView();
        var navigationMessageId = telegram.sendInline(message.getChat().getId(), view.text(), view.keyboard());
        setNavigationMessage(message.getFrom(), navigationMessageId);
        analytics.track(AnalyticsAction.MAIN_MENU_VIEWED, message.getFrom().getId(), message.getChat().getId());
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
        return new BotView("🧩 Select the QR type.", keyboard(List.of(
                row(button("📄 Content", TYPE_PREFIX + QrType.CONTENT)),
                row(button("🔐 Protected content", TYPE_PREFIX + QrType.PROTECTED_CONTENT)),
                row(button("🎟️ One-time content", TYPE_PREFIX + QrType.ONE_TIME_CONTENT)),
                row(button("🏠 Main menu", MENU_HOME)))));
    }

    public BotView selectType(User actor, long chatId, QrType type) {
        saveUser(actor, BotUser.State.WAITING_FOR_CONTENT, type, null, null);
        users.save(users.findById(actor.getId()).orElseThrow().withPendingContentItems(List.of()));
        analytics.track(AnalyticsAction.QR_TYPE_SELECTED, actor.getId(), chatId, null,
                Map.of("qrType", type.name()));
        log.info("QR type selected userId={} type={}", actor.getId(), type);
        return new BotView("⏳ Waiting for content…\n\nSend one or more messages, then tap Done.",
                keyboard(List.of(row(button("❌ Cancel", MENU_HOME)))));
    }

    public boolean isWaitingForContent(long userId) {
        return hasState(userId, BotUser.State.WAITING_FOR_CONTENT);
    }

    public synchronized void acceptContent(Message message) {
        var user = requiredUser(message.getFrom().getId(), BotUser.State.WAITING_FOR_CONTENT);
        var chatId = message.getChat().getId();
        var contentItems = new ArrayList<QrContentItem>();
        if (user.pendingContentItems() != null) {
            contentItems.addAll(user.pendingContentItems());
        }
        var contentItem = contentItem(message, contentItems.size());
        contentItems.add(contentItem);
        var storedMessageIds = telegram.sendComposedContent(properties.getContentChannelId(), contentItems);
        telegram.deleteMessage(chatId, message.getMessageId());
        deleteNavigation(message.getFrom().getId(), chatId);
        var storedMessageId = storedMessageIds.get(0);
        analytics.track(AnalyticsAction.CONTENT_SELECTED, message.getFrom().getId(), chatId, null,
                Map.of("qrType", user.selectedType().name()));
        saveUser(message.getFrom(), BotUser.State.WAITING_FOR_CONTENT, user.selectedType(), storedMessageId,
                null, storedMessageIds, null);
        users.save(users.findById(message.getFrom().getId()).orElseThrow().withPendingContentItems(contentItems));
        var displayedMessageIds = new ArrayList<>(telegram.sendComposedContent(chatId, contentItems));
        var view = contentReadyView(contentItems.size());
        var navigationMessageId = telegram.sendInline(chatId, view.text(), view.keyboard());
        displayedMessageIds.add(navigationMessageId);
        setNavigationMessage(message.getFrom(), navigationMessageId);
        setDisplayedMessages(message.getFrom(), displayedMessageIds);
    }

    public BotView finishContentSelection(User actor, long chatId) {
        var user = requiredUser(actor.getId(), BotUser.State.WAITING_FOR_CONTENT);
        var type = user.selectedType();
        var storedMessageIds = user.pendingMessageIds();
        if (storedMessageIds == null || storedMessageIds.isEmpty()) {
            return contentReadyView(0);
        }
        var storedMessageId = storedMessageIds.get(0);
        if (type == QrType.PROTECTED_CONTENT) {
            saveUser(actor, BotUser.State.WAITING_FOR_CREATION_PASSWORD,
                    type, storedMessageId, null, storedMessageIds, null);
            deleteNavigation(actor.getId(), chatId);
            var navigationMessageId = telegram.sendInline(chatId,
                    "🔐 Send the password/code for this QR. Your message will be deleted immediately.",
                    keyboard(List.of(row(button("❌ Cancel", MENU_HOME)))));
            setNavigationMessage(actor, navigationMessageId);
            return null;
        }
        finishCreation(actor, chatId, type, storedMessageIds, null);
        return null;
    }

    public boolean isWaitingForCreationPassword(long userId) {
        return hasState(userId, BotUser.State.WAITING_FOR_CREATION_PASSWORD);
    }

    public void acceptCreationPassword(Message message) {
        var user = requiredUser(message.getFrom().getId(), BotUser.State.WAITING_FOR_CREATION_PASSWORD);
        telegram.deleteMessage(message.getChat().getId(), message.getMessageId());
        var passwordValue = passwords.hash(requirePassword(message));
        analytics.track(AnalyticsAction.CREATION_PASSWORD_SET, message.getFrom().getId(), message.getChat().getId());
        finishCreation(message.getFrom(), message.getChat().getId(), user.selectedType(),
                user.pendingMessageIds(), passwordValue);
    }

    public BotView listCreatedQrs(User actor, long chatId) {
        touchUser(actor);
        var user = users.findById(actor.getId()).orElseThrow();
        var preferences = preferences(user);
        var all = filteredQrs(actor.getId(), preferences);
        var activeCount = countActive(actor.getId());
        var text = new StringBuilder("📚 Your QRs\n\n✅ Active: ").append(activeCount)
                .append("\n👁 Showing: ").append(all.size())
                .append("\n📅 Created: ").append(preferences.sort() == QrListSort.NEWEST ? "newest first" : "oldest first");
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        rows.add(row(
                checkboxButton(QrType.CONTENT, preferences.types()),
                checkboxButton(QrType.PROTECTED_CONTENT, preferences.types()),
                checkboxButton(QrType.ONE_TIME_CONTENT, preferences.types())));
        rows.add(row(button(preferences.sort() == QrListSort.NEWEST ? "📅 ↓ Newest" : "📅 ↑ Oldest",
                SORT_PREFIX + (preferences.sort() == QrListSort.NEWEST
                        ? QrListSort.OLDEST : QrListSort.NEWEST))));
        var index = 1;
        for (var qrCode : all) {
            var description = index + ". " + typeEmoji(qrCode.type()) + " " + typeLabel(qrCode.type()) + " · "
                    + "👁 " + qrCode.openCount();
            rows.add(row(button(description, VIEW_PREFIX + qrCode.id())));
            index++;
        }
        if (all.isEmpty()) {
            text.append("\n\n📭 No QRs match these filters.");
        }
        rows.add(row(button("🏠 Main menu", MENU_HOME)));
        analytics.track(AnalyticsAction.QR_LIST_VIEWED, actor.getId(), chatId, null,
                Map.of("activeCount", Long.toString(activeCount)));
        return new BotView(text.toString(), keyboard(rows));
    }

    public BotView updateListPreferences(User actor, long chatId, String callbackData) {
        touchUser(actor);
        var user = users.findById(actor.getId()).orElseThrow();
        var current = preferences(user);
        var types = EnumSet.noneOf(QrType.class);
        types.addAll(current.types());
        var status = current.status();
        var sort = current.sort();
        if (callbackData.startsWith(FILTER_TYPE_PREFIX)) {
            var type = QrType.valueOf(callbackData.substring(FILTER_TYPE_PREFIX.length()));
            if (!types.remove(type)) {
                types.add(type);
            }
        } else if (callbackData.startsWith(SORT_PREFIX)) {
            sort = QrListSort.valueOf(callbackData.substring(SORT_PREFIX.length()));
        }
        users.save(new BotUser(user.id(), actor.getUsername(), user.state(), user.selectedType(),
                user.channelMessageId(), user.pendingQrId(), new QrListPreferences(types, status, sort),
                user.navigationMessageId(), clock.instant(), user.pendingMessageIds(), user.pendingMediaGroupId(),
                user.displayedMessageIds(), user.pendingContentItems()));
        return listCreatedQrs(actor, chatId);
    }

    public void showQrDetails(String qrId, User actor, long chatId) {
        touchUser(actor);
        var qrCode = qrCodes.findByIdAndOwnerId(qrId, actor.getId()).orElse(null);
        deleteNavigation(actor.getId(), chatId);
        if (qrCode == null || effectiveStatus(qrCode) == QrStatus.DELETED) {
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR not found.\n\n");
            return;
        }
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        rows.add(row(button("📷 Show QR and link", SHOW_QR_PREFIX + qrCode.id())));
        if (effectiveStatus(qrCode) == QrStatus.ACTIVE) {
            rows.add(row(button("🗑️ Delete QR", DELETE_PREFIX + qrCode.id())));
        }
        rows.add(row(button("⬅️ Back to My QRs", MENU_LIST), button("🏠 Main menu", MENU_HOME)));
        var displayedMessageIds = previewContent(qrCode, chatId);
        var details = "🔎 QR item\n\n"
                + "🆔 " + qrCode.id()
                + "\n" + typeEmoji(qrCode.type()) + " Type: " + typeLabel(qrCode.type())
                + "\n✅ Status: " + effectiveStatus(qrCode)
                + "\n👁 Successful opens: " + qrCode.openCount()
                + "\n📅 Created: " + DISPLAY_DATE.format(qrCode.createdAt());
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
            sendMainNavigation(actor, chatId, "🔍 QR not found.\n\n");
            return;
        }
        var link = deepLink(qrCode.id());
        var qrMessageId = telegram.sendPhoto(chatId, "qr-" + qrCode.id() + ".png", imageGenerator.generatePng(link),
                "🔗 " + link, keyboard(List.of(row(button("⬅️ Back to item", VIEW_PREFIX + qrCode.id())),
                        row(button("📚 My QRs", MENU_LIST), button("🏠 Main menu", MENU_HOME)))));
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

    public OpenResult open(String payload, Message message) {
        saveUser(message.getFrom(), BotUser.State.IDLE, null, null, null);
        var qrCode = findByPayload(payload);
        analytics.track(AnalyticsAction.QR_SCANNED, message.getFrom().getId(), message.getChat().getId(), qrCode);
        if (qrCode == null || effectiveStatus(qrCode) != QrStatus.ACTIVE) {
            trackNotFound(message, qrCode);
            deleteNavigation(message.getFrom().getId(), message.getChat().getId());
            sendMainNavigation(message.getFrom(), message.getChat().getId(), "🔍 QR not found.\n\n");
            return OpenResult.NOT_FOUND;
        }
        if (qrCode.type() == QrType.ONE_TIME_CONTENT) {
            if (qrCode.ownerId() != message.getFrom().getId()) {
                trackNotFound(message, qrCode);
                return OpenResult.NOT_FOUND;
            }
            saveUser(message.getFrom(), BotUser.State.WAITING_FOR_REDEEM_CONFIRMATION, null, null, qrCode.id());
            deleteNavigation(message.getFrom().getId(), message.getChat().getId());
            var displayedMessageIds = previewContent(qrCode, message.getChat().getId());
            var navigationMessageId = telegram.sendInline(message.getChat().getId(),
                    "🎟️ Redeem this one-time QR now?",
                    keyboard(List.of(row(button("✅ Redeem", REDEEM_PREFIX + qrCode.id())),
                            row(button("❌ Cancel", MENU_HOME)))));
            setNavigationMessage(message.getFrom(), navigationMessageId);
            displayedMessageIds.add(navigationMessageId);
            setDisplayedMessages(message.getFrom(), displayedMessageIds);
            return OpenResult.CONFIRMATION_REQUIRED;
        }
        if (qrCode.type() == QrType.PROTECTED_CONTENT) {
            saveUser(message.getFrom(), BotUser.State.WAITING_FOR_OPEN_PASSWORD, null, null, qrCode.id());
            deleteNavigation(message.getFrom().getId(), message.getChat().getId());
            var navigationMessageId = telegram.sendInline(message.getChat().getId(),
                    "🔐 Enter the password/code. Your message will be deleted immediately.",
                    keyboard(List.of(row(button("❌ Cancel", MENU_HOME)))));
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
        if (effectiveStatus(qrCode) != QrStatus.ACTIVE
                || !passwords.matches(requirePassword(message), qrCode.passwordSalt(), qrCode.passwordHash())) {
            deleteNavigation(message.getFrom().getId(), message.getChat().getId());
            var navigationMessageId = telegram.sendInline(message.getChat().getId(),
                    "❌ Incorrect password/code. Try again.",
                    keyboard(List.of(row(button("❌ Cancel", MENU_HOME)))));
            setNavigationMessage(message.getFrom(), navigationMessageId);
            analytics.track(AnalyticsAction.PASSWORD_REJECTED, message.getFrom().getId(),
                    message.getChat().getId(), qrCode);
            return false;
        }
        completeDelivery(qrCode, message.getFrom(), message.getChat().getId());
        return true;
    }

    public boolean confirmRedemption(String qrId, User actor, long chatId) {
        var user = requiredUser(actor.getId(), BotUser.State.WAITING_FOR_REDEEM_CONFIRMATION);
        if (!qrId.equals(user.pendingQrId())) {
            deleteNavigation(actor.getId(), chatId);
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR not found.\n\n");
            return false;
        }
        var qrCode = qrCodes.findById(qrId).orElse(null);
        if (qrCode == null || qrCode.ownerId() != actor.getId() || effectiveStatus(qrCode) != QrStatus.ACTIVE) {
            deleteNavigation(actor.getId(), chatId);
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            sendMainNavigation(actor, chatId, "🔍 QR not found.\n\n");
            return false;
        }
        if (user.navigationMessageId() != null) {
            telegram.deleteMessage(chatId, user.navigationMessageId());
        }
        var redeemed = redeemOneTime(qrCode, actor, chatId);
        if (redeemed) {
            saveUser(actor, BotUser.State.IDLE, null, null, null);
            setDisplayedMessages(actor, List.of());
            telegram.sendText(chatId, "⬆️ Content delivered");
            sendMainNavigation(actor, chatId, "");
        }
        return redeemed;
    }

    private void finishCreation(User actor, long chatId, QrType type, List<Integer> storedMessageIds,
                                PasswordHasher.PasswordValue passwordValue) {
        var id = UUID.randomUUID().toString();
        var salt = passwordValue == null ? null : passwordValue.salt();
        var hash = passwordValue == null ? null : passwordValue.hash();
        var finalizedMessageIds = List.copyOf(storedMessageIds);
        var storedMessageId = finalizedMessageIds.get(0);
        var qrCode = qrCodes.insert(new QrCode(id, null, type, QrStatus.ACTIVE, actor.getId(),
                properties.getContentChannelId(), storedMessageId, salt, hash, clock.instant(), 0,
                List.copyOf(finalizedMessageIds), null));
        deleteNavigation(actor.getId(), chatId);
        saveUser(actor, BotUser.State.IDLE, null, null, null);
        var link = deepLink(qrCode.id());
        telegram.sendPhoto(chatId, "qr-" + qrCode.id() + ".png",
                imageGenerator.generatePng(link), "🔗 " + link);
        analytics.track(AnalyticsAction.QR_CREATED, actor.getId(), chatId, qrCode);
        var view = mainMenuView();
        var navigationMessageId = telegram.sendInline(chatId,
                "✨ Ready for another QR.\n\n" + view.text(), view.keyboard());
        setNavigationMessage(actor, navigationMessageId);
    }

    private boolean redeemOneTime(QrCode qrCode, User actor, long chatId) {
        var activeStatus = new Criteria().orOperator(Criteria.where("status").is(QrStatus.ACTIVE),
                Criteria.where("status").exists(false), Criteria.where("status").is(null));
        var query = Query.query(Criteria.where("id").is(qrCode.id()).and("ownerId").is(actor.getId())
                .and("type").is(QrType.ONE_TIME_CONTENT).andOperator(activeStatus));
        var redeemed = mongo.findAndModify(query, new Update().set("status", QrStatus.REDEEMED),
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

    private void completeDelivery(QrCode qrCode, User actor, long chatId) {
        deleteNavigation(actor.getId(), chatId);
        deliverAndRecord(qrCode, actor, chatId);
        telegram.sendText(chatId, "⬆️ Content delivered");
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
        return new BotView("👋 Choose an action.", keyboard(List.of(
                row(button("➕ Create QR", MENU_CREATE)),
                row(button("📚 My QRs", MENU_LIST)))));
    }

    private BotView contentReadyView(int count) {
        return new BotView("⏳ Collecting content…\n\n📦 Items added: " + count
                + "\nSend more items or tap Done.",
                keyboard(List.of(row(button("✅ Done", CONTENT_DONE)),
                        row(button("❌ Cancel", MENU_HOME)))));
    }

    private void sendMainNavigation(User actor, long chatId, String prefix) {
        var view = mainMenuView();
        var navigationMessageId = telegram.sendInline(chatId, prefix + view.text(), view.keyboard());
        setNavigationMessage(actor, navigationMessageId);
    }

    private QrCode findByPayload(String payload) {
        return qrCodes.findById(payload).orElseGet(() -> qrCodes.findByToken(payload).orElse(null));
    }

    private long countActive(long ownerId) {
        var status = new Criteria().orOperator(Criteria.where("status").is(QrStatus.ACTIVE),
                Criteria.where("status").exists(false), Criteria.where("status").is(null));
        return mongo.count(Query.query(Criteria.where("ownerId").is(ownerId).andOperator(status)), QrCode.class);
    }

    private List<QrCode> filteredQrs(long ownerId, QrListPreferences preferences) {
        var criteria = Criteria.where("ownerId").is(ownerId);
        if (preferences.types().size() < QrType.values().length) {
            criteria = criteria.and("type").in(preferences.types());
        }
        criteria = criteria.and("status").is(QrStatus.ACTIVE);
        var direction = preferences.sort() == QrListSort.NEWEST ? Sort.Direction.DESC : Sort.Direction.ASC;
        var query = Query.query(criteria).with(Sort.by(direction, "createdAt")).limit(10);
        return mongo.find(query, QrCode.class);
    }

    private QrListPreferences preferences(BotUser user) {
        if (user.listPreferences() == null || user.listPreferences().types() == null) {
            return QrListPreferences.defaults();
        }
        return user.listPreferences();
    }

    private String typeEmoji(QrType type) {
        return switch (type) {
            case CONTENT -> "📄";
            case PROTECTED_CONTENT -> "🔐";
            case ONE_TIME_CONTENT -> "🎟️";
        };
    }

    private InlineKeyboardButton checkboxButton(QrType type, Set<QrType> selectedTypes) {
        var check = selectedTypes.contains(type) ? "☑️" : "⬜";
        return button(check + " " + typeEmoji(type) + " " + typeLabel(type), FILTER_TYPE_PREFIX + type);
    }

    private QrStatus effectiveStatus(QrCode qrCode) {
        return qrCode.status() == null ? QrStatus.ACTIVE : qrCode.status();
    }

    private String typeLabel(QrType type) {
        return switch (type) {
            case CONTENT -> "Content";
            case PROTECTED_CONTENT -> "Protected";
            case ONE_TIME_CONTENT -> "One-time";
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
                    current.pendingMediaGroupId(), current.displayedMessageIds(), current.pendingContentItems()));
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
                users.findById(actor.getId()).map(BotUser::pendingContentItems).orElse(null)));
    }

    private void resetUser(User actor) {
        users.save(new BotUser(actor.getId(), actor.getUsername(), BotUser.State.IDLE,
                null, null, null, QrListPreferences.defaults(), null, clock.instant(), null, null, null, null));
    }

    private void setNavigationMessage(User actor, int messageId) {
        var user = users.findById(actor.getId()).orElseThrow();
        users.save(new BotUser(user.id(), actor.getUsername(), user.state(), user.selectedType(),
                user.channelMessageId(), user.pendingQrId(), preferences(user), messageId, clock.instant(),
                user.pendingMessageIds(), user.pendingMediaGroupId(), user.displayedMessageIds(),
                user.pendingContentItems()));
    }

    private void setDisplayedMessages(User actor, List<Integer> messageIds) {
        var user = users.findById(actor.getId()).orElseThrow();
        users.save(new BotUser(user.id(), actor.getUsername(), user.state(), user.selectedType(),
                user.channelMessageId(), user.pendingQrId(), preferences(user), user.navigationMessageId(),
                clock.instant(), user.pendingMessageIds(), user.pendingMediaGroupId(), List.copyOf(messageIds),
                user.pendingContentItems()));
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
        var password = message.getText();
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password/code must be text");
        }
        return password;
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
