#include "quantize.hpp"

#include <algorithm>
#include <cstdlib>

namespace ctg {

namespace {

struct Cell {
    int r, g, b; // 5-bit components
    std::uint32_t count;
};

struct Box {
    std::vector<Cell> cells;
    int minR, maxR, minG, maxG, minB, maxB;
    std::uint64_t totalCount;

    void computeBounds() {
        minR = minG = minB = 32;
        maxR = maxG = maxB = -1;
        totalCount = 0;
        for (const Cell& cell : cells) {
            minR = std::min(minR, cell.r); maxR = std::max(maxR, cell.r);
            minG = std::min(minG, cell.g); maxG = std::max(maxG, cell.g);
            minB = std::min(minB, cell.b); maxB = std::max(maxB, cell.b);
            totalCount += cell.count;
        }
    }

    int longestAxisLength() const {
        return std::max({maxR - minR, maxG - minG, maxB - minB});
    }

    bool splittable() const { return cells.size() > 1 && longestAxisLength() > 0; }
};

std::uint8_t expand5To8(int value5) {
    return static_cast<std::uint8_t>((value5 << 3) | (value5 >> 2));
}

} // namespace

Palette buildPalette(const std::vector<Frame>& frames, int maxColors) {
    // The palette index must fit a byte alongside the transparent slot.
    maxColors = std::clamp(maxColors, 1, 255);
    std::vector<std::uint32_t> histogram(32768, 0);
    for (const Frame& frame : frames) {
        for (std::size_t i = 0; i < frame.rgba.size(); i += 4) {
            if (frame.rgba[i + 3] < 128) continue;
            ++histogram[rgb555(frame.rgba[i], frame.rgba[i + 1], frame.rgba[i + 2])];
        }
    }

    Box initial;
    for (int index = 0; index < 32768; ++index) {
        if (histogram[index] == 0) continue;
        initial.cells.push_back({(index >> 10) & 31, (index >> 5) & 31, index & 31,
                                 histogram[index]});
    }

    Palette palette;
    if (initial.cells.empty()) {
        // Fully transparent animation; one dummy color keeps the GIF valid.
        palette.colors.push_back({0, 0, 0});
        return palette;
    }
    initial.computeBounds();

    std::vector<Box> boxes;
    boxes.push_back(std::move(initial));

    while (static_cast<int>(boxes.size()) < maxColors) {
        // Split the box with the largest weighted extent.
        std::size_t bestIndex = boxes.size();
        std::uint64_t bestScore = 0;
        for (std::size_t index = 0; index < boxes.size(); ++index) {
            if (!boxes[index].splittable()) continue;
            std::uint64_t score = boxes[index].totalCount *
                                  static_cast<std::uint64_t>(boxes[index].longestAxisLength());
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        if (bestIndex == boxes.size()) break;

        Box box = std::move(boxes[bestIndex]);
        int lengthR = box.maxR - box.minR, lengthG = box.maxG - box.minG,
            lengthB = box.maxB - box.minB;
        auto key = [&](const Cell& cell) {
            if (lengthG >= lengthR && lengthG >= lengthB) return cell.g;
            if (lengthR >= lengthB) return cell.r;
            return cell.b;
        };
        std::sort(box.cells.begin(), box.cells.end(),
                  [&](const Cell& a, const Cell& b) { return key(a) < key(b); });

        std::uint64_t half = box.totalCount / 2;
        std::uint64_t accumulated = 0;
        std::size_t splitAt = 0;
        for (; splitAt + 1 < box.cells.size(); ++splitAt) {
            accumulated += box.cells[splitAt].count;
            if (accumulated >= half) break;
        }
        // Both halves must stay non-empty even when one cell dominates the count.
        splitAt = std::min(splitAt, box.cells.size() - 2);

        Box low, high;
        low.cells.assign(box.cells.begin(), box.cells.begin() + splitAt + 1);
        high.cells.assign(box.cells.begin() + splitAt + 1, box.cells.end());
        low.computeBounds();
        high.computeBounds();
        boxes[bestIndex] = std::move(low);
        boxes.push_back(std::move(high));
    }

    for (const Box& box : boxes) {
        std::uint64_t sumR = 0, sumG = 0, sumB = 0;
        for (const Cell& cell : box.cells) {
            sumR += static_cast<std::uint64_t>(cell.r) * cell.count;
            sumG += static_cast<std::uint64_t>(cell.g) * cell.count;
            sumB += static_cast<std::uint64_t>(cell.b) * cell.count;
        }
        palette.colors.push_back({expand5To8(static_cast<int>(sumR / box.totalCount)),
                                  expand5To8(static_cast<int>(sumG / box.totalCount)),
                                  expand5To8(static_cast<int>(sumB / box.totalCount))});
    }

    for (int index = 0; index < 32768; ++index) {
        int r = expand5To8((index >> 10) & 31);
        int g = expand5To8((index >> 5) & 31);
        int b = expand5To8(index & 31);
        int bestColor = 0;
        int bestDistance = 1 << 30;
        for (std::size_t color = 0; color < palette.colors.size(); ++color) {
            int dr = r - palette.colors[color][0];
            int dg = g - palette.colors[color][1];
            int db = b - palette.colors[color][2];
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestColor = static_cast<int>(color);
            }
        }
        palette.lookup[index] = static_cast<std::uint8_t>(bestColor);
    }
    return palette;
}

} // namespace ctg
