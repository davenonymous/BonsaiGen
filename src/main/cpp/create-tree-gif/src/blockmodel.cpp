#include "blockmodel.hpp"

#include "log.hpp"
#include "nlohmann/json.hpp"

#include <algorithm>
#include <cmath>
#include <sstream>

using nlohmann::json;

namespace ctg {

namespace {

struct ResourceLocation {
    std::string ns;
    std::string path;
};

ResourceLocation parseLocation(const std::string& location) {
    auto colonPos = location.find(':');
    if (colonPos == std::string::npos) return {"minecraft", location};
    return {location.substr(0, colonPos), location.substr(colonPos + 1)};
}

std::optional<json> readJson(const AssetSource& assets, const std::string& innerPath) {
    auto bytes = assets.readBytes(innerPath);
    if (!bytes) return std::nullopt;
    json parsed = json::parse(bytes->begin(), bytes->end(), nullptr, false);
    if (parsed.is_discarded()) {
        log::warnOnce("malformed json: " + innerPath);
        return std::nullopt;
    }
    return parsed;
}

// Blockstate property values in `when` clauses may be JSON booleans or numbers
// while ref properties are always strings; compare through a string form.
std::string jsonScalarToString(const json& value) {
    if (value.is_boolean()) return value.get<bool>() ? "true" : "false";
    if (value.is_string()) return value.get<std::string>();
    return value.dump();
}

bool propertyMatches(const BlockProperties& properties, const std::string& key,
                     const std::string& alternatives) {
    auto found = properties.find(key);
    if (found == properties.end()) return false;

    std::stringstream stream(alternatives);
    std::string alternative;
    while (std::getline(stream, alternative, '|')) {
        if (found->second == alternative) return true;
    }
    return false;
}

// Variant keys look like "" or "axis=y" or "age=0,facing=south".
bool matchVariantKey(const std::string& key, const BlockProperties& properties) {
    if (key.empty()) return true;
    std::stringstream stream(key);
    std::string condition;
    while (std::getline(stream, condition, ',')) {
        auto eqPos = condition.find('=');
        if (eqPos == std::string::npos) return false;
        if (!propertyMatches(properties, condition.substr(0, eqPos), condition.substr(eqPos + 1)))
            return false;
    }
    return true;
}

bool matchWhen(const json& when, const BlockProperties& properties) {
    if (!when.is_object()) return false;
    if (when.contains("OR")) {
        for (const auto& clause : when["OR"]) {
            if (matchWhen(clause, properties)) return true;
        }
        return false;
    }
    if (when.contains("AND")) {
        for (const auto& clause : when["AND"]) {
            if (!matchWhen(clause, properties)) return false;
        }
        return true;
    }
    for (const auto& [key, value] : when.items()) {
        if (!propertyMatches(properties, key, jsonScalarToString(value))) return false;
    }
    return true;
}

int variantSpecificity(const std::string& key) {
    if (key.empty()) return 0;
    return 1 + static_cast<int>(std::count(key.begin(), key.end(), ','));
}

constexpr Vec3 kDirVectors[kDirCount] = {
    {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
};

// Corner positions per direction, wound clockwise seen from outside the cube
// (renderer uses glFrontFace(GL_CW)). Corner i takes uv slot i, where the uv
// slots run (u1,v1),(u2,v1),(u2,v2),(u1,v2) — texture v points down, matching
// both Minecraft's convention and stb_image row order.
constexpr float kCanonicalCorners[kDirCount][4][3] = {
    {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}}, // Down
    {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}}, // Up
    {{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}}, // North
    {{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}}, // South
    {{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}}, // West
    {{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}}, // East
};

using Mat3 = std::array<std::array<int, 3>, 3>;

constexpr Mat3 kIdentity = {{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}};
// 90-degree steps matching Minecraft blockstate rotation (x first, then y).
constexpr Mat3 kRotX90 = {{{1, 0, 0}, {0, 0, 1}, {0, -1, 0}}};
constexpr Mat3 kRotY90 = {{{0, 0, -1}, {0, 1, 0}, {1, 0, 0}}};

Mat3 multiply(const Mat3& a, const Mat3& b) {
    Mat3 result{};
    for (int row = 0; row < 3; ++row)
        for (int col = 0; col < 3; ++col)
            for (int k = 0; k < 3; ++k) result[row][col] += a[row][k] * b[k][col];
    return result;
}

Mat3 rotationFor(int degreesX, int degreesY) {
    Mat3 rotation = kIdentity;
    for (int step = 0; step < ((degreesX / 90) % 4 + 4) % 4; ++step)
        rotation = multiply(kRotX90, rotation);
    for (int step = 0; step < ((degreesY / 90) % 4 + 4) % 4; ++step)
        rotation = multiply(kRotY90, rotation);
    return rotation;
}

Vec3 rotateVector(const Mat3& m, const Vec3& v) {
    return {m[0][0] * v.x + m[0][1] * v.y + m[0][2] * v.z,
            m[1][0] * v.x + m[1][1] * v.y + m[1][2] * v.z,
            m[2][0] * v.x + m[2][1] * v.y + m[2][2] * v.z};
}

Dir dirFromVector(const Vec3& v) {
    for (int index = 0; index < kDirCount; ++index) {
        const Vec3& d = kDirVectors[index];
        if (std::abs(v.x - d.x) < 0.5f && std::abs(v.y - d.y) < 0.5f && std::abs(v.z - d.z) < 0.5f)
            return static_cast<Dir>(index);
    }
    return Dir::Up; // unreachable for 90-degree rotations
}

std::string resolveTextureRef(const std::map<std::string, std::string>& textures, std::string ref) {
    for (int hop = 0; hop < 16 && !ref.empty() && ref[0] == '#'; ++hop) {
        auto found = textures.find(ref.substr(1));
        if (found == textures.end()) return "";
        ref = found->second;
    }
    if (!ref.empty() && ref[0] == '#') return "";
    return ref;
}

bool isCutoutByName(const std::string& blockName) {
    static const char* kMarkers[] = {"leaves", "vine", "_fan", "cobweb", "firefly"};
    for (const char* marker : kMarkers) {
        if (blockName.find(marker) != std::string::npos) return true;
    }
    return false;
}

int dirIndexFromName(const std::string& name) {
    if (name == "down") return static_cast<int>(Dir::Down);
    if (name == "up") return static_cast<int>(Dir::Up);
    if (name == "north") return static_cast<int>(Dir::North);
    if (name == "south") return static_cast<int>(Dir::South);
    if (name == "west") return static_cast<int>(Dir::West);
    if (name == "east") return static_cast<int>(Dir::East);
    return -1;
}

FaceQuad makeCanonicalQuad(int dirIndex, const float uv[4], int uvRotation, int textureLayer,
                           const float tint[3]) {
    FaceQuad quad;
    quad.dir = static_cast<Dir>(dirIndex);
    quad.textureLayer = textureLayer;
    quad.tint[0] = tint[0];
    quad.tint[1] = tint[1];
    quad.tint[2] = tint[2];

    float u1 = uv[0] / 16.0f, v1 = uv[1] / 16.0f;
    float u2 = uv[2] / 16.0f, v2 = uv[3] / 16.0f;
    const float uvSlots[4][2] = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};

    int shift = ((uvRotation / 90) % 4 + 4) % 4;
    for (int corner = 0; corner < 4; ++corner) {
        quad.pos[corner] = {kCanonicalCorners[dirIndex][corner][0],
                            kCanonicalCorners[dirIndex][corner][1],
                            kCanonicalCorners[dirIndex][corner][2]};
        quad.uv[corner][0] = uvSlots[(corner + shift) % 4][0];
        quad.uv[corner][1] = uvSlots[(corner + shift) % 4][1];
    }
    return quad;
}

FaceQuad rotateQuad(const FaceQuad& quad, const Mat3& rotation) {
    FaceQuad rotated = quad;
    for (int corner = 0; corner < 4; ++corner) {
        Vec3 centered{quad.pos[corner].x - 0.5f, quad.pos[corner].y - 0.5f,
                      quad.pos[corner].z - 0.5f};
        Vec3 turned = rotateVector(rotation, centered);
        rotated.pos[corner] = {turned.x + 0.5f, turned.y + 0.5f, turned.z + 0.5f};
    }
    rotated.dir = dirFromVector(rotateVector(rotation, kDirVectors[static_cast<int>(quad.dir)]));
    return rotated;
}

std::string stateKey(const std::string& blockName, const BlockProperties& properties) {
    std::string key = blockName;
    for (const auto& [propertyName, value] : properties) {
        key += "|" + propertyName + "=" + value;
    }
    return key;
}

BlockModel makeCheckerboardCube() {
    BlockModel model;
    const float fullUv[4] = {0, 0, 16, 16};
    const float white[3] = {1, 1, 1};
    for (int dirIndex = 0; dirIndex < kDirCount; ++dirIndex) {
        model.faces[dirIndex] = {makeCanonicalQuad(dirIndex, fullUv, 0, 0, white)};
    }
    model.fullyOpaque = true;
    model.resolved = false;
    return model;
}

} // namespace

BlockModelResolver::BlockModelResolver(const AssetSource& assets, TextureStore& textures,
                                       const std::map<std::string, std::uint32_t>& tintOverrides,
                                       bool cullLeaves)
    : assets_(assets), textures_(textures), tintOverrides_(tintOverrides), cullLeaves_(cullLeaves) {}

const BlockModel& BlockModelResolver::resolve(const std::string& blockName,
                                              const BlockProperties& properties) {
    std::string key = stateKey(blockName, properties);
    auto found = cache_.find(key);
    if (found != cache_.end()) return found->second;

    BlockModel model;
    try {
        model = resolveUncached(blockName, properties);
    } catch (const std::exception& e) {
        // Malformed mod data must cost one block, not the whole batch.
        log::warnOnce("failed to resolve " + key + ": " + e.what());
        model = makeCheckerboardCube();
    }
    if (model.resolved) {
        ++resolvedStateCount_;
    } else {
        unresolvedStates_.insert(key);
    }
    return cache_.emplace(std::move(key), std::move(model)).first->second;
}

const BlockModelResolver::ModelData& BlockModelResolver::loadModelChain(
    const std::string& modelLocation) {
    auto found = modelCache_.find(modelLocation);
    if (found != modelCache_.end()) return found->second;

    // Insert first to act as a cycle guard: a recursive hit sees an empty entry.
    ModelData& data = modelCache_[modelLocation];

    ResourceLocation location = parseLocation(modelLocation);
    auto modelJson = readJson(assets_, "assets/" + location.ns + "/models/" + location.path + ".json");
    if (!modelJson) {
        log::warnOnce("missing model: " + modelLocation);
        return data;
    }
    data.loaded = true;

    if (modelJson->contains("parent") && (*modelJson)["parent"].is_string()) {
        const ModelData parent = loadModelChain((*modelJson)["parent"].get<std::string>());
        data.textures = parent.textures;
        data.faces = parent.faces;
    }
    const json ownTextures = modelJson->value("textures", json::object());
    for (const auto& [textureName, value] : ownTextures.items()) {
        if (value.is_string()) data.textures[textureName] = value.get<std::string>();
    }

    if (modelJson->contains("elements")) {
        data.faces.clear(); // elements replace any inherited geometry entirely
        for (const auto& element : (*modelJson)["elements"]) {
            const json faces = element.value("faces", json::object());
            for (const auto& [faceName, face] : faces.items()) {
                int dirIndex = dirIndexFromName(faceName);
                if (dirIndex < 0) continue;

                ModelData::Face parsed;
                parsed.textureRef = face.value("texture", "");
                if (face.contains("uv") && face["uv"].size() == 4) {
                    for (int i = 0; i < 4; ++i) parsed.uv[i] = face["uv"][i].get<float>();
                }
                parsed.rotation = face.value("rotation", 0);
                parsed.tintIndex = face.value("tintindex", -1);
                data.faces[dirIndex].push_back(parsed);
            }
        }
    }
    return data;
}

std::array<float, 3> BlockModelResolver::tintFor(const std::string& blockName) const {
    std::uint32_t rgb = 0x48B518; // plains foliage default
    if (blockName == "minecraft:spruce_leaves") rgb = 0x619961;
    else if (blockName == "minecraft:birch_leaves") rgb = 0x80A755;
    else if (blockName == "minecraft:mangrove_leaves") rgb = 0x92C648;

    auto override = tintOverrides_.find(blockName);
    if (override != tintOverrides_.end()) rgb = override->second;

    return {static_cast<float>((rgb >> 16) & 0xFF) / 255.0f,
            static_cast<float>((rgb >> 8) & 0xFF) / 255.0f,
            static_cast<float>(rgb & 0xFF) / 255.0f};
}

void BlockModelResolver::applyModel(BlockModel& out, const std::string& blockName,
                                    const std::string& modelLocation, int rotX, int rotY,
                                    bool& anyNonOpaqueTexture) {
    const ModelData& model = loadModelChain(modelLocation);
    Mat3 rotation = rotationFor(rotX, rotY);

    for (const auto& [dirIndex, layers] : model.faces) {
        std::vector<FaceQuad> stack;
        for (const ModelData::Face& face : layers) {
            std::string textureName = resolveTextureRef(model.textures, face.textureRef);
            if (textureName.empty()) continue;

            const Texture& texture = textures_.ensure(textureName);
            if (!texture.fullyOpaque) anyNonOpaqueTexture = true;
            std::array<float, 3> tint = {1, 1, 1};
            // Explicit --tint overrides also reach blocks whose tint comes from
            // mod code instead of a model tintindex (e.g. cobblemon leaves).
            if (face.tintIndex >= 0 || tintOverrides_.count(blockName)) tint = tintFor(blockName);

            FaceQuad quad = makeCanonicalQuad(dirIndex, face.uv, face.rotation,
                                              texture.layer, tint.data());
            stack.push_back(rotateQuad(quad, rotation));
        }
        if (stack.empty()) continue;

        // The whole stack rotates as one unit; earlier applies win per direction.
        int targetDir = static_cast<int>(stack.front().dir);
        if (out.faces[targetDir].empty()) out.faces[targetDir] = std::move(stack);
    }
}

BlockModel BlockModelResolver::resolveUncached(const std::string& blockName,
                                               const BlockProperties& properties) {
    ResourceLocation location = parseLocation(blockName);
    auto blockstate = readJson(assets_,
                               "assets/" + location.ns + "/blockstates/" + location.path + ".json");
    if (!blockstate) {
        log::warnOnce("no blockstate found for " + blockName);
        return makeCheckerboardCube();
    }

    struct Apply {
        std::string model;
        int rotX = 0;
        int rotY = 0;
    };
    std::vector<Apply> applies;

    auto parseApply = [](const json& value) -> Apply {
        const json* entry = &value;
        if (value.is_array()) {
            if (value.empty()) return {};
            entry = &value.front();
        }
        if (!entry->is_object()) return {};
        return {entry->value("model", ""), entry->value("x", 0), entry->value("y", 0)};
    };

    if (blockstate->contains("variants") && blockstate->at("variants").is_object()) {
        std::vector<std::pair<int, const json*>> candidates;
        for (const auto& [key, value] : blockstate->at("variants").items()) {
            if (matchVariantKey(key, properties)) {
                candidates.emplace_back(variantSpecificity(key), &value);
            }
        }
        if (candidates.empty()) {
            log::warnOnce("no matching variant for " + stateKey(blockName, properties));
            return makeCheckerboardCube();
        }
        auto best = std::max_element(candidates.begin(), candidates.end(),
                                     [](const auto& a, const auto& b) { return a.first < b.first; });
        applies.push_back(parseApply(*best->second));
    } else if (blockstate->contains("multipart") && blockstate->at("multipart").is_array()) {
        const json& multipart = blockstate->at("multipart");
        for (const auto& part : multipart) {
            if (!part.is_object() || !part.contains("apply")) continue;
            if (part.contains("when") && !matchWhen(part.at("when"), properties)) continue;
            applies.push_back(parseApply(part.at("apply")));
        }
        if (applies.empty() && !multipart.empty() && multipart.front().is_object() &&
            multipart.front().contains("apply")) {
            // e.g. a mushroom stem with every side flag false: keep some texture.
            applies.push_back(parseApply(multipart.front().at("apply")));
        }
    } else {
        log::warnOnce("blockstate has neither variants nor multipart: " + blockName);
        return makeCheckerboardCube();
    }

    BlockModel out;
    std::string particleTexture;
    int appliedModels = 0;
    bool anyNonOpaqueTexture = false;
    for (const Apply& apply : applies) {
        if (apply.model.empty()) continue;
        ++appliedModels;
        applyModel(out, blockName, apply.model, apply.rotX, apply.rotY, anyNonOpaqueTexture);
        if (particleTexture.empty()) {
            particleTexture = resolveTextureRef(loadModelChain(apply.model).textures, "#particle");
        }
    }
    if (appliedModels == 0) {
        log::warnOnce("no usable model for " + stateKey(blockName, properties));
        return makeCheckerboardCube();
    }

    // Fill directions not covered by element faces (cross/fan/vine models and
    // builtin/entity) with an already-present face's texture, else particle.
    int elementFaceCount = 0;
    const FaceQuad* donor = nullptr;
    for (const auto& stack : out.faces) {
        if (stack.empty()) continue;
        ++elementFaceCount;
        if (!donor) donor = &stack.front();
    }

    if (elementFaceCount < kDirCount) {
        int fillLayer;
        float fillTint[3] = {1, 1, 1};
        if (donor) {
            fillLayer = donor->textureLayer;
            fillTint[0] = donor->tint[0];
            fillTint[1] = donor->tint[1];
            fillTint[2] = donor->tint[2];
        } else if (!particleTexture.empty()) {
            fillLayer = textures_.ensure(particleTexture).layer;
        } else {
            log::warnOnce("no texture at all for " + stateKey(blockName, properties));
            return makeCheckerboardCube();
        }
        const float fullUv[4] = {0, 0, 16, 16};
        for (int dirIndex = 0; dirIndex < kDirCount; ++dirIndex) {
            if (out.faces[dirIndex].empty()) {
                out.faces[dirIndex] = {makeCanonicalQuad(dirIndex, fullUv, 0, fillLayer, fillTint)};
            }
        }
    }

    out.resolved = true;
    out.fullyOpaque = elementFaceCount == kDirCount && !anyNonOpaqueTexture &&
                      (cullLeaves_ || !isCutoutByName(blockName));
    return out;
}

} // namespace ctg
