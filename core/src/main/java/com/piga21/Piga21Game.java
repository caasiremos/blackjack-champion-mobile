package com.piga21;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.util.Stack;

import com.piga21.auth.GoogleSignInInterface;
import com.piga21.screens.SplashScreen;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.CommonGameFunctions;
import com.piga21.utils.CommonUtilInterface;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms.
 */
public class Piga21Game extends Game {
    AssetStore assetStore;
    ExtendViewport extendViewport;
    OrthographicCamera orthographicCamera;
    private final WebSocketHandler webSocketHandler;
    private final GoogleSignInInterface googleSignInInterface;
    private final CommonUtilInterface commonUtilInterface;
    private final Stack<Screen> screenStack = new Stack<>();

    private static final long EXIT_PRESS_WINDOW_MS = 2000;
    private long lastRootBackPress = 0;


    public Piga21Game(WebSocketHandler webSocketHandler, GoogleSignInInterface googleSignInInterface, CommonUtilInterface commonUtilInterface) {
        this.webSocketHandler = webSocketHandler;
        this.googleSignInInterface = googleSignInInterface;
        this.commonUtilInterface = commonUtilInterface;
    }


    @Override
    public void create() {
        // Handle Android back ourselves instead of letting it close the app on every screen
        Gdx.input.setCatchKey(Input.Keys.BACK, true);

        orthographicCamera = new OrthographicCamera();
        extendViewport = new ExtendViewport(1920, 1080, orthographicCamera);
        assetStore = new AssetStore();
        assetStore.loadAssets();
        setScreen(new SplashScreen(this, assetStore, extendViewport, webSocketHandler, commonUtilInterface));
    }

    public void pushScreen(Screen screen) {
        if (getScreen() != null) screenStack.push(getScreen());
        setScreen(screen);
    }

    @Override
    public void render() {
        // Checked here, before the screen renders, so every screen gets the same back behaviour
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            goBack();
        }
        super.render();
    }

    /**
     * What the back button does: let the current screen handle it first (close a dialog,
     * confirm leaving a game), otherwise go to the previous screen. On the main menu a
     * second press within two seconds exits.
     */
    public void goBack() {
        Screen current = getScreen();
        if (current instanceof BackHandler && ((BackHandler) current).onBack()) {
            return;
        }

        // Any other open dialog (e.g. the settings menu) closes before we leave the screen
        if (CommonGameFunctions.isOverlayShowing()) {
            CommonGameFunctions.hideOverlayScreen(true);
            return;
        }

        if (!screenStack.isEmpty()) {
            popScreen();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRootBackPress <= EXIT_PRESS_WINDOW_MS) {
            Gdx.app.exit();
        } else {
            lastRootBackPress = now;
            commonUtilInterface.showToast("Press back again to exit");
        }
    }

    /**
     * Swap the current screen for another without growing the back stack (e.g. a rematch table).
     */
    public void replaceScreen(Screen screen) {
        Screen leaving = getScreen();
        CommonGameFunctions.hideOverlayScreen(true);
        setScreen(screen);
        if (leaving != null) {
            Gdx.app.postRunnable(leaving::dispose);
        }
    }

    public void popScreen() {
        if (screenStack.isEmpty()) {
            Gdx.app.exit();
            return;
        }

        Screen leaving = getScreen();
        // An open dialog belongs to the screen we're leaving
        CommonGameFunctions.hideOverlayScreen(true);
        setScreen(screenStack.pop());

        // Nothing returns to a popped screen; free it once the current input event is done with its stage
        if (leaving != null) {
            Gdx.app.postRunnable(leaving::dispose);
        }
    }

    public GoogleSignInInterface getAuthInterface() {
        return googleSignInInterface;
    }
}
