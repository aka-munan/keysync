//
// Created by munan on 02/02/26.
//
#pragma once

#ifndef KEY_SYNC_INPUTREADER_H

#include <unistd.h>

#define KEY_SYNC_INPUTREADER_H


struct KeyEntry {
    int androidKey;
    uint32_t flags;
};

class InputReader {
private:
    int active_fd = -1;
public:
    int grabActiveDevice() const;
public:
    int releaseActiveDevice() const;
public:
    int open_input_device(const char *path);

private:
    int setup_epoll(int fd);

public:
    void run(int fd, void (*onEventDown)(int), void (*onEventUp)(int));
};


#endif //KEY_SYNC_INPUTREADER_H