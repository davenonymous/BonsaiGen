#pragma once

#include "encode.hpp"
#include "options.hpp"
#include "scene.hpp"
#include "texture.hpp"

#include <memory>
#include <string>
#include <vector>

namespace ctg {

// Owns the headless GL context (SDL offscreen driver, with a direct EGL
// surfaceless fallback) and an offscreen framebuffer sized canvas*supersample.
class Renderer {
public:
    Renderer();
    ~Renderer();
    Renderer(const Renderer&) = delete;
    Renderer& operator=(const Renderer&) = delete;

    bool init(const Options& options);
    std::string contextDescription() const;

    // Renders the full turntable and returns frames at final canvas size,
    // supersampling already resolved. Empty result signals a GL failure.
    std::vector<Frame> renderTurntable(const SceneMesh& mesh, TextureStore& textures,
                                       const Options& options);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace ctg
