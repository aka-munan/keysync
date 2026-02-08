package com.devoid.keysync;

import com.devoid.keysync.IJniInputCallback;

interface IInputReaderService {
    void destroy() =16777114 ;
    void exit() = 1;
    String run() =2 ;
    void registerCallback(IJniInputCallback callback) =3 ;
    int releaseActiveDevice()=4;
    int grabActiveDevice()=5;
}