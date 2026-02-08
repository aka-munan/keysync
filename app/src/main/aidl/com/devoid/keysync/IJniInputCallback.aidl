package com.devoid.keysync;

interface IJniInputCallback {
    void onKeyDown( int code);
    void onKeyUp(int code);
}