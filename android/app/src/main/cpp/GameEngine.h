#pragma once
#include <glm/glm.hpp>
#include <deque>
#include <random>
#include <vector>

// As 3 cores do Coloron original
constexpr int kColorCount = 3;
extern const glm::vec3 kPalette[kColorCount];

enum class GameState { Idle = 0, Playing = 1, Over = 2 };

struct Tile {
    long index;
    int color = -1;          // -1 = neutra (cinza)
    bool hasGem = false;
    float pulse = 0.f;       // brilho ao acertar
    float fall = 0.f;        // >0 quando quebra por erro
    float pop = 0.f;         // animação ao trocar cor
};

struct Particle {
    glm::vec3 pos, vel, color;
    float life, size;
};

class GameEngine {
public:
    GameEngine();
    void start(int skin);
    void update(float dt);
    void selectColor(int c);   // jogador pinta a próxima plataforma

    GameState state = GameState::Idle;
    int score = 0, lives = 3, combo = 0, bestCombo = 0, gems = 0;
    int skin = 0;
    int ballColor = 0;

    // Dados para render
    std::deque<Tile> tiles;
    std::vector<Particle> particles;
    std::deque<glm::vec3> trail;
    glm::vec3 ballPos{0.f};
    float squash = 1.f;
    float shake = 0.f;
    float time = 0.f;
    float hopT = 0.f;          // 0..1 progresso do salto
    long hopIndex = 0;         // plataforma de onde saiu
    float hopDuration = 1.1f;

    static constexpr float kSpacing = 3.2f;
    static constexpr float kHopHeight = 2.4f;

    Tile* tileAt(long idx);

private:
    std::mt19937 rng;
    int nextBallColor();
    void land();
    void burst(glm::vec3 p, glm::vec3 color, int n, float speed);
    void ensureTiles();
};
