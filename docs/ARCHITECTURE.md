# VELOCITY — Arquitetura Técnica (v0.1, Fase 1)

> Versão: Velo 0.05. Estado honesto: este documento descreve o **alvo**. A seção "Status" no fim lista o que existe
> de fato no código. Nada que esteja marcado como PENDENTE foi implementado.

## A. Arquitetura técnica

### Princípios
1. **Servidor autoritativo.** Física, combustível, dano, peças e fixadores rodam só no servidor.
   O cliente envia *intenções* (entrada do motorista, ações) e renderiza estado replicado.
2. **Orientado a dados.** Veículo = definição JSON (data pack) + assets. Nenhum veículo novo
   exige classe Java nova. Comportamentos especiais entram como *componentes* registráveis.
3. **Separação client/server em tempo de compilação.** Loom `splitEnvironmentSourceSets()`:
   `src/main` não enxerga classes de cliente; um servidor dedicado nunca carrega render/HUD/áudio.
4. **Física pura e testável.** `dev.velocity.physics` não depende de `World`; recebe aderência e
   inclinação já amostradas. Testes JUnit rodam sem Minecraft.

### Módulos (pacotes)
| Módulo | Pacote | Lado | Responsabilidade |
|---|---|---|---|
| Core | `dev.velocity.core` | comum | init, registros, config, log |
| Vehicle | `dev.velocity.vehicle` | comum | `VehicleEntity`, `VehicleDefinition`, carregador de dados |
| Physics | `dev.velocity.physics` | comum (executa no servidor) | trem de força, pneus, freios, direção, suspensão |
| Network | `dev.velocity.network` | comum | payloads C2S/S2C, validação |
| Mechanics | `dev.velocity.mechanics` *(Fase 5+)* | comum | ComponentGraph, Fastener, AssemblyDependency, Wear, Diagnostic |
| Garage | `dev.velocity.garage` *(Fase 5)* | comum | blocos/equipamentos, persistência |
| Rendering | `dev.velocity.client.render` | cliente | modelos, materiais, animação, LOD |
| Audio | `dev.velocity.client.audio` *(Fase 2)* | cliente | camadas de motor por RPM/carga |
| Input | `dev.velocity.client.input` | cliente | teclas, envio de entrada |
| UI | `dev.velocity.client.hud`, `.screen` | cliente | HUD, garagem, oficina |

### Fluxo de um tick (servidor, 20 Hz)
```
VehicleInputPayload (C2S, cada tick do cliente)
  └─> VelocityNetwork valida: jogador é o motorista do veículo?
        └─> VehicleEntity.receiveInput()  (timeout de 10 ticks → entrada zero)
VehicleEntity.tick()
  ├─ amostra superfície (SurfaceGrip) e inclinação
  ├─ VehiclePhysics.step(def, input, dt, grip, slope)  → VehicleState
  ├─ Entity.move()  (colisão vanilla com blocos, step-height)
  └─ DataTracker ← speed, rpm, gear, steer, fuel, flags   → replicado aos clientes
```
`getControllingPassenger()` retorna **null de propósito**: assim o cliente vanilla não envia
`VehicleMoveC2SPacket` e não vira autoridade sobre a posição. Custo: ~1 RTT de latência de controle.
Mitigação planejada (Fase 6): predição no cliente rodando o mesmo `VehiclePhysics` + reconciliação.

### Plataforma de mecânica (seção 19) — desenho
- **VehicleComponentGraph**: árvore de `ComponentInstance` (id de peça, estado instalado/removido,
  desgaste, integridade, parâmetros). Cada nó referencia um *slot* da definição do veículo.
- **FastenerSystem**: cada slot lista fixadores `{posição local, tipo de cabeça, medida, torque Nm,
  estado de aperto, desgaste}`. Ferramentas declaram `{tipos de cabeça, medidas, torque máx.}`.
- **AssemblyDependencySystem**: pré-condições declarativas (`requires_removed: [wheel_fl]`,
  `requires_fasteners_loose`, `requires_support: jack_stand`). A remoção é permitida só se todas
  forem satisfeitas — validado no servidor.
- **ToolInteractionSystem**: raycast cliente → *ponto de interação* (não bloco); servidor revalida
  distância, ferramenta, pose, dependências. Lock por componente evita conflito em multiplayer.
- **Wear/Diagnostic**: desgaste acumulado por uso → sintomas (ruído, vibração, temperatura,
  códigos de falha) expostos só via ferramentas de inspeção, nunca revelados diretamente.
- O efeito sobre a física é sempre por **parâmetro**: pastilha gasta reduz `max_brake_force_n`,
  turbo altera a curva de torque, etc. A física não conhece "peças"; recebe uma definição efetiva.

## B. Bibliotecas (verificadas em maven.fabricmc.net em 2026-11-11)
| Item | Versão | Uso |
|---|---|---|
| Minecraft | 1.21.1 | alvo (1.21 "Tricky Trials", última da linha 1.21.x com Yarn estável e ecossistema maduro) |
| Fabric Loader | 0.20.1 | |
| Fabric API | 0.116.18+1.21.1 | networking, keybinds, HUD, render registry, reload listeners |
| Yarn | 1.21.1+build.3 | mapeamentos |
| Fabric Loom | 1.11.8 | build (Gradle 8.14.3 via wrapper) |
| Java | 21 | |

**Sem dependências extras na Fase 1.** Candidatas avaliadas para fases futuras (verificar versão
exata para 1.21.1 antes de adotar):
- *GeckoLib* — animação de entidades por keyframes exportados do Blockbench. Útil para portas,
  capô, pose do piloto. **Decisão: não usar para a carroceria/rodas** (movidas por estado físico,
  não por keyframes); avaliar só para animações de personagem.
- *Cloth Config / ModMenu* — telas de configuração (Fase 3).
- *Iris/Sodium* — compatibilidade testada, não dependência. O mod deve ficar bom sem shaders.

## C. Pipeline de modelagem, animação e renderização

**Escolha:** Blockbench (modelagem/UV/pivôs) → exportação **OBJ/JSON próprio** → *baker* em build
time → malha estática por parte, renderizada com `VertexConsumer` e transformações por parte
calculadas do estado físico. Blender para peças orgânicas (bancos, curvas de carroceria) com
retopologia para baixo número de polígonos, importadas no Blockbench para pivôs e UV final.

Justificativa: o formato `ModelPart` vanilla só aceita cubos alinhados — insuficiente para capô
curvo, para-brisa inclinado e rodas com raios. Malhas arbitrárias (triângulos/quads) são
necessárias e o Minecraft as renderiza bem se forem *baked* em buffers por material.

1. **Criação**: blocking de proporção em Blockbench sobre *blueprint* lateral/frontal/superior
   em escala 1 bloco = 1 m. Detalhe em malha poligonal (Blockbench "Generic Model" ou Blender).
2. **Organização**: um grupo por componente físico (`body/hood`, `wheel_fl/rim`, `brake_fl/caliper`…);
   os nomes coincidem com os *slots* da definição — é o elo entre mecânica e geometria.
3. **Pivôs/hierarquia**: pivô no eixo real de rotação (centro do cubo da roda, dobradiça da porta,
   eixo da coluna de direção). Filhos herdam: `steer_fl → hub → wheel → rim`.
4. **UV**: atlas por veículo 1024² (Ultra) / 512² (Low), texel density uniforme (≈64 px/m), ilhas
   separadas por material para permitir troca de pintura sem retexturizar.
5. **Texturas/materiais**: *máscaras* em vez de cores fixas — canal de pintura tingido em runtime
   (cor + acabamento), cromado/metal com textura de reflexo fake (matcap), vidro em camada
   translúcida, emissivos em passe com luz máxima (já usado no protótipo para faróis/freio).
6. **Exportação**: OBJ por componente + JSON de pivôs/slots, convertidos por tarefa Gradle em
   formato binário compacto em `assets/velocity/vehicle_models/`.
7. **Carregamento**: reload listener de cliente lê os modelos, monta buffers por (parte, material).
8. **Animação**: procedural pelo estado (roda = odômetro/raio, direção = ângulo de esterço,
   suspensão = compressão simulada, inclinação da moto = ângulo de rolagem). Keyframes só para
   eventos discretos (porta, capô) com curvas de easing.
9. **LOD**: LOD0 completo (≤ 8 m), LOD1 sem interior/motor (≤ 32 m), LOD2 casca + rodas simplificadas;
   interior só renderizado se a câmera estiver dentro ou a porta aberta. Peças sob o carro só com
   o veículo elevado ou câmera de maca.
10. **Consistência**: guia de estilo (escala, densidade de texel, paleta de materiais, raio mínimo
    de chanfro), checklist do §F e revisão lado a lado com veículos já aprovados.

## D. Estrutura de diretórios
```
velocity/
├─ build.gradle, settings.gradle, gradle.properties, gradlew
├─ docs/ARCHITECTURE.md
├─ art/                         (fontes .bbmodel/.blend — Fase 2, não empacotadas)
└─ src/
   ├─ main/java/dev/velocity/
   │  ├─ core/        VelocityMod, VelocityRegistries
   │  ├─ vehicle/     VehicleEntity, VehicleDefinition(s), VehicleSpawnerItem
   │  ├─ physics/     VehiclePhysics, VehicleState, VehicleInput, SurfaceGrip
   │  ├─ network/     payloads + VelocityNetwork
   │  ├─ mechanics/   (Fase 5) graph, fasteners, dependencies, wear, diagnostic
   │  └─ garage/      (Fase 5) equipamentos/blocos
   ├─ main/resources/
   │  ├─ fabric.mod.json
   │  ├─ data/velocity/velocity_vehicles/*.json      ← definições técnicas
   │  └─ assets/velocity/{lang,textures,models,vehicle_models,sounds}
   ├─ client/java/dev/velocity/client/
   │  ├─ VelocityClient
   │  ├─ input/  render/  hud/  audio/  screen/
   └─ test/java/…  (JUnit da física)
```

## E. Primeiro carro esportivo — Aurion VX (marca fictícia Aurion Motorworks)

**Conceito**: cupê 2+0 de motor dianteiro-central (V6 biturbo atrás do eixo dianteiro), tração
traseira. Silhueta "long hood / short deck", cabine recuada, para-lamas traseiros musculosos.

**Proporções** (m): comprimento 4,40 · largura 1,92 · altura 1,24 · entre-eixos 2,62 ·
balanço dianteiro 0,92 / traseiro 0,86 · bitolas 1,62/1,64 · rodas 19" diant. 245/35, 20" tras. 285/30
(raio rolante ≈ 0,34 m) · altura do solo 0,11 m.

**Desenhos conceituais (descrição)**
- *Lateral*: linha de cintura sobe suavemente da roda dianteira para a traseira; coluna A bem
  inclinada (≈ 62°), coluna C em "barbatana" separada do vidro traseiro; entrada de ar lateral
  atrás da porta alimentando intercoolers; saia lateral em carbono.
- *Frente*: capô com dois vincos convergindo para um emblema em seta; faróis finos em "L" com
  4 projetores LED e DRL superior; grade inferior trapezoidal larga com splitter.
- *Traseira*: lanterna contínua de lado a lado com assinatura em 3 barras; difusor de 4 aletas;
  4 saídas de escape em dois pares; spoiler ativo que sobe a 90 km/h.
- *Interior*: cockpit orientado ao motorista; painel digital de 12,3" em capota; volante de base
  reta com aletas; console alto com seletor curto; bancos concha com apoio de cabeça integrado.

**Hierarquia de componentes (slots)**
```
vehicle
├─ body: hood, roof, door_l{shell, handle, window, mirror{housing, glass}}, door_r{…},
│        fender_fl/fr, quarter_l/r, bumper_front{grille, splitter, fog_l/r},
│        bumper_rear{diffuser, exhaust_tips×4}, trunk_lid{spoiler_active}, side_skirt_l/r,
│        headlight_l/r{housing, projector×4, drl}, taillight_bar, windshield, rear_glass
├─ interior: dashboard{cluster_display, infotainment, vents×4}, steering_column{wheel, paddles},
│            seat_l/r, console{shifter}, pedals{accel, brake}, door_card_l/r, rear_view_mirror
├─ chassis: subframe_front/rear, jack_points×4, underbody_panel×3
├─ suspension_fl/fr/rl/rr: upper_arm, lower_arm, coilover, knuckle, hub
├─ wheel_fl/fr/rl/rr: rim, tire, lug_nut×5          ← fixadores do 1º teste de manutenção
├─ brake_fl/fr/rl/rr: disc, caliper{bolt×2, pad×2}
├─ powertrain: engine_v6tt{…}, transmission_dct6, driveshaft, differential_rear
├─ exhaust: downpipe_l/r, mid_pipe, muffler, tips
└─ electrical/cooling/fuel: battery, radiator, intercooler_l/r, fuel_tank, fuel_pump
```

## F. Critérios objetivos de "veículo visualmente completo"
Um veículo só é **completo** se TODOS os itens passarem:
1. Silhueta reconhecível em vista lateral a 32 blocos (teste cego com 3 avaliadores vs. outros 5 veículos).
2. Proporções medidas no modelo dentro de ±3 % da ficha (§E).
3. Nenhuma superfície externa visível é um cubo sem chanfro maior que 0,3 m de aresta.
4. ≥ 1 entrada de ar, ≥ 2 vincos/linhas de carroceria, faróis e lanternas com ≥ 3 elementos internos cada.
5. Rodas: aro com raios modelados (não textura), pneu com ombro, disco + pinça visíveis através do aro.
6. Interior: painel, volante animado, bancos com encosto e apoio, console, pedais, forro de porta.
7. Todas as peças móveis com pivô correto: roda gira sem "bambolear"; porta abre sem atravessar o para-lama.
8. Materiais: pintura tingível, vidro translúcido, ≥ 1 emissivo, cromado/metal distinto de plástico.
9. Parte inferior modelada (subchassi, escape, cárter, braços) — sem textura plana.
10. Orçamento: LOD0 ≤ 12k triângulos (carro) / 8k (moto), ≤ 4 draw calls por veículo em LOD0.
11. Funciona sem shaders e com Iris/Sodium sem artefatos.

## Status (o que existe de verdade nesta revisão)
| Item | Estado |
|---|---|
| Projeto Gradle/Loom, split client/server | ✅ compila (`./gradlew build`) |
| Entidade `velocity:vehicle`, item de colocação, NBT, recolher (shift+ataque) | ✅ compila; ✅ servidor dedicado inicia |
| Definições JSON via data pack + validação + log de erro | ✅ carregada em servidor real |
| Física longitudinal, câmbio automático, esterçamento, aderência por bloco, combustível | ✅ 8 testes JUnit |
| Rede C2S validada (entrada, motor, faróis, buzina) | ✅ compila; ⚠️ não testado com cliente |
| Carregador OBJ + JSON (pivôs, hierarquia, materiais), luzes emissivas | ✅ teste visual automatizado no jogo (Xvfb + Mesa) |
| HUD mínima | ✅ compila; ⚠️ não capturada no teste visual (HUD oculta) |
| Modelo 3D do Aurion VX | ⚠️ carroceria, freios/suspensão e interior com volante animado (~13k tri, 39 partes); parte inferior e animações de portas PENDENTES |
| Sons próprios | ❌ PENDENTE (sons vanilla temporários) |
| Inclinação de terreno, suspensão, colisão com dano visual | ❌ PENDENTE (Fase 3) |
| Motos, garagem, mecânica, pintura | ❌ PENDENTE (Fases 4–6, seção 19) |
