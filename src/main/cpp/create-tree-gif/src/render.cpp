#include "render.hpp"

#include "log.hpp"

#include <SDL.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdlib>
#include <cstring>

#ifdef CTG_HAVE_EGL
#include <EGL/egl.h>
#ifndef EGL_PLATFORM_SURFACELESS_MESA
#define EGL_PLATFORM_SURFACELESS_MESA 0x31DD
#endif
#endif

namespace ctg {

// ---------------------------------------------------------------------------
// Minimal GL declarations - only what this renderer uses; loaded at runtime.
// ---------------------------------------------------------------------------

using GLenum = unsigned int;
using GLuint = unsigned int;
using GLint = int;
using GLsizei = int;
using GLboolean = unsigned char;
using GLbitfield = unsigned int;
using GLfloat = float;
using GLchar = char;
using GLubyte = unsigned char;
using GLsizeiptr = std::ptrdiff_t;

constexpr GLenum GL_VENDOR_ = 0x1F00;
constexpr GLenum GL_RENDERER_ = 0x1F01;
constexpr GLenum GL_VERSION_ = 0x1F02;
constexpr GLbitfield GL_COLOR_BUFFER_BIT_ = 0x4000;
constexpr GLbitfield GL_DEPTH_BUFFER_BIT_ = 0x100;
constexpr GLenum GL_DEPTH_TEST_ = 0x0B71;
constexpr GLenum GL_CULL_FACE_ = 0x0B44;
constexpr GLenum GL_BACK_ = 0x0405;
constexpr GLenum GL_CW_ = 0x0900;
constexpr GLenum GL_TEXTURE_2D_ = 0x0DE1;
constexpr GLenum GL_TEXTURE_2D_ARRAY_ = 0x8C1A;
constexpr GLenum GL_RGBA_ = 0x1908;
constexpr GLenum GL_RGBA8_ = 0x8058;
constexpr GLenum GL_UNSIGNED_BYTE_ = 0x1401;
constexpr GLenum GL_TEXTURE_MIN_FILTER_ = 0x2801;
constexpr GLenum GL_TEXTURE_MAG_FILTER_ = 0x2800;
constexpr GLenum GL_NEAREST_ = 0x2600;
constexpr GLenum GL_TEXTURE_WRAP_S_ = 0x2802;
constexpr GLenum GL_TEXTURE_WRAP_T_ = 0x2803;
constexpr GLint GL_CLAMP_TO_EDGE_ = 0x812F;
constexpr GLenum GL_FRAMEBUFFER_ = 0x8D40;
constexpr GLenum GL_COLOR_ATTACHMENT0_ = 0x8CE0;
constexpr GLenum GL_DEPTH_ATTACHMENT_ = 0x8D00;
constexpr GLenum GL_RENDERBUFFER_ = 0x8D41;
constexpr GLenum GL_DEPTH_COMPONENT24_ = 0x81A6;
constexpr GLenum GL_DEPTH_COMPONENT16_ = 0x81A5;
constexpr GLenum GL_FRAMEBUFFER_COMPLETE_ = 0x8CD5;
constexpr GLenum GL_ARRAY_BUFFER_ = 0x8892;
constexpr GLenum GL_ELEMENT_ARRAY_BUFFER_ = 0x8893;
constexpr GLenum GL_STATIC_DRAW_ = 0x88E4;
constexpr GLenum GL_TRIANGLES_ = 0x0004;
constexpr GLenum GL_UNSIGNED_INT_ = 0x1405;
constexpr GLenum GL_FLOAT_ = 0x1406;
constexpr GLenum GL_VERTEX_SHADER_ = 0x8B31;
constexpr GLenum GL_FRAGMENT_SHADER_ = 0x8B30;
constexpr GLenum GL_COMPILE_STATUS_ = 0x8B81;
constexpr GLenum GL_LINK_STATUS_ = 0x8B82;
constexpr GLenum GL_PACK_ALIGNMENT_ = 0x0D05;
constexpr GLenum GL_UNPACK_ALIGNMENT_ = 0x0CF5;

// X-macro: return type, name, parameter list.
#define CTG_GL_FUNCTIONS(X)                                                                        \
    X(const GLubyte*, glGetString, (GLenum))                                                       \
    X(GLenum, glGetError, ())                                                                      \
    X(void, glEnable, (GLenum))                                                                    \
    X(void, glCullFace, (GLenum))                                                                  \
    X(void, glFrontFace, (GLenum))                                                                 \
    X(void, glViewport, (GLint, GLint, GLsizei, GLsizei))                                          \
    X(void, glClearColor, (GLfloat, GLfloat, GLfloat, GLfloat))                                    \
    X(void, glClear, (GLbitfield))                                                                 \
    X(void, glGenTextures, (GLsizei, GLuint*))                                                     \
    X(void, glBindTexture, (GLenum, GLuint))                                                       \
    X(void, glTexImage2D,                                                                          \
      (GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*))                \
    X(void, glTexImage3D,                                                                          \
      (GLenum, GLint, GLint, GLsizei, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*))       \
    X(void, glTexParameteri, (GLenum, GLenum, GLint))                                              \
    X(void, glDeleteTextures, (GLsizei, const GLuint*))                                            \
    X(void, glGenFramebuffers, (GLsizei, GLuint*))                                                 \
    X(void, glBindFramebuffer, (GLenum, GLuint))                                                   \
    X(void, glFramebufferTexture2D, (GLenum, GLenum, GLenum, GLuint, GLint))                       \
    X(GLenum, glCheckFramebufferStatus, (GLenum))                                                  \
    X(void, glDeleteFramebuffers, (GLsizei, const GLuint*))                                        \
    X(void, glGenRenderbuffers, (GLsizei, GLuint*))                                                \
    X(void, glBindRenderbuffer, (GLenum, GLuint))                                                  \
    X(void, glRenderbufferStorage, (GLenum, GLenum, GLsizei, GLsizei))                             \
    X(void, glFramebufferRenderbuffer, (GLenum, GLenum, GLenum, GLuint))                           \
    X(void, glDeleteRenderbuffers, (GLsizei, const GLuint*))                                       \
    X(GLuint, glCreateShader, (GLenum))                                                            \
    X(void, glShaderSource, (GLuint, GLsizei, const GLchar* const*, const GLint*))                 \
    X(void, glCompileShader, (GLuint))                                                             \
    X(void, glGetShaderiv, (GLuint, GLenum, GLint*))                                               \
    X(void, glGetShaderInfoLog, (GLuint, GLsizei, GLsizei*, GLchar*))                              \
    X(void, glDeleteShader, (GLuint))                                                              \
    X(GLuint, glCreateProgram, ())                                                                 \
    X(void, glAttachShader, (GLuint, GLuint))                                                      \
    X(void, glBindAttribLocation, (GLuint, GLuint, const GLchar*))                                 \
    X(void, glLinkProgram, (GLuint))                                                               \
    X(void, glGetProgramiv, (GLuint, GLenum, GLint*))                                              \
    X(void, glGetProgramInfoLog, (GLuint, GLsizei, GLsizei*, GLchar*))                             \
    X(void, glUseProgram, (GLuint))                                                                \
    X(void, glDeleteProgram, (GLuint))                                                             \
    X(GLint, glGetUniformLocation, (GLuint, const GLchar*))                                        \
    X(void, glUniformMatrix4fv, (GLint, GLsizei, GLboolean, const GLfloat*))                       \
    X(void, glUniform1i, (GLint, GLint))                                                           \
    X(void, glGenBuffers, (GLsizei, GLuint*))                                                      \
    X(void, glBindBuffer, (GLenum, GLuint))                                                        \
    X(void, glBufferData, (GLenum, GLsizeiptr, const void*, GLenum))                               \
    X(void, glDeleteBuffers, (GLsizei, const GLuint*))                                             \
    X(void, glGenVertexArrays, (GLsizei, GLuint*))                                                 \
    X(void, glBindVertexArray, (GLuint))                                                           \
    X(void, glDeleteVertexArrays, (GLsizei, const GLuint*))                                        \
    X(void, glEnableVertexAttribArray, (GLuint))                                                   \
    X(void, glVertexAttribPointer, (GLuint, GLint, GLenum, GLboolean, GLsizei, const void*))       \
    X(void, glDrawElements, (GLenum, GLsizei, GLenum, const void*))                                \
    X(void, glReadPixels, (GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void*))                 \
    X(void, glPixelStorei, (GLenum, GLint))                                                        \
    X(void, glFinish, ())

namespace gl {
#define CTG_DECLARE_GL(ret, name, params) ret(*name) params = nullptr;
CTG_GL_FUNCTIONS(CTG_DECLARE_GL)
#undef CTG_DECLARE_GL

bool loadAll(void* (*getProc)(const char*), std::string& missing) {
#define CTG_LOAD_GL(ret, name, params)                                                             \
    name = reinterpret_cast<ret(*) params>(getProc(#name));                                        \
    if (!name) { missing = #name; return false; }
    CTG_GL_FUNCTIONS(CTG_LOAD_GL)
#undef CTG_LOAD_GL
    return true;
}
} // namespace gl

// ---------------------------------------------------------------------------
// Small column-major mat4 helpers.
// ---------------------------------------------------------------------------

namespace {

struct Mat4 {
    float m[16] = {};
};

Mat4 multiply(const Mat4& a, const Mat4& b) {
    Mat4 out;
    for (int col = 0; col < 4; ++col) {
        for (int row = 0; row < 4; ++row) {
            float sum = 0;
            for (int k = 0; k < 4; ++k) sum += a.m[k * 4 + row] * b.m[col * 4 + k];
            out.m[col * 4 + row] = sum;
        }
    }
    return out;
}

Mat4 orthoMatrix(float halfWidth, float halfHeight, float nearZ, float farZ) {
    Mat4 out;
    out.m[0] = 1.0f / halfWidth;
    out.m[5] = 1.0f / halfHeight;
    out.m[10] = -2.0f / (farZ - nearZ);
    out.m[14] = -(farZ + nearZ) / (farZ - nearZ);
    out.m[15] = 1.0f;
    return out;
}

Mat4 perspectiveMatrix(float fovYRadians, float aspect, float nearZ, float farZ) {
    Mat4 out;
    float f = 1.0f / std::tan(fovYRadians / 2.0f);
    out.m[0] = f / aspect;
    out.m[5] = f;
    out.m[10] = (farZ + nearZ) / (nearZ - farZ);
    out.m[11] = -1.0f;
    out.m[14] = 2.0f * farZ * nearZ / (nearZ - farZ);
    return out;
}

Mat4 lookAtMatrix(const float eye[3], const float target[3], const float up[3]) {
    auto normalize = [](float v[3]) {
        float length = std::sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= length; v[1] /= length; v[2] /= length;
    };
    float forward[3] = {target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]};
    normalize(forward);
    float side[3] = {forward[1] * up[2] - forward[2] * up[1],
                     forward[2] * up[0] - forward[0] * up[2],
                     forward[0] * up[1] - forward[1] * up[0]};
    normalize(side);
    float trueUp[3] = {side[1] * forward[2] - side[2] * forward[1],
                       side[2] * forward[0] - side[0] * forward[2],
                       side[0] * forward[1] - side[1] * forward[0]};

    Mat4 out;
    out.m[0] = side[0];   out.m[4] = side[1];   out.m[8] = side[2];
    out.m[1] = trueUp[0]; out.m[5] = trueUp[1]; out.m[9] = trueUp[2];
    out.m[2] = -forward[0]; out.m[6] = -forward[1]; out.m[10] = -forward[2];
    out.m[12] = -(side[0] * eye[0] + side[1] * eye[1] + side[2] * eye[2]);
    out.m[13] = -(trueUp[0] * eye[0] + trueUp[1] * eye[1] + trueUp[2] * eye[2]);
    out.m[14] = forward[0] * eye[0] + forward[1] * eye[1] + forward[2] * eye[2];
    out.m[15] = 1.0f;
    return out;
}

constexpr double kPi = 3.14159265358979323846;

const char* kVertexShaderBody = R"(
uniform mat4 uMvp;
in vec3 aPos;
in vec3 aUvLayer;
in vec3 aColor;
out vec3 vUvLayer;
out vec3 vColor;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
    vUvLayer = aUvLayer;
    vColor = aColor;
}
)";

const char* kFragmentShaderBody = R"(
uniform highp sampler2DArray uTex;
in vec3 vUvLayer;
in vec3 vColor;
out vec4 outColor;
void main() {
    vec4 texel = texture(uTex, vUvLayer);
    if (texel.a < 0.5) discard;
    outColor = vec4(texel.rgb * vColor, 1.0);
}
)";

void setDefaultEnv(const char* name, const char* value) {
    if (!std::getenv(name)) setenv(name, value, 0);
}

} // namespace

// ---------------------------------------------------------------------------
// Renderer implementation.
// ---------------------------------------------------------------------------

struct Renderer::Impl {
    SDL_Window* window = nullptr;
    SDL_GLContext sdlContext = nullptr;
    bool sdlInitialized = false;
    bool usingGles = false;
    bool usingEglFallback = false;
#ifdef CTG_HAVE_EGL
    EGLDisplay eglDisplay = EGL_NO_DISPLAY;
    EGLContext eglContext = EGL_NO_CONTEXT;
#endif

    int canvasWidth = 0;
    int canvasHeight = 0;
    int supersample = 1;
    int fboWidth = 0;
    int fboHeight = 0;

    GLuint fbo = 0;
    GLuint colorTexture = 0;
    GLuint depthBuffer = 0;
    GLuint program = 0;
    GLint mvpLocation = -1;
    GLuint textureArray = 0;
    std::uint64_t uploadedTextureVersion = 0;
    std::string glVersion, glRenderer, glVendor;

    bool createSdlContext();
#ifdef CTG_HAVE_EGL
    bool createEglContext();
#endif
    bool createFramebuffer();
    bool compileProgram();
    GLuint compileShader(GLenum type, const std::string& source);
    void uploadTextures(TextureStore& textures);
    void destroy();
};

bool Renderer::Impl::createSdlContext() {
    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
        log::debug(std::string("SDL_Init failed: ") + SDL_GetError());
        return false;
    }
    sdlInitialized = true;

    struct Profile {
        int major, minor, profileMask;
        bool gles;
        const char* label;
    };
    const Profile profiles[] = {
        {3, 3, SDL_GL_CONTEXT_PROFILE_CORE, false, "OpenGL 3.3 core"},
        {3, 0, SDL_GL_CONTEXT_PROFILE_ES, true, "OpenGL ES 3.0"},
    };

    for (const Profile& profile : profiles) {
        SDL_GL_ResetAttributes();
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, profile.major);
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, profile.minor);
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, profile.profileMask);
        SDL_GL_SetAttribute(SDL_GL_RED_SIZE, 8);
        SDL_GL_SetAttribute(SDL_GL_GREEN_SIZE, 8);
        SDL_GL_SetAttribute(SDL_GL_BLUE_SIZE, 8);
        SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE, 8);
        SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, 24);
        SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 0);

        window = SDL_CreateWindow("create-tree-gif", 0, 0, 64, 64,
                                  SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
        if (!window) {
            log::debug(std::string("SDL_CreateWindow failed (") + profile.label + "): " +
                       SDL_GetError());
            continue;
        }
        sdlContext = SDL_GL_CreateContext(window);
        if (!sdlContext) {
            log::debug(std::string("SDL_GL_CreateContext failed (") + profile.label + "): " +
                       SDL_GetError());
            SDL_DestroyWindow(window);
            window = nullptr;
            continue;
        }
        usingGles = profile.gles;
        log::debug(std::string("created ") + profile.label + " context via SDL driver '" +
                   (SDL_GetCurrentVideoDriver() ? SDL_GetCurrentVideoDriver() : "?") + "'");
        return true;
    }
    return false;
}

#ifdef CTG_HAVE_EGL
bool Renderer::Impl::createEglContext() {
    using GetPlatformDisplayFn = EGLDisplay(EGLAPIENTRY*)(EGLenum, void*, const EGLint*);
    auto getPlatformDisplay = reinterpret_cast<GetPlatformDisplayFn>(
        eglGetProcAddress("eglGetPlatformDisplayEXT"));

    eglDisplay = EGL_NO_DISPLAY;
    if (getPlatformDisplay) {
        eglDisplay = getPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, nullptr);
    }
    if (eglDisplay == EGL_NO_DISPLAY) {
        eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    }
    if (eglDisplay == EGL_NO_DISPLAY) {
        log::debug("EGL fallback: no display");
        return false;
    }
    if (!eglInitialize(eglDisplay, nullptr, nullptr)) {
        log::debug("EGL fallback: eglInitialize failed");
        return false;
    }

    EGLint configAttribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT, EGL_NONE};
    EGLint glesConfigAttribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE};
    EGLConfig config;
    EGLint configCount = 0;

    if (eglBindAPI(EGL_OPENGL_API) &&
        eglChooseConfig(eglDisplay, configAttribs, &config, 1, &configCount) && configCount > 0) {
        EGLint contextAttribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 3,
                                   EGL_NONE};
        eglContext = eglCreateContext(eglDisplay, config, EGL_NO_CONTEXT, contextAttribs);
        usingGles = false;
    }
    if (eglContext == EGL_NO_CONTEXT && eglBindAPI(EGL_OPENGL_ES_API) &&
        eglChooseConfig(eglDisplay, glesConfigAttribs, &config, 1, &configCount) &&
        configCount > 0) {
        EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
        eglContext = eglCreateContext(eglDisplay, config, EGL_NO_CONTEXT, contextAttribs);
        usingGles = true;
    }
    if (eglContext == EGL_NO_CONTEXT) {
        log::debug("EGL fallback: eglCreateContext failed");
        return false;
    }
    if (!eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext)) {
        log::debug("EGL fallback: surfaceless eglMakeCurrent failed");
        return false;
    }
    usingEglFallback = true;
    log::debug(std::string("created EGL surfaceless context (") +
               (usingGles ? "GLES 3" : "GL 3.3") + ")");
    return true;
}
#endif

bool Renderer::Impl::createFramebuffer() {
    gl::glGenFramebuffers(1, &fbo);
    gl::glBindFramebuffer(GL_FRAMEBUFFER_, fbo);

    gl::glGenTextures(1, &colorTexture);
    gl::glBindTexture(GL_TEXTURE_2D_, colorTexture);
    gl::glTexImage2D(GL_TEXTURE_2D_, 0, GL_RGBA8_, fboWidth, fboHeight, 0, GL_RGBA_,
                     GL_UNSIGNED_BYTE_, nullptr);
    gl::glTexParameteri(GL_TEXTURE_2D_, GL_TEXTURE_MIN_FILTER_, GL_NEAREST_);
    gl::glTexParameteri(GL_TEXTURE_2D_, GL_TEXTURE_MAG_FILTER_, GL_NEAREST_);
    gl::glFramebufferTexture2D(GL_FRAMEBUFFER_, GL_COLOR_ATTACHMENT0_, GL_TEXTURE_2D_,
                               colorTexture, 0);

    gl::glGenRenderbuffers(1, &depthBuffer);
    gl::glBindRenderbuffer(GL_RENDERBUFFER_, depthBuffer);

    for (GLenum depthFormat : {GL_DEPTH_COMPONENT24_, GL_DEPTH_COMPONENT16_}) {
        gl::glRenderbufferStorage(GL_RENDERBUFFER_, depthFormat, fboWidth, fboHeight);
        gl::glFramebufferRenderbuffer(GL_FRAMEBUFFER_, GL_DEPTH_ATTACHMENT_, GL_RENDERBUFFER_,
                                      depthBuffer);
        if (gl::glCheckFramebufferStatus(GL_FRAMEBUFFER_) == GL_FRAMEBUFFER_COMPLETE_) {
            return true;
        }
    }
    log::error("framebuffer incomplete at " + std::to_string(fboWidth) + "x" +
               std::to_string(fboHeight));
    return false;
}

GLuint Renderer::Impl::compileShader(GLenum type, const std::string& source) {
    GLuint shader = gl::glCreateShader(type);
    const char* text = source.c_str();
    gl::glShaderSource(shader, 1, &text, nullptr);
    gl::glCompileShader(shader);

    GLint ok = 0;
    gl::glGetShaderiv(shader, GL_COMPILE_STATUS_, &ok);
    if (!ok) {
        char infoLog[2048];
        GLsizei length = 0;
        gl::glGetShaderInfoLog(shader, sizeof(infoLog), &length, infoLog);
        log::error(std::string("shader compile failed: ") + std::string(infoLog, length));
        gl::glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool Renderer::Impl::compileProgram() {
    std::string prologue = usingGles
                               ? "#version 300 es\nprecision highp float;\n"
                               : "#version 330 core\n";

    GLuint vertexShader = compileShader(GL_VERTEX_SHADER_, prologue + kVertexShaderBody);
    if (!vertexShader) return false;
    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER_, prologue + kFragmentShaderBody);
    if (!fragmentShader) { gl::glDeleteShader(vertexShader); return false; }

    program = gl::glCreateProgram();
    gl::glAttachShader(program, vertexShader);
    gl::glAttachShader(program, fragmentShader);
    gl::glBindAttribLocation(program, 0, "aPos");
    gl::glBindAttribLocation(program, 1, "aUvLayer");
    gl::glBindAttribLocation(program, 2, "aColor");
    gl::glLinkProgram(program);
    gl::glDeleteShader(vertexShader);
    gl::glDeleteShader(fragmentShader);

    GLint ok = 0;
    gl::glGetProgramiv(program, GL_LINK_STATUS_, &ok);
    if (!ok) {
        char infoLog[2048];
        GLsizei length = 0;
        gl::glGetProgramInfoLog(program, sizeof(infoLog), &length, infoLog);
        log::error(std::string("program link failed: ") + std::string(infoLog, length));
        gl::glDeleteProgram(program);
        program = 0;
        return false;
    }
    mvpLocation = gl::glGetUniformLocation(program, "uMvp");
    gl::glUseProgram(program);
    gl::glUniform1i(gl::glGetUniformLocation(program, "uTex"), 0);
    return true;
}

void Renderer::Impl::uploadTextures(TextureStore& textures) {
    if (textureArray != 0 && uploadedTextureVersion == textures.version()) return;

    if (textureArray != 0) gl::glDeleteTextures(1, &textureArray);
    gl::glGenTextures(1, &textureArray);
    gl::glBindTexture(GL_TEXTURE_2D_ARRAY_, textureArray);

    int layerSize = 0;
    std::vector<std::uint8_t> layerData = textures.buildLayers(layerSize);
    gl::glPixelStorei(GL_UNPACK_ALIGNMENT_, 1);
    gl::glTexImage3D(GL_TEXTURE_2D_ARRAY_, 0, GL_RGBA8_, layerSize, layerSize,
                     textures.layerCount(), 0, GL_RGBA_, GL_UNSIGNED_BYTE_, layerData.data());
    gl::glTexParameteri(GL_TEXTURE_2D_ARRAY_, GL_TEXTURE_MIN_FILTER_, GL_NEAREST_);
    gl::glTexParameteri(GL_TEXTURE_2D_ARRAY_, GL_TEXTURE_MAG_FILTER_, GL_NEAREST_);
    gl::glTexParameteri(GL_TEXTURE_2D_ARRAY_, GL_TEXTURE_WRAP_S_, GL_CLAMP_TO_EDGE_);
    gl::glTexParameteri(GL_TEXTURE_2D_ARRAY_, GL_TEXTURE_WRAP_T_, GL_CLAMP_TO_EDGE_);
    uploadedTextureVersion = textures.version();
    log::debug("uploaded " + std::to_string(textures.layerCount()) + " texture layers at " +
               std::to_string(layerSize) + "px");
}

void Renderer::Impl::destroy() {
    if (sdlContext) {
        SDL_GL_DeleteContext(sdlContext);
        sdlContext = nullptr;
    }
    if (window) {
        SDL_DestroyWindow(window);
        window = nullptr;
    }
#ifdef CTG_HAVE_EGL
    if (eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (eglContext != EGL_NO_CONTEXT) eglDestroyContext(eglDisplay, eglContext);
        eglTerminate(eglDisplay);
        eglDisplay = EGL_NO_DISPLAY;
        eglContext = EGL_NO_CONTEXT;
    }
#endif
    if (sdlInitialized) {
        SDL_Quit();
        sdlInitialized = false;
    }
}

Renderer::Renderer() : impl_(std::make_unique<Impl>()) {}

Renderer::~Renderer() {
    if (impl_) impl_->destroy();
}

bool Renderer::init(const Options& options) {
    setDefaultEnv("SDL_VIDEODRIVER", "offscreen");
    setDefaultEnv("LIBGL_ALWAYS_SOFTWARE", "1");
    setDefaultEnv("GALLIUM_DRIVER", "llvmpipe");

    impl_->canvasWidth = options.width;
    impl_->canvasHeight = options.height;
    impl_->supersample = options.supersample;
    impl_->fboWidth = options.width * options.supersample;
    impl_->fboHeight = options.height * options.supersample;

    void* (*getProc)(const char*) = nullptr;
    if (impl_->createSdlContext()) {
        getProc = reinterpret_cast<void* (*)(const char*)>(&SDL_GL_GetProcAddress);
    } else {
        log::warn("SDL could not create a GL context, trying direct EGL surfaceless fallback");
#ifdef CTG_HAVE_EGL
        if (impl_->createEglContext()) {
            getProc = reinterpret_cast<void* (*)(const char*)>(&eglGetProcAddress);
        }
#endif
    }
    if (!getProc) {
        log::error("no usable GL context (SDL offscreen and EGL surfaceless both failed)");
        return false;
    }

    std::string missing;
    if (!gl::loadAll(getProc, missing)) {
        log::error("GL function not available: " + missing);
        return false;
    }

    auto glString = [](GLenum name) -> std::string {
        const GLubyte* text = gl::glGetString(name);
        return text ? reinterpret_cast<const char*>(text) : "?";
    };
    impl_->glVersion = glString(GL_VERSION_);
    impl_->glRenderer = glString(GL_RENDERER_);
    impl_->glVendor = glString(GL_VENDOR_);
    log::debug("GL: " + impl_->glVersion + " / " + impl_->glRenderer);

    if (!impl_->createFramebuffer()) return false;
    if (!impl_->compileProgram()) return false;

    gl::glEnable(GL_DEPTH_TEST_);
    gl::glEnable(GL_CULL_FACE_);
    gl::glCullFace(GL_BACK_);
    // Cube faces are wound clockwise seen from outside (see blockmodel.cpp).
    gl::glFrontFace(GL_CW_);
    return true;
}

std::string Renderer::contextDescription() const {
    std::string driver = impl_->usingEglFallback
                             ? "EGL surfaceless"
                             : std::string("SDL '") +
                                   (SDL_GetCurrentVideoDriver() ? SDL_GetCurrentVideoDriver() : "?") +
                                   "'";
    return "context: " + driver + "\nversion: " + impl_->glVersion +
           "\nrenderer: " + impl_->glRenderer + "\nvendor: " + impl_->glVendor;
}

std::vector<Frame> Renderer::renderTurntable(const SceneMesh& mesh, TextureStore& textures,
                                             const Options& options) {
    Impl& state = *impl_;
    state.uploadTextures(textures);

    GLuint vao = 0, vbo = 0, ebo = 0;
    gl::glGenVertexArrays(1, &vao);
    gl::glBindVertexArray(vao);
    gl::glGenBuffers(1, &vbo);
    gl::glBindBuffer(GL_ARRAY_BUFFER_, vbo);
    gl::glBufferData(GL_ARRAY_BUFFER_,
                     static_cast<GLsizeiptr>(mesh.vertices.size() * sizeof(float)),
                     mesh.vertices.data(), GL_STATIC_DRAW_);
    gl::glGenBuffers(1, &ebo);
    gl::glBindBuffer(GL_ELEMENT_ARRAY_BUFFER_, ebo);
    gl::glBufferData(GL_ELEMENT_ARRAY_BUFFER_,
                     static_cast<GLsizeiptr>(mesh.indices.size() * sizeof(std::uint32_t)),
                     mesh.indices.data(), GL_STATIC_DRAW_);

    const GLsizei stride = kVertexFloats * sizeof(float);
    gl::glEnableVertexAttribArray(0);
    gl::glVertexAttribPointer(0, 3, GL_FLOAT_, 0, stride, reinterpret_cast<void*>(0));
    gl::glEnableVertexAttribArray(1);
    gl::glVertexAttribPointer(1, 3, GL_FLOAT_, 0, stride, reinterpret_cast<void*>(3 * sizeof(float)));
    gl::glEnableVertexAttribArray(2);
    gl::glVertexAttribPointer(2, 3, GL_FLOAT_, 0, stride, reinterpret_cast<void*>(6 * sizeof(float)));

    double pitch = options.pitchDeg * kPi / 180.0;
    float boundRadius = std::sqrt(mesh.horizontalRadius * mesh.horizontalRadius +
                                  mesh.halfHeight * mesh.halfHeight);
    float distance = 4.0f * boundRadius;
    float nearZ = distance - 2.0f * boundRadius;
    float farZ = distance + 2.0f * boundRadius;

    float marginFactor = 1.0f + static_cast<float>(options.margin);
    float halfWidth = mesh.horizontalRadius * marginFactor;
    float halfHeight = (mesh.horizontalRadius * static_cast<float>(std::sin(pitch)) +
                        mesh.halfHeight * static_cast<float>(std::cos(pitch))) *
                       marginFactor;
    float aspect = static_cast<float>(state.canvasWidth) / static_cast<float>(state.canvasHeight);
    if (halfWidth / halfHeight > aspect) {
        halfHeight = halfWidth / aspect;
    } else {
        halfWidth = halfHeight * aspect;
    }

    Mat4 projection;
    if (options.projection == Projection::Ortho) {
        projection = orthoMatrix(halfWidth, halfHeight, nearZ, farZ);
    } else {
        float fovY = 2.0f * std::atan2(halfHeight, distance);
        projection = perspectiveMatrix(fovY, aspect, nearZ * 0.25f, farZ);
    }

    if (options.backgroundRgb) {
        std::uint32_t rgb = *options.backgroundRgb;
        gl::glClearColor(static_cast<float>((rgb >> 16) & 0xFF) / 255.0f,
                         static_cast<float>((rgb >> 8) & 0xFF) / 255.0f,
                         static_cast<float>(rgb & 0xFF) / 255.0f, 1.0f);
    } else {
        gl::glClearColor(0, 0, 0, 0);
    }

    gl::glBindFramebuffer(GL_FRAMEBUFFER_, state.fbo);
    gl::glViewport(0, 0, state.fboWidth, state.fboHeight);
    gl::glUseProgram(state.program);
    gl::glBindTexture(GL_TEXTURE_2D_ARRAY_, state.textureArray);

    std::vector<Frame> frames;
    std::vector<std::uint8_t> rawPixels(static_cast<std::size_t>(state.fboWidth) *
                                        state.fboHeight * 4);

    for (int frameIndex = 0; frameIndex < options.frames; ++frameIndex) {
        double spin = 360.0 * frameIndex / options.frames * (options.spinClockwise ? -1.0 : 1.0);
        double yaw = (options.yawStartDeg + spin) * kPi / 180.0;

        float eye[3] = {
            mesh.center[0] + distance * static_cast<float>(std::cos(pitch) * std::sin(yaw)),
            mesh.center[1] + distance * static_cast<float>(std::sin(pitch)),
            mesh.center[2] + distance * static_cast<float>(std::cos(pitch) * std::cos(yaw))};
        const float up[3] = {0, 1, 0};
        Mat4 view = lookAtMatrix(eye, mesh.center, up);
        Mat4 mvp = multiply(projection, view);

        gl::glClear(GL_COLOR_BUFFER_BIT_ | GL_DEPTH_BUFFER_BIT_);
        gl::glUniformMatrix4fv(state.mvpLocation, 1, 0, mvp.m);
        gl::glDrawElements(GL_TRIANGLES_, static_cast<GLsizei>(mesh.indices.size()),
                           GL_UNSIGNED_INT_, nullptr);
        gl::glFinish();

        gl::glPixelStorei(GL_PACK_ALIGNMENT_, 1);
        gl::glReadPixels(0, 0, state.fboWidth, state.fboHeight, GL_RGBA_, GL_UNSIGNED_BYTE_,
                         rawPixels.data());

        Frame frame;
        frame.width = state.canvasWidth;
        frame.height = state.canvasHeight;
        frame.rgba.resize(static_cast<std::size_t>(frame.width) * frame.height * 4);

        int sampleScale = state.supersample;
        int samples = sampleScale * sampleScale;
        for (int outY = 0; outY < frame.height; ++outY) {
            // GL rows are bottom-up; flip while downsampling.
            for (int outX = 0; outX < frame.width; ++outX) {
                unsigned sumR = 0, sumG = 0, sumB = 0, sumA = 0;
                for (int subY = 0; subY < sampleScale; ++subY) {
                    int srcY = state.fboHeight - 1 - (outY * sampleScale + subY);
                    const std::uint8_t* row = &rawPixels[(static_cast<std::size_t>(srcY) *
                                                          state.fboWidth + outX * sampleScale) * 4];
                    for (int subX = 0; subX < sampleScale; ++subX) {
                        // The framebuffer is effectively premultiplied (background
                        // is transparent black, geometry writes alpha 1), so all
                        // four channels average directly.
                        sumR += row[subX * 4 + 0];
                        sumG += row[subX * 4 + 1];
                        sumB += row[subX * 4 + 2];
                        sumA += row[subX * 4 + 3];
                    }
                }
                std::uint8_t* out = &frame.rgba[(static_cast<std::size_t>(outY) * frame.width +
                                                 outX) * 4];
                unsigned alpha = sumA / samples;
                if (alpha == 0) {
                    out[0] = out[1] = out[2] = out[3] = 0;
                } else {
                    // Un-premultiply back to straight alpha for the encoders.
                    out[0] = static_cast<std::uint8_t>(std::min(255u, sumR * 255u / sumA));
                    out[1] = static_cast<std::uint8_t>(std::min(255u, sumG * 255u / sumA));
                    out[2] = static_cast<std::uint8_t>(std::min(255u, sumB * 255u / sumA));
                    out[3] = static_cast<std::uint8_t>(alpha);
                }
            }
        }
        frames.push_back(std::move(frame));
    }

    gl::glDeleteBuffers(1, &vbo);
    gl::glDeleteBuffers(1, &ebo);
    gl::glDeleteVertexArrays(1, &vao);
    return frames;
}

} // namespace ctg
