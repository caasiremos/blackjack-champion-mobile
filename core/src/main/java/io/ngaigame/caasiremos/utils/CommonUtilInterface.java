package io.ngaigame.caasiremos.utils;

public interface CommonUtilInterface {
    void showToast(String message);
    void showLongToast(String message);
    void shareApp(String text, String subject);
    boolean isNetworkAvailable();
}
