# Assets 3D y sonido

Mientras no haya ficheros, la app genera placeholders en código (`PlaceholderModels`) y sonidos sintetizados
(`ProceduralSounds`). En cuanto se copien los ficheros descritos aquí, se usan automáticamente.

## Coche: `assets/models/car/car.glb` (o `car.gltf` + ficheros)

Modelo de referencia: Renault Clio III 1.5 dCi (2005-2012). Requisitos:

- Unidades en **metros**, eje **+Y arriba**, el coche mira hacia **+Z** y la **izquierda** del coche es **+X**
  (mismo sistema que la física; en Blender exportar con "+Y up" y girar el coche para que mire a +Z... en Blender
  eso es el eje -Y de Blender).
- El **origen** del modelo debe estar en el **centro de masas** aproximado: en el eje longitudinal, a
  `comHeight` (0,38 m) del suelo y `comOffsetZ` (0,15 m) por delante del punto medio entre ejes (ver `vehicle.json`).
- Nodos con estos nombres (los que falten se ignoran con un aviso en el log):

| Nodo               | Para qué                                                        |
|--------------------|-----------------------------------------------------------------|
| `wheel_fl` `wheel_fr` `wheel_rl` `wheel_rr` | Ruedas (su origen en el centro de la rueda; la física las coloca) |
| `steering_wheel`   | Volante: gira sobre su eje **Y local** (orientar el nodo con Y = eje del volante) |
| `gear_lever`       | Palanca: el origen en la base; se inclina según la marcha        |
| `driver_eye`       | Empty en la posición de los ojos del conductor (asiento izquierdo) |
| `pedal_clutch` `pedal_brake` `pedal_throttle` `handbrake_lever` | Opcionales, animación futura |
| `needle_rpm` `needle_speed` | Opcionales: agujas del cuadro (giran sobre Y local)          |

- Materiales con estos nombres: `mirror_center`, `mirror_left`, `mirror_right` (la cara reflectante con UV 0..1);
  la app les pinta la vista trasera como textura emisiva.
- Los cristales deben ser un material con `alphaMode BLEND` y alfa ~0,3 para ver a través.
- Tamaño recomendado: < 100k triángulos en total, texturas ≤ 2048 px (≤ 1024 en móvil).

Dónde buscar: Sketchfab (filtrar "downloadable", formato glTF, licencia CC-BY o CC0), buscando
"Renault Clio 3" / "Clio III". Hay que abrir el modelo en Blender para renombrar nodos según la tabla, colocar el
origen y exportar como glTF binario (.glb) con "Apply modifiers" y "+Y up".

## Entorno: `assets/models/env/<tipo>.glb`

Para cada tipo de módulo de escenario (`cone`, `parked_car`, `sign_stop`, `sign_yield`, `island`...) se puede dejar
un glTF con ese nombre; se instancia en lugar del placeholder. Origen en el suelo, centrado, mirando a +Z.
Las calzadas y aceras se siguen construyendo en código (cajas y arcos) por tamaño variable.

Fuentes CC0 recomendadas: Kenney (kenney.nl, "City Kit", "Car Kit"), Quaternius (quaternius.com), Poly Haven
(texturas de asfalto, hormigón).

## Sonido: `assets/sounds/`

Todos se sintetizan en código (`SfxRecipes`) y se guardan como WAV en `cache/sfx/v<versión>/` (local, fuera de
git) la primera vez; al cambiar la síntesis se sube `ProceduralSounds.VERSION` y se regeneran. Cualquiera de
ellos se puede sustituir dejando `assets/sounds/<nombre>.ogg` (o `.wav`, `.mp3`). Mono, 44,1 kHz recomendado.
Los **bucles** deben enlazar sin corte (cortar en un cruce por cero o con un número entero de periodos).

**Bucles de motor** (diésel 1.5 dCi visto desde la cabina). Se mezclan con fundido cruzado por rpm y el tono se
ajusta a las rpm (rango 0,5–2 del original), así que cada muestra debe grabarse a esas rpm estables:

| Fichero                                   | Uso                                                        |
|-------------------------------------------|------------------------------------------------------------|
| `engine_800_on` / `engine_800_off`        | Ralentí (800 rpm), con carga (gas) / sin carga (retención) |
| `engine_1500_on` / `engine_1500_off`      | 1.500 rpm                                                  |
| `engine_2500_on` / `engine_2500_off`      | 2.500 rpm                                                  |
| `engine_4000_on` / `engine_4000_off`      | 4.000 rpm                                                  |
| `engine_<rpm>`                            | Si solo hay una toma a esas rpm, sirve para con y sin carga |
| `engine_low` + `engine_high`              | Formato antiguo (~1.000 y ~3.000 rpm): se usan solo si están los dos y no hay ningún `engine_<rpm>*` |

**Otros bucles** (volumen y tono los pone la mezcla):

| Fichero        | Uso                                                                  |
|----------------|----------------------------------------------------------------------|
| `turbo`        | Silbido/soplido del turbo (sube con gas y rpm, con retardo)          |
| `road`         | Rodadura de neumáticos sobre asfalto, grabada a ~50 km/h             |
| `wind`         | Viento en la carrocería, grabado a ~100 km/h                         |
| `skid`         | Chirrido de neumáticos (frenazo, freno de mano en marcha, curva rápida) |
| `gear_whine`   | Silbido de engranajes de la marcha atrás, a ~2.000 rpm de motor      |

**Efectos sueltos**:

| Fichero          | Uso                                                                     |
|------------------|-------------------------------------------------------------------------|
| `starter`        | Motor de arranque (≥ 1,2 s; se corta con fundido al prender el motor)   |
| `stall`          | Calado: el motor se ahoga y se para con una sacudida                    |
| `engine_off`     | Parada con la llave                                                     |
| `grind`          | Rascada de marcha (también, más corta, al intentar meter la R en marcha)|
| `gear_shift`     | Palanca de cambios al cambiar de marcha                                 |
| `handbrake_on`   | Freno de mano al subirlo (carraca)                                      |
| `handbrake_off`  | Freno de mano al bajarlo                                                |
| `indicator_on`   | "Tic" del relé del intermitente (si falta, se usa `indicator`)          |
| `indicator_off`  | "Tac" del relé (si falta, se usa `indicator`)                           |
| `curb`           | Rueda que sube o baja un bordillo / chasis que roza el bordillo         |
| `impact`         | Golpe contra una valla o un coche aparcado                              |
| `cone`           | Golpe a un cono                                                         |

Para oír y ver los sintetizados sin abrir el juego: `gradlew sfxPreview` escribe cada uno como WAV y una hoja de
espectrogramas en `assets/cache/sfx_preview/`. Están hechos para sonar como desde el asiento (motor y arranque
apagados por el salpicadero, retumbo del habitáculo) y el choque imita un coche: masa, chapa, plástico que se agrieta
y trocitos.

Fuentes si se quieren grabaciones: freesound.org (filtrar CC0), Kenney Audio.

## Señales de tráfico: `assets/theory/signs/<CODIGO>.png`

Las imágenes de señales del módulo de teoría no se descargan: las dibuja con Java2D `SignGenerator`
(módulo `lwjgl3`, sin libGDX), siguiendo colores y proporciones del Catálogo Oficial de Señales de Circulación.

```
gradlew signs
```

Escribe un PNG de 256x256 con fondo transparente por señal, con el código del catálogo como nombre (`R-1.png`,
`R-301_50.png`, `P-13a.png`...; el sufijo `_NN` es el número de la señal). Para revisarlas de un vistazo deja una hoja
de contacto en `assets/cache/signs_sheet.png` (no versionada). Para añadir una señal: una función de dibujo y una
entrada en `SignGenerator.catalog()`.

## Licencias

Anotar cada asset descargado en `assets/CREDITS.md` (autor, enlace, licencia).
