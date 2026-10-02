#pragma once
#include <GLES3/gl3.h>
#include <glm/glm.hpp>
#include "GameEngine.h"

struct Mesh {
    GLuint vao = 0, vbo = 0, ibo = 0;
    GLsizei count = 0;
};

class Renderer {
public:
    bool init();
    void resize(int w, int h);
    void draw(const GameEngine& g);

private:
    GLuint litProg = 0, skyProg = 0;
    GLint uMVP, uModel, uColor, uEmissive, uAlpha, uCamPos, uSpec, uTime, uRim;
    GLint uSkyTop, uSkyBottom, uSkyTime;
    GLuint skyVao = 0;
    Mesh cube, sphere, octa;
    int width = 1, height = 1;
    glm::mat4 viewProj{1.f};
    glm::vec3 camPos{0.f};

    void drawMesh(const Mesh& m, const glm::mat4& model, glm::vec3 color,
                  float emissive, float alpha = 1.f, float spec = 0.4f, float rim = 0.3f);
};
