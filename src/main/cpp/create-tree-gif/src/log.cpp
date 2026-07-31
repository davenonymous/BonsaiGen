#include "log.hpp"

#include <cstdio>
#include <mutex>
#include <unordered_set>

namespace ctg::log {

namespace {
Level g_level = Level::Normal;
std::unordered_set<std::string> g_seenWarnings;
std::mutex g_mutex;
} // namespace

void setLevel(Level level) { g_level = level; }
Level level() { return g_level; }

void info(const std::string& message) {
    if (g_level < Level::Normal) return;
    std::fprintf(stderr, "%s\n", message.c_str());
}

void debug(const std::string& message) {
    if (g_level < Level::Verbose) return;
    std::fprintf(stderr, "[debug] %s\n", message.c_str());
}

void warn(const std::string& message) {
    if (g_level < Level::Normal) return;
    std::fprintf(stderr, "[warn] %s\n", message.c_str());
}

bool warnOnce(const std::string& message) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_seenWarnings.insert(message).second) return false;
    warn(message);
    return true;
}

void error(const std::string& message) {
    std::fprintf(stderr, "[error] %s\n", message.c_str());
}

} // namespace ctg::log
