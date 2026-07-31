#pragma once

#include <string>

namespace ctg::log {

enum class Level { Quiet = 0, Normal = 1, Verbose = 2 };

void setLevel(Level level);
Level level();

void info(const std::string& message);
void debug(const std::string& message);
void warn(const std::string& message);
// Emits each distinct message only once per process; returns true when it was new.
bool warnOnce(const std::string& message);
void error(const std::string& message);

} // namespace ctg::log
