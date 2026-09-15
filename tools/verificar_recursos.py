#!/usr/bin/env python3
"""Comprueba referencias que el compilador no siempre atrapa.

El proyecto ya se rompio varias veces por lo mismo: una referencia a un recurso
que no existe (@dimen/space_xxs), un estilo inventado (Widget.App.RadioButton) y
un destino de navegacion que nadie registro. Las dos primeras tumban la
compilacion, lo cual esta bien; la tercera no, porque la navegacion se resuelve
por reflexion y el fallo aparece en tiempo de ejecucion.

Uso:  python3 tools/verificar_recursos.py
Salida: lista de problemas y codigo de salida 1 si encuentra alguno.
"""

import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(RAIZ, 'app', 'src', 'main', 'res')
JAVA = os.path.join(RAIZ, 'app', 'src', 'main', 'java')

TIPOS_XML = {
    'string': 'values', 'color': 'values', 'dimen': 'values', 'style': 'values',
    'integer': 'values', 'bool': 'values', 'attr': 'values', 'fraction': 'values',
    'string-array': 'values', 'integer-array': 'values', 'array': 'values',
    'plurals': 'values', 'id': 'values',
}
# tipo -> carpeta de recursos
CARPETA = {
    'drawable': 'drawable', 'layout': 'layout', 'menu': 'menu', 'navigation': 'navigation',
    'anim': 'anim', 'animator': 'animator', 'font': 'font', 'xml': 'xml',
    'mipmap': 'mipmap', 'color': 'color', 'raw': 'raw', 'interpolator': 'interpolator',
}

problemas = []


def leer(ruta):
    with open(ruta, encoding='utf-8') as f:
        return f.read()


def recorrer(raiz, extensiones):
    for base, _, ficheros in os.walk(raiz):
        for f in ficheros:
            if f.endswith(extensiones):
                yield os.path.join(base, f)


# --------------------------------------------------------------- 1. Definidos

def definidos():
    """Devuelve {(tipo, nombre)} de todo lo que el proyecto declara."""
    d = set()

    for ruta in recorrer(os.path.join(RES, 'values'), ('.xml',)):
        x = leer(ruta)
        for tipo in list(TIPOS_XML) + ['item']:
            if tipo == 'item':
                continue
            for m in re.finditer(r'<' + tipo + r'\s[^>]*name="([^"]+)"', x):
                d.add((tipo, m.group(1)))
        # <item type="id" name="..."/> dentro de un <declare-styleable> o suelto
        for m in re.finditer(r'<item\s+[^>]*type="id"[^>]*name="([^"]+)"', x):
            d.add(('id', m.group(1)))
        for m in re.finditer(r'<item\s+[^>]*name="([^"]+)"[^>]*type="id"', x):
            d.add(('id', m.group(1)))

    # recursos por fichero
    for tipo, carpeta in CARPETA.items():
        base = os.path.join(RES, carpeta)
        if not os.path.isdir(base):
            continue
        for ruta in recorrer(base, ('.xml', '.png', '.jpg', '.webp', '.svg', '.ttf', '.otf', '.json', '.mp3')):
            rel = os.path.relpath(ruta, base)
            nombre = rel.split(os.sep)[0]
            if nombre.startswith('mipmap-'):
                # res/mipmap-anydpi-v26/ic_launcher.xml -> nombre ic_launcher
                nombre = os.path.splitext(os.path.basename(rel))[0]
            else:
                nombre = os.path.splitext(nombre)[0]
            d.add((tipo, nombre))

    # los @id/... declarados en layouts cuentan como ids
    for ruta in recorrer(os.path.join(RES, 'layout'), ('.xml',)):
        for m in re.finditer(r'android:id="@\+id/([A-Za-z0-9_]+)"', leer(ruta)):
            d.add(('id', m.group(1)))
    return d


# ------------------------------------------------------- 2. Referencias @tipo/

def referencias_res():
    """Devuelve [(ruta, linea, tipo, nombre)] de cada @tipo/nombre en res/."""
    refs = []
    for ruta in recorrer(RES, ('.xml',)):
        for n, linea in enumerate(leer(ruta).splitlines(), 1):
            for m in re.finditer(r'"@(?!\+|\?|android:|\*)([a-zA-Z]+)/([A-Za-z0-9_.]+)"', linea):
                refs.append((ruta, n, m.group(1), m.group(2)))
    return refs


# --------------------------------------------------------- 3. Referencias R.*

def referencias_java():
    refs = []
    for ruta in recorrer(JAVA, ('.java',)):
        for n, linea in enumerate(leer(ruta).splitlines(), 1):
            if linea.strip().startswith('*') or linea.strip().startswith('//'):
                continue
            for m in re.finditer(r'\bR\.([a-z]+)\.([A-Za-z0-9_]+)', linea):
                refs.append((ruta, n, m.group(1), m.group(2)))
    return refs


# ------------------------------------------------- 4. Destinos de navegacion

def destinos_nav():
    """{fichero: {ids declarados}} y los ids usados en navigate(...)."""
    declarados, usados = {}, []
    for ruta in recorrer(os.path.join(RES, 'navigation'), ('.xml',)):
        d = set(re.findall(r'android:id="@\+id/([A-Za-z0-9_]+)"', leer(ruta)))
        declarados[os.path.basename(ruta)] = d

    todos = set().union(*declarados.values()) if declarados else set()
    for ruta in recorrer(JAVA, ('.java',)):
        x = leer(ruta)
        for n, linea in enumerate(x.splitlines(), 1):
            for m in re.finditer(r'navigate\(\s*R\.id\.([A-Za-z0-9_]+)', linea):
                usados.append((ruta, n, m.group(1)))
            for m in re.finditer(r'R\.id\.([A-Za-z0-9_]+)', linea):
                pass
    return declarados, todos, usados


# ------------------------------------------------------ 5. Doble hint en campo

def doble_hint():
    """TextInputLayout con hint + TextInputEditText interior con hint propio.

    Material dibuja el hint del layout en el mismo hueco que el del campo
    interior cuando este esta vacio: los dos textos salen superpuestos.
    """
    fallos = []
    for ruta in recorrer(os.path.join(RES, 'layout'), ('.xml',)):
        x = leer(ruta)
        patron = (r'<com\.google\.android\.material\.textfield\.TextInputLayout\b.*?'
                  r'</com\.google\.android\.material\.textfield\.TextInputLayout>')
        for m in re.finditer(patron, x, re.S):
            bloque = m.group(0)
            if 'android:hint=' not in bloque.split('TextInputEditText')[-1]:
                continue
            if re.search(r'android:hint="', bloque) and \
               re.search(r'<com\.google\.android\.material\.textfield\.TextInputEditText\b[^>]*android:hint="', bloque, re.S):
                linea = x[:m.start()].count('\n') + 1
                fallos.append((ruta, linea))
    return fallos


def main():
    definidos_ok = definidos()

    for ruta, n, tipo, nombre in referencias_res():
        if tipo in ('id',) and nombre.startswith('+'):   # @+id/...
            continue
        if tipo == 'id':
            continue
        # un @color puede resolverse contra res/color/ o contra values
        if (tipo, nombre) in definidos_ok:
            continue
        problemas.append(f'{os.path.relpath(ruta, RAIZ)}:{n}: @{tipo}/{nombre} no existe')

    for ruta, n, tipo, nombre in referencias_java():
        if tipo in ('id', 'string', 'drawable', 'layout', 'color', 'dimen', 'style',
                    'menu', 'navigation', 'anim', 'font', 'xml', 'array', 'bool',
                    'integer', 'plurals', 'mipmap', 'raw', 'attr', 'interpolator'):
            pass
        else:
            continue
        if tipo == 'id':
            continue
        if (tipo, nombre) not in definidos_ok:
            problemas.append(f'{os.path.relpath(ruta, RAIZ)}:{n}: R.{tipo}.{nombre} no existe')

    declarados, todos, usados = destinos_nav()
    for ruta, n, destino in usados:
        if destino not in todos:
            problemas.append(
                f'{os.path.relpath(ruta, RAIZ)}:{n}: navigate(R.id.{destino}) no es un '
                f'destino declarado en res/navigation/')

    for ruta, n in doble_hint():
        problemas.append(
            f'{os.path.relpath(ruta, RAIZ)}:{n}: TextInputLayout con hint y su '
            f'TextInputEditText tambien: los dos textos se dibujan superpuestos')

    if problemas:
        print(f'{len(problemas)} problema(s):\n')
        for p in problemas:
            print('  ' + p)
        return 1
    print('Sin problemas: todas las referencias resuelven.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
