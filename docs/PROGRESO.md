# Autoescuela — Progreso

Última actualización: 2026-10-14. Diseño: [DISENO.md](DISENO.md).

## Estado por hitos

| Hito | Contenido | Estado |
|---|---|---|
| M0 | Esqueleto Gradle (core, lwjgl3, android), menú, fuentes, i18n, tests headless con bullet | Hecho (13-10) |
| M1 | Coche bullet sobre suelo, placeholder, cámara de cabina, teclado y mando | Hecho (13-10) |
| M2 | Transmisión realista (motor, embrague, caja, calado), HUD, audio sintetizado | Hecho (13-10) |
| M3 | Escenarios JSON, contactos, evaluación de maniobras, I.1, menú de prácticas, resultados | Hecho (13-10) |
| M4 | G, H, I.2, I.3, arranque sin calar, arranque en pendiente, STOP, rotonda; arcos, isletas, señales | Hecho (13-10) |
| M5 | Teoría: 12 temas, 60 preguntas, tests, simulacro, estadísticas, importador JSON/CSV | Hecho (13-10) |
| M5b | Banco de 300+ preguntas con imágenes de señales | Hecho (14-10): 359 preguntas, 26 señales generadas, temario revisado contra el BOE |
| M6 | Carga glTF, espejos por FBO, sonidos externos, documentación de assets | Hecho (13/14-10). Modelo del Clio III integrado el 14-10 |
| M7 | Controles táctiles, ajustes de calidad, registro en publish-apk | Hecho en código (13-10); sin probar en dispositivo |
| M8 | Ajustes, pausa, README, reasignación de teclas, ayuda de controles | Hecho (13/14-10); inglés pendiente |
| M9 | "Aprende a conducir": 8 lecciones guiadas paso a paso, rediseño visual (skin, HUD, menús), cámara de cabina | Hecho (14-10) |
| M10 | Mapas con texturas, sombras, niebla y decorado; sonido nuevo; minimapa, pausa, navegación de lecciones; espejos y táctil corregidos | Hecho (14-10) |

## Verificado

- `gradlew core:test`: 60 tests en verde (física, transmisión, evaluadores, escenarios, contactos, teoría, banco
  ampliado, barajado de opciones y lecciones). `LessonsTest` hace que un alumno automático (`LessonBot`) complete
  las 8 lecciones con la física real (entre 6 y 42 s simulados cada una).
- `gradlew android:assembleDebug`: APK de depuración con natives de bullet y freetype en 4 ABIs (~15 MB).
- Capturas automáticas desde la app: menú, conducción en cabina y exterior, HUD, escenario I.1 con coches
  aparcados y marcas, resultados, pantallas de teoría, test, examen, estadísticas, ajustes, controles táctiles,
  panel de controles, reasignación de teclas, Clio real con ruedas, volante giratorio y espejos. El 14-10 (2.ª
  sesión): menú principal y de prácticas nuevos, lecciones con su panel, HUD nuevo, pantalla de lección completada,
  teoría, examen, ajustes y controles táctiles restilizados; cámara de cabina con volante, cuadro y espejos a la vista.
- `gradlew checkCar` sobre `car.glb`: todos los nodos y materiales de espejo presentes; batalla 2,60 m, vía
  1,55 m, radio de rueda 0,314 m.

## Registro de cambios

### 2026-10-13

- Plan aprobado (M0–M8). Decisiones: Clio III 1.5 dCi 85 CV, uso privado, banco propio + importador, assets
  CC-BY aceptados.
- M0–M5 completos, M6–M8 en su mayor parte. Commit inicial `3d6062e` (154 ficheros).
- Problemas resueltos por el camino: la caja del chasis rozaba el asfalto (altura libre y hundimiento estático);
  `btRaycastVehicle` no informa del objeto bajo la rueda (rayo propio); raycast impreciso contra cajas enormes
  (plano base, losas ≤ 50 m, GJK); `ModelBuilder` ignora `globalTransform` en los nodos (usar
  `translation`/`rotation`); par de motor insuficiente a bajas vueltas (regulador de ralentí tipo diésel y mapa
  de pedal progresivo); `Array.shuffle` sin semilla (Fisher-Yates propio).

### 2026-10-14

- Guía y script de Blender; comprobador `checkCar`.
- Modelo "Renault_clio_rs_2007_3ds (1)" de Fister (Sketchfab, CC-BY-4.0) procesado sin interfaz y exportado
  a `assets/models/car/car.glb`; `MirrorRenderer` localiza los espejos por material en lugar de por nodo.
- Panel de controles en la práctica (F1, visible al empezar), explicación del embrague, teclas para mirar a
  los lados y atrás, etiquetas de tecla en el HUD, reasignación de teclas en Ajustes → Controles.
- Commit `ae668b1` con todo lo anterior.

### 2026-10-14 (2.ª sesión): tutorial, banco de preguntas y rediseño

- **Aprende a conducir**: 8 lecciones (`l1_controls` … `l8_hill`): mandos, arrancar y salir, frenar, cambiar de marcha,
  marcha atrás, girar, qué hacer si se cala y arranque en cuesta. Maniobra `TUTORIAL` (pasos con objetivos que se
  comprueban con el estado del coche), `TutorialPanel`, resaltado de mandos en el HUD, tecla Continuar (C), progreso
  guardado (`Progress`) y pantalla final con lo aprendido y paso a la siguiente lección.
- **Banco de preguntas**: de 60 a 359 (30 por tema; 16 con imagen de señal). Redactadas a partir de la normativa y
  comprobadas contra el BOE consolidado, incluidas las reformas del RGC por el RD 465/2025 (renumeración del Título
  IV) y el RD 518/2026 (en vigor el 1-10-2026: carril de emergencia, -20 km/h y 1,5 m al adelantar a ciclistas,
  5 m tras un ciclista en ciudad, casco y 15 años en VMP, fin de la exención del cinturón para taxistas…). Se
  corrigieron errores del temario y de preguntas existentes (ruedas al aparcar en pendiente, novel a los 2 años,
  detector de radar = 3 puntos, citas de artículos…). Las opciones se barajan en cada test.
- **Señales**: `gradlew signs` (Java2D) genera 26 señales en `assets/theory/signs/`.
- **Rediseño**: skin con esquinas redondeadas y formas con antialiasing (`Shapes`, `Icons`), fuentes en negrita,
  fondo degradado, menú principal con tarjetas y progreso, menú de prácticas con pestañas, HUD nuevo (aguja,
  patrón H de marchas, franja del punto de fricción, testigos), controles táctiles con el mismo estilo.
- **Cámara de cabina**: los ojos estaban dentro del pilar A; ahora en (0,37; 0,80; -0,30) con `eyeOverride` en
  `vehicle.json` (opciones `--eye` y `--fov` para afinar).
- **Fallo corregido**: con el coche frenado y quieto, bullet informaba de ~0,1 m/s (impulsos de la rueda aplicados
  tras integrar) y nunca se llegaba a "parado"; con frenos actuando, por debajo de 0,25 m/s se toma como 0.
- Otros: banner de instrucciones descentrado, colores del HUD, avisos con la tecla asignada, maniobra abandonada
  ya no dice "¡Perfecto!", recolocar respeta "motor apagado".

### 2026-10-14 (3.ª sesión): mapas, sonido, HUD y correcciones

- **Lecciones**: se puede volver a pasos anteriores (botones < > del panel o tecla B) y avanzar hasta el paso al que
  se había llegado; un paso revisitado queda "en repaso" y se sigue con Continuar (no se da por superado solo).
- **Mapas**: texturas generadas en código (`render/env/EnvTextures`: asfalto, hierba, baldosa, fachadas con ventanas
  y persianas, hormigón, tejado), sombras del sol que siguen a la cámara (`DirectionalShadowLight`, según la
  calidad), niebla que funde el horizonte, suelo de 3 km, decorado automático alrededor de cada escenario
  (`SceneryBuilder`: calles con edificios de varias plantas, baldosa, farolas, árboles, colinas lejanas; rural si no
  hay aceras) y coches aparcados con el modelo real del Clio en ocho colores.
- **Sonido** (`audio/`): motor diésel con cuatro bucles (800-4.000 rpm) en versión con y sin carga y fundido cruzado,
  traqueteo de inyección, turbo, rodadura, viento, chirrido de neumáticos, relé del intermitente, trinquete del freno
  de mano, golpe de palanca, rascada, calado, motor de arranque (antes no sonaba al arrancar con embrague),
  bordillo y golpes. Caché en `cache/sfx/v2/`; `ProceduralSoundsTest` comprueba niveles y empalmes.
- **HUD y usabilidad**: minimapa que gira con el coche y resalta la zona objetivo; volante indicador; sugerencia de
  cambio de marcha (subir a partir de 2.200 rpm, bajar por debajo de 1.050); menú de pausa con Esc o P (continuar,
  reiniciar, controles, salir) en lugar de salir directamente; botón de pausa en táctil.
- **Fallos corregidos**:
  - Espejos laterales invertidos (suelo arriba) y mirando hacia dentro del coche: volteo vertical en la proyección
    y giro hacia fuera; sin cielo de fondo en ellos (su shader no admite la proyección volteada). `--shots` vuelca
    además las imágenes de los espejos a `cache/mirror_*.png`.
  - Botón táctil del freno de mano parpadeando: al reflejar el estado del coche se volvía a "pulsar" en cada frame
    (eventos programáticos de scene2d); lo mismo pasaba con las marchas.
  - Con los controles táctiles activados, las barras de pedales no mostraban el teclado: los pedales y el volante de
    pantalla reflejan ahora el estado real del coche.

- **Color del coche** (Ajustes): 16 colores oficiales de la gama Clio III fase 1 con su nombre de catálogo, código de
  pintura y acabado (`app/CarPaint`: p. ej. Bleu Iceberg RNK nacarado, Gris Platine D69, Rouge Vif 727). Los RGB son
  aproximados a la vista; Renault no los publica. Los coches aparcados también usan colores de la gama.
  `--paint=BLEU_ICEBERG` prueba un color sin tocar los ajustes guardados.

- **Cuadro de instrumentos en el salpicadero** (`render/DashboardCluster`): la textura del material `Compteur` del Clio
  (128 x 32 px en el modelo) se sustituye por un framebuffer de 1024 x 256 que se redibuja cada frame en la cabina:
  cuentarrevoluciones, velocímetro, pantalla central con marcha y velocidad, intermitentes, freno de mano, combustible
  y temperatura. Las UV del modelo van en espejo horizontal y con V negativa: se compensa en la proyección. Ajustes →
  "Relojes en la cabina": en pantalla (por defecto) o solo en el salpicadero (oculta los de la interfaz en la cabina).
  `--shots` vuelca la textura a `cache/cluster.png` (sale invertida en el PNG, es normal).
- **Táctil**: la columna de botones R-N-1..5 (que se solapaba con el minimapa) se sustituye por una palanca en H a la
  derecha del volante, como en el coche; los mandos se recolocan según el tamaño real de la pantalla
  (`TouchControls.relayout`). El botón de pausa va junto al minimapa.
- **Resolución y tamaño del HUD**: las fuentes se generan a la resolución del monitor (hasta x2,5) con mipmaps y se
  reducen, en vez de ampliar las de 720p (se veían borrosas). Ajustes → "Tamaño del HUD": pequeño, normal (algo más
  pequeño que antes) o grande.

- **Motor apagado evidente**: el cuadro del salpicadero se apaga (sin retroiluminación, solo los testigos rojos de
  batería y aceite) y en la interfaz el cuentarrevoluciones se oscurece con "MOTOR APAGADO" y cómo arrancar; con los
  relojes solo en el salpicadero, el aviso va en una franja abajo.
- **"El coche se mueve al girar con el motor apagado"**: medido, girando 10 s de tope a tope se desplaza < 1 cm. Lo que
  se veía era el asentamiento inicial: el coche aparecía unos centímetros en el aire y botaba el primer segundo. Ahora
  `Vehicle.settle(1,5 s)` lo deja asentado antes de empezar y al recolocar.
- **Volante**: el indicador de volante del HUD solo sale con las cámaras exteriores (en la cabina ya se ve el del coche).
  En táctil, la zona para girar se coloca encima del volante 3D (se proyecta su centro y su aro a la pantalla) con un
  aro tenue de guía; fuera de la cabina se dibuja el volante abajo en el centro. El reloj táctil pasa a la izquierda.
- **Retrovisor derecho en pantalla** (`MirrorInset`, opción en Ajustes, activada por defecto): recuadro bajo el
  minimapa con la imagen del espejo derecho; se oculta al mirar hacia ese lado y con las cámaras exteriores.
- Ajustes en un panel desplazable (ya no cabían).

- **Acelerador**: el motor sin carga subía hasta el corte con cualquier toque de gas (el par pedido no dependía de
  las vueltas). Ahora `Engine` tiene un regulador de todo régimen como un diésel: cada posición del pedal tiene un
  techo de vueltas (10 % ≈ 1.400 rpm, 25 % ≈ 2.100, 50 % ≈ 3.100 en punto muerto) y con carga apenas influye. Con
  teclado, el pedal baja en 1 s en vez de 0,33 s (un toque es poco gas). `FreeRevTest` lo comprueba, y también que
  con medio embrague y sin gas el coche avanza a ralentí (unos 6-7 km/h en primera) sin calarse.

### 2026-10-14 (4.ª sesión): revisión del táctil, diseño y preguntas de la DGT

- **Táctil rediseñado** tras revisarlo a proporción de móvil (`--size=1600x720 --touch`):
  - Mandos colocados como en el coche: intermitentes a los lados del volante (se encienden en verde al parpadear),
    llave a la derecha del volante (se ilumina con el motor en marcha), freno de mano "P" sobre la palanca en H,
    cámara y recolocar junto al minimapa; botones redondos de 64 unidades con iconos (`Icons`: key, camera, reset).
  - Arrastrar el dedo por cualquier zona libre mira alrededor (al soltar vuelve al frente); el volante solo
    responde dentro de su círculo.
  - El pedal de embrague marca la franja del punto de fricción.
  - Las lecciones y los avisos nombran los mandos táctiles ("el pedal izquierdo", "el botón de la llave"...) en vez
    de las teclas del PC (`InputMapping.setNameOverride`, claves `touchname.*`).
  - El panel de la lección va arriba a la izquierda también en táctil; el aviso de motor apagado, en el parabrisas.
- **Diseño**: temario centrado con títulos de sección destacados; tarjeta del test ajustada a su contenido y con
  barra de progreso; barras de progreso vacías sin el trocito azul; gravedad de las faltas en etiquetas de color;
  botón "Seguir aprendiendo: lección N" en el menú principal.
- **Preguntas de la DGT**: el simulador oficial de la sede (`sedeweb.dgt.gob.es/WEB_AUTO-9`) solo permite uso personal
  y privado y necesita JavaScript; no se usa. La revista de la DGT "Tráfico y Seguridad Vial" publica un test en cada
  número y su aviso legal autoriza reproducir los textos citando la fuente (no las imágenes). `gradlew dgtRevista`
  (`RevistaDgtTests`) descarga el texto de los números 261-279 (2022-2026) a `assets/import/revista_dgt_bruto.json`;
  revisadas una a una (tema, vigencia con la normativa de octubre de 2026, explicación propia), las válidas van a
  `assets/data/preguntas_dgt.json` con el campo `fuente`, que la tarjeta de la pregunta muestra siempre.
  Resultado: de 261 preguntas descargadas quedan **196** (bancos propio + DGT: 555). Descartadas 65: 34 dependen de
  la imagen, 4 quedaron desfasadas por el RD 465/2025 o el RD 518/2026 (cinturón del repartidor, señal V-1 de la
  policía, menores en bici), 4 son ambiguas, 8 son de moto y 15 están repetidas. `TheoryTest` comprueba que todas
  tienen fuente y que no repiten ids con el banco propio.
  Ningún banco "abierto" de GitHub tenía licencia válida (copian bancos comerciales).

- **Bancos del simulador de la DGT y de TodoTest** (con permiso del usuario): 173 preguntas del simulador oficial
  (90 normales y 87 de lectura fácil, menos repetidas; 30 de lectura fácil venían sin respuesta y se dedujeron a mano)
  y 2.024 de TodoTest (de 2.133; las demás estaban repetidas o mal formadas). Después, 15 revisores las comprobaron
  contra el BOE vigente: quedan 160 del simulador y 1.780 de TodoTest, todas con tema revisado y explicación propia
  (descartadas 206 repetidas, 27 ambiguas, 15 desfasadas por el RD 518/2026 o la V-16, y unas pocas no verificables;
  17 claves corregidas, 15 del simulador mal registradas por el descargador). Total del banco: 2.495.
  La app admite ahora preguntas de dos opciones. Ver FORMATO_PREGUNTAS.md.

### 2026-10-14 (5.ª sesión): táctil por manos
- El volante 3D giraba al revés (a la izquierda se veía girar a la derecha): corregido el signo en `CarView`.
  Con controles táctiles se dibuja con 1 vuelta de tope a tope (`setWheelTurnsShown`), así sigue al dedo.
- Disposición táctil por manos: pedales a un lado y palanca + freno de mano + llave al otro (ajuste
  `PedalSide`, por defecto pedales a la izquierda). Los intermitentes nunca pisan esos grupos; el retrovisor en
  pantalla se va arriba si no cabe; tira compacta de velocidad y rpm sobre los pedales. Sin solapes con HUD
  pequeño, normal y grande a 20:9 (`--hud=small|normal|large`, `--pedals=left|right` para capturas).
- Embrague y calado revisados (`DrivetrainTest`, 25 casos): se cala al soltar de golpe sin gas, al frenar hasta
  parar sin pisar el embrague, contra el freno de mano, saliendo en 3ª, con una marcha larga a poca velocidad y
  yéndose hacia atrás con la 1ª; no se cala con el embrague pisado, en punto muerto, soltando despacio, ni en la
  fricción con gas. El par del embrague entra ahora progresivo en la franja de fricción (`t²` en vez de
  smoothstep): el coche se arrastra al ralentí en casi dos tercios de la franja, más fácil de dosificar en el móvil.
- Colisiones con el decorado: edificios (cajas), troncos de árbol y farolas (cilindros) y postes de las señales
  son obstáculos rígidos (`BodyTag.BARRIER`): golpe, sonido y falta eliminatoria en las prácticas.
- Habitáculo en antracita (materiales `Interieur` y `Siege`, `CarView.darkenInterior`), también en los aparcados.
- Ajustes desde el menú de pausa: se abren los mismos ajustes y, al volver, la práctica sigue donde estaba y se
  aplican volumen, tamaño del HUD, controles táctiles y lado de los pedales, color, espejos y retrovisor en pantalla
  (`DrivingScreen.applySettings`); la calidad gráfica, en la siguiente práctica.

### 2026-10-14 (6.ª sesión): tráfico, examen por recorrido y mejoras de uso
- **Tráfico y peatones** (`sim/world/TrafficSystem`, `TrafficAgent`, `traffic` en el JSON del escenario): coches y
  peatones con cuerpo cinemático que siguen un camino (polilínea o arco, en bucle o ida y vuelta, con disparo por
  distancia), frenan ante lo que tienen delante y se desatascan en los cruces. Faltas: chocar
  (`TRAFFIC_CAR`/`PEDESTRIAN`), obligar a un coche con preferencia a frenar fuerte (`noYield`) y pasar por un
  paso de peatones con alguien cruzando (`pedestrianNotYielded`), todas eliminatorias. Glorieta y STOP con
  tráfico; escenario nuevo `crosswalk` (maniobra CROSSWALK) con marca `ZEBRA`. `TrafficTest`.
- **Examen por recorrido** (`exam_route`, maniobra ROUTE, `route` en el JSON): indicaciones del examinador por
  tramos con velocidad máxima, STOP, intermitente y calles prohibidas; se leen en alto (`app/Speech`: Android
  TextToSpeech, Windows System.Speech; ajuste «Voz del examinador»). `RouteTest`. Fachadas a lo largo de las
  aceras (`SceneryBuilder.streetFronts`) para que las calles parezcan calles.
- **Repetición en los resultados** (`ReplayMap` sobre `MapPainter`, compartido con el minimapa): recorrido grabado
  cada 0,1 s (`EvaluationResult.trace`), faltas numeradas donde ocurrieron, reproducir, barra de tiempo y tocar
  una falta para ir a ese momento.
- **Repaso inteligente de teoría**: repetición espaciada (cajas de Leitner en `StatsData.cards`, 10 min / 1, 3, 7,
  14 y 30 días), test que mezcla lo que toca repasar con preguntas nuevas de los temas flojos, y estimación de
  aprobado (`theory/Readiness`: fallos esperados por examen y probabilidad de no pasar de 3).
- **Móvil**: vibración en el punto de fricción, al calar y al chocar; embrague táctil que se queda (opcional);
  minimapa plegable (toque o Tab); vista de cabina «Más carretera»; primera configuración guiada
  (`OnboardingScreen`); FPS opcionales y bajada automática de calidad (quita sombras en marcha:
  `SceneRenderer.setShadows`).
- **Icono propio** (`gradlew appIcon`, `AppIconGenerator`): volante blanco con una «L» sobre azul de autoescuela.
- **APK release** (`publish-apk.ps1 -App Autoescuela -Release`): sin depuración, solo ARM, firmada con la clave
  debug del PC para instalarse encima sin perder el progreso.
- Código en GitHub (privado): https://github.com/PelayoCdC/Autoescuela.
- Sonido sintetizado más realista, imitando grabaciones desde el asiento (síntesis v3): motor con filtro de
  habitáculo, más irregularidad y bucles de 2 s; arranque más apagado con el tirón de cada compresión; cambio con
  la rejilla y el fuelle; choque de coche (masa, chapa, plástico, trocitos). `gradlew sfxPreview` para oírlos.

## Pendiente, por prioridad

1. **Afinar la conducción con el usuario al volante**: sensación de embrague, facilidad de calado, velocidad de
   la dirección con teclado, posición de los ojos y del volante en el Clio (`eye` en `clio_rs_2007.json`,
   `vehicle.json`).
2. **Probar el APK en el móvil**: rendimiento con PBR y espejos, controles táctiles, calidad baja/media.
3. **Banco de preguntas**: seguir ampliando (más preguntas con imagen: añadir señales a `SignGenerator`) y revisar
   el temario cuando cambie la normativa.
4. **Sonido**: escuchar con el usuario la síntesis v3 y afinarla (`gradlew sfxPreview`); el usuario prefiere
   sintetizado a descargar grabaciones.
5. **Entorno**: conos, señales y peatones siguen siendo modelos sencillos (se pueden poner glTF en
   `models/env/`); más recorridos de examen y tráfico que gire en los cruces.
6. **Tests de la revista de la DGT**: al salir números nuevos, `gradlew dgtRevista -Pdesde=280 -Phasta=...` y
   revisarlos como los anteriores.
7. **Más escenarios**: variantes más exigentes (hueco estrecho en I.1, curva a la izquierda en G, cuesta del
   15 %), incorporación a una vía rápida con tráfico.
8. **Pulido**: inglés (`strings_en.properties`), panel de
   depuración (F2) menos intrusivo, animar pedales y agujas del cuadro del Clio (nodos opcionales).
9. **Deuda técnica**: el chasis placeholder y el glTF comparten `CarView`, pero el glTF no expone pedales ni
   agujas; `TouchControls` sin probar con multitáctil real; `HudOverlay` dibuja con el píxel de la skin (sin
   antialiasing en el aro del tacómetro).

## Cómo retomar el trabajo

```
gradlew core:test                                   # todo en verde
gradlew lwjgl3:run                                  # jugar
gradlew lwjgl3:run --args="--scenario=i1_parallel_basic --script=0.5:handbrake,gear=1,clutch=1|1.5:throttle=0.4|3:clutch=0 --shots=4 --exit=5"
gradlew checkCar                                    # validar car.glb
"F:\Archivos de programa\Blender Foundation\Blender 5.1\blender.exe" --background --python tools\blender\prepare_car.py -- config=tools\blender\clio_rs_2007.json
```

Datos del usuario en escritorio: `assets/save/` (estadísticas, preguntas importadas), `assets/import/`
(bancos a importar), `assets/cache/` (sonidos sintetizados, capturas). Preferencias en `~/.prefs/autoescuela`
y `~/.prefs/autoescuela-keys`.
