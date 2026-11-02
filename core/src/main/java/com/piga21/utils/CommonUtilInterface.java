package com.piga21.utils;

public interface CommonUtilInterface {
    void showToast(String message);
    void showLongToast(String message);
    void shareApp(String text, String subject);
    boolean isNetworkAvailable();
}
