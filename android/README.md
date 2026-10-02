# Coloron 3D (Android · Kotlin + C++)

Uma releitura 3D do Coloron original (web). A bola neon salta de plataforma em plataforma
num mundo 3D, e o jogador **pinta a próxima plataforma com a cor da bola** antes que ela pouse.

## Arquitetura
| Camada | Tecnologia | Arquivos |
|---|---|---|
| Motor do jogo e render 3D | C++17, OpenGL ES 3, **GLM** (via CMake FetchContent) | `app/src/main/cpp/` |
| Ponte | JNI | `jni_bridge.cpp`, `engine/NativeBridge.kt` |
| UI / HUD / menus | Kotlin, **Jetpack Compose**, Material 3 | `ui/Screens.kt` |
| Progresso salvo | **DataStore Preferences** | `data/RewardsRepository.kt` |
| Lembretes de retorno | **WorkManager** + notificações | `data/ReminderWorker.kt` |

## Visual
Céu em degradê com estrelas e sol neon, plataformas flutuantes com pilares, colunas neon laterais,
iluminação Blinn-Phong + rim light, névoa, bola com squash & stretch, rastro, sombra, partículas e tremor de câmera.

## Recompensas para fazer o jogador voltar
- **Calendário diário de 7 dias** (50 → 600 moedas); a sequência zera se perder um dia.
- **Baú grátis a cada 4 horas**, com prêmio que cresce com o nível.
- **Missões diárias** (jogar partidas, pontos, combo, gemas) que reiniciam todo dia.
- **Gemas** nas plataformas, **combos**, **XP e níveis**.
- **Loja de skins** para a bola (Neon, Cromo, Lava, Galáxia).
- **Notificações** quando o prêmio diário, a sequência ou o baú estão esperando.

## Como compilar
Requer Android Studio (Ladybug ou mais novo) com NDK 27 e CMake 3.22.
```
cd android
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`.
