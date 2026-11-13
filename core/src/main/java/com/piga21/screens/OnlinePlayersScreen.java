package com.piga21.screens;

import static com.piga21.utils.Util.startCountdown;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.piga21.AssetStore;
import com.piga21.BackHandler;
import com.piga21.Piga21Game;
import com.piga21.points.GamePoint;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.CommonGameFunctions;
import com.piga21.utils.CommonUtilInterface;
import com.piga21.utils.Constants;
import com.piga21.utils.OverlayContainer;
import com.piga21.utils.ChipBadge;
import com.piga21.utils.GameText;
import com.piga21.utils.PlayerApi;
import com.piga21.utils.PairPlayerDialog;
import com.piga21.utils.ProfileDialog;
import com.piga21.utils.UiKit;
import com.piga21.utils.Util;
import com.piga21.wallet.WalletClient;

/**
 * The lobby. Three columns: who's online to challenge (left), your stake card (centre) and this
 * week's leaderboard (right), under a bar with Back and Share.
 */
public class OnlinePlayersScreen implements Screen, BackHandler {
    private static final float TOP_BAR_HEIGHT = 128;
    private static final float GAP = 30;

    private final Game game;
    private final AssetStore assetStore;
    private final WebSocketHandler webSocketHandler;
    private final CommonUtilInterface commonUtilInterface;
    private final Stage stage;
    private final ExtendViewport extendViewport;
    private String pendingRequestId; // store request ID when message arrives

    public Label timeCountLabel;
    boolean overlayShowing = false;

    private String outgoingRequestId;
    private String challengedName;
    private PairPlayerDialog pairDialog; // open while typing a player ID, until the server answers

    private final Table playersContent = new Table();
    private final Table leaderboardContent = new Table();
    private final Map<String, Boolean> knownPlayers = new HashMap<>(); // Track known players
    private Timer.Task pollingTask;
    private final TextureRegion goldTR, silverTR, bronzeTR;
    private Label balanceLabel, myNameLabel, myRankLabel;
    // The weekly bonus for 1st to 5th place, filled in from the server's leaderboard message
    private static final int BONUS_PLACES = 5;
    private final Label[] bonusLabels = new Label[BONUS_PLACES];
    private Cell<Actor> myAvatarCell;

    private final int myStake = ChipBadge.amountOf(ChipBadge.selectedKey());


    public OnlinePlayersScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
                               WebSocketHandler webSocketHandler,
                               CommonUtilInterface commonUtilInterface) {

        this.game = game;
        this.assetStore = assetStore;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        this.extendViewport = extendViewport;
        this.stage = new Stage(extendViewport);

        goldTR = assetStore.otherComponents.findRegion("gold");
        silverTR = assetStore.otherComponents.findRegion("silver");
        bronzeTR = assetStore.otherComponents.findRegion("bronze");

        float width = stage.getViewport().getWorldWidth();
        float height = stage.getViewport().getWorldHeight();
        addBackground(width, height);

        // Three equal columns
        float columnWidth = (width - 2 * 44 - 2 * GAP) / 3f;
        Table players = buildPlayersPanel();
        // The stake chip, and the weekly bonus for the top five under it
        Table centre = new Table();
        centre.add(buildStakeCard()).expand().fill().row();
        centre.add(buildWeeklyBonusCard()).expandX().fillX().padTop(GAP);
        Table leaderboard = buildLeaderboardPanel();

        Table body = new Table();
        body.add(players).width(columnWidth).expandY().fillY();
        body.add(centre).width(columnWidth).expandY().fillY().padLeft(GAP).padRight(GAP);
        body.add(leaderboard).width(columnWidth).expandY().fillY();

        Table root = new Table();
        root.setFillParent(true);
        root.top();
        root.add(buildTopBar()).expandX().fillX().height(TOP_BAR_HEIGHT).padLeft(44).padRight(44).row();
        root.add(new Image(UiKit.gradient("hairline", new Color(1, 1, 1, 0.06f), new Color(1, 1, 1, 0.06f)))).expandX().fillX().height(2).padLeft(44).padRight(44).row();
        root.add(body).expand().fill().pad(GAP, 44, 36, 44);
        stage.addActor(root);

        // The three columns rise in one after another
        int i = 0;
        for (Actor column : new Actor[]{players, centre, leaderboard}) {
            column.getColor().a = 0;
            column.addAction(Actions.sequence(Actions.delay(0.07f * i++),
                Actions.parallel(Actions.fadeIn(0.35f), Actions.sequence(Actions.moveBy(0, -24), Actions.moveBy(0, 24, 0.35f, Interpolation.pow2Out)))));
        }
    }

    /**
     * Depth without noise: a burgundy gradient, the game's pattern barely visible on it, a warm
     * glow behind the stake card and darker edges.
     */
    private void addBackground(float width, float height) {
        Image base = new Image(UiKit.gradient("lobbyBase", Color.valueOf("#4A0914"), Color.valueOf("#1A030B")));
        base.setSize(width, height);
        stage.addActor(base);

        Image pattern = assetStore.redBackground();
        pattern.setSize(width, height);
        pattern.getColor().a = 0.16f;
        stage.addActor(pattern);

        Image glow = new Image(UiKit.glow("lobbyGlow", new Color(0.9f, 0.12f, 0.22f, 0.55f)));
        glow.setSize(width * 0.62f, height * 1.2f);
        glow.setPosition((width - glow.getWidth()) / 2, (height - glow.getHeight()) / 2 - height * 0.05f);
        stage.addActor(glow);

        Image vignette = new Image(UiKit.vignette());
        vignette.setSize(width, height);
        stage.addActor(vignette);
    }

    /**
     * Back and the title on the left, Share on the right.
     */
    private Table buildTopBar() {
        Stack backIcon = new Stack();
        backIcon.add(new Image(UiKit.disc("backButton", new Color(0.2f, 0.02f, 0.06f, 0.9f), new Color(0.96f, 0.7f, 0.19f, 0.7f), 0.06f)));
        Table chevron = new Table();
        chevron.add(new Image(UiKit.chevronLeft(UiKit.GOLD))).size(40, 40);
        backIcon.add(chevron);
        tappable(backIcon, () -> ((Piga21Game) game).goBack());

        Label title = GameText.label(assetStore.headingFont, "PLAY ONLINE");
        title.setColor(UiKit.INK);
        Label subtitle = GameText.label(assetStore.captionFont, "Challenge a player  ·  Climb this week's board");
        subtitle.setColor(UiKit.SOFT);
        Table titles = new Table();
        titles.add(title).left().row();
        titles.add(subtitle).left().padTop(-4);

        // The art is a round share icon followed by a "SHARE" pill; only the icon is used
        TextureRegion shareArt = assetStore.otherComponents.findRegion("share");
        Image shareIcon = new Image(new TextureRegion(shareArt, 0, 0, shareArt.getRegionHeight(), shareArt.getRegionHeight()));
        Label shareText = GameText.label(assetStore.smallButtonFont, "SHARE");
        shareText.setColor(UiKit.GOLD);
        Table share = new Table();
        share.setBackground(UiKit.tag("share", UiKit.GOLD));
        share.add(shareIcon).size(58, 58).padRight(14).padLeft(-8);
        share.add(shareText).padRight(10);
        tappable(share, this::shareApp);

        // You: tap for your profile
        String myName = Gdx.app.getPreferences(GamePoint.GAME_PREFS).getString(Constants.PLAYER_NAME, "You");
        myNameLabel = GameText.label(assetStore.bodyFont, myName);
        myNameLabel.setColor(UiKit.INK);
        myNameLabel.setEllipsis(true);
        myRankLabel = GameText.label(assetStore.captionFont, "Weekly rank loading...");
        myRankLabel.setColor(UiKit.SOFT);
        Table names = new Table();
        names.add(myNameLabel).right().minWidth(0).maxWidth(380).row();
        names.add(myRankLabel).right();
        Table me = new Table();
        me.add(names).right().padRight(18);
        myAvatarCell = me.add((Actor) UiKit.avatar(assetStore, myName, 72, UiKit.GOLD, true, true)).size(72, 72);
        tappable(me, this::showMyProfile);

        // Your points, in the middle, as on the game screen
        balanceLabel = GameText.label(assetStore.buttonFont, pointsText());
        balanceLabel.setColor(UiKit.GOLD);
        Table balance = new Table();
        balance.setBackground(UiKit.pill());
        balance.add(balanceLabel);

        Table left = new Table();
        left.add(backIcon).size(88, 88).padRight(26);
        left.add(titles).left();
        Table right = new Table();
        right.add(me).right().padRight(36);
        right.add(share).right().height(76);

        Table bar = new Table();
        bar.add(left).left().expandX().uniformX();
        bar.add(balance).center();
        bar.add(right).right().expandX().uniformX();
        return bar;
    }

    private void shareApp() {
        commonUtilInterface.shareApp(
            "Play Piga21 against me! Download the game and challenge me online.", "Piga21 challenge");
    }

    // ---- Online players ----

    private Table buildPlayersPanel() {
        Image liveDot = new Image(UiKit.disc("liveDot", UiKit.ONLINE, null, 0));
        liveDot.setOrigin(Align.center);
        liveDot.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.35f, 0.9f), Actions.alpha(1f, 0.9f))));

        Table header = new Table();
        header.add(liveDot).size(18, 18).padRight(14);
        header.add(heading("ONLINE NOW")).left().expandX();

        playersContent.top();
        playersContent.add(soft("Looking for players...")).padTop(80);
        Table panel = panel(header, "Tap a player to challenge them for their stake", playersContent);
        // Anyone, online list or not, can be asked by their player ID
        panel.row();
        panel.add(UiKit.button(assetStore, "PAIR WITH PLAYER", UiKit.Style.SMALL, this::openPairDialog))
            .expandX().fillX().height(84).padTop(18);
        return panel;
    }

    // ---- Your stake ----

    /**
     * The stake others challenge you for, as a chip with its amount.
     */
    private Table buildStakeCard() {
        Label caption = GameText.label(assetStore.captionFont, "Y O U R   S T A K E");
        caption.setColor(UiKit.GOLD);

        // Just the coin, floating on a soft gold halo
        TextureRegion coinRegion = ChipBadge.coinRegion(assetStore, ChipBadge.selectedKey());
        float coinWidth = 170, coinHeight = coinWidth * coinRegion.getRegionHeight() / coinRegion.getRegionWidth();
        Image coin = new Image(coinRegion);
        Table coinBox = new Table();
        coinBox.add(coin).size(coinWidth, coinHeight);
        coin.addAction(Actions.forever(Actions.sequence(
            Actions.moveBy(0, 6, 1.6f, Interpolation.sine), Actions.moveBy(0, -6, 1.6f, Interpolation.sine))));
        Table halo = new Table();
        halo.add(new Image(UiKit.glow("chipGlow", new Color(0.96f, 0.7f, 0.19f, 0.4f)))).size(380, 300);
        Stack chip = new Stack();
        chip.add(halo);
        chip.add(coinBox);

        Label stakeValue = GameText.label(assetStore.valueFont, String.format(Locale.US, "%,d", myStake));
        stakeValue.setColor(UiKit.GOLD);
        Label stakeUnit = GameText.label(assetStore.bodyFont, "points");
        stakeUnit.setColor(UiKit.SOFT);
        Table stake = new Table();
        stake.add(stakeValue).bottom();
        stake.add(stakeUnit).bottom().padLeft(12).padBottom(8);

        Label explain = GameText.label(assetStore.captionFont, String.format(Locale.US,
            "Players challenge you for this  ·  %,d service points a game", ChipBadge.feeFor(myStake)));
        explain.setColor(UiKit.SOFT);

        Table card = new Table();
        card.setBackground(UiKit.heroCard());
        card.add(caption).padTop(-6).row();
        card.add().expandY().row();
        card.add(chip).height(210).row();
        card.add(stake).padTop(-14).row();
        card.add(explain).padTop(2).row();
        card.add().expandY().row();
        return card;
    }

    // ---- Weekly bonus ----

    /**
     * What this week's top five on the leaderboard win: place and amount, one row each. The
     * amounts come with the leaderboard, so the dashboard can change them.
     */
    private Table buildWeeklyBonusCard() {
        Label title = heading("WEEKLY BONUS");
        Label position = GameText.label(assetStore.captionFont, "POSITION");
        position.setColor(UiKit.GOLD);
        Label amount = GameText.label(assetStore.captionFont, "AMOUNT");
        amount.setColor(UiKit.GOLD);

        Table rows = new Table();
        rows.add(position).left().expandX();
        rows.add(amount).right().row();
        for (int i = 0; i < BONUS_PLACES; i++) {
            Label place = GameText.label(assetStore.bodyFont, String.valueOf(i + 1));
            place.setColor(i == 0 ? UiKit.GOLD : UiKit.INK);
            bonusLabels[i] = GameText.label(assetStore.bodyFont, "...");
            bonusLabels[i].setColor(i == 0 ? UiKit.GOLD : UiKit.INK);
            rows.add(new Image(UiKit.fadeRule("bonusRule", new Color(0.96f, 0.55f, 0.15f, 0.45f)))).colspan(2).expandX().fillX().height(2).padTop(8).padBottom(8).row();
            rows.add(place).left().expandX();
            rows.add(bonusLabels[i]).right().row();
        }

        Table card = new Table();
        card.setBackground(UiKit.surface());
        card.add(title).left().row();
        card.add(rows).expandX().fillX().padTop(14);
        return card;
    }

    private void updateWeeklyBonus(JsonArray amounts) {
        for (int i = 0; i < BONUS_PLACES; i++) {
            int amount = i < amounts.size() ? amounts.get(i).getAsInt() : 0;
            bonusLabels[i].setText(amount > 0 ? String.format(Locale.US, "%,d", amount) : "--");
        }
    }

    // ---- Leaderboard ----

    private Table buildLeaderboardPanel() {
        Label week = GameText.label(assetStore.captionFont, "THIS WEEK");
        week.setColor(UiKit.GOLD);
        Table weekTag = new Table();
        weekTag.setBackground(UiKit.tag("week", UiKit.GOLD));
        weekTag.add(week);

        Table header = new Table();
        header.add(new Image(goldTR)).size(34, 37).padRight(14);
        header.add(heading("LEADERBOARD")).left().expandX();
        header.add(weekTag).right();

        leaderboardContent.top();
        leaderboardContent.add(soft("Loading...")).padTop(80);
        return panel(header, "Most games won since Monday", leaderboardContent);
    }

    // ---- Shared pieces ----

    /**
     * A dark surface with a header, a one-line explanation and a scrolling list.
     */
    private Table panel(Table header, String explanation, Table content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, true);

        Label line = GameText.label(assetStore.captionFont, explanation);
        line.setColor(UiKit.SOFT);

        Table panel = new Table();
        panel.setBackground(UiKit.surface());
        panel.top();
        panel.add(header).expandX().fillX().row();
        panel.add(line).left().padTop(4).padBottom(20).row();
        panel.add(scroll).expand().fill().top();
        return panel;
    }

    private Label heading(String text) {
        Label label = GameText.label(assetStore.smallButtonFont, text);
        label.setColor(UiKit.INK);
        return label;
    }

    private Label soft(String text) {
        Label label = GameText.label(assetStore.bodyFont, text);
        label.setColor(UiKit.SOFT);
        label.setAlignment(Align.center);
        return label;
    }

    private String pointsText() {
        WalletClient wallet = WalletClient.get();
        return wallet.isLoaded() ? String.format(Locale.US, "POINTS  %,d", wallet.getBalance()) : "POINTS  ...";
    }

    /**
     * Grows a little while pressed, like the rest of the game's buttons.
     */
    private void tappable(Actor actor, Runnable onTap) {
        actor.setTouchable(Touchable.enabled);
        if (actor instanceof Group group) group.setTransform(true);
        actor.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                actor.setOrigin(Align.center);
                actor.addAction(Actions.scaleTo(1.05f, 1.05f, 0.08f));
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                actor.addAction(Actions.scaleTo(1f, 1f, 0.08f));
                super.touchUp(event, x, y, pointer, button);
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                onTap.run();
            }
        });
    }

    /**
     * A list row: sinks slightly and lights up while pressed, so it's clear it was tapped.
     */
    private void pressable(Table row, Drawable normal, Runnable onTap) {
        row.setBackground(normal);
        row.setTouchable(Touchable.enabled);
        row.setTransform(true);
        row.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                row.setOrigin(Align.center);
                row.setBackground(UiKit.tilePressed());
                row.addAction(Actions.scaleTo(0.98f, 0.98f, 0.06f));
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                row.setBackground(normal);
                row.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                super.touchUp(event, x, y, pointer, button);
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                onTap.run();
            }
        });
    }

    private void listenToServer() {
        webSocketHandler.setMessageListener(message -> Gdx.app.postRunnable(() -> {
            Gdx.app.log("OnlinePlayersScreen", "Received: " + message);
            try {
                handleServerMessage(message, JsonParser.parseString(message).getAsJsonObject());
            } catch (Exception e) {
                Gdx.app.error("OnlinePlayersScreen", "Failed to handle message: " + message, e);
            }
        }));
    }

    private void handleServerMessage(String raw, JsonObject message) {
        // Points change when a game takes or pays a stake
        if (WalletClient.get().handle(message)) {
            balanceLabel.setText(pointsText());
            return;
        }
        if (message.has("status") && "error".equals(message.get("status").getAsString())) {
            String error = message.get("message").getAsString();
            // A request by player ID that failed: say so in the pair dialog, where they can fix the ID
            if (pairDialog != null && pairDialog.isOpen()) {
                pairDialog.showError(error);
            } else {
                commonUtilInterface.showToast(error);
            }
            return;
        }

        String action = message.has("action") ? message.get("action").getAsString() : "";

        switch (action) {
            case "online_players" -> populatePlayersList(message.getAsJsonArray("players"));
            case "player_profile" -> showPlayerProfile(message);
            case "leaderboard" -> {
                populateLeaderboard(message.getAsJsonArray("players"),
                    message.has("me") && message.get("me").isJsonObject() ? message.getAsJsonObject("me") : null);
                if (message.has("weekly_bonus") && message.get("weekly_bonus").isJsonArray()) {
                    updateWeeklyBonus(message.getAsJsonArray("weekly_bonus"));
                }
            }
            case "game_request" -> {
                closePairDialog(); // someone challenged us while we were typing an ID
                pendingRequestId = message.get("request_id").getAsString();
                String fromName = message.has("from_name") ? message.get("from_name").getAsString() : "A player";
                int amount = message.has("amount") ? message.get("amount").getAsInt() : 0;
                int fee = message.has("fee") ? message.get("fee").getAsInt() : ChipBadge.feeFor(amount);
                boolean rematch = message.has("rematch") && message.get("rematch").getAsBoolean();
                acceptGameRequestDialog(fromName, rematch ? fromName + " wants a rematch!" : fromName + " challenges you!", String.format(Locale.US, "Stake: %,d points  ·  Service points: %,d", amount, fee));
            }
            case "request_sent" -> {
                outgoingRequestId = message.get("request_id").getAsString();
                if (challengedName == null && message.has("to_name")) {
                    challengedName = message.get("to_name").getAsString(); // asked by player ID
                }
                if (pairDialog != null) {
                    pairDialog.dismiss();
                    pairDialog = null;
                }
                sendGameRequestDialog();
            }
            case "request_declined" -> closeRequestDialog("Your challenge was declined");
            case "request_expired" -> closeRequestDialog("The challenge expired");
            case "request_canceled" -> closeRequestDialog("The challenge was canceled");
            case "game_started" -> {
                closeRequestDialog(null);
                ((Piga21Game) game).pushScreen(new TwoPlayerGamePlayScreen(game, assetStore, extendViewport, webSocketHandler, commonUtilInterface, raw));
            }
            default -> { }
        }
    }

    /**
     * Server-ranked board: games won this week, ties going to whoever got there first. One row
     * per player, the top three with their medals; you're added underneath if not listed.
     */
    private void populateLeaderboard(JsonArray players, JsonObject me) {
        leaderboardContent.clearChildren();
        int myId = Util.getUserId();
        updateMe(me);

        if (players.isEmpty()) {
            leaderboardContent.add(new Image(goldTR)).size(70, 76).padTop(70).row();
            leaderboardContent.add(soft("No wins yet this week")).padTop(18).row();
            Label hint = GameText.label(assetStore.captionFont, "Win a game to take first place");
            hint.setColor(UiKit.SOFT);
            leaderboardContent.add(hint).padTop(6).row();
            return;
        }

        boolean meListed = false;
        for (int i = 0; i < players.size(); i++) {
            JsonObject player = players.get(i).getAsJsonObject();
            boolean isMe = player.get("id").getAsInt() == myId;
            meListed |= isMe;
            addLeaderboardRow(player, isMe);
        }
        if (!meListed && me != null && !me.get("rank").isJsonNull()) {
            addLeaderboardRow(me, true);
        }
    }

    /**
     * Your name and weekly rank on the stake card, from the leaderboard's "me".
     */
    private void updateMe(JsonObject me) {
        if (me == null) return;
        // The board's name is the nickname if there is one, as other players see it
        String name = me.has("name") && !me.get("name").isJsonNull() ? me.get("name").getAsString() : myNameLabel.getText().toString();
        myNameLabel.setText(name);
        myAvatarCell.setActor(UiKit.avatar(assetStore, name, 72, UiKit.GOLD, true, true));
        boolean ranked = me.has("rank") && !me.get("rank").isJsonNull();
        int wins = me.has("week_wins") ? me.get("week_wins").getAsInt() : 0;
        myRankLabel.setText(ranked
            ? String.format(Locale.US, "Rank #%d this week  ·  %d %s", me.get("rank").getAsInt(), wins, wins == 1 ? "win" : "wins")
            : "No wins yet this week");
    }

    private static Color medalColor(int rank) {
        return rank == 1 ? UiKit.GOLD : rank == 2 ? UiKit.SILVER : UiKit.BRONZE;
    }

    private void addLeaderboardRow(JsonObject player, boolean isMe) {
        int rank = player.get("rank").getAsInt();
        String name = player.get("name").getAsString();
        boolean medalled = rank <= 3;
        Color color = medalled ? medalColor(rank) : UiKit.SOFT;

        // Top three get their medal, everyone else their number
        Actor rankCell;
        if (medalled) {
            rankCell = new Image(rank == 1 ? goldTR : rank == 2 ? silverTR : bronzeTR);
        } else {
            Label number = GameText.label(assetStore.buttonFont, "#" + rank);
            number.setColor(isMe ? UiKit.GOLD : UiKit.SOFT);
            number.setAlignment(Align.center);
            rankCell = number;
        }

        Label nameLabel = GameText.label(assetStore.bodyFont, name);
        nameLabel.setColor(isMe ? UiKit.GOLD : UiKit.INK);
        nameLabel.setEllipsis(true);

        Table row = new Table();
        Table rankBox = new Table();
        rankBox.add(rankCell).size(medalled ? 50 : 72, medalled ? 54 : 50);
        row.add(rankBox).width(72).padRight(14);
        row.add(UiKit.avatar(assetStore, name, 68, isMe ? UiKit.GOLD : color, isMe, false)).size(68, 68).padRight(18);
        row.add(nameLabel).left().expandX().fillX().minWidth(0);
        if (isMe) {
            Label you = GameText.label(assetStore.captionFont, "YOU");
            you.setColor(UiKit.GOLD);
            Table tag = new Table();
            tag.setBackground(UiKit.tag("you", UiKit.GOLD));
            tag.add(you);
            row.add(tag).padLeft(10).padRight(6);
        }
        int playerId = player.get("id").getAsInt();
        pressable(row, isMe ? UiKit.tileMe() : UiKit.tile(), () -> openProfile(playerId, isMe));
        leaderboardContent.add(row).expandX().fillX().height(104).padBottom(12).row();
    }

    private void openProfile(int playerId, boolean isMe) {
        if (isMe) {
            showMyProfile();
        } else {
            requestPlayerProfile(playerId);
        }
    }

    /**
     * Your own profile comes from the API (it includes your email, which other players never get).
     */
    private void showMyProfile() {
        PlayerApi.fetchProfile(new PlayerApi.Callback() {
            @Override
            public void onSuccess(JsonObject player) {
                ProfileDialog.show(stage, assetStore, commonUtilInterface, player, true, null, null);
            }

            @Override
            public void onFailure(String message) {
                commonUtilInterface.showToast("Couldn't load your profile: " + PlayerApi.readableMessage(message));
            }
        });
    }

    private void requestPlayerProfile(int playerId) {
        if (!webSocketHandler.isConnected()) {
            commonUtilInterface.showToast("Not connected to the game server");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("action", "player_profile");
        data.put("player_id", playerId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
    }

    private void showPlayerProfile(JsonObject message) {
        JsonObject player = message.getAsJsonObject("player");
        boolean online = message.get("online").getAsBoolean();
        boolean inGame = message.get("in_game").getAsBoolean();
        int playerId = player.get("id").getAsInt();

        String status = inGame ? "In a game" : online ? "Online" : "Offline";
        // Only someone online and free can be challenged
        int stake = message.has("stake") ? message.get("stake").getAsInt() : ChipBadge.amountOf(ChipBadge.KEYS[0]);
        Runnable challenge = online && !inGame ? () -> challenge(playerId, ProfileDialog.displayName(player), stake) : null;
        ProfileDialog.show(stage, assetStore, commonUtilInterface, player, false, status, challenge);
    }

    private void requestLeaderboard() {
        if (!webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "leaderboard");
        webSocketHandler.sendMessage(new Gson().toJson(data));
    }

    private void closeRequestDialog(String toast) {
        pendingRequestId = null;
        outgoingRequestId = null;
        if (overlayShowing) {
            CommonGameFunctions.hideOverlayScreen(true);
            overlayShowing = false;
        }
        if (toast != null) {
            commonUtilInterface.showToast(toast);
        }
    }

    private void startPollingOnlinePlayers() {
        stopPollingOnlinePlayers();
        pollingTask = Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                getOnlinePlayers();
            }
        }, 0, 5); // Delay 0, repeat every 5 seconds
    }

    private void stopPollingOnlinePlayers() {
        if (pollingTask != null) {
            pollingTask.cancel();
        }
    }

    private void populatePlayersList(JsonArray playersArray) {
        playersContent.clearChildren();

        Map<String, Boolean> newKnownPlayers = new HashMap<>();
        int userId = Util.getUserId();

        for (int i = 0; i < playersArray.size(); i++) {
            JsonObject player = playersArray.get(i).getAsJsonObject();
            int playerId = player.get("id").getAsInt();
            if (playerId == userId) continue; // not ourselves

            String displayName = ProfileDialog.displayName(player);
            int betAmount = player.get("bet_amount").getAsInt();

            Label nameLabel = GameText.label(assetStore.bodyFont, displayName);
            nameLabel.setColor(UiKit.INK);
            nameLabel.setEllipsis(true);

            // Their chosen stake: what a challenge to them is played for
            Label stakeAmount = GameText.label(assetStore.captionFont, String.format(Locale.US, "%,d", betAmount));
            stakeAmount.setFontScale(1.15f);
            stakeAmount.setColor(UiKit.GOLD);

            // A right-pointing chevron in its own column, clear of the amount and the row's edge
            Image go = new Image(UiKit.chevronRight(UiKit.GOLD));
            Table arrow = new Table();
            arrow.add(go).size(26, 26);

            Table row = new Table();
            row.add(UiKit.avatar(assetStore, displayName, 76, UiKit.GOLD, false, true)).size(76, 76).padRight(20);
            row.add(nameLabel).left().expandX().fillX().minWidth(0);
            row.add(stakeAmount).right().padLeft(16);
            row.add(arrow).width(26).padLeft(28).padRight(14);
            pressable(row, UiKit.tile(), () -> challenge(playerId, displayName, betAmount));

            // Fade in players who just came online
            if (!knownPlayers.containsKey(displayName)) {
                row.getColor().a = 0f;
                row.addAction(Actions.fadeIn(0.5f));
            }

            playersContent.add(row).expandX().fillX().height(118).padBottom(12).row();
            newKnownPlayers.put(displayName, true);
        }

        // The list can contain only us, which we skip, so check what was actually shown
        if (newKnownPlayers.isEmpty()) {
            Stack seat = new Stack();
            seat.add(new Image(UiKit.disc("emptySeat", new Color(1, 1, 1, 0.03f), new Color(1, 1, 1, 0.12f), 0.05f)));
            Label dash = GameText.label(assetStore.headingFont, "?");
            dash.setColor(new Color(1, 1, 1, 0.3f));
            dash.setAlignment(Align.center);
            seat.add(dash);
            playersContent.add(seat).size(110, 110).padTop(60).row();
            playersContent.add(soft("No one else is online right now")).padTop(22).row();
            Label hint = GameText.label(assetStore.captionFont, "Share Piga21 and challenge your friends");
            hint.setColor(UiKit.SOFT);
            playersContent.add(hint).padTop(6).row();
            playersContent.add(UiKit.button(assetStore, "SHARE PIGA21", UiKit.Style.SMALL, this::shareApp)).height(76).padTop(28).row();
        }
        knownPlayers.clear();
        knownPlayers.putAll(newKnownPlayers);
    }

    private void acceptGameRequestDialog(String fromName, String title, String detail) {
        timeCountLabel = GameText.label(assetStore.headingFont, String.valueOf(Util.CHALLENGE_SECONDS));
        timeCountLabel.setColor(UiKit.GOLD);

        Table buttons = new Table();
        buttons.add(UiKit.button(assetStore, "DECLINE", UiKit.Style.SECONDARY, () -> {
            declineInitiatedGame();
            closeRequestDialog(null);
        })).width(300).height(100).padRight(40);
        buttons.add(UiKit.button(assetStore, "ACCEPT", UiKit.Style.PRIMARY, this::acceptInitiatedGame)).width(300).height(100);

        showRequestPanel(fromName, title, detail, buttons);

        // Not accepted in time counts as a decline
        final String requestId = pendingRequestId;
        startCountdown(timeCountLabel, () -> {
            if (requestId != null && requestId.equals(pendingRequestId)) {
                declineInitiatedGame();
                closeRequestDialog(null);
            }
        });

    }

    /**
     * Challenge dialogs share one look: the other player's avatar, title, detail line, a
     * countdown in a ring, and the buttons.
     */
    private void showRequestPanel(String otherName, String title, String detail, Table buttons) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.8f);

        Stack countdown = new Stack();
        countdown.add(new Image(UiKit.disc("countdownRing", new Color(0, 0, 0, 0.3f), UiKit.GOLD, 0.07f)));
        timeCountLabel.setAlignment(Align.center);
        countdown.add(timeCountLabel);

        Label titleLabel = GameText.label(assetStore.headingFont, title);
        titleLabel.setColor(UiKit.INK);
        Label detailLabel = GameText.label(assetStore.bodyFont, detail);
        detailLabel.setColor(UiKit.SOFT);

        Table panel = new Table();
        panel.setBackground(UiKit.heroCard());
        panel.add(UiKit.avatar(assetStore, otherName, 112, UiKit.GOLD, false, true)).size(112, 112).padBottom(18).row();
        panel.add(titleLabel).padBottom(6).row();
        panel.add(detailLabel).padBottom(24).row();
        panel.add(countdown).size(104, 104).padBottom(30).row();
        panel.add(buttons).row();
        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() / 2 - panel.getHeight() / 2);

        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();
        overlayShowing = true;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        // Also re-claims the socket when coming back from a game
        listenToServer();
        requestLeaderboard();
        WalletClient.get().refresh();

        // Check for internet connection
        if (!commonUtilInterface.isNetworkAvailable()) {
            commonUtilInterface.showToast("You are offline");
        }
        startPollingOnlinePlayers(); // Ensure polling restarts when screen is shown
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public boolean onBack() {
        if (pairDialog != null && pairDialog.isOpen()) {
            closePairDialog();
            return true;
        }
        // Back on a challenge dialog closes it (declining an incoming one) instead of leaving
        if (overlayShowing) {
            declineInitiatedGame();   // an incoming challenge
            cancelOutgoingRequest();  // or one we sent
            closeRequestDialog(null);
            return true;
        }
        return false;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        stopPollingOnlinePlayers(); // Stop polling when screen is hidden
    }

    @Override
    public void dispose() {
        stopPollingOnlinePlayers(); // Stop polling when screen is hidden
        stage.dispose();
    }

    private void sendGameRequestDialog() {
        timeCountLabel = GameText.label(assetStore.headingFont, String.valueOf(Util.CHALLENGE_SECONDS));
        timeCountLabel.setColor(UiKit.GOLD);

        Table buttons = new Table();
        buttons.add(UiKit.button(assetStore, "CANCEL", UiKit.Style.SECONDARY, () -> {
            cancelOutgoingRequest();
            closeRequestDialog(null);
        })).width(300).height(100);

        String who = challengedName != null ? challengedName : "your opponent";
        showRequestPanel(who, "CHALLENGE SENT", "Waiting for " + who + " to accept...", buttons);

        // No answer in time: withdraw the challenge (only if this dialog's challenge is still the open one)
        final String requestId = outgoingRequestId;
        startCountdown(timeCountLabel, () -> {
            if (requestId != null && requestId.equals(outgoingRequestId)) {
                cancelOutgoingRequest();
                closeRequestDialog(challengedName + " didn't answer");
            }
        });
    }

    /**
     * Challenge a player for the stake they chose, if we have the points for it and its service fee.
     */
    private void challenge(int opponentId, String opponentName, int stake) {
        WalletClient wallet = WalletClient.get();
        if (wallet.isLoaded() && wallet.getBalance() < stake + ChipBadge.feeFor(stake)) {
            commonUtilInterface.showToast("You don't have enough points to request a game");
            return;
        }
        initiateGameRequest(opponentId, opponentName, stake);
    }

    private void openPairDialog() {
        if (!webSocketHandler.isConnected()) {
            commonUtilInterface.showToast("Not connected to the game server");
            return;
        }
        pairDialog = PairPlayerDialog.show(stage, assetStore, this::requestGameByCode);
    }

    private void closePairDialog() {
        if (pairDialog != null) {
            pairDialog.close();
            pairDialog = null;
        }
    }

    /**
     * Ask whoever has this player ID for a game, for the stake they chose. The server finds the
     * player and checks they're online and both have the points.
     */
    private void requestGameByCode(String playerCode) {
        if (!webSocketHandler.isConnected()) {
            pairDialog.showError("Not connected to the game server");
            return;
        }
        challengedName = null; // the server tells us who it is
        Map<String, Object> data = new HashMap<>();
        data.put("action", "request_game");
        data.put("player_code", playerCode);
        webSocketHandler.sendMessage(new Gson().toJson(data));
    }

    private void initiateGameRequest(int opponentId, String opponentName, int amount) {
        challengedName = opponentName;
        Map<String, Object> data = new HashMap<>();
        data.put("action", "request_game");
        data.put("opponent_id", opponentId);
        data.put("amount", amount);

        // Use Gson instead of LibGDX Json
        String jsonString = new Gson().toJson(data);

        if (!webSocketHandler.isConnected()) {
            return;
        }

        webSocketHandler.sendMessage(jsonString);
    }

    private void acceptInitiatedGame() {
        if (pendingRequestId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "accept_request");
        data.put("request_id", pendingRequestId);
        data.put("user_id", Util.getUserId());

        String jsonString = new Gson().toJson(data);
        Gdx.app.log("GameScreen", "Sending accept request: " + jsonString);

        if (webSocketHandler.isConnected()) {
            webSocketHandler.sendMessage(jsonString);
        }
    }

    private void cancelOutgoingRequest() {
        if (outgoingRequestId == null || !webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "cancel_request");
        data.put("request_id", outgoingRequestId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
        outgoingRequestId = null;
    }

    private void declineInitiatedGame() {
        if (pendingRequestId == null || !webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "decline_request");
        data.put("request_id", pendingRequestId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
    }

    private void getOnlinePlayers() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "list_players");
        String jsonString = new Gson().toJson(data);
        if (webSocketHandler.isConnected()) {
            webSocketHandler.sendMessage(jsonString);
        }
    }
}
