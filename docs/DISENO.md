# Autoescuela — Documento de diseño

Versión del documento: 2026-10-14 (2.ª sesión: lecciones, banco ampliado, rediseño). Estado del proyecto: ver [PROGRESO.md](PROGRESO.md).

## 1. Objetivo y alcance

Aplicación de escritorio (Windows, Linux, macOS) y Android para preparar el **permiso de conducir B** en
España, de uso privado (no se publica). Tres partes:

1. **Aprende a conducir**: lecciones guiadas paso a paso para quien nunca ha conducido (pedales, embrague y punto
   de fricción, arrancar, frenar, cambiar de marcha, marcha atrás, girar, calado, arranque en cuesta), en el mismo
   simulador, con objetivos que se comprueban con el estado del coche.
2. **Simulador 3D de conducción** en primera persona de un coche de cambio manual, con físicas realistas, para
   practicar las maniobras del examen práctico de la DGT y ejercicios típicos de principiante, con evaluación
   automática de faltas y veredicto APTO / NO APTO.
3. **Teoría y tests**: temario, tests por tema, simulacro del examen oficial (30 preguntas, 30 minutos, máximo
   3 fallos), repaso de falladas, estadísticas e importación de bancos de preguntas propios.

Coche de referencia: **Renault Clio III 1.5 dCi 85 CV** (2005-2012), el habitual de autoescuela: dimensiones,
motor diésel, caja de 5 marchas, cuadro analógico, palanca con anillo de desbloqueo para la marcha atrás.

### Fuera de alcance (por ahora)

Tráfico con otros vehículos en movimiento, peatones, semáforos activos, meteorología, modo multijugador,
publicación en tiendas.

## 2. Decisiones de base

| Decisión | Motivo |
|---|---|
| libGDX 1.14.2, Java 17, layout gdx-liftoff (`core`, `lwjgl3`, `android`) | Es la pila que usa el resto de proyectos del workspace; un solo código para escritorio y Android. |
| gdx-bullet para la física del coche | `btRaycastVehicle` da suspensión, dirección y frenos estables con poco código; funciona en Android (minSdk 21 + multidex, reglas R8 para el wrapper JNI). |
| Transmisión propia (motor, embrague, caja) sobre bullet | Bullet solo aporta fuerzas en las ruedas; el realismo del embrague, el calado y las marchas es el núcleo pedagógico y se modela aparte, en código probado sin ventana. |
| gdx-gltf para modelos PBR | Permite usar modelos glTF de Sketchfab/Blender con materiales realistas; con placeholders en código mientras no haya assets. |
| Skin de scene2d construida en código, textos en `strings.properties` | Convención del workspace; sin ficheros de skin externos. |
| Datos en JSON (`vehicle.json`, escenarios, temas, preguntas) | Afinar y añadir contenido sin recompilar; recarga en caliente del coche con F5. |
| Paso fijo determinista (1/120 s) | La evaluación y los tests de regresión no dependen de la tasa de refresco. |
| Banco de preguntas propio + importador | No se copian bancos comerciales de autoescuelas; las preguntas se redactan a partir de los reglamentos (RGC, RGCond., RGV) y el usuario puede importar lo que tenga. |
| Assets externos CC-BY/CC0, descargados por el usuario | Uso privado; cada asset queda anotado en `assets/CREDITS.md`. |

## 3. Arquitectura

```
lwjgl3/   Lwjgl3Launcher (escritorio), CarModelCheck (gradlew checkCar), SignGenerator (gradlew signs)
android/  AndroidLauncher, natives bullet/freetype para 4 ABIs
core/     com.pelayo.autoescuela
  AutoescuelaGame      Game: Bullet.init, Assets, Settings, InputMapping, I18n, Skin, teoría perezosa
  app/                 Assets (AssetManager + loaders glTF + fuentes FreeType), Settings (Preferences),
                       Progress (lecciones hechas, prácticas superadas), I18n, DevOptions, Screenshots
  data/                POJOs Json: VehicleSpec, ScenarioDef, TemaDef, PreguntaDef, StatsData
  sim/                 PhysicsWorld, FixedStepper, ContactRouter, BodyTag
    vehicle/           Engine, Clutch, Gearbox, Drivetrain, BulletVehicle, Vehicle, VehicleState, VehicleEvent
    world/             Scenario, ScenarioLoader, ScenarioGeometry, Zone, CurbLine, Marking
    eval/              Maneuver, Fault, FaultSeverity, EvaluationResult, Verdict, ManeuverContext,
                       ManeuverRegistry, probes/ (MovementCounter, SpeedStats), maneuvers/ (9 maniobras y Tutorial)
  input/               DriverInput, DriverInputState, InputMapping, KeyboardMouseDriverInput,
                       GamepadDriverInput, TouchDriverInput, ScriptedDriverInput, CompositeDriverInput,
                       PedalSmoother
  render/              SceneRenderer (gdx-gltf, sombras, niebla), CarView, PlaceholderModels, MarkingRenderer,
                       CockpitCamera, env/ (EnvTextures, EnvModels, SceneryBuilder),
                       ChaseCamera, MirrorRenderer, PhysicsDebugView
  audio/               ProceduralSounds (caché WAV), SfxRecipes + Dsp (síntesis), EngineAudio + LoopVoice (mezcla)
  theory/              TheoryRepository, TestGenerator, TestSession, ExamRules, StatsStore, QuestionImporter
  ui/                  SkinFactory, Shapes (formas SDF con antialiasing), Icons, screens/ (Loading, MainMenu, PracticeMenu, Driving, Results, Theory, Topic,
                       Test, TestResult, Stats, Import, Settings, Controls), widgets/ (HudOverlay,
                       ControlsOverlay, TutorialPanel, TouchControls, QuestionCard, RichText)
assets/   data/, i18n/, fonts/, models/, sounds/, theory/; en escritorio también save/, cache/, import/
tools/    blender/prepare_car.py + configuraciones JSON por modelo
docs/     este documento, PROGRESO.md, ASSETS.md, BLENDER.md, FORMATO_PREGUNTAS.md
```

### Flujo de pantallas

```
Loading → MainMenu ─┬─ PracticeMenu [Aprende | Ejercicios | Examen] → Driving → Results
                    │        (Repetir | Siguiente lección | PracticeMenu)
                    ├─ Theory ─┬─ Topic → Test
                    │          ├─ Test (tema / aleatorio / repaso / examen) → TestResult
                    │          ├─ Stats
                    │          └─ Import
                    └─ Settings → Controls
```

Cada pantalla es un `ScreenAdapter` con su propio `Stage`; se construyen al entrar y se liberan al salir.
`AutoescuelaGame` mantiene los servicios compartidos. Las opciones de desarrollo (`DevOptions`) permiten
arrancar directamente en cualquier pantalla o escenario.

## 4. Simulación del coche

### 4.1 Capa física (bullet)

- Mundo `btDiscreteDynamicsWorld`, gravedad 9,81, paso fijo `DT = 1/120 s` sin subpasos (`stepSimulation(DT, 0, DT)`).
- Chasis: `btRigidBody` con `btCompoundShape` que contiene una caja desplazada hacia arriba, de modo que el
  origen del cuerpo (centro de masas) queda a `comHeight` (0,38 m) del suelo y la caja empieza en los bajos
  (`groundClearance` más el hundimiento estático de la suspensión). Grupo de colisión `GROUP_PLAYER`.
- `btRaycastVehicle` con cuatro ruedas; parámetros de suspensión, agarre y dirección en `vehicle.json`.
- Qué pisa cada rueda: `btRaycastVehicle` no informa del objeto (devuelve un cuerpo fijo interno), así que
  `BulletVehicle` lanza un rayo corto propio alrededor del punto de contacto, filtrado al grupo del escenario y
  con `kF_UseGjkConvexCastRaytest` (el raycast por defecto es impreciso contra cajas grandes). El terreno base es
  un `btStaticPlaneShape` y las calzadas largas se trocean en losas de ≤ 50 m por el mismo motivo.
- Velocidad: `btRaycastVehicle` aplica los impulsos de suspensión y freno después de integrar, así que la velocidad
  leída lleva un residuo (~0,1 m/s con el coche frenado y quieto). `Vehicle` la toma como 0 si los frenos actúan y
  es menor de 0,25 m/s; si no, ninguna maniobra detectaría "parado".
- Contactos del chasis: `ContactRouter` (`ContactListener.onContactAdded` con `userValue`) anota por paso con
  qué etiqueta (`BodyTag`: ROAD, CURB, CONE, BARRIER, PARKED_CAR, OTHER) ha chocado el chasis.

Ejes: **+Z adelante, +Y arriba, +X izquierda** (sistema diestro de bullet con `setCoordinateSystem(0,1,2)`).
Rumbo `yawDeg` = 0 hacia +Z, crece hacia la izquierda.

### 4.2 Transmisión (`sim/vehicle`)

Orden por paso fijo en `Vehicle.step`:

1. `DriverInput.poll` rellena `DriverInputState` (ejes 0..1, pulsos).
2. Dirección: el ángulo de las ruedas persigue el mando a velocidad limitada.
3. `Drivetrain.step`:
   - `Gearbox`: cambio pedido solo con embrague > 0,85 (si no, evento `GRIND` y no entra); marcha atrás solo con
     |v| < 0,5 m/s (`REVERSE_REFUSED`); durante `shiftTime` la relación es 0.
   - Velocidad del eje primario `ω_in = v_fwd / r_rueda · ratio · grupo`.
   - `Engine.produceTorque(gas)`: mapa de pedal progresivo (`gas^0,6`), par bruto = `eff · (curva(rpm) + freno
     motor)`, regulador de ralentí `max(bruto, clamp(Kp·(idle − rpm) + drag(idle), 0, idleTorqueMax))`, menos el
     freno motor `drag(rpm) = c0 + c1·rpm/1000`. Corte a `revLimitRpm`. Regulador de todo régimen: con pedal p el gasóleo se recorta linealmente entre
     `idle + p·(corte − idle)` y 450 rpm más (sin carga el motor se estabiliza ahí).
   - `Clutch.solve`: acoplamiento de fricción resuelto de forma implícita. Capacidad `cap = smoothstep(bite) ·
     maxTorque`; par de bloqueo `T_lock = T_e − I_e · (ω_in − ω_e) / dt`; si `|T_lock| ≤ cap` el motor queda
     solidario (`T_c = T_lock`), si no patina (`T_c = ±cap`). Estable con cualquier paso.
   - `Engine.integrate(T_e − T_c)`: integra el cigüeñal; calado si `rpm < stallRpm` más de 0,12 s con el motor
     en marcha; arranque con motor de arranque hasta 520 rpm; evento `START_IN_GEAR` si se arranca con marcha
     y sin embrague (el motor de arranque empuja el coche).
   - Par en rueda `T_c · ratio · eficiencia`.
4. Fuerza de tracción repartida en las ruedas motrices (`applyEngineForce`), frenos con reparto 65/35 y freno
   de mano en las traseras (`setBrake`), resistencia a la rodadura y aerodinámica como fuerza central.
5. `physics.step()` y lectura del `VehicleState` (pose, velocidades, rpm, marcha, pedales, contactos de rueda,
   eventos).

Parámetros del Clio III en `assets/data/vehicle.json` (masa 1.165 kg, batalla 2,575, par 200 Nm a 1.900 rpm,
corte 4.800, relaciones JR5 3,73/2,05/1,32/0,97/0,76, R 3,55, grupo 3,59, embrague 230 Nm). Se documenta cada
valor en el propio JSON y se recarga con F5.

### 4.3 Entrada

`DriverInput` se consulta una vez por **paso fijo** (nunca por frame). Implementaciones:

- `KeyboardMouseDriverInput`: pedales digitales convertidos en analógicos por `PedalSmoother` (rampas distintas
  al pisar y al soltar; el embrague suelta despacio para encontrar el punto de fricción; tecla de medio
  embrague que lo mantiene en 0,55). Dirección con ritmo y auto-centrado. Mirar con teclas o arrastrando con
  el botón derecho. Las teclas vienen de `InputMapping` (reasignables, guardadas en Preferences).
- `GamepadDriverInput` (gdx-controllers): gatillos analógicos, sticks, botones.
- `TouchDriverInput` + `TouchControls`: volante arrastrable, pedales por posición del dedo, botones.
- `ScriptedDriverInput`: guion `t:clave=valor|...` para capturas y regresión (incluye `continue` para las lecciones).
- `CompositeDriverInput`: fusiona fuentes (máximo por eje, OR en pulsos).

## 5. Escenarios y evaluación

### 5.1 Definición (`assets/data/scenarios/*.json`, `ScenarioDef`)

- `modules`: piezas físicas y visuales: `road`, `sidewalk` (bordillo con etiqueta CURB), `slope`, `road_arc`,
  `sidewalk_arc` (arcos por tramos de 5°), `cone` (dinámico, 1,2 kg), `parked_car`, `wall`, `block`, `island`,
  `sign` (solo visual).
- `curbs`: polilíneas (o arcos) del borde del bordillo a nivel de calzada, para medir distancias.
- `markings`: `LANE`, `STOP` (pintadas), `BAY` (plaza rectangular, con contorno pintado) y `GUIDE` (eje
  invisible para la evaluación).
- `zones`: rectángulos con nombre que usan las maniobras (`finish`, `exit_done`, `far_end`...).
- `params`: límites de la maniobra (movimientos, distancia al bordillo, tiempo, retrocesos...).
- `startPose` con `pitchDeg` para empezar en cuesta; `engineOff` para ejercicios de arranque.

`ScenarioLoader` crea cuerpos bullet y modelos (placeholder o glTF del catálogo `models/env/<tipo>.glb`);
`ScenarioGeometry` es la parte puramente geométrica que usan los evaluadores, y se puede construir sin bullet
para los tests.

### 5.2 Evaluación (`sim/eval`)

`Maneuver` es una máquina de fases que recibe el `VehicleState` cada paso. La clase base aplica las faltas
comunes: tocar bordillo (deficiente) / subirse (eliminatoria), cono tocado o desplazado (deficiente), colisión
con coche o valla (eliminatoria), salirse de la calzada (deficiente), calar (leve, 3 veces deficiente), tiempo.
Cada maniobra añade sus criterios:

| Maniobra | Criterios específicos |
|---|---|
| `PARALLEL_PARKING` (I.1) | ≤ 3 movimientos para entrar y para salir; ruedas del lado del bordillo ≤ 0,30 m (leve hasta 0,50, deficiente más); paralelo ± 5°; parado con freno de mano ≥ 1 s dentro de la plaza. |
| `BAY_PARKING` (I.2, I.3) | Todas las ruedas dentro de la plaza, descentrado ≤ 0,25 m, alineación ± 5°, no pisar las líneas, ≤ 3 movimientos en cada sentido. |
| `REVERSE_CURVE` (G) | Parar ≥ 10 m después de la curva; una sola marcha atrás; velocidad uniforme (desviación típica ≤ 0,4 m/s); centrado ≤ 0,6 m respecto al eje guía; no pisar líneas. |
| `TURN_AROUND` (H) | Entrar por la mitad derecha de la calle; exactamente una marcha atrás; salir con rumbo invertido (± 25°). |
| `HILL_START` | Retroceso ≤ 0,3 m (leve) / ≤ 1,0 m (deficiente) hasta llegar a la zona alta. |
| `START_NO_STALL` | Arrancar con el motor apagado y avanzar hasta la zona final; cuenta calados. |
| `STOP_SIGN` | Parada completa ≥ 1 s con las ruedas delanteras antes de la línea; cruzar sin parar es eliminatoria; parar sobre la línea, deficiente. |
| `ROUNDABOUT` | Entrar en el anillo, salir por la salida indicada (otra: deficiente), intermitente derecho antes de salir (leve). |
| `FREE_DRIVE` | Sin evaluación. |
| `TUTORIAL` | Lección guiada (ver 5.3): sin veredicto; termina al completar el último paso. |

`MovementCounter` cuenta movimientos: un cambio de sentido cuenta cuando se recorren > 15 cm en el nuevo
sentido. `Verdict`: NO APTO con 1 eliminatoria, 2 deficientes o 10 leves. El resultado lleva faltas con
tiempo, métricas y veredicto a `ResultsScreen`.

### 5.3 Lecciones guiadas ("Aprende a conducir")

Escenarios con `category: "lesson"`, maniobra `TUTORIAL` y un bloque `lesson` (`ScenarioDef.LessonDef`):

- `steps`: cada paso tiene `title`, `text` (explicación), `goals`, `hold` (segundos que deben cumplirse todos los
  objetivos a la vez), `highlight` (mandos que el HUD señala con un halo ámbar), `hint` (pista tras `hintAfter`
  segundos) y `ack` (paso de lectura que se supera con Continuar, tecla C).
- Tipos de objetivo: `clutch`, `brake`, `throttle`, `steer` (rango `min`/`max`), `rpm`, `speed` (km/h), `gear`
  (`value`), `handbrake`, `engine`, `stopped`, `moving`, `distance` (metros en el paso; negativos marcha atrás),
  `zone`, `indicator`, `look` (mirar atrás) y `event` (p. ej. `STALL`).
- En los textos, `{ACCION}` (nombre de `InputMapping.Action`) se sustituye por la tecla asignada, resaltada.
- `Tutorial` evalúa los objetivos cada paso fijo; `TutorialPanel` muestra paso, explicación, objetivos con su marca,
  pistas (también inmediatas tras calar, rascar o arrancar con marcha) y Continuar. Recolocar reinicia la lección.
- Al terminar, `ResultsScreen` muestra "Lección completada", el resumen `summary` y el botón a la siguiente;
  `Progress` lo guarda y el menú marca la lección.
- Lecciones: 1 mandos, 2 arrancar y salir, 3 frenar, 4 cambiar de marcha, 5 marcha atrás, 6 girar (eslalon),
  7 calado y recuperación, 8 arranque en cuesta. `LessonsTest` comprueba con un alumno automático que todas se
  pueden completar con la física real.

## 6. Render

- `SceneRenderer`: `SceneManager` de gdx-gltf con shader PBR, sol direccional, IBL procedural de exterior y
  cielo. GL30 en escritorio y Android (con fallback a GL20).
- `CarView`: enlaza el modelo del coche con el estado. Carga `assets/models/car/car.glb` si existe
  (convención de nodos en [ASSETS.md](ASSETS.md)); si no, placeholder de `PlaceholderModels` con los mismos
  nombres de nodo. Mueve chasis, ruedas (transformaciones de bullet), volante (giro sobre su Y local según las
  vueltas de volante) y palanca (patrón en H simplificado).
- Cámaras: `CockpitCamera` en `eyeX/Y/Z` de `vehicle.json` si `eyeOverride` (ahora 0,37; 0,80; -0,30: volante,
  cuadro y espejos a la vista) o, si no, en el nodo `driver_eye` con giro de cuello suavizado; `ChaseCamera` exterior y
  cenital (90 m) para depuración y revisión de escenarios.
- `MirrorRenderer` (los laterales se voltean también en vertical y miran hacia fuera; sin skybox): por cada material `mirror_center/left/right` renderiza la escena desde el centro de esa
  parte de malla mirando hacia atrás en un framebuffer (proyección reflejada en X, caras invertidas) y la pinta
  como textura emisiva. Opciones: sin espejos / interior / todos; en Android alterna un espejo por frame.
- Entorno: `EnvTextures` genera texturas periódicas con mipmaps (asfalto, hierba, baldosa, fachadas, hormigón,
  tejado) y `ScenarioLoader` las aplica a escala real según el tipo de cuerpo (calzada, bordillo, barrera); un módulo
  con `color` propio sigue siendo liso. `SceneryBuilder` añade, fuera del rectángulo que ocupan los módulos, un
  decorado solo visual en un único modelo (una parte por material): edificios, baldosa, farolas y árboles si hay
  aceras; casas y arboledas si no; colinas lejanas. Los coches aparcados usan `car.glb` con otra pintura (material
  `Body`) y apoyados en el suelo por su caja.
- Sombras: `DirectionalShadowLight` de 70 x 70 m centrada en el suelo delante de la cámara (2048 en calidad alta,
  1024 en media o Android, sin sombras en baja). Niebla exponencial hacia el color del horizonte.
- `MarkingRenderer`: marcas viales como losetas finas; `PhysicsDebugView`: alambre de bullet (F3).

## 7. Audio

Todo el sonido se sintetiza en código (`SfxRecipes`, con utilidades de `Dsp`) y `ProceduralSounds` lo guarda
como WAV en `cache/sfx/v<VERSION>/` la primera vez (en paralelo, ~0,1 s en escritorio; subir `VERSION` al cambiar
la síntesis regenera la caché y borra la anterior). Si existen `assets/sounds/<nombre>.ogg|wav|mp3`, se usan en
lugar de los sintetizados (nombres en [ASSETS.md](ASSETS.md)).

- **Motor**: bucles de ~1 s a 800, 1.500, 2.500 y 4.000 rpm, cada uno con y sin carga. Cada bucle contiene un
  número entero de ciclos (2 vueltas) y se construye periódico (impulsos circulares, filtros "en anillo"), así
  enlaza sin clic. Capas: impulsos de combustión de los 4 cilindros (rpm/30 por segundo) con pequeñas
  diferencias fijas entre cilindros, su paso por escape (grave, resonancia de ~90 Hz) y bloque (medios), rumor
  de la frecuencia de explosión, traqueteo de inyección (ruido metálico corto con inyección piloto), ruido
  mecánico y bramido de admisión con carga.
- **Mezcla** (`EngineAudio`, solo a partir de `VehicleState`): tono = rpm / referencia (0,5–2), fundido cruzado
  de potencia constante en escala logarítmica entre las dos referencias vecinas (ningún bucle con peso
  apreciable se estira más de ~±35 %); la carga estimada (pedal con el mapa de `Engine` o gasóleo del regulador
  de ralentí; 0 en retención y en el corte del limitador) reparte entre las versiones con/sin carga y sube el
  volumen; turbo con retardo; rodadura y viento por velocidad; chirrido por frenazo, freno de mano en marcha o
  aceleración lateral; silbido de la marcha atrás. Cada bucle (`LoopVoice`) suaviza su volumen y solo ocupa
  una voz mientras suena (libGDX tiene 16 en escritorio y en Android).
- **Efectos**: arranque (se corta con fundido al prender), calado, parada con llave, rascada, palanca, freno de
  mano (carraca/golpe), relé del intermitente (tic-tac, ~85 por minuto), bordillo (rueda que lo sube o baja) y
  golpes del chasis contra cono, valla o coche. Los eventos y contactos de un paso se procesan una sola vez
  aunque `update` se llame varias veces con el mismo estado; lo demás se deduce de cambios en el estado.

## 8. Teoría

- Datos: `temas.json` (12 temas con secciones en marcado mínimo, revisados contra el BOE consolidado de octubre de
  2026) y `preguntas.json` (359 propias, 30 por tema, 16 con imagen de señal; `PreguntaDef`
  con 3 opciones, correcta, explicación, imagen opcional, dificultad, fuente). Formato en
  [FORMATO_PREGUNTAS.md](FORMATO_PREGUNTAS.md).
- `TheoryRepository`: carga y valida (ids únicos, 3 opciones, tema existente) el banco propio y el del usuario
  (`save/preguntas_usuario.json`).
- `TestGenerator`: por tema, aleatorio, repaso de falladas y simulacro (30 sin repetir, al menos una por tema y
  reparto proporcional; barajado Fisher-Yates con semilla para reproducibilidad en tests).
- Las opciones se barajan en cada test (`PreguntaDef.withShuffledOptions`), salvo si alguna alude a las demás.
- `TestSession`: respuestas, cronómetro (30 min en examen), corrección, veredicto (`ExamRules`: ≤ 3 fallos).
- `StatsStore` (`save/stats.json`): aciertos por tema, falladas pendientes (se retiran tras 2 aciertos seguidos
  en repaso), historial de simulacros.
- `QuestionImporter`: JSON o CSV (`;`), deduplica por enunciado normalizado, genera ids, copia imágenes.
- UI: lista de temas con porcentaje, lectura con `RichText`, test con corrección inmediata (práctica) o
  diferida con navegación y cronómetro (examen), resultado con repaso de falladas, estadísticas, importación.

## 9. Interfaz

Skin en código (`SkinFactory`): fondo azul noche con degradado, acento azul, verde/rojo/ámbar; botones y paneles
con esquinas redondeadas y círculos, anillos, flechas e iconos rasterizados con antialiasing a partir de funciones de
distancia (`Shapes`, `Icons`), sin ficheros de imagen. Fuentes Roboto con FreeType; las "negritas" se generan con un
contorno del mismo color. Menú principal con tres tarjetas y su progreso; menú de prácticas con pestañas.
Cuadro de instrumentos vivo en el salpicadero (`DashboardCluster`, sustituye la textura `Compteur`), con opción de
ocultar los relojes de pantalla en la cabina. Fuentes generadas a la densidad del monitor; tamaño del HUD ajustable
(la resolución virtual del HUD se multiplica por 0,92-1,3). En táctil, mandos repartidos por manos: pedales a un lado (por defecto a la izquierda, ajuste «Pedales táctiles») y palanca en H con freno de mano y llave al otro, para pisar el embrague con una mano mientras la otra cambia o gira; el volante 3D se toca directamente y, en táctil, se dibuja con media vuelta de tope a tope para seguir al dedo. La velocidad y las revoluciones van en una tira sobre los pedales; el retrovisor derecho sube junto a los botones si debajo del minimapa no cabe. Color de la carrocería elegible en Ajustes (`CarPaint`, colores oficiales del Clio III; `CarView.setPaint` cambia el
material `Body`, pasando el sRGB de la muestra a lineal). Minimapa (`MiniMap`, gira con el coche y resalta las zonas objetivo de `Maneuver.getTargetZones`), menú de pausa
(Esc/P) y, en las lecciones, navegación entre pasos (B atrás, C continuar). HUD de conducción (`HudOverlay`): volante
indicador, sugerencia de cambio de marcha, cuentarrevoluciones de aguja con la velocidad en el centro, marcha con su
posición en el patrón H del Clio, pedales con su tecla y la franja del punto de fricción, testigos (freno de mano,
intermitentes, motor), instrucción de la maniobra, avisos y halos para el tutorial. Panel de controles (`ControlsOverlay`) con todas las
teclas y la explicación del embrague; se muestra al empezar y con F1. Reasignación en `ControlsScreen`.

## 10. Pipeline de assets

- Coche: modelo glTF con la convención de nodos y materiales de [ASSETS.md](ASSETS.md). El script
  `tools/blender/prepare_car.py` (Blender 4/5, sin interfaz) aplana la jerarquía, orienta, escala, separa las
  ruedas por esquina, extrae volante y palanca por región, reparte el material de los espejos, pone los
  cristales translúcidos, sitúa el centro de masas en el origen, crea `driver_eye` y exporta. Configuración
  por modelo en JSON (`clio_rs_2007.json`). `gradlew checkCar` valida el `.glb`. Guía en [BLENDER.md](BLENDER.md).
- Señales: `gradlew signs` dibuja con Java2D 26 señales del catálogo oficial en `assets/theory/signs/`.
- Entorno: catálogo opcional `models/env/<tipo>.glb`; mientras no exista, placeholders.
- Sonidos: `assets/sounds/`; créditos en `assets/CREDITS.md`.

## 11. Desarrollo y verificación

- Tests JUnit 5 con `gdx-backend-headless` (y natives de bullet para los de física), `workingDir = assets`:
  física del coche (reposo, tracción, dirección, pendiente), transmisión (ralentí, calado, punto de fricción,
  rascada, marcha atrás, freno motor, arranque en pendiente), evaluadores con un coche sintético (`SyntheticCar`)
  sobre los escenarios reales, carga de todos los escenarios, contactos, teoría (banco, examen, sesión,
  estadísticas, importador).
- Opciones de línea de comandos (`--scenario`, `--screen`, `--script`, `--shots`, `--wshots`, `--exit`,
  `--abort`, `--touch`, `--notouch`, `--nohelp`) para reproducir situaciones y capturar pantallas sin
  intervención; es la forma de verificar visualmente desde la terminal (la simulación de teclas por sistema no
  funciona por el foco de ventana).
- `gradlew core:test`, `gradlew lwjgl3:run`, `gradlew android:assembleDebug`, `gradlew checkCar`.

## 12. Riesgos y limitaciones conocidas

- `btRaycastVehicle` no modela inercia de rueda ni deslizamiento longitudinal: a velocidades de maniobra es
  correcto, pero no sirve para derrapes.
- El raycast de bullet contra convexos grandes es impreciso: mitigado con plano base, losas ≤ 50 m y GJK.
- Un solo `ContactListener` global de gdx-bullet: `ContactRouter` es el único.
- Rendimiento en Android con PBR y tres espejos: ajustes de calidad y alternancia de espejos; pendiente de
  medir en dispositivo.
- Los modelos de Sketchfab traen jerarquías y escalas arbitrarias: el script de Blender se configura por modelo.
