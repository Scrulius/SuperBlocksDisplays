# Plan: reescritura de la animación a la API nativa de Display (sin comandos)

> ✅ **IMPLEMENTADO (2026-06-11, v1.1.0)** con el diseño híbrido descrito abajo + fallback por
> comando para payloads no mapeables (detección de residuo). Pendiente de verificación visual
> en el server (orientación de la matriz: si sale deformado, quitar el `.transpose()` en
> `AnimationManager.tryParse`).

Objetivo: eliminar `dispatchCommand` por tick, el toggle de gamerules, `SilentCommandSender`
y `CommandFeedbackFilter` del **camino de animación**. Mantener `/summon` para el spawn (una vez).
Confirmado con los datos (`Ranas_animadas`): 1174 keyframes mutan `transformation`+`interpolation_*`,
18 mutan `block_state`, **0 mutan `item:`** → 100% viable SIN NMS.

## Mapeo API
- `transformation:[16 floats]` → `Display.setTransformationMatrix(Matrix4f)`
- `interpolation_duration:N` → `Display.setInterpolationDuration(N)`
- `start_interpolation:K` → `Display.setInterpolationDelay(K)` (normalmente 0)
- `block_state:{Name,Properties}` → `BlockDisplay.setBlock(Bukkit.createBlockData("name[k=v,...]"))`

## ⚠️ Orientación de la matriz (el ÚNICO riesgo, verificar a ojo en el server)
MC da los 16 floats en **row-major**; JOML `Matrix4f` es **column-major**.
Construir así: `new Matrix4f().set(floats).transpose()`.
(`set(float[])` los lee column-major → queda la transpuesta → `.transpose()` la corrige.)
Si el modelo sale DEFORMADO al probar: quitar el `.transpose()` (o usar el constructor explícito
`new Matrix4f(m00,m10,m20,m30, m01,m11,m21,m31, m02,m12,m22,m32, m03,m13,m23,m33)`).

## Resolver bde_N → entidad (robusto a recarga de chunk)
- En `ModelGroup.spawn`, ya guardamos la UUID de cada parte y su scoreboard tag `bde_N`.
  Añadir un `Map<String,UUID> tagToUuid` (clave "bde_0"...) poblado al taggear cada parte
  (leer su tag `bde_\d+` de la NBT o de `getScoreboardTags()`).
- En runtime resolver con `Bukkit.getEntity(uuid)` (lookup O(1)); si null (chunk descargado) → skip.
  NO cachear la referencia Entity (se invalida al recargar chunk); cachear la UUID sí.

## Estructura compilada (sustituye a la actual de comandos horneados)
`CompiledAnim { int maxTick; Map<Integer,List<FrameAction>> framesByTick; }`
`FrameAction { UUID target; Matrix4f matrix /*nullable*/; int interpDuration; int interpDelay; BlockData block /*nullable*/; }`
Compilar una vez por grupo (cache en AnimationManager, invalidar en `removeGroup`).

### Parsing del payload (regex, sin libs)
- `transformation:\[([^\]]*)\]` → split `,`, quitar sufijo `f`, 16 floats.
- `interpolation_duration:(-?\d+)`
- `start_interpolation:(-?\d+)`
- `block_state:\{Name:"([^"]+)"(?:,Properties:\{([^}]*)\})?\}` → Properties `k:"v"` → `k=v`
  (quitar comillas), construir `name[k=v,...]` para `Bukkit.createBlockData`.

## run() nuevo (main thread, sin gamerules)
```
for cada grupo animando+ready con anim "default":
    compiled = cache.computeIfAbsent(gid, compile(group))
    avanzar tick(s) con el accumulator (igual que ahora)
    para currentAnimTick: for FrameAction fa in frame:
        Entity e = Bukkit.getEntity(fa.target); if e==null or !(e instanceof Display d) continue;
        d.setInterpolationDelay(fa.interpDelay);
        d.setInterpolationDuration(fa.interpDuration);
        if (fa.matrix != null) d.setTransformationMatrix(fa.matrix);
        if (fa.block != null && d instanceof BlockDisplay bd) bd.setBlock(fa.block);
    mantener el fix de modo "once" (renderizar último frame antes de parar)
```
Quitar `dispatchBatch`, gamerules, `SilentCommandSender` del path de animación.

## Limpieza posible tras la reescritura
- `SilentCommandSender` y `CommandFeedbackFilter`: SOLO los sigue necesitando el `/summon` del spawn
  (que emite "Summoned new"). Se pueden dejar para el spawn, o también migrar el spawn a la API
  (`world.spawn(loc, BlockDisplay.class, e -> {...})`) — pero el spawn por SNBT/comando es más simple
  y es de una vez, así que de momento DEJARLO con su toggle puntual.
- `compile()` actual (que hornea strings de comando) se reemplaza por el que produce `FrameAction`s.

## Verificación (la hace el autor en su server, no testeable con Mock ni en build)
1. `gradlew`/`mvn package`, copiar jar, REINICIAR (no hot-swap).
2. Spawnear `Ranas_animadas`, ver que anima fluido y NO deformado (si deforme → tema matriz arriba).
3. Confirmar que YA NO aparecen toggles de gamerule ni spam, y que el feedback de comandos de un OP
   durante la animación funciona normal.
4. `block_state` (la vela de la rana): comprobar que cambia de estado en el frame correspondiente.
