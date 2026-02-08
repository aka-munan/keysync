//
// Created by munan on 02/02/26.
//
#include "InputReader.h"
#include <cstdio>
//#include <iostream>
//#include <cerrno>
#include <__fwd/string.h>
#include <sys/epoll.h>

#include <fcntl.h>
#include <unistd.h>

#include <linux/input.h>

#include "Mappings.h"

int InputReader::open_input_device(const char *path) {

    int fd = open(path, O_RDONLY | O_NONBLOCK);
    if (fd < 0) {
        perror("open input");
        return -1;
    }

    // Grab device so Android does NOT also receive keys
    if (grantpt(fd) < 0) {
        perror("failed to grab device");
    }

    return fd;
}

int grabDevice(int fd) {
    return ioctl(fd, EVIOCGRAB, 1);
}

int releaseDevice(int fd) {
    return ioctl(fd, EVIOCGRAB, 0);
}

int InputReader::grabActiveDevice() const {
    if (active_fd < 0) {
        return -1;
    }
    return grabDevice(active_fd);
}

int InputReader::releaseActiveDevice() const {
    if (active_fd < 0) {
        return -1;
    }
    return releaseDevice(active_fd);
}

int InputReader::setup_epoll(int fd) {
    int ep = epoll_create1(0);
    if (ep < 0) {
        perror("epoll_create");
        return -1;
    }

    struct epoll_event ev = {};
    ev.events = EPOLLIN;
    ev.data.fd = fd;

    epoll_ctl(ep, EPOLL_CTL_ADD, fd, &ev);
    return ep;
}

void InputReader::run(int fd, void (*onEventDown)(int), void (*onEventUp)(int)) {
    if (active_fd > 0) {
        close(active_fd);
    }
    active_fd = fd;
    int ep = setup_epoll(fd);
    if (ep < 0) {
        perror("Failed to setup epoll");
        return;
    }
    KeyLayoutMap keyLayoutMap;

    struct epoll_event ev{};
    struct input_event ie{};

    while (true) {
        epoll_wait(ep, &ev, 1, -1);

        while (read(fd, &ie, sizeof(ie)) > 0) {
            int androidKeyCode;
            keyLayoutMap.nativeToAndroidKeycode(ie.code, androidKeyCode);
            if (androidKeyCode == -1) {
                continue;
            }
            if (ie.type == EV_KEY && ie.value == 1) { // key down
                onEventDown(androidKeyCode);
//                __android_log_print(ANDROID_LOG_INFO, "native", "native key down: %d\n", ie.code);
            } else if (ie.type == EV_KEY && ie.value == 0) { // key up
                onEventUp(androidKeyCode);
//                __android_log_print(ANDROID_LOG_INFO, "native", "native key up: %d\n", ie.code);
            }
        }
    }
}
