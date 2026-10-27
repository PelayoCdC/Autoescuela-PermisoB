# Formato de temas y preguntas

## Preguntas (`assets/data/preguntas.json` y bancos importados)

Lista JSON de objetos con estos campos:

| Campo         | Tipo     | Obligatorio | Descripción |
|---------------|----------|-------------|-------------|
| `id`          | texto    | sí (único)  | Identificador estable, p.ej. `q0042`. Si se importa sin id, se genera `u00001`, `u00002`... |
| `tema`        | texto    | sí          | Id de un tema de `temas.json` (`t01`..`t12`). |
| `enunciado`   | texto    | sí          | La pregunta. |
| `opciones`    | lista    | sí (3)      | Exactamente tres respuestas. |
| `correcta`    | número   | sí          | Índice de la correcta: 0, 1 o 2. |
| `explicacion` | texto    | no          | Por qué es correcta (se muestra al corregir). Conviene citar el artículo del reglamento. |
| `imagen`      | texto    | no          | Ruta relativa a `assets/` (o a `local/`) de una imagen PNG/JPG. Señales disponibles: `theory/signs/<código>.png` (ver `gradlew signs`). |
| `dificultad`  | número   | no          | 1 fácil, 2 media, 3 difícil (por defecto 1). |
| `fuente`      | texto    | no          | Procedencia: `propia`, `DGT`, `importada`. |

Las opciones se barajan en cada test, así que no deben aludir unas a otras ("todas las anteriores", "A y B"...):
si alguna lo hace, esa pregunta se muestra en su orden original.

Ejemplo:

```json
[
  {
    "id": "q0001",
    "tema": "t02",
    "enunciado": "En una autopista, ¿cuál es la velocidad máxima genérica para un turismo?",
    "opciones": ["100 km/h", "120 km/h", "130 km/h"],
    "correcta": 1,
    "explicacion": "El límite genérico en autopistas y autovías para turismos es 120 km/h (RGC art. 48).",
    "imagen": null,
    "dificultad": 1
  }
]
```

## Preguntas de la revista de la DGT

`assets/data/preguntas_dgt.json` tiene el mismo formato y se carga junto al banco propio. Sus enunciados y opciones
son los de los tests de la revista "Tráfico y Seguridad Vial" (texto reproducible citando la fuente), con el campo
`fuente` ("Revista Tráfico y Seguridad Vial (DGT), nº N, fecha"), que la app muestra bajo el enunciado. Para
actualizarlo: `gradlew dgtRevista -Pdesde=280 -Phasta=285` descarga el texto en bruto a `assets/import/`, y cada
pregunta se revisa (tema, vigencia, explicación propia) antes de añadirla. Las imágenes de la revista no se usan.

## Bancos externos opcionales

`TheoryRepository` carga también, si existen, `assets/data/preguntas_sede_dgt.json` y
`assets/data/preguntas_todotest.json`, con sus imágenes en `assets/theory/dgt/` y `assets/theory/todotest/`. No se
incluyen en este repositorio porque su contenido es de terceros; sin ellos la app usa el banco propio y el de la
revista.

## Importar bancos propios

En la app: **Teoría → Importar preguntas**. Se leen todos los `.json` y `.csv` de la carpeta
`assets/import/` (en escritorio) o `import/` dentro de los datos de la app (en Android) y se fusionan en
`save/preguntas_usuario.json`. Las preguntas con el mismo enunciado (ignorando mayúsculas y acentos) se
descartan como duplicadas. Las imágenes que estén junto al fichero importado se copian a `theory/`.

### CSV

Separador punto y coma, codificación UTF-8, una pregunta por línea. Cabecera opcional.

```
tema;enunciado;opcion1;opcion2;opcion3;correcta;explicacion;imagen
t02;¿Velocidad máxima en autopista para un turismo?;100 km/h;120 km/h;130 km/h;2;RGC art. 48;
```

`correcta` puede ser `1`, `2`, `3` o `A`, `B`, `C`.

## Temas (`assets/data/temas.json`)

```json
[
  {
    "id": "t01",
    "titulo": "Normas generales de circulación",
    "orden": 1,
    "resumen": "Vías, usuarios y posición en la calzada",
    "secciones": [
      { "titulo": "Posición en la calzada", "texto": "# Regla general\nSe circula por la **derecha**...\n- Viñeta\n- Otra", "imagen": null }
    ]
  }
]
```

Marcado admitido en `texto`: `# Título`, `## Subtítulo`, `- viñeta`, `**negrita**`,
`![alt](ruta/imagen.png)` y párrafos separados por líneas en blanco.
