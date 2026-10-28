package io.ngaigame.caasiremos.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import io.ngaigame.caasiremos.AssetStore;
import io.ngaigame.caasiremos.wallet.WalletClient;
import io.ngaigame.caasiremos.wallet.WalletTransaction;

/**
 * The cash wallet: balance, money on hold for withdrawals in progress, and every deposit and
 * withdrawal with its status. Updates live as the server reports changes.
 */
public class WalletHistoryDialog implements WalletClient.Listener {
    private static final Color SUCCESS = Color.valueOf("#5BD47A");

    private final Stage stage;
    private final AssetStore assetStore;
    private final WalletClient wallet = WalletClient.get();
    private OverlayContainer overlay;
    private Label balanceLabel, heldLabel, connectionLabel;
    private Table list;
    private ScrollPane scroll;

    private WalletHistoryDialog(Stage stage, AssetStore assetStore) {
        this.stage = stage;
        this.assetStore = assetStore;
    }

    public static void show(Stage stage, AssetStore assetStore) {
        new WalletHistoryDialog(stage, assetStore).build();
    }

    private void build() {
        overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);

        Table panel = new Table();
        panel.setBackground(UiKit.panel());

        balanceLabel = GameText.label(assetStore.dialogFont, "");
        heldLabel = GameText.label(assetStore.listFont, "");
        heldLabel.setColor(UiKit.MUTED);
        connectionLabel = GameText.label(assetStore.listFont, "");
        connectionLabel.setColor(UiKit.ERROR);

        Table header = new Table();
        Table titles = new Table();
        titles.add(GameText.title(assetStore, "CASH WALLET")).left().row();
        titles.add(balanceLabel).left().row();
        titles.add(heldLabel).left().row();
        header.add(titles).expandX().left();
        header.add(UiKit.button(assetStore, "DEPOSIT", UiKit.Style.PRIMARY, () -> WalletDialog.show(stage, assetStore, true))).width(260).height(90).padRight(20);
        header.add(UiKit.button(assetStore, "WITHDRAW", UiKit.Style.SECONDARY, () -> WalletDialog.show(stage, assetStore, false))).width(260).height(90);

        list = new Table();
        list.top();
        scroll = new ScrollPane(list);
        scroll.setScrollingDisabled(true, false);
        scroll.setFadeScrollBars(false);

        panel.add(header).width(1300).padBottom(16).row();
        panel.add(connectionLabel).padBottom(6).row();
        panel.add(scroll).width(1300).height(520).row();

        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() / 2 - panel.getHeight() / 2);
        overlay.addActor(panel);
        overlay.addActor(closeButton());
        CommonGameFunctions.showOverlay();

        wallet.addListener(this);
        render();
        // Always ask: what's on the phone may be stale (missed pushes while on another screen)
        wallet.refresh();
        panel.addAction(Actions.forever(Actions.sequence(Actions.delay(1f), Actions.run(() -> {
            if (!wallet.isConnected()) wallet.reconnect();
            updateConnection();
        }))));
    }

    private void render() {
        balanceLabel.setText(wallet.isLoaded() ? wallet.money(wallet.getBalance()) : "");
        heldLabel.setText(wallet.getReserved() > 0 ? wallet.money(wallet.getReserved()) + " on hold for withdrawals in progress" : "");
        updateConnection();

        float scrollY = scroll.getScrollY();
        list.clearChildren();
        if (!wallet.isLoaded()) {
            list.add(muted(wallet.isConnected() ? "Loading your wallet..." : "You're offline.")).padTop(120);
            return;
        }
        if (wallet.getTransactions().isEmpty()) {
            list.add(GameText.label(assetStore.messageFont, "No transactions yet")).padTop(110).row();
            list.add(muted("Deposit with mobile money to get started.")).padTop(10).row();
            return;
        }

        for (WalletTransaction transaction : wallet.getTransactions()) {
            list.add(row(transaction)).width(1260).height(96).padBottom(10).row();
        }
        if (wallet.hasMoreHistory()) {
            list.add(UiKit.button(assetStore, "LOAD MORE", UiKit.Style.SMALL, wallet::loadMoreHistory)).height(76).padTop(6).row();
        }
        list.layout();
        scroll.layout();
        scroll.setScrollY(scrollY);
    }

    private Table row(WalletTransaction transaction) {
        Table row = new Table();
        row.setBackground(UiKit.card());
        row.setTouchable(Touchable.enabled);

        Table left = new Table();
        left.add(GameText.label(assetStore.messageFont, transaction.isDeposit() ? "Deposit" : "Withdrawal")).left().row();
        left.add(muted(transaction.createdText() + "  ·  " + transaction.phone + "  ·  Ref " + transaction.shortReference())).left();

        boolean success = WalletTransaction.SUCCESSFUL.equals(transaction.status);
        boolean failed = WalletTransaction.FAILED.equals(transaction.status) || WalletTransaction.REVERSED.equals(transaction.status);
        Label amount = GameText.label(assetStore.messageFont, (transaction.isDeposit() ? "+ " : "- ") + wallet.money(transaction.amount));
        amount.setColor(failed ? UiKit.MUTED : transaction.isDeposit() ? SUCCESS : Color.WHITE);
        Label status = GameText.label(assetStore.listFont, transaction.statusText());
        status.setColor(success ? SUCCESS : failed ? UiKit.ERROR : UiKit.GOLD);

        Table right = new Table();
        right.add(amount).right().row();
        right.add(status).right();

        row.add(left).expandX().left().padLeft(14);
        row.add(right).right().padRight(14);
        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                WalletDialog.showTransaction(stage, assetStore, transaction);
            }
        });
        return row;
    }

    private void updateConnection() {
        connectionLabel.setText(wallet.isConnected() ? "" : "You're offline. Reconnecting... Balances may be out of date.");
    }

    @Override
    public void onWalletChanged() {
        if (!isAlive()) return;
        render();
    }

    @Override
    public void onRequestFailed(String requestId, String code, String message) {
        // A refused history/summary request (not a deposit or withdrawal: those have a request ID)
        if (isAlive() && requestId == null && message != null) {
            connectionLabel.setText(message);
        }
    }

    private boolean isAlive() {
        boolean alive = overlay.hasParent() && CommonGameFunctions.overlayContainer == overlay;
        if (!alive) wallet.removeListener(this);
        return alive;
    }

    private Label muted(String text) {
        Label label = GameText.label(assetStore.listFont, text);
        label.setColor(UiKit.MUTED);
        return label;
    }

    private Image closeButton() {
        Image close = new Image(assetStore.otherComponents.findRegion("exit"));
        close.setSize(110, 110);
        close.setPosition(stage.getWidth() - close.getWidth() - 40, stage.getHeight() - close.getHeight() - 40);
        close.setOrigin(close.getWidth() / 2, close.getHeight() / 2);
        close.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                close.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                close.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                wallet.removeListener(WalletHistoryDialog.this);
                CommonGameFunctions.hideOverlayScreen();
            }
        });
        return close;
    }
}
