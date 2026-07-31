#pragma once

#include "assets.hpp"
#include "texture.hpp"

#include <array>
#include <cstdint>
#include <map>
#include <optional>
#include <set>
#include <string>
#include <vector>

namespace ctg {

// -Y +Y -Z +Z -X +X, matching Minecraft's down/up/north/south/west/east.
enum class Dir : int { Down = 0, Up, North, South, West, East };
constexpr int kDirCount = 6;

struct Vec3 {
    float x = 0, y = 0, z = 0;
};

// One cube face in model space (unit cube). Vertices are wound clockwise when
// viewed from outside; the renderer pairs this with glFrontFace(GL_CW).
struct FaceQuad {
    Vec3 pos[4];
    float uv[4][2];       // normalized 0..1
    int textureLayer = 0;
    Dir dir = Dir::Up;
    float tint[3] = {1.0f, 1.0f, 1.0f};
};

// A direction may carry several coplanar layers (e.g. tinted leaves + untinted
// overlay); they are drawn in order, first layer winning via the depth test.
struct BlockModel {
    std::array<std::vector<FaceQuad>, kDirCount> faces;
    bool fullyOpaque = false;
    bool resolved = false; // false => blockstate/model lookup failed, checkerboard shown
};

using BlockProperties = std::map<std::string, std::string>;

// Resolves a blockstate (name + properties) to six textured cube faces by
// walking blockstates/<name>.json -> model parent chain -> element faces,
// applying variant x/y rotations to the geometry.
class BlockModelResolver {
public:
    BlockModelResolver(const AssetSource& assets, TextureStore& textures,
                       const std::map<std::string, std::uint32_t>& tintOverrides,
                       bool cullLeaves);

    const BlockModel& resolve(const std::string& blockName, const BlockProperties& properties);

    // Distinct block states that fell back to the checkerboard (for --report).
    const std::set<std::string>& unresolvedStates() const { return unresolvedStates_; }
    int resolvedStateCount() const { return resolvedStateCount_; }

private:
    struct ModelData {
        std::map<std::string, std::string> textures;
        // Element faces flattened to: direction -> (texture ref, uv, rotation, tintindex).
        struct Face {
            std::string textureRef;
            float uv[4] = {0, 0, 16, 16};
            int rotation = 0;
            int tintIndex = -1;
        };
        std::map<int, std::vector<Face>> faces; // Dir index -> layers in element order
        bool loaded = false;
    };

    const ModelData& loadModelChain(const std::string& modelLocation);
    BlockModel resolveUncached(const std::string& blockName, const BlockProperties& properties);
    void applyModel(BlockModel& out, const std::string& blockName,
                    const std::string& modelLocation, int rotX, int rotY,
                    bool& anyNonOpaqueTexture);
    std::array<float, 3> tintFor(const std::string& blockName) const;

    const AssetSource& assets_;
    TextureStore& textures_;
    std::map<std::string, std::uint32_t> tintOverrides_;
    bool cullLeaves_;

    std::map<std::string, BlockModel> cache_;        // key: name + canonical props
    std::map<std::string, ModelData> modelCache_;    // key: model resource location
    std::set<std::string> unresolvedStates_;
    int resolvedStateCount_ = 0;
};

} // namespace ctg
