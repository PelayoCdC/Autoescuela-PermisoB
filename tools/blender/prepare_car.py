# -*- coding: utf-8 -*-
"""
Prepara el modelo del coche para Autoescuela y lo exporta como car.glb.

Uso: abrir en la pestaña Scripting de Blender, editar CONFIG y ejecutar (Alt+P), o bien
  blender --background --python tools/blender/prepare_car.py

Modos (CONFIG["mode"]):
  "list"   -> normaliza (aplana, orienta, escala) e imprime una tabla de objetos con su centro y tamaño en
              metros, para identificar volante, palanca, etc. No exporta ni toca nombres.
  "export" -> todo el proceso y exporta car.glb.

Qué hace "export", en orden:
 1. Importa CONFIG["input"] si no está vacío; borra Cube/Camera/Light por defecto; aplana la jerarquía.
 2. Gira el coche para que mire a -Y de Blender (+Z en la app) y lo escala a CONFIG["target_length"] metros.
 3. Separa los objetos de CONFIG["wheel_parts"] en piezas sueltas y las agrupa en cuatro ruedas por esquina.
 4. Une y renombra volante y palanca; pone cada origen donde toca.
 5. Mueve todo para que el centro de masas (0,38 m sobre el suelo, 0,15 m por delante del centro entre ejes)
    quede en el origen del mundo.
 6. Orienta el eje Z local del volante a lo largo de la columna; crea driver_eye; renombra materiales de espejo.
 7. Exporta glTF binario (+Y up, sin Draco) e imprime un informe.
"""

import math
import os

import bpy
from mathutils import Matrix, Vector

CONFIG = {
    "mode": "list",   # "list" para identificar objetos, "export" para generar car.glb
    # Fichero a importar (.glb/.gltf/.fbx/.obj). Vacío = usar la escena actual.
    "input": r"",
    # Destino. La app lo busca exactamente aquí.
    "output": r"F:\IntelliJIDEA Workspace\Autoescuela\assets\models\car\car.glb",
    # Hacia dónde mira el coche importado, en ejes de Blender: "-Y", "+Y", "+X" o "-X".
    "front_axis": "-Y",
    # Largo real del coche (m): el modelo se escala hasta esta longitud. None = no escalar.
    "target_length": 3.99,
    # Objetos que contienen las cuatro ruedas juntas (llantas, neumáticos...). Se separan por esquinas.
    "wheel_parts": ["Object_12", "Object_13"],
    # Nombres actuales de volante y palanca (una lista une varios objetos; None si no existe).
    "names": {
        "steering_wheel": None,
        "gear_lever": None,
    },
    # Si las ruedas ya son objetos separados, ponerlas aquí en lugar de en wheel_parts.
    "wheels": {"wheel_fl": None, "wheel_fr": None, "wheel_rl": None, "wheel_rr": None},
    # Centro de masas (ver vehicle.json): altura sobre el suelo y desplazamiento hacia delante
    # respecto al punto medio entre ejes.
    "com_height": 0.38,
    "com_offset_forward": 0.15,
    # Ojos del conductor respecto al centro de masas: izquierda (+X), arriba, adelante (m).
    "eye": (0.37, 0.77, 0.15),
    # Dirección de la columna de dirección (hacia el conductor) en ejes de Blender con el coche mirando a -Y:
    # sube (+Z) y va hacia atrás (+Y). None = no tocar la orientación del volante.
    "steering_axis": (0.0, 0.92, 0.39),
    # Materiales de los espejos: nombre en la app -> nombre actual del material (None = no renombrar).
    "mirror_materials": {"mirror_center": None, "mirror_left": None, "mirror_right": None},
    # Modo list: objetos a separar en piezas para buscar el volante ("all" = todos), o None.
    "inspect": None,
    # Modo list: caja [x0,y0,z0,x1,y1,z1] (centro del coche, suelo z=0; x+ izquierda, y- delante) en la que
    # contar vértices por objeto, para localizar el volante fundido con otra pieza.
    "region": None,
    # Modo export: piezas fundidas en otro objeto que hay que extraer por región
    # (caja respecto al centro del coche y al suelo, como "region"):
    # [{"name": "steering_wheel", "from": "Object_19", "region": [x0,y0,z0,x1,y1,z1]}, ...]
    "extract": None,
    # Modo export: repartir un material de espejos en los tres de la app por regiones:
    # {"material": "Miroir", "center": [x0,y0,z0,x1,y1,z1], "left": [...], "right": [...]}
    "mirror_split": None,
    # Materiales de cristal y su opacidad: {"Vitre": 0.35}
    "glass_materials": None,
    # Renderizar vistas de comprobación (preview_exterior.png, preview_interior.png junto al script).
    "render": True,
}


REPORT = []


def log(msg):
    line = "[prepare_car] " + str(msg)
    print(line)
    REPORT.append(line)


def save_report():
    """Guarda el informe junto al script y en un texto de Blender (desplegable del editor de texto)."""
    text = "\n".join(REPORT) + "\n"
    try:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "prepare_car_report.txt")
    except NameError:
        path = os.path.join(bpy.app.tempdir, "prepare_car_report.txt")
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        print("[prepare_car] Informe guardado en " + path)
    except OSError as e:
        print("[prepare_car] No se pudo guardar el informe: %s" % e)
    doc = bpy.data.texts.get("prepare_car_report") or bpy.data.texts.new("prepare_car_report")
    doc.clear()
    doc.write(text)


def select_only(objs):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    if objs:
        bpy.context.view_layer.objects.active = objs[0]


def mesh_objects():
    return [o for o in bpy.context.scene.objects if o.type == "MESH"]


def top_level_objects():
    return [o for o in bpy.context.scene.objects if o.parent is None]


def world_bounds(objs):
    lo = Vector((math.inf, math.inf, math.inf))
    hi = Vector((-math.inf, -math.inf, -math.inf))
    for o in objs:
        if o.type != "MESH":
            continue
        for c in o.bound_box:
            p = o.matrix_world @ Vector(c)
            lo = Vector((min(lo.x, p.x), min(lo.y, p.y), min(lo.z, p.z)))
            hi = Vector((max(hi.x, p.x), max(hi.y, p.y), max(hi.z, p.z)))
    return lo, hi


def center_of(obj):
    lo, hi = world_bounds([obj])
    return (lo + hi) / 2


def get_objects(name):
    """Devuelve la lista de objetos para un nombre, lista de nombres o None."""
    if name is None:
        return []
    names = name if isinstance(name, (list, tuple)) else [name]
    objs = []
    for n in names:
        o = bpy.data.objects.get(n)
        if o is None:
            raise RuntimeError("No existe el objeto '%s'" % n)
        objs.append(o)
    return objs


def join(objs, new_name):
    select_only(objs)
    if len(objs) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    obj.name = new_name
    return obj


def apply_transforms(objs, location=True, rotation=True, scale=True):
    objs = [o for o in objs if o.type in ("MESH", "EMPTY")]
    if objs:
        select_only(objs)
        bpy.ops.object.transform_apply(location=location, rotation=rotation, scale=scale)


def set_origin_to_point(obj, point):
    bpy.context.scene.cursor.location = point
    select_only([obj])
    bpy.ops.object.origin_set(type="ORIGIN_CURSOR")


def set_origin_to_bounds_center(obj):
    select_only([obj])
    bpy.ops.object.origin_set(type="ORIGIN_GEOMETRY", center="BOUNDS")


def orient_local_z(obj, direction):
    """Gira el objeto para que su eje Z local apunte a 'direction' sin mover la malla."""
    d = Vector(direction).normalized()
    rot = Vector((0.0, 0.0, 1.0)).rotation_difference(d)
    obj.data.transform(rot.to_matrix().to_4x4().inverted())
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = rot


def separate_loose(obj):
    """Separa un objeto en sus piezas sueltas; devuelve la lista de objetos resultantes."""
    before = set(bpy.data.objects)
    select_only([obj])
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.separate(type="LOOSE")
    bpy.ops.object.mode_set(mode="OBJECT")
    parts = [obj] + [o for o in bpy.data.objects if o not in before]
    for p in parts:
        set_origin_to_bounds_center(p)
    return parts


def normalize(cfg):
    """Pasos comunes a ambos modos: importar, limpiar, aplanar, orientar y escalar."""
    if bpy.context.object and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")

    src = cfg["input"]
    if src:
        ext = os.path.splitext(src)[1].lower()
        log("Importando " + src)
        if ext in (".glb", ".gltf"):
            bpy.ops.import_scene.gltf(filepath=src)
        elif ext == ".fbx":
            bpy.ops.import_scene.fbx(filepath=src)
        elif ext == ".obj":
            bpy.ops.wm.obj_import(filepath=src)
        else:
            raise RuntimeError("Formato no soportado: " + ext)
    if not mesh_objects():
        raise RuntimeError("La escena no tiene mallas: pon la ruta del modelo en CONFIG['input']")

    for name in ("Cube", "Camera", "Light"):
        o = bpy.data.objects.get(name)
        if o is not None and o.parent is None:
            log("Borrando el objeto por defecto '%s'" % name)
            bpy.data.objects.remove(o, do_unlink=True)

    # Aplanar: todas las mallas al nivel superior conservando su posición; fuera los empties
    meshes = mesh_objects()
    select_only(meshes)
    bpy.ops.object.parent_clear(type="CLEAR_KEEP_TRANSFORM")
    for o in [o for o in bpy.context.scene.objects if o.type == "EMPTY" and o.name != "driver_eye"]:
        bpy.data.objects.remove(o, do_unlink=True)
    apply_transforms(meshes)
    log("Jerarquía aplanada: %d mallas" % len(meshes))

    # Orientar
    yaw = {"-Y": 0.0, "+X": 90.0, "+Y": 180.0, "-X": -90.0}[cfg["front_axis"]]
    if yaw:
        for o in top_level_objects():
            o.matrix_world = Matrix.Rotation(math.radians(yaw), 4, "Z") @ o.matrix_world
        apply_transforms(mesh_objects())
    # Escalar al largo real
    lo, hi = world_bounds(mesh_objects())
    length = hi.y - lo.y
    if cfg["target_length"]:
        scale = cfg["target_length"] / length
        for o in top_level_objects():
            o.matrix_world = Matrix.Scale(scale, 4) @ o.matrix_world
        apply_transforms(mesh_objects())
        log("Largo %.2f -> %.2f m (escala x%.4f)" % (length, cfg["target_length"], scale))
    lo, hi = world_bounds(mesh_objects())
    log("Dimensiones ahora: largo %.2f (Y), ancho %.2f (X), alto %.2f (Z), suelo en Z=%.2f"
        % (hi.y - lo.y, hi.x - lo.x, hi.z - lo.z, lo.z))


def list_objects():
    lo, hi = world_bounds(mesh_objects())
    cx = (lo.x + hi.x) / 2
    log("")
    log("%-14s %7s %7s %7s | %6s %6s %6s | %s" % ("objeto", "X", "Y", "Z", "ancho", "largo", "alto", "pista"))
    log("-" * 90)
    for o in sorted(mesh_objects(), key=lambda o: o.name):
        olo, ohi = world_bounds([o])
        c = (olo + ohi) / 2
        size = ohi - olo
        hints = []
        if size.x < 0.5 and size.y < 0.5 and size.z < 0.5 and 0.25 < size.x < 0.45 and c.x > cx + 0.2 and c.z > lo.z + 0.8:
            hints.append("¿volante?")
        if size.x < 0.12 and size.y < 0.25 and 0.1 < size.z < 0.35 and abs(c.x - cx) < 0.3 and c.z > lo.z + 0.4:
            hints.append("¿palanca?")
        if size.x < 0.3 and 0.3 < size.y < 0.4 and 0.3 < size.z < 0.4 and c.z < lo.z + 0.6:
            hints.append("¿rueda?")
        if size.x > 0.3 and size.y > 0.4 and size.z > 0.4 and abs(c.x - cx) < 0.6 and c.z > lo.z + 0.4:
            hints.append("¿asiento?")
        log("%-14s %7.2f %7.2f %7.2f | %6.2f %6.2f %6.2f | %s"
            % (o.name, c.x, c.y, c.z, size.x, size.y, size.z, " ".join(hints)))
    log("")
    log("X positivo = lado izquierdo del coche (conductor); Y negativo = delante; Z = altura sobre el origen.")
    log("Pon en CONFIG['names'] los objetos del volante y la palanca y cambia mode a 'export'.")


def inspect_parts(cfg):
    """Separa en piezas sueltas los objetos indicados (o todos) y lista las piezas del habitáculo
    con pinta de volante o de palanca. Solo en memoria: no se guarda nada."""
    names = cfg.get("inspect")
    objs = mesh_objects() if names == "all" else [bpy.data.objects[n] for n in (names or [])]
    if not objs:
        return
    lo, hi = world_bounds(mesh_objects())
    cx = (lo.x + hi.x) / 2
    ground = lo.z
    log("")
    log("Piezas sueltas del habitáculo (objeto original -> pieza, centro, tamaño):")
    for obj in objs:
        original = obj.name
        parts = separate_loose(obj)
        if len(parts) == 1:
            continue
        for p in parts:
            plo, phi = world_bounds([p])
            c = (plo + phi) / 2
            s = phi - plo
            in_cabin = abs(c.x - cx) < 0.9 and -1.3 < c.y < 1.2 and c.z > ground + 0.3
            if not in_cabin or max(s) < 0.05 or max(s) > 0.8:
                continue
            hints = []
            if 0.28 < s.x < 0.45 and c.x > cx + 0.15 and c.z > ground + 0.7:
                hints.append("¿volante?")
            if s.x < 0.15 and s.y < 0.3 and 0.08 < s.z < 0.35 and abs(c.x - cx) < 0.35:
                hints.append("¿palanca?")
            log("  %-12s -> %-16s (%5.2f %5.2f %5.2f) tam (%4.2f %4.2f %4.2f) %s"
                % (original, p.name, c.x, c.y, c.z, s.x, s.y, s.z, " ".join(hints)))


def material_report():
    """Para cada material: objetos que lo usan y caja envolvente de sus caras (centro/suelo relativos)."""
    lo, hi = world_bounds(mesh_objects())
    base = Vector(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z))
    boxes = {}
    for o in mesh_objects():
        mw = o.matrix_world
        mats = o.data.materials
        for poly in o.data.polygons:
            name = mats[poly.material_index].name if mats and mats[poly.material_index] else "(sin material)"
            box = boxes.setdefault(name, [Vector((math.inf,) * 3), Vector((-math.inf,) * 3), set(), 0])
            for vi in poly.vertices:
                p = mw @ o.data.vertices[vi].co - base
                box[0] = Vector((min(box[0].x, p.x), min(box[0].y, p.y), min(box[0].z, p.z)))
                box[1] = Vector((max(box[1].x, p.x), max(box[1].y, p.y), max(box[1].z, p.z)))
            box[2].add(o.name)
            box[3] += 1
    log("")
    log("Materiales (caja de sus caras respecto al centro/suelo; x+ izquierda, y- delante):")
    for name in sorted(boxes):
        b = boxes[name]
        log("  %-16s %5d caras  x %5.2f..%5.2f  y %5.2f..%5.2f  z %5.2f..%5.2f  en %s"
            % (name, b[3], b[0].x, b[1].x, b[0].y, b[1].y, b[0].z, b[1].z, sorted(b[2])))


def split_mirrors(cfg):
    """Reparte las caras del material de espejos en mirror_center/left/right según la región de cada
    uno, y les pone UV 0..1 proyectadas en su plano para que la app pinte la vista trasera."""
    import bmesh
    spec = cfg.get("mirror_split")
    if not spec:
        return
    source = bpy.data.materials.get(spec["material"])
    if source is None:
        log("AVISO: no existe el material de espejos '%s'" % spec["material"])
        return
    lo, hi = world_bounds(mesh_objects())
    base = Vector(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z))
    for key in ("center", "left", "right"):
        region = spec.get(key)
        if not region:
            continue
        name = "mirror_" + key
        mat = bpy.data.materials.get(name) or source.copy()
        mat.name = name
        x0, y0, z0, x1, y1, z1 = region
        total = 0
        for o in mesh_objects():
            if source.name not in [m.name for m in o.data.materials if m]:
                continue
            mw = o.matrix_world
            bm = bmesh.new()
            bm.from_mesh(o.data)
            src_index = [m.name if m else None for m in o.data.materials].index(source.name)
            if name not in [m.name if m else None for m in o.data.materials]:
                o.data.materials.append(mat)
            dst_index = [m.name if m else None for m in o.data.materials].index(name)
            faces = []
            for f in bm.faces:
                if f.material_index != src_index:
                    continue
                c = mw @ f.calc_center_median() - base
                if x0 <= c.x <= x1 and y0 <= c.y <= y1 and z0 <= c.z <= z1:
                    faces.append(f)
            if faces:
                normal = Vector((0, 0, 0))
                for f in faces:
                    normal += (mw.to_3x3() @ f.normal)
                normal.normalize()
                # Base del plano del espejo: u horizontal, v vertical
                v_axis = (Vector((0, 0, 1)) - normal * normal.z).normalized()
                u_axis = v_axis.cross(normal).normalized()
                uv_layer = bm.loops.layers.uv.verify()
                us, vs = [], []
                for f in faces:
                    for loop in f.loops:
                        p = mw @ loop.vert.co
                        us.append(p.dot(u_axis))
                        vs.append(p.dot(v_axis))
                umin, umax, vmin, vmax = min(us), max(us), min(vs), max(vs)
                for f in faces:
                    f.material_index = dst_index
                    for loop in f.loops:
                        p = mw @ loop.vert.co
                        loop[uv_layer].uv = ((p.dot(u_axis) - umin) / max(1e-6, umax - umin),
                                             (p.dot(v_axis) - vmin) / max(1e-6, vmax - vmin))
                total += len(faces)
            bm.to_mesh(o.data)
            bm.free()
        log("%s: %d caras" % (name, total))


def set_glass(cfg):
    """Materiales de cristal: mezcla alfa con la opacidad indicada ({"Vitre": 0.35, ...})."""
    for name, alpha in (cfg.get("glass_materials") or {}).items():
        mat = bpy.data.materials.get(name)
        if mat is None:
            log("AVISO: no existe el material de cristal '%s'" % name)
            continue
        mat.blend_method = "BLEND"
        if mat.use_nodes and mat.node_tree:
            for node in mat.node_tree.nodes:
                if node.type == "BSDF_PRINCIPLED":
                    node.inputs["Alpha"].default_value = alpha
        log("cristal %s: alfa %.2f" % (name, alpha))


def probe_region(cfg):
    """Cuenta los vértices de cada objeto dentro de la caja cfg['region'] = [x0,y0,z0,x1,y1,z1]
    (coordenadas relativas al centro de la caja envolvente del coche, suelo en z=0)."""
    r = cfg.get("region")
    if not r:
        return
    lo, hi = world_bounds(mesh_objects())
    base = Vector(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z))
    x0, y0, z0, x1, y1, z1 = r
    log("")
    log("Vértices dentro de la región x %.2f..%.2f  y %.2f..%.2f  z %.2f..%.2f (respecto al centro/suelo):"
        % (x0, x1, y0, y1, z0, z1))
    for o in sorted(mesh_objects(), key=lambda o: o.name):
        n = 0
        mw = o.matrix_world
        for v in o.data.vertices:
            p = mw @ v.co - base
            if x0 <= p.x <= x1 and y0 <= p.y <= y1 and z0 <= p.z <= z1:
                n += 1
        if n:
            log("  %-12s %6d de %6d vértices" % (o.name, n, len(o.data.vertices)))
    dump = cfg.get("dump")
    if dump and dump in bpy.data.objects:
        o = bpy.data.objects[dump]
        mw = o.matrix_world
        try:
            folder = os.path.dirname(os.path.abspath(__file__))
        except NameError:
            folder = bpy.app.tempdir
        path = os.path.join(folder, "region_vertices.txt")
        with open(path, "w") as f:
            for v in o.data.vertices:
                p = mw @ v.co - base
                if x0 <= p.x <= x1 and y0 <= p.y <= y1 and z0 <= p.z <= z1:
                    f.write("%.3f %.3f %.3f\n" % (p.x, p.y, p.z))
        log("Vértices de %s en la región volcados a %s" % (dump, path))


def extract_region(obj, region, new_name):
    """Separa en un objeto nuevo las caras de obj cuyos vértices caen todos dentro de la región."""
    import bmesh
    lo, hi = world_bounds(mesh_objects())
    base = Vector(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z))
    x0, y0, z0, x1, y1, z1 = region
    select_only([obj])
    bpy.ops.object.mode_set(mode="EDIT")
    bm = bmesh.from_edit_mesh(obj.data)
    mw = obj.matrix_world
    for f in bm.faces:
        f.select = all(x0 <= (mw @ v.co - base).x <= x1 and y0 <= (mw @ v.co - base).y <= y1
                       and z0 <= (mw @ v.co - base).z <= z1 for v in f.verts)
    bmesh.update_edit_mesh(obj.data)
    before = set(bpy.data.objects)
    bpy.ops.mesh.separate(type="SELECTED")
    bpy.ops.object.mode_set(mode="OBJECT")
    new = [o for o in bpy.data.objects if o not in before]
    if not new:
        raise RuntimeError("No hay caras de %s dentro de la región" % obj.name)
    part = new[0]
    part.name = new_name
    set_origin_to_bounds_center(part)
    plo, phi = world_bounds([part])
    log("%s extraído de %s: centro %s tamaño %s" % (new_name, obj.name,
        tuple(round(v, 2) for v in (plo + phi) / 2), tuple(round(v, 2) for v in phi - plo)))
    return part


def render_previews(cfg):
    """Renderiza (Workbench) una vista exterior y otra desde los ojos del conductor junto al script."""
    try:
        folder = os.path.dirname(os.path.abspath(__file__))
    except NameError:
        folder = bpy.app.tempdir
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.resolution_x = 960
    scene.render.resolution_y = 540
    scene.render.resolution_percentage = 100
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "MATERIAL"
    cam_data = bpy.data.cameras.new("preview_cam")
    cam = bpy.data.objects.new("preview_cam", cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    lo, hi = world_bounds(mesh_objects())
    center = (lo + hi) / 2
    # Ojos del conductor estimados por la caja: asiento izquierdo, 1,15 m sobre el suelo
    eye = Vector((center.x + 0.37, center.y + 0.2, lo.z + 1.15))
    views = {
        "preview_exterior": (Vector((center.x + 5.5, center.y - 6.0, center.z + 2.5)), center, 35.0),
        "preview_interior": (eye, eye + Vector((-0.1, -3.0, -0.5)), 80.0),
        "preview_dash": (Vector((center.x + 0.3, center.y + 1.3, lo.z + 1.3)),
                         Vector((center.x + 0.3, center.y - 0.5, lo.z + 0.9)), 55.0),
        "preview_console": (Vector((center.x + 0.45, center.y + 0.35, lo.z + 1.25)),
                            Vector((center.x, center.y - 0.2, lo.z + 0.7)), 60.0),
    }
    for name, (pos, target, fov) in views.items():
        cam.location = pos
        direction = target - pos
        cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
        cam_data.angle = math.radians(fov)
        cam_data.clip_start = 0.03
        scene.render.filepath = os.path.join(folder, name + ".png")
        bpy.ops.render.render(write_still=True)
        log("Render " + scene.render.filepath)
    bpy.data.objects.remove(cam, do_unlink=True)


def build_wheels(cfg):
    """Devuelve dict nombre -> objeto para las cuatro ruedas, a partir de objetos sueltos o de piezas juntas."""
    found = {}
    explicit = {k: get_objects(v) for k, v in cfg["wheels"].items() if v}
    if len(explicit) == 4:
        for k, objs in explicit.items():
            found[k] = join(objs, k)
        return found
    parts = []
    for name in cfg["wheel_parts"]:
        for o in get_objects(name):
            parts.extend(separate_loose(o))
    if not parts:
        return found
    centers = [center_of(p) for p in parts]
    cx = sum(c.x for c in centers) / len(centers)
    cy = sum(c.y for c in centers) / len(centers)
    groups = {"wheel_fl": [], "wheel_fr": [], "wheel_rl": [], "wheel_rr": []}
    for p, c in zip(parts, centers):
        key = ("wheel_f" if c.y < cy else "wheel_r") + ("l" if c.x > cx else "r")
        groups[key].append(p)
    for key, objs in groups.items():
        if not objs:
            log("AVISO: no hay piezas para " + key)
            continue
        found[key] = join(objs, key)
        log("%s: %d piezas unidas" % (key, len(objs)))
    return found


def export(cfg):
    found = build_wheels(cfg)
    for target, current in cfg["names"].items():
        objs = get_objects(current)
        if objs:
            found[target] = join(objs, target)
            log("%s <- %s" % (target, current))
    # Piezas fundidas en otro objeto: se extraen por región (antes de mover el centro de masas,
    # con coordenadas relativas al centro del coche y al suelo)
    for spec in cfg.get("extract") or []:
        src = bpy.data.objects.get(spec["from"])
        if src is None:
            raise RuntimeError("extract: no existe el objeto " + spec["from"])
        found[spec["name"]] = extract_region(src, spec["region"], spec["name"])

    # Orígenes
    for key in ("wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr", "steering_wheel"):
        if key in found:
            set_origin_to_bounds_center(found[key])
    if "gear_lever" in found:
        lo, hi = world_bounds([found["gear_lever"]])
        set_origin_to_point(found["gear_lever"], Vector(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z)))

    # Centro de masas al origen
    lo, hi = world_bounds(mesh_objects())
    wheels = [found[k] for k in ("wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr") if k in found]
    if len(wheels) == 4:
        front_y = (found["wheel_fl"].location.y + found["wheel_fr"].location.y) / 2
        rear_y = (found["wheel_rl"].location.y + found["wheel_rr"].location.y) / 2
        axle_mid_y = (front_y + rear_y) / 2
        center_x = sum(w.location.x for w in wheels) / 4
        wlo, whi = world_bounds(wheels)
        ground_z = wlo.z
        log("Batalla %.3f m, vía %.3f m, radio de rueda %.3f m" % (
            abs(front_y - rear_y), abs(found["wheel_fl"].location.x - found["wheel_fr"].location.x),
            (whi.z - wlo.z) / 2))
    else:
        log("AVISO: sin las cuatro ruedas; el centro de masas se estima por la caja envolvente")
        axle_mid_y = (lo.y + hi.y) / 2
        center_x = (lo.x + hi.x) / 2
        ground_z = lo.z
    com = Vector((center_x, axle_mid_y - cfg["com_offset_forward"], ground_z + cfg["com_height"]))
    for o in top_level_objects():
        o.matrix_world = Matrix.Translation(-com) @ o.matrix_world
    log("Centro de masas en el origen (estaba en %s)" % (tuple(round(v, 3) for v in com),))
    apply_transforms([o for o in mesh_objects() if o not in found.values()])

    if "steering_wheel" in found and cfg["steering_axis"]:
        orient_local_z(found["steering_wheel"], cfg["steering_axis"])
        log("Volante: eje Z local orientado a %s" % (cfg["steering_axis"],))

    eye = bpy.data.objects.get("driver_eye")
    if eye is None:
        eye = bpy.data.objects.new("driver_eye", None)
        bpy.context.scene.collection.objects.link(eye)
    ex, eup, efwd = cfg["eye"]
    eye.location = Vector((ex, -efwd, eup))
    eye.empty_display_type = "PLAIN_AXES"
    eye.empty_display_size = 0.1
    for target, current in cfg["mirror_materials"].items():
        if current and current in bpy.data.materials:
            bpy.data.materials[current].name = target
            log("material %s <- %s" % (target, current))
    split_mirrors(cfg)
    set_glass(cfg)

    out = cfg["output"]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=out,
        export_format="GLB",
        export_yup=True,
        export_apply=True,
        export_animations=False,
        export_draco_mesh_compression_enable=False,
        export_image_format="AUTO",
        use_selection=False,
    )
    lo, hi = world_bounds(mesh_objects())
    log("Exportado " + out)
    log("Dimensiones finales: largo %.2f (Y), ancho %.2f (X), alto %.2f (Z); suelo en Z=%.2f"
        % (hi.y - lo.y, hi.x - lo.x, hi.z - lo.z, lo.z))
    missing = [k for k in ("wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr", "steering_wheel", "gear_lever")
               if k not in found]
    if missing:
        log("Nodos sin asignar (la app los ignorará): %s" % missing)
    mats = [m.name for m in bpy.data.materials if m.name.startswith("mirror_")]
    log("Materiales de espejo: %s" % (mats or "ninguno"))
    log("Comprueba con: gradlew checkCar")


def apply_argv_overrides():
    """blender --background --python prepare_car.py -- config=ajustes.json mode=export input="ruta" ..."""
    import json
    import sys
    if "--" not in sys.argv:
        return
    for arg in sys.argv[sys.argv.index("--") + 1:]:
        if "=" not in arg:
            continue
        key, raw = arg.split("=", 1)
        if key == "config":
            with open(raw, "r", encoding="utf-8") as f:
                for k, v in json.load(f).items():
                    CONFIG[k] = v
            log("CONFIG cargada de " + raw)
            continue
        try:
            value = json.loads(raw)
        except ValueError:
            value = None if raw in ("None", "null", "") else raw
        target = CONFIG
        parts = key.split(".")
        for p in parts[:-1]:
            target = target[p]
        target[parts[-1]] = value
        log("CONFIG %s = %r" % (key, value))


def main():
    apply_argv_overrides()
    try:
        normalize(CONFIG)
        if CONFIG["mode"] == "list":
            list_objects()
            material_report()
            probe_region(CONFIG)
            if CONFIG.get("render"):
                render_previews(CONFIG)
            inspect_parts(CONFIG)
        else:
            export(CONFIG)
            if CONFIG.get("render"):
                render_previews(CONFIG)
    except Exception as e:  # que el error quede también en el informe
        log("ERROR: %s" % e)
        raise
    finally:
        save_report()


main()
