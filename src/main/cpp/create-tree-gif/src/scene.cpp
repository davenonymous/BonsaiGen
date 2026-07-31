#include "scene.hpp"

#include "log.hpp"
#include "nlohmann/json.hpp"

#include <cmath>
#include <fstream>

using nlohmann::json;

namespace ctg {

namespace {

// Minecraft-style directional diffuse, indexed by Dir.
constexpr float kFaceShade[kDirCount] = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};

constexpr int kDirOffsets[kDirCount][3] = {
    {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
};

struct RefBlock {
    std::string name;
    BlockProperties properties;
};

// The shape parsed into a validated rectangular grid: rows[x * sizeY + row],
// every row exactly sizeZ characters. All structural checks happen here so the
// mesh code below can index freely.
struct ShapeGrid {
    int sizeX = 0, sizeY = 0, sizeZ = 0;
    std::vector<std::string> rows;

    // '\0' = air. y is flipped: row 0 in the file is the TOP of the tree.
    char refKeyAt(int x, int y, int z) const {
        char key = rows[static_cast<std::size_t>(x) * sizeY + (sizeY - 1 - y)][z];
        return key == ' ' ? '\0' : key;
    }
};

std::optional<ShapeGrid> parseShape(const json& model, const std::string& path) {
    if (!model.contains("shape") || !model["shape"].is_array() || model["shape"].empty()) {
        log::error("model has no shape: " + path);
        return std::nullopt;
    }

    ShapeGrid grid;
    const json& shape = model["shape"];
    grid.sizeX = static_cast<int>(shape.size());
    for (const json& layer : shape) {
        if (!layer.is_array() || layer.empty()) {
            log::error("shape layer is not a non-empty array in " + path);
            return std::nullopt;
        }
        if (grid.sizeY == 0) grid.sizeY = static_cast<int>(layer.size());
        if (static_cast<int>(layer.size()) != grid.sizeY) {
            log::error("ragged shape (layer height differs) in " + path);
            return std::nullopt;
        }
        for (const json& row : layer) {
            if (!row.is_string()) {
                log::error("shape row is not a string in " + path);
                return std::nullopt;
            }
            std::string text = row.get<std::string>();
            if (grid.sizeZ == 0) grid.sizeZ = static_cast<int>(text.size());
            if (static_cast<int>(text.size()) != grid.sizeZ || grid.sizeZ == 0) {
                log::error("ragged shape (row width differs or empty) in " + path);
                return std::nullopt;
            }
            grid.rows.push_back(std::move(text));
        }
    }
    return grid;
}

std::optional<json> loadModelFile(const std::string& path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) {
        log::error("cannot open model file: " + path);
        return std::nullopt;
    }
    json parsed = json::parse(file, nullptr, false);
    if (parsed.is_discarded()) {
        log::error("malformed json in model file: " + path);
        return std::nullopt;
    }
    return parsed;
}

std::optional<std::map<char, RefBlock>> parseRefs(const json& model, const std::string& path) {
    if (!model.contains("ref") || !model["ref"].is_object()) {
        log::error("model has no ref map: " + path);
        return std::nullopt;
    }

    std::map<char, RefBlock> refs;
    for (const auto& [key, value] : model["ref"].items()) {
        if (key.size() != 1 || !value.is_object() || !value.contains("Name") ||
            !value["Name"].is_string()) {
            log::error("bad ref entry '" + key + "' in " + path);
            return std::nullopt;
        }
        RefBlock block;
        block.name = value["Name"].get<std::string>();
        const json properties = value.value("Properties", json::object());
        for (const auto& [propertyName, propertyValue] : properties.items()) {
            block.properties[propertyName] = propertyValue.is_string()
                                                 ? propertyValue.get<std::string>()
                                                 : propertyValue.dump();
        }
        refs[key[0]] = std::move(block);
    }
    return refs;
}

void emitQuad(SceneMesh& mesh, const FaceQuad& quad, int x, int y, int z) {
    std::uint32_t baseVertex = static_cast<std::uint32_t>(mesh.vertices.size() / kVertexFloats);
    float shade = kFaceShade[static_cast<int>(quad.dir)];

    for (int corner = 0; corner < 4; ++corner) {
        mesh.vertices.push_back(quad.pos[corner].x + static_cast<float>(x));
        mesh.vertices.push_back(quad.pos[corner].y + static_cast<float>(y));
        mesh.vertices.push_back(quad.pos[corner].z + static_cast<float>(z));
        mesh.vertices.push_back(quad.uv[corner][0]);
        mesh.vertices.push_back(quad.uv[corner][1]);
        mesh.vertices.push_back(static_cast<float>(quad.textureLayer));
        mesh.vertices.push_back(quad.tint[0] * shade);
        mesh.vertices.push_back(quad.tint[1] * shade);
        mesh.vertices.push_back(quad.tint[2] * shade);
    }
    for (std::uint32_t index : {0u, 1u, 2u, 0u, 2u, 3u}) {
        mesh.indices.push_back(baseVertex + index);
    }
    ++mesh.quadCount;
}

} // namespace

std::optional<SceneMesh> buildScene(const std::string& modelFilePath, BlockModelResolver& resolver) {
    auto model = loadModelFile(modelFilePath);
    if (!model) return std::nullopt;

    if (model->value("loader", "") != "bonsaitrees4:multiblockmodel") {
        log::error("not a bonsaitrees4 multiblock model: " + modelFilePath);
        return std::nullopt;
    }
    if (model->value("version", 0) != 4) {
        log::warn("unexpected model version " + std::to_string(model->value("version", 0)) +
                  " in " + modelFilePath + " (expecting 4)");
    }

    auto refs = parseRefs(*model, modelFilePath);
    if (!refs) return std::nullopt;

    // shape[outer][row][char]: row is vertical (row 0 = top), outer and char
    // index are the horizontal axes. Mapped as x = outer, z = char, y flipped.
    auto grid = parseShape(*model, modelFilePath);
    if (!grid) return std::nullopt;
    int sizeX = grid->sizeX, sizeY = grid->sizeY, sizeZ = grid->sizeZ;

    SceneMesh mesh;
    int minCell[3] = {sizeX, sizeY, sizeZ};
    int maxCell[3] = {-1, -1, -1};

    for (int x = 0; x < sizeX; ++x) {
        for (int y = 0; y < sizeY; ++y) {
            for (int z = 0; z < sizeZ; ++z) {
                char key = grid->refKeyAt(x, y, z);
                if (key == '\0') continue;

                auto ref = refs->find(key);
                if (ref == refs->end()) {
                    log::warnOnce("shape uses undefined ref '" + std::string(1, key) + "' in " +
                                  modelFilePath);
                    continue;
                }

                const BlockModel& block = resolver.resolve(ref->second.name, ref->second.properties);
                ++mesh.blockCount;
                for (int axis = 0; axis < 3; ++axis) {
                    int cell[3] = {x, y, z};
                    minCell[axis] = std::min(minCell[axis], cell[axis]);
                    maxCell[axis] = std::max(maxCell[axis], cell[axis]);
                }

                for (int dirIndex = 0; dirIndex < kDirCount; ++dirIndex) {
                    if (block.faces[dirIndex].empty()) continue;

                    int nx = x + kDirOffsets[dirIndex][0];
                    int ny = y + kDirOffsets[dirIndex][1];
                    int nz = z + kDirOffsets[dirIndex][2];
                    bool neighborInside = nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY &&
                                          nz >= 0 && nz < sizeZ;
                    if (neighborInside) {
                        char neighborKey = grid->refKeyAt(nx, ny, nz);
                        if (neighborKey != '\0') {
                            auto neighborRef = refs->find(neighborKey);
                            if (neighborRef != refs->end() &&
                                resolver.resolve(neighborRef->second.name,
                                                 neighborRef->second.properties).fullyOpaque) {
                                continue;
                            }
                        }
                    }
                    for (const FaceQuad& layer : block.faces[dirIndex]) {
                        emitQuad(mesh, layer, x, y, z);
                    }
                }
            }
        }
    }

    if (mesh.blockCount == 0) {
        log::error("model contains no blocks: " + modelFilePath);
        return std::nullopt;
    }

    for (int axis = 0; axis < 3; ++axis) {
        mesh.center[axis] = (static_cast<float>(minCell[axis]) + static_cast<float>(maxCell[axis]) + 1.0f) / 2.0f;
    }
    mesh.halfHeight = (static_cast<float>(maxCell[1] - minCell[1]) + 1.0f) / 2.0f;

    float maxRadius = 0.0f;
    for (int x = minCell[0]; x <= maxCell[0]; ++x) {
        for (int y = minCell[1]; y <= maxCell[1]; ++y) {
            for (int z = minCell[2]; z <= maxCell[2]; ++z) {
                if (grid->refKeyAt(x, y, z) == '\0') continue;
                float dx = static_cast<float>(x) + 0.5f - mesh.center[0];
                float dz = static_cast<float>(z) + 0.5f - mesh.center[2];
                maxRadius = std::max(maxRadius, std::sqrt(dx * dx + dz * dz));
            }
        }
    }
    mesh.horizontalRadius = maxRadius + 0.87f; // + half cube diagonal

    log::debug(modelFilePath + ": " + std::to_string(mesh.blockCount) + " blocks, " +
               std::to_string(mesh.quadCount) + " quads, size " + std::to_string(sizeX) + "x" +
               std::to_string(sizeY) + "x" + std::to_string(sizeZ));
    return mesh;
}

} // namespace ctg
