# Pedidos e feedback do dono do projeto

Registro do que foi pedido, para orientar as próximas versões. Status atualizado a cada entrega.

## 2026-11-12 — Feedback após Velo 0.07

> "Tá muito lindo." — o exterior foi aprovado.

### 1. Interior está feio → refazer
- O interior atual (Velo 0.05) foi feito com blocos simples e não ficou bom.
- **Plano:** refazer o interior com geometria curva (loft, como a carroceria): painel esculpido,
  bancos com formato anatômico, console integrado, volante com aro redondo de verdade,
  materiais com textura (couro, alcântara, carbono), iluminação ambiente.
- **Status:** pendente — **Velo 0.08** (passa na frente da física).

### 2. Dano realista: amassa onde bate, explode conforme a velocidade
- Batida fraca → **amassa no ponto exato do impacto** (deformação local da malha 3D, não só
  número de vida). Peças podem quebrar/soltar (para-choque, farol, retrovisor, porta).
- Batida forte → dano estrutural, motor para, fumaça, fogo.
- Batida muito forte → **explosão proporcional à energia do impacto**.
- **Plano técnico:** colisão orientada (formato real do carro) → ponto e energia do impacto
  (½·m·v²) → deformação dos vértices próximos ao ponto (persistente e sincronizada) → limiares
  de energia por peça → fumaça/fogo/explosão. Explosão configurável (servidores podem desligar
  a destruição de blocos).
- **Status:** pendente — **Velo 0.09**.

### 3. Cada carro tem seu terreno ideal
- Esportivo é **sensível**: na grama/terra/pedra perde aderência, raspa o assoalho, quebra
  suspensão, para-choque e cárter com facilidade.
- 4x4 / **Monster Truck** é "ignorante": passa por cima de tudo, aguenta pancada.
- **Plano técnico:** cada veículo ganha no JSON altura do solo, ângulos de ataque/saída,
  resistência por peça, tração 4x4 e tipo de pneu; o terreno (bloco) causa desgaste e dano
  conforme velocidade e altura do solo.
- **Status:** pendente — **Velo 0.10** (junto com a primeira 4x4 / Monster Truck).

### 4. Montar o próprio carro com peças
- Escolher e trocar: **motor** (ex.: V8 aspirado), **turbo**, **nitro**, **suspensão** (até
  colocar suspensão de Monster Truck num esportivo), **aerofólio**, **vidro fumê**, rodas etc.
- Cada peça muda de verdade o carro: potência, peso, altura, aderência, visual e som.
- **Plano técnico:** sistema modular de peças com encaixes (slots) no JSON do veículo;
  "kits livres" permitem combinações malucas (Monster Truck + esportivo) com consequências
  físicas reais; som do motor gerado para cada motor (V8, I4, V12…) com o sintetizador.
- **Status:** pendente — **Velo 0.11–0.13** (oficina de montagem); une-se ao sistema de
  mecânica da seção 19 (parafusos, ferramentas, troca física de peças).

### 5. Som do motor não está bom → sistema de áudio completo por motor
- O som atual (V6 sintetizado da Velo 0.07) **não agradou**.
- **Cada motor tem que ter o seu som**: V8 aspirado, V6 biturbo, I4, I6, V10, V12, boxer,
  rotativo, diesel, motos, elétrico — timbre, ronco e marcha lenta próprios.
- **A marcha muda o som**: cada marcha tem caráter diferente (1ª gritando curta, marchas longas
  mais graves e cheias), queda de giro audível na troca, "pipoco"/estalo de escape ao soltar,
  corte de giro no limite.
- Turbo (assobio, alívio de pressão "psssh"), nitro, admissão e escape modificados também mudam o som.
- **Tem que estar bem completo.**
- **Plano técnico:** perfil de áudio por motor no JSON (ordem de ignição, nº de cilindros,
  escape, timbre); camadas mais densas (mais RPMs gravados, carga parcial); camada por marcha;
  eventos (troca, corte de giro, estalos, turbo); avaliar gravações livres/licenciadas além da
  síntese; som muda com as peças instaladas (pedido 4).
- **Status:** pendente — entra junto com a **montagem de carros (Velo 0.11–0.12)**, já que cada
  motor trocável precisa do próprio som; ajustes no som atual podem vir antes.
