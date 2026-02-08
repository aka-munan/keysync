//
// Created by munan on 05/02/26.
//
#pragma

#ifndef KEY_SYNC_KEYBOARDDETECTOR_H

#include <string>
#include <linux/input.h>
#include <vector>

#define KEY_SYNC_KEYBOARDDETECTOR_H

struct KeyboardDeviceInfo {
    std::string path;
    std::string name;
    bool external;
    struct input_id id;
};

class KeyboardDetector {
private:
     bool isKeyboardDevice(int fd);
private:
    bool isExternalBus(int fd);
public:
    std::vector<KeyboardDeviceInfo> scanInputDevices();
};



#endif //KEY_SYNC_KEYBOARDDETECTOR_H
