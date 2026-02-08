//
// Created by munan on 05/02/26.
//

#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <linux/input.h>
#include <sys/ioctl.h>
#include <cstring>
#include <cstdio>
#include <vector>
#include <string>
#include "KeyboardDetector.h"


static bool testBit(const unsigned long *array, int bit) {
    return (array[bit / (sizeof(unsigned long) * 8)] >>
                                                     (bit % (sizeof(unsigned long) * 8))) & 1;
}


bool KeyboardDetector::isKeyboardDevice(int fd) {
    unsigned long evBits[(EV_MAX + 7) / 8] = {};
    unsigned long keyBits[(KEY_MAX + 7) / 8] = {};

    if (ioctl(fd, EVIOCGBIT(0, sizeof(evBits)), evBits) < 0)
        return false;

    if (!testBit(evBits, EV_KEY))
        return false;

    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBits)), keyBits) < 0)
        return false;

    // Must have alphabetical keys
    if (testBit(keyBits, KEY_A) && testBit(keyBits, KEY_Z))
        return true;

    return false;
}

bool KeyboardDetector::isExternalBus(int fd) {
    struct input_id id{};
    if (ioctl(fd, EVIOCGID, &id) < 0)
        return false;

    switch (id.bustype) {
        case BUS_USB:
        case BUS_BLUETOOTH:
        case BUS_I2C:
        case BUS_SPI:
            return true;
        default:
            return false;
    }
}

std::vector<KeyboardDeviceInfo> KeyboardDetector::scanInputDevices() {
    std::vector<KeyboardDeviceInfo> keyboards;

    DIR *dir = opendir("/dev/input");
    if (!dir) {
        perror("opendir");
        return keyboards;
    }


    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0)
            continue;

        std::string path = "/dev/input/";
        path += entry->d_name;

        int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0)
            continue;

        char name[256] = {};
        ioctl(fd, EVIOCGNAME(sizeof(name)), name);

        bool keyboard = isKeyboardDevice(fd);
        bool external = isExternalBus(fd);

        if (keyboard) {
            struct KeyboardDeviceInfo keyboardDeviceInfo;
            keyboardDeviceInfo.path = path;
            keyboardDeviceInfo.name = name;
            keyboardDeviceInfo.external = external;
            ioctl(fd, EVIOCGID, &keyboardDeviceInfo.id);
            keyboards.push_back(keyboardDeviceInfo);
        }

        close(fd);
    }
    closedir(dir);
    return keyboards;
}
