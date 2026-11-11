# VELOCITY — Patch Notes

Downloads: pasta [`releases/`](releases/). Instale o jar em `.minecraft/mods` junto com o
**Fabric Loader ≥ 0.16** e a **Fabric API** para **Minecraft 1.21.1**.

---

## Velo 0.02 — Modelos 3D de verdade
**Jar:** `releases/Velocity-Velo-0.02-MC1.21.1.jar` · Minecraft 1.21.1 · Fabric API 0.116.18

![Velo 0.02](docs/screenshots/velo-0.02.png)

### Novidades
- **Carregador de malhas 3D (OBJ):** veículos agora são desenhados com polígonos de verdade,
  não com cubos. Isso libera carrocerias curvas, rodas com raios, interiores etc.
- Partes com **pivôs e hierarquia** definidos em JSON (`assets/<mod>/vehicle_models/<id>.json`):
  rodas giram em torno do próprio eixo e as dianteiras esterçam em torno do pivô de direção.
- **Materiais por papel:** pintura (cor aplicada em tempo real — base para a pintura customizada),
  vidro translúcido, emissivo (faróis e lanternas acendem) e opaco.
- **Novo visual do protótipo Aurion VX** (~4.000 triângulos): carroceria esculpida por seções,
  caixas de roda recortadas, cabine com para-brisa inclinado, rodas com 5 raios duplos,
  pneus com banda e ombro, retrovisores, grade, difusor e 2 saídas de escape.
- Recarregar resource packs (**F3+T**) recarrega os modelos; um modelo com erro aparece no log
  e o veículo usa o modelo antigo de cubos em vez de sumir.

### Correções
- Luz de freio não acendia ao pisar no freio com o carro parado e motor desligado
  (o carro engatava ré). Agora a ré só engata com o motor ligado.

### Testado
- 9 testes automáticos da física passam.
- **Primeiro teste visual dentro do jogo:** o Minecraft foi aberto (renderização por software),
  o carro foi colocado num mundo e fotografado de 9 ângulos, de dia e de noite.
  Confirmado: modelo aparece, rodas no lugar, faróis e luz de freio acendem,
  e esterçar para a direita vira o carro para a direita.

### Limitações conhecidas
- Ainda é um **protótipo**: o desenho definitivo do Aurion VX vem na Velo 0.03.
- Sem interior (a cabine é fechada) e sem discos/pinças de freio visíveis (Velo 0.04/0.05).
- Cores sólidas por material, sem texturas detalhadas ainda.
- Sons temporários; atraso de controle em servidores; sem suspensão (inalterados).

---

## Velo 0.01 — Fundação
**Jar:** `releases/Velocity-Velo-0.01-MC1.21.1.jar` · Minecraft 1.21.1 · Fabric API 0.116.18

### Novidades
- Primeiro veículo dirigível: **Aurion VX (Protótipo)**, na aba *Ferramentas* do criativo.
- Física simulada no servidor: curva de torque do motor, 6 marchas com câmbio automático,
  arrasto aerodinâmico, resistência ao rolamento, freio, freio-motor e freio de mão.
- Aderência por superfície: asfalto/pedra, grama, cascalho, areia, lama, neve e gelo.
- Ré automática: com o carro parado, segure **S**.
- Combustível com consumo proporcional à potência; o motor desliga quando o tanque esvazia.
- Faróis (**J**) e luz de freio acendem com brilho total, mesmo à noite.
- Buzina (**H**) e ligar/desligar motor (**N**). Teclas remapeáveis em *Controles → VELOCITY*.
- HUD: velocidade em km/h, marcha, barra de RPM, combustível e estado do motor.
- Batidas reduzem a integridade do veículo. **Shift + ataque** recolhe o veículo como item.
- Veículos definidos por arquivos JSON (data pack), sem precisar de código novo.

### Testado
- Compila; 8 testes automáticos da física passam (0–100 km/h em 3,4 s).
- Servidor dedicado inicia e carrega a definição do veículo.

### Não testado / limitações conhecidas
- **Não testado com o jogo aberto** (o ambiente de build não tem placa de vídeo).
- O visual é um **modelo provisório feito de cubos**, não o carro final.
- Sons de motor/buzina são sons do Minecraft temporários.
- O sentido em que as rodas dianteiras viram pode estar invertido.
- ~1 ping de atraso nos controles em servidores (o servidor decide o movimento).
- Sem suspensão nem inclinação em rampas; sobe degraus de até 0,6 bloco.
- Velocidade máxima (~319 km/h) está alta para este motor; recalibração na Velo 0.08.
- A lista de mods do Fabric mostra a versão como "0.1" (o nome do mod mostra "Velo 0.01").
