#include "GameEngine.h"
#include <algorithm>
#include <cmath>

const glm::vec3 kPalette[kColorCount] = {
    {1.00f, 0.27f, 0.44f},   // #FF4571
    {1.00f, 0.82f, 0.27f},   // #FFD145
    {0.51f, 0.38f, 0.96f},   // #8260F6
};

GameEngine::GameEngine() : rng(std::random_device{}()) { ensureTiles(); }

Tile* GameEngine::tileAt(long idx) {
    for (auto& t : tiles) if (t.index == idx) return &t;
    return nullptr;
}

void GameEngine::ensureTiles() {
    long first = hopIndex - 3;
    while (!tiles.empty() && tiles.front().index < first) tiles.pop_front();
    long next = tiles.empty() ? first : tiles.back().index + 1;
    while (next <= hopIndex + 14) {
        Tile t; t.index = next;
        t.hasGem = next > 3 && (next % 7 == 0);
        if (state != GameState::Playing) t.color = int(std::abs(next)) % kColorCount; // decorativo no menu
        tiles.push_back(t);
        ++next;
    }
}

int GameEngine::nextBallColor() {
    std::uniform_int_distribution<int> d(0, kColorCount - 1);
    int c;
    do { c = d(rng); } while (c == ballColor);
    return c;
}

void GameEngine::start(int s) {
    skin = s;
    state = GameState::Playing;
    score = 0; lives = 3; combo = 0; bestCombo = 0; gems = 0;
    hopIndex = 0; hopT = 0.f; hopDuration = 1.15f;
    tiles.clear(); particles.clear(); trail.clear();
    ensureTiles();
    tileAt(0)->color = ballColor; // plataforma inicial segura
    ballColor = nextBallColor();
}

void GameEngine::selectColor(int c) {
    if (state != GameState::Playing || c < 0 || c >= kColorCount) return;
    if (Tile* t = tileAt(hopIndex + 1)) {
        if (t->color != c) { t->color = c; t->pop = 1.f; }
    }
}

void GameEngine::burst(glm::vec3 p, glm::vec3 color, int n, float speed) {
    std::uniform_real_distribution<float> u(-1.f, 1.f);
    for (int i = 0; i < n; ++i) {
        glm::vec3 v{u(rng), std::abs(u(rng)) + 0.4f, u(rng)};
        particles.push_back({p, glm::normalize(v) * speed * (0.5f + 0.5f * std::abs(u(rng))),
                             color, 1.f, 0.08f + 0.08f * std::abs(u(rng))});
    }
}

void GameEngine::land() {
    Tile* t = tileAt(hopIndex);
    glm::vec3 p = {0.f, 0.3f, -hopIndex * kSpacing};
    squash = 0.6f;
    if (t && t->color == ballColor) {
        ++score; ++combo;
        bestCombo = std::max(bestCombo, combo);
        t->pulse = 1.f;
        burst(p, kPalette[ballColor], 18 + std::min(combo, 20), 4.f + combo * 0.1f);
        if (t->hasGem) { ++gems; t->hasGem = false; burst(p + glm::vec3(0, 1.2f, 0), {1.f, 0.9f, 0.4f}, 25, 6.f); }
        // acelera com a pontuação, como no original
        hopDuration = std::max(0.42f, 1.15f - score * 0.022f);
    } else {
        combo = 0; --lives; shake = 0.5f;
        if (t) t->fall = 0.01f;
        burst(p, {0.5f, 0.5f, 0.55f}, 30, 5.f);
        hopDuration = std::min(1.15f, hopDuration + 0.1f); // erro desacelera
        if (lives <= 0) { state = GameState::Over; return; }
    }
    ballColor = nextBallColor();
}

void GameEngine::update(float dt) {
    dt = std::min(dt, 0.05f);
    time += dt;

    if (state == GameState::Playing) {
        hopT += dt / hopDuration;
        if (hopT >= 1.f) {
            hopT -= 1.f;
            ++hopIndex;
            land();
            ensureTiles();
        }
    } else {
        // menu / game over: a bola continua pulando em demo
        hopT += dt / 1.0f;
        if (hopT >= 1.f) {
            hopT -= 1.f; ++hopIndex; squash = 0.7f; ensureTiles();
            if (Tile* t = tileAt(hopIndex)) { ballColor = t->color < 0 ? 0 : t->color; t->pulse = 1.f; }
        }
    }

    float z0 = -hopIndex * kSpacing;
    ballPos = {0.f, 0.55f + kHopHeight * 4.f * hopT * (1.f - hopT), z0 - hopT * kSpacing};
    squash += (1.f - squash) * std::min(1.f, dt * 10.f);
    shake = std::max(0.f, shake - dt);

    trail.push_front(ballPos);
    while (trail.size() > 14) trail.pop_back();

    for (auto& t : tiles) {
        t.pulse = std::max(0.f, t.pulse - dt * 1.5f);
        t.pop = std::max(0.f, t.pop - dt * 4.f);
        if (t.fall > 0.f) t.fall += dt;
    }
    for (auto& p : particles) {
        p.vel.y -= 9.8f * dt;
        p.pos += p.vel * dt;
        p.life -= dt * 1.2f;
    }
    particles.erase(std::remove_if(particles.begin(), particles.end(),
                                   [](const Particle& p) { return p.life <= 0.f; }),
                    particles.end());
}
