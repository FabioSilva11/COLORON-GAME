#include "Renderer.h"
#include <android/log.h>
#include <glm/gtc/constants.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/type_ptr.hpp>
#include <cmath>
#include <vector>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Coloron3D", __VA_ARGS__)

namespace {

const char* kLitVS = R"(#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vWorld;
out vec3 vNormal;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    gl_Position = uMVP * vec4(aPos, 1.0);
})";

const char* kLitFS = R"(#version 300 es
precision mediump float;
in vec3 vWorld;
in vec3 vNormal;
uniform vec3 uColor;
uniform float uEmissive;
uniform float uAlpha;
uniform float uSpec;
uniform float uRim;
uniform vec3 uCamPos;
out vec4 fragColor;
void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(vec3(-0.4, 1.0, 0.5));
    vec3 v = normalize(uCamPos - vWorld);
    vec3 h = normalize(l + v);
    float diff = max(dot(n, l), 0.0);
    float hemi = 0.5 + 0.5 * n.y;
    float spec = pow(max(dot(n, h), 0.0), 48.0) * uSpec;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * uRim;
    vec3 ambient = mix(vec3(0.10, 0.07, 0.22), vec3(0.35, 0.30, 0.55), hemi);
    vec3 col = uColor * (ambient + diff * vec3(1.0, 0.95, 0.9)) + spec + rim * uColor * 1.5;
    col += uColor * uEmissive;
    // névoa roxa no horizonte
    float dist = length(uCamPos - vWorld);
    float fog = clamp((dist - 18.0) / 40.0, 0.0, 1.0);
    col = mix(col, vec3(0.16, 0.08, 0.30), fog);
    col = col / (col + vec3(0.8)) * 1.6; // tonemap suave
    fragColor = vec4(col, uAlpha);
})";

const char* kSkyVS = R"(#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.999, 1.0);
})";

const char* kSkyFS = R"(#version 300 es
precision mediump float;
in vec2 vUv;
uniform vec3 uTop;
uniform vec3 uBottom;
uniform float uTime;
out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    vec3 col = mix(uBottom, uTop, smoothstep(0.0, 1.0, vUv.y));
    // estrelas cintilantes
    vec2 g = floor(vUv * vec2(90.0, 160.0));
    float s = hash(g);
    if (s > 0.992 && vUv.y > 0.45) col += vec3(0.8) * (0.5 + 0.5 * sin(uTime * 3.0 + s * 100.0));
    // sol neon
    vec2 c = vUv - vec2(0.5, 0.55);
    float d = length(c * vec2(1.0, 1.8));
    col += vec3(1.0, 0.35, 0.55) * smoothstep(0.22, 0.0, d) * 0.6;
    fragColor = vec4(col, 1.0);
})";

GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[1024]; glGetShaderInfoLog(s, 1024, nullptr, log); LOGE("shader: %s", log); }
    return s;
}

GLuint link(const char* vs, const char* fs) {
    GLuint p = glCreateProgram();
    GLuint v = compile(GL_VERTEX_SHADER, vs), f = compile(GL_FRAGMENT_SHADER, fs);
    glAttachShader(p, v); glAttachShader(p, f);
    glLinkProgram(p);
    GLint ok; glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { char log[1024]; glGetProgramInfoLog(p, 1024, nullptr, log); LOGE("link: %s", log); }
    glDeleteShader(v); glDeleteShader(f);
    return p;
}

Mesh upload(const std::vector<float>& v, const std::vector<unsigned short>& idx) {
    Mesh m;
    glGenVertexArrays(1, &m.vao);
    glBindVertexArray(m.vao);
    glGenBuffers(1, &m.vbo);
    glBindBuffer(GL_ARRAY_BUFFER, m.vbo);
    glBufferData(GL_ARRAY_BUFFER, v.size() * sizeof(float), v.data(), GL_STATIC_DRAW);
    glGenBuffers(1, &m.ibo);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m.ibo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size() * sizeof(unsigned short), idx.data(), GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 24, (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 24, (void*)12);
    glBindVertexArray(0);
    m.count = (GLsizei)idx.size();
    return m;
}

Mesh makeCube() {
    std::vector<float> v; std::vector<unsigned short> idx;
    const glm::vec3 n[6] = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    for (int f = 0; f < 6; ++f) {
        glm::vec3 nn = n[f];
        glm::vec3 u = std::abs(nn.y) > 0.5f ? glm::vec3(1,0,0) : glm::vec3(0,1,0);
        glm::vec3 w = glm::cross(nn, u);
        unsigned short base = (unsigned short)(v.size() / 6);
        for (int i = 0; i < 4; ++i) {
            float a = (i == 0 || i == 3) ? -0.5f : 0.5f, b = (i < 2) ? -0.5f : 0.5f;
            glm::vec3 p = nn * 0.5f + u * a + w * b;
            v.insert(v.end(), {p.x, p.y, p.z, nn.x, nn.y, nn.z});
        }
        // ordem anti-horária vista de fora
        glm::vec3 p0 = nn*0.5f + u*-0.5f + w*-0.5f, p1 = nn*0.5f + u*0.5f + w*-0.5f, p2 = nn*0.5f + u*0.5f + w*0.5f;
        bool ccw = glm::dot(glm::cross(p1 - p0, p2 - p0), nn) > 0.f;
        if (ccw) idx.insert(idx.end(), {base, (unsigned short)(base+1), (unsigned short)(base+2), base, (unsigned short)(base+2), (unsigned short)(base+3)});
        else     idx.insert(idx.end(), {base, (unsigned short)(base+2), (unsigned short)(base+1), base, (unsigned short)(base+3), (unsigned short)(base+2)});
    }
    return upload(v, idx);
}

Mesh makeSphere(int seg = 28, int rings = 18) {
    std::vector<float> v; std::vector<unsigned short> idx;
    for (int r = 0; r <= rings; ++r) {
        float phi = glm::pi<float>() * r / rings;
        for (int s = 0; s <= seg; ++s) {
            float th = glm::two_pi<float>() * s / seg;
            glm::vec3 p{std::sin(phi) * std::cos(th), std::cos(phi), std::sin(phi) * std::sin(th)};
            v.insert(v.end(), {p.x * 0.5f, p.y * 0.5f, p.z * 0.5f, p.x, p.y, p.z});
        }
    }
    for (int r = 0; r < rings; ++r)
        for (int s = 0; s < seg; ++s) {
            unsigned short a = r * (seg + 1) + s, b = a + seg + 1;
            idx.insert(idx.end(), {a, (unsigned short)(a + 1), b, b, (unsigned short)(a + 1), (unsigned short)(b + 1)});
        }
    return upload(v, idx);
}

Mesh makeOcta() { // gema
    const glm::vec3 p[6] = {{0,0.6f,0},{0,-0.6f,0},{0.4f,0,0},{-0.4f,0,0},{0,0,0.4f},{0,0,-0.4f}};
    const int f[8][3] = {{0,4,2},{0,2,5},{0,5,3},{0,3,4},{1,2,4},{1,5,2},{1,3,5},{1,4,3}};
    std::vector<float> v; std::vector<unsigned short> idx;
    for (auto& t : f) {
        glm::vec3 n = glm::normalize(glm::cross(p[t[1]] - p[t[0]], p[t[2]] - p[t[0]]));
        for (int k = 0; k < 3; ++k) {
            idx.push_back((unsigned short)(v.size() / 6));
            v.insert(v.end(), {p[t[k]].x, p[t[k]].y, p[t[k]].z, n.x, n.y, n.z});
        }
    }
    return upload(v, idx);
}

glm::vec3 hsv(float h) {
    glm::vec3 k = glm::clamp(glm::abs(glm::mod(h * 6.f + glm::vec3(0, 4, 2), 6.f) - 3.f) - 1.f, 0.f, 1.f);
    return k;
}

} // namespace

bool Renderer::init() {
    litProg = link(kLitVS, kLitFS);
    skyProg = link(kSkyVS, kSkyFS);
    uMVP = glGetUniformLocation(litProg, "uMVP");
    uModel = glGetUniformLocation(litProg, "uModel");
    uColor = glGetUniformLocation(litProg, "uColor");
    uEmissive = glGetUniformLocation(litProg, "uEmissive");
    uAlpha = glGetUniformLocation(litProg, "uAlpha");
    uSpec = glGetUniformLocation(litProg, "uSpec");
    uRim = glGetUniformLocation(litProg, "uRim");
    uCamPos = glGetUniformLocation(litProg, "uCamPos");
    uSkyTop = glGetUniformLocation(skyProg, "uTop");
    uSkyBottom = glGetUniformLocation(skyProg, "uBottom");
    uSkyTime = glGetUniformLocation(skyProg, "uTime");
    glGenVertexArrays(1, &skyVao);
    cube = makeCube();
    sphere = makeSphere();
    octa = makeOcta();
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    return litProg && skyProg;
}

void Renderer::resize(int w, int h) {
    width = w; height = h > 0 ? h : 1;
    glViewport(0, 0, width, height);
}

void Renderer::drawMesh(const Mesh& m, const glm::mat4& model, glm::vec3 color,
                        float emissive, float alpha, float spec, float rim) {
    glm::mat4 mvp = viewProj * model;
    glUniformMatrix4fv(uMVP, 1, GL_FALSE, glm::value_ptr(mvp));
    glUniformMatrix4fv(uModel, 1, GL_FALSE, glm::value_ptr(model));
    glUniform3fv(uColor, 1, glm::value_ptr(color));
    glUniform1f(uEmissive, emissive);
    glUniform1f(uAlpha, alpha);
    glUniform1f(uSpec, spec);
    glUniform1f(uRim, rim);
    glBindVertexArray(m.vao);
    glDrawElements(GL_TRIANGLES, m.count, GL_UNSIGNED_SHORT, nullptr);
}

void Renderer::draw(const GameEngine& g) {
    glClearColor(0.1f, 0.05f, 0.2f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Céu em degradê
    glDepthMask(GL_FALSE);
    glUseProgram(skyProg);
    glUniform3f(uSkyTop, 0.05f, 0.03f, 0.18f);
    glUniform3f(uSkyBottom, 0.45f, 0.15f, 0.45f);
    glUniform1f(uSkyTime, g.time);
    glBindVertexArray(skyVao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDepthMask(GL_TRUE);

    // Câmera que segue a bola em terceira pessoa
    float aspect = float(width) / float(height);
    glm::vec3 focus{0.f, 0.8f, g.ballPos.z - 3.5f};
    float sx = g.shake > 0.f ? std::sin(g.time * 80.f) * g.shake * 0.4f : 0.f;
    camPos = glm::vec3(1.6f + sx, 5.2f, g.ballPos.z + 7.5f);
    glm::mat4 proj = glm::perspective(glm::radians(aspect < 1.f ? 62.f : 50.f), aspect, 0.1f, 120.f);
    viewProj = proj * glm::lookAt(camPos, focus, {0, 1, 0});

    glUseProgram(litProg);
    glUniform3fv(uCamPos, 1, glm::value_ptr(camPos));

    const glm::vec3 neutral{0.32f, 0.30f, 0.42f};
    long target = g.hopIndex + 1;

    // Plataformas
    for (const auto& t : g.tiles) {
        float z = -t.index * GameEngine::kSpacing;
        float y = -0.25f - t.fall * t.fall * 9.f;
        float wave = std::sin(g.time * 2.f + t.index * 0.6f) * 0.05f;
        float s = 1.f + t.pop * 0.25f + t.pulse * 0.15f;
        glm::mat4 m = glm::translate(glm::mat4(1.f), {0.f, y + wave, z});
        if (t.fall > 0.f) m = glm::rotate(m, t.fall * 3.f, {1, 0, 0.3f});
        m = glm::scale(m, {2.2f * s, 0.5f, 2.2f * s});
        glm::vec3 c = t.color >= 0 ? kPalette[t.color] : neutral;
        float em = 0.15f + t.pulse * 1.2f;
        if (t.index == target && g.state == GameState::Playing && t.color < 0)
            em += 0.35f + 0.25f * std::sin(g.time * 10.f);   // destaque: pinte esta!
        drawMesh(cube, m, c, em, 1.f, 0.6f, 0.4f);

        // pilar sob a plataforma
        glm::mat4 pm = glm::translate(glm::mat4(1.f), {0.f, y - 3.f + wave, z});
        pm = glm::scale(pm, {0.6f, 5.f, 0.6f});
        drawMesh(cube, pm, neutral * 0.6f, 0.0f, 1.f, 0.2f, 0.1f);

        // colunas neon laterais
        for (int side = -1; side <= 1; side += 2) {
            float h = 1.5f + 1.2f * (0.5f + 0.5f * std::sin(t.index * 1.7f));
            glm::mat4 cm = glm::translate(glm::mat4(1.f), {side * 5.5f, h * 0.5f - 2.f, z});
            cm = glm::scale(cm, {0.35f, h, 0.35f});
            drawMesh(cube, cm, kPalette[(t.index + (side > 0)) % kColorCount], 0.9f, 1.f, 0.2f, 0.5f);
        }

        if (t.hasGem) {
            glm::mat4 gm = glm::translate(glm::mat4(1.f), {0.f, 1.6f + std::sin(g.time * 3.f) * 0.15f, z});
            gm = glm::rotate(gm, g.time * 2.f, {0, 1, 0});
            drawMesh(octa, gm, {1.f, 0.85f, 0.3f}, 0.8f, 1.f, 1.f, 0.8f);
        }
    }

    // Bola (skins)
    glm::vec3 bc = kPalette[g.ballColor];
    float em = 0.3f, spec = 0.6f, rim = 0.5f;
    switch (g.skin) {
        case 1: em = 1.2f; rim = 1.2f; break;                                // Neon
        case 2: spec = 2.0f; em = 0.1f; rim = 1.0f; break;                   // Cromo
        case 3: em = 0.6f + 0.5f * std::sin(g.time * 8.f); break;            // Lava
        case 4: rim = 1.5f; bc = glm::mix(bc, hsv(std::fmod(g.time * 0.2f, 1.f)), 0.25f); break; // Galáxia
        default: break;
    }
    float sq = g.squash;
    glm::mat4 bm = glm::translate(glm::mat4(1.f), g.ballPos + glm::vec3(0.f, (sq - 1.f) * 0.4f, 0.f));
    bm = glm::scale(bm, glm::vec3(1.f / std::sqrt(sq), sq, 1.f / std::sqrt(sq)) * 0.9f);
    drawMesh(sphere, bm, bc, em, 1.f, spec, rim);

    // Partículas
    for (const auto& p : g.particles) {
        glm::mat4 m = glm::translate(glm::mat4(1.f), p.pos);
        m = glm::rotate(m, p.life * 6.f, {1, 1, 0});
        m = glm::scale(m, glm::vec3(p.size * (0.5f + p.life)));
        drawMesh(cube, m, p.color, 1.0f, 1.f, 0.3f, 0.2f);
    }

    // Sombra e rastro (transparentes)
    glEnable(GL_BLEND);
    glDepthMask(GL_FALSE);
    float shadowScale = 1.0f - (g.ballPos.y - 0.55f) / (GameEngine::kHopHeight + 0.5f) * 0.6f;
    glm::mat4 sm = glm::translate(glm::mat4(1.f), {g.ballPos.x, 0.02f, g.ballPos.z});
    sm = glm::scale(sm, {0.9f * shadowScale, 0.02f, 0.9f * shadowScale});
    drawMesh(sphere, sm, {0.f, 0.f, 0.f}, 0.f, 0.45f * shadowScale, 0.f, 0.f);

    for (size_t i = 1; i < g.trail.size(); ++i) {
        float k = 1.f - float(i) / g.trail.size();
        glm::mat4 tm = glm::translate(glm::mat4(1.f), g.trail[i]);
        tm = glm::scale(tm, glm::vec3(0.7f * k));
        drawMesh(sphere, tm, bc, 1.0f, 0.35f * k, 0.f, 0.f);
    }
    glDepthMask(GL_TRUE);
    glDisable(GL_BLEND);
}
