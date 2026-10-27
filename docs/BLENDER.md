# Preparar el modelo del coche en Blender

Objetivo: convertir un modelo descargado (Sketchfab, etc.) en `assets/models/car/car.glb` con el origen, la
orientación y los nombres de nodo que espera la app. Hay dos caminos: el **script** `tools/blender/prepare_car.py`
hace los pasos 4 a 9 solo; la guía manual sirve para entender qué hace y para retocar.

Comprobación final (sin abrir la app): `gradlew checkCar` lee el `.glb` y lista nodos encontrados y que faltan,
materiales de espejo y dimensiones.

## 0. Instalar Blender

Descarga la versión 4.x de https://www.blender.org/download/ (gratuita). Todo lo de abajo está escrito para
Blender 4.x; en 3.6 es casi igual.

## 1. Descargar el modelo

En Sketchfab busca "Renault Clio 3" o "Clio III", activa el filtro **Downloadable**, y descarga en formato
**glTF** (si solo hay FBX u OBJ también vale, Blender los importa). Descomprime en una carpeta aparte.

Apunta el autor, el enlace y la licencia en `assets/CREDITS.md`.

## 2. Importar

`File → Import → glTF 2.0` (o FBX / Wavefront OBJ) y elige el fichero. Blender pone automáticamente Z hacia
arriba. Pulsa `N` en el visor para ver el panel lateral y comprueba las **dimensiones** del coche en la pestaña
Item: debe medir unos 4 m de largo. Si mide 400 (centímetros) o 0,04, selecciona todo (`A`) y escala (`S`,
`0.01` o `100`, Intro).

## 3. Entender los ejes

En Blender: **Z arriba**, y el coche debe mirar hacia **−Y** (el lado izquierdo del coche queda en +X). Al
exportar con "+Y up" (opción por defecto) eso se convierte en lo que usa la app: +Z adelante, +Y arriba, +X
izquierda. Si el modelo importado mira a otro lado, selecciona todo y gíralo sobre Z: `R`, `Z`, `90` (o `-90`,
`180`), Intro.

## 4. Aplicar transformaciones

Con todo seleccionado: `Ctrl+A → All Transforms`. Así la escala y la rotación quedan "horneadas" en la malla y
los pasos siguientes son fiables.

## 5. Colocar el origen del mundo en el centro de masas

La app supone que el origen del modelo está en el centro de masas del coche: en el eje longitudinal, a **0,38 m**
del suelo y **0,15 m por delante** del punto medio entre ejes (valores de `vehicle.json`: `comHeight`,
`comOffsetZ`).

1. Mira las ruedas: la Y del punto medio entre el eje delantero y el trasero, y la Z del suelo (parte más baja
   de las ruedas).
2. Sitúa el cursor 3D en el punto: `Shift+S → Cursor to World Origin`, y en el panel `N → View → 3D Cursor`
   escribe `X = 0` (centro del coche), `Y = (punto medio entre ejes) − 0.15` (recuerda que el frente es −Y),
   `Z = suelo + 0.38`.
3. Selecciona todo y muévelo para que ese punto quede en el origen del mundo: la forma fácil es, con todo
   seleccionado, `Object → Set Origin → Origin to 3D Cursor` en el objeto principal y después
   `Alt+G` (limpiar posición) **solo** si todos los objetos están emparentados al principal. Si no, mueve todo con
   `G` escribiendo las coordenadas negativas del cursor.

El script hace esto a partir de las posiciones de las ruedas.

## 6. Nombres de nodo

Renombra los objetos (doble clic en el Outliner) exactamente así:

| Objeto | Nombre | Origen del objeto |
|--------|--------|-------------------|
| Rueda delantera izquierda | `wheel_fl` | en el centro de la rueda |
| Rueda delantera derecha | `wheel_fr` | en el centro de la rueda |
| Rueda trasera izquierda | `wheel_rl` | en el centro de la rueda |
| Rueda trasera derecha | `wheel_rr` | en el centro de la rueda |
| Volante | `steering_wheel` | en el centro del aro |
| Palanca de cambios | `gear_lever` | en la base de la palanca |
| Carrocería y resto | cualquier nombre | — |

Reglas:

- Las ruedas, el volante y la palanca deben estar en el **nivel superior** (sin padre): selecciónalos y
  `Alt+P → Clear and Keep Transformation`. Si una rueda son dos objetos (neumático y llanta), únelos: selecciona
  ambos y `Ctrl+J`.
- Para poner el origen en el centro de la rueda: selecciona la rueda, `Object → Set Origin → Origin to Geometry`
  (o `Origin to Center of Mass (Volume)`).
- El volante gira alrededor del **eje Z local** del objeto, que debe apuntar a lo largo de la columna de
  dirección (hacia el conductor). Con el origen en el centro del aro, activa en la cabecera del visor
  `Options → Affect Only: Origins` y rota el objeto (`R`) hasta que su eje Z local (la flecha azul del gizmo)
  siga la columna; desactiva después "Affect Only Origins". El script lo hace si le das el vector de la columna.
- La palanca se inclina alrededor de sus ejes X y Z locales: basta con que tenga rotación cero (`Ctrl+A →
  Rotation`) y el origen en la base (`Set Origin → Origin to 3D Cursor` con el cursor en la base).

## 7. Ojos del conductor

Añade un Empty: `Shift+A → Empty → Plain Axes`, nómbralo `driver_eye` y colócalo en la posición de los ojos
del conductor (asiento izquierdo): aproximadamente `X = 0.37`, `Y = −0.15`, `Z = 0.77` respecto al origen
(equivale a 1,15 m del suelo). Ajusta mirando el modelo para que quede delante del reposacabezas.

## 8. Espejos y cristales

- Cada espejo (interior, izquierdo, derecho) necesita un **material propio** en las caras del cristal, con
  los nombres `mirror_center`, `mirror_left`, `mirror_right`. En Edit mode selecciona las caras del cristal,
  en el panel Material pulsa `+`, `New`, nómbralo y `Assign`.
- Esas caras deben tener UV de 0 a 1: con las caras seleccionadas, `U → Reset`.
- La app pinta en ese material la vista trasera; da igual el color que tenga.
- Los cristales de las ventanillas: en su material, `Settings → Blend Mode: Alpha Blend` y alfa ~0,3 en el
  Principled BSDF, para ver a través desde dentro.

## 9. Exportar

`File → Export → glTF 2.0`:

- Format: **glTF Binary (.glb)**.
- Include → Limit to: nada marcado (exporta toda la escena).
- Transform: **+Y Up** marcado.
- Data → Mesh: **Apply Modifiers** marcado; Compression (Draco): **desmarcado** (la app no lo soporta).
- Data → Material: Export; Images: Automatic.
- Animation: desmarcado.

Guarda como `F:\IntelliJIDEA Workspace\Autoescuela\assets\models\car\car.glb`.

## 10. Comprobar

```
gradlew checkCar
```

Lista los nodos encontrados y los que faltan, los materiales `mirror_*`, y el tamaño del modelo (largo ≈ 3,99 m,
ancho ≈ 1,72 m, alto ≈ 1,45 m, mínimo Y ≈ −0,38). Después `gradlew lwjgl3:run`: el log dice "El modelo del
coche no tiene el nodo..." para cada nodo que falte.

Si el coche aparece girado, hundido o flotando, los culpables son la orientación (paso 3) o el origen (paso 5).
Si las ruedas aparecen desplazadas, su origen no está en el centro (paso 6).

## Usar el script (recomendado: sin abrir Blender)

El script se controla con un fichero JSON; `tools/blender/clio_rs_2007.json` es el que se usó para el modelo
"Renault_clio_rs_2007_3ds (1)" de Fister (Sketchfab, CC-BY-4.0) y sirve de plantilla. Se lanza así:

```
"F:\Archivos de programa\Blender Foundation\Blender 5.1\blender.exe" --background --python tools\blender\prepare_car.py -- config=tools\blender\clio_rs_2007.json
```

El informe queda en `tools/blender/prepare_car_report.txt` y las vistas de comprobación en
`tools/blender/preview_*.png` (exterior, ojos del conductor, salpicadero, consola).

Flujo para un modelo nuevo:

1. `mode=list`: aplana la jerarquía, orienta, escala a 3,99 m y lista cada objeto con su centro y tamaño en
   metros, más una tabla de materiales con la caja de sus caras (para localizar espejos y cristales).
   Coordenadas: X positivo = lado del conductor, Y negativo = delante, Z = altura sobre el suelo.
2. Si el volante o la palanca están fundidos en otro objeto, se localizan con `region=[x0,y0,z0,x1,y1,z1]`
   (cuenta vértices por objeto dentro de la caja) y `dump=Object_N` (vuelca esos vértices a
   `region_vertices.txt` para afinar la caja), y se extraen en el export con `extract`.
3. Si todas las ruedas están en un solo objeto (llantas, neumáticos, discos), `wheel_parts` las separa en
   piezas sueltas y las agrupa por esquina en las cuatro ruedas.
4. Si los espejos comparten un material, `mirror_split` lo reparte en `mirror_center/left/right` por regiones
   y les genera las UV; `glass_materials` pone los cristales translúcidos.
5. `mode=export` genera `assets/models/car/car.glb`; después `gradlew checkCar` y `gradlew lwjgl3:run`.

Los parámetros sueltos también se pueden pasar en la línea de comandos (`mode=list input="ruta"`), pero las
listas y objetos JSON van mejor en el fichero de configuración.

Desde la interfaz: pestaña **Scripts**, `Abrir` → `prepare_car.py`, editar `CONFIG` y `▶ Ejecutar script`.
El informe aparece como texto `prepare_car_report` en el desplegable del editor. Ojo: el editor no recarga
el fichero solo; tras cambiarlo en disco hay que usar `Texto → Recargar`.
