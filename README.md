# VELOCITY — Ultimate Vehicle Mod

Mod de veículos para **Minecraft Java 1.21.1 / Fabric / Java 21**. Atualmente na **Fase 1 (fundação)**.
Arquitetura, pipeline de assets e critérios de qualidade: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Compilar
```bash
./gradlew build          # jar em build/libs/
./gradlew test           # testes da física
./gradlew runClient      # abre o Minecraft de desenvolvimento
```

## Testar no jogo (Fase 1)
1. `./gradlew runClient`, crie um mundo em modo criativo.
2. Na aba **Ferramentas**, pegue **Aurion VX (Protótipo)** e clique com o botão direito no chão.
3. Clique com o botão direito no veículo para entrar. **N** liga o motor.
4. **W** acelera, **S** freia (parado: ré), **A/D** esterça, **Espaço** freio de mão, **J** faróis, **H** buzina.
5. **Shift** sai. **Shift + ataque** recolhe o veículo.
6. Teste aderência: dirija sobre gelo, areia e grama.

> ⚠️ O modelo visual atual é um **protótipo de cubos para validar pivôs e animação**, não o asset final.
> Veja a tabela de status no fim do documento de arquitetura.
