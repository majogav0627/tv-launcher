# Inicio — launcher para Android TV

Pantalla de inicio ligera, estilo Apple TV, pensada para manejarse solo con el control remoto.
Hecha con Kotlin + Views + RecyclerView (sin librerías pesadas). Probada para Android TV 10 con poca RAM.

## Funciones

- Reloj grande (12 h) y fecha en español arriba a la derecha.
- Fila **Favoritas** con tarjetas grandes (usa el banner de la app o su icono sobre un color derivado del icono).
- Debajo, el resto de apps en cuadrícula.
- Al enfocar una tarjeta: se agranda, gana sombra y brillo, y aparece el nombre.
- **Pulsación larga en OK** (o botón Menú) sobre una app: añadir/quitar de favoritas, mover, información de la app, ocultar.
- Botón ⚙ (Ajustes del televisor) y botón de ajustes propios (**Personalizar Inicio**): fondo, apps ocultas, restablecer.
- Fondos degradados incluidos, imagen propia por selector de archivos o copiada con ADB.
- La lista se actualiza sola al instalar o desinstalar apps.
- Atrás nunca cierra el launcher (vuelve a la primera favorita).

## Fondo con ADB

```powershell
.\adb push C:\ruta\a\mi-imagen.jpg /sdcard/Android/data/com.majo.tvhome/files/fondo.jpg
```

Luego en la TV: **Personalizar Inicio → Usar imagen copiada con ADB**. (Abre el launcher al menos una vez antes para que exista la carpeta.)

## Compilación

GitHub Actions (`.github/workflows/build.yml`) compila en cada push a `main` y también a mano
(pestaña **Actions → Compilar APK → Run workflow**). El APK queda en **Releases** y como artifact.

## Firma

El APK se firma siempre con la misma clave para poder actualizar con `adb install -r`.
La clave **no** está en el repositorio: vive en los *Secrets* del repo:

| Secret | Contenido |
|---|---|
| `KEYSTORE_BASE64` | el archivo `.jks` codificado en base64 |
| `KEYSTORE_PASSWORD` | contraseña del almacén |
| `KEY_ALIAS` | alias de la clave |
| `KEY_PASSWORD` | contraseña de la clave |

Para cambiarlos: **Settings → Secrets and variables → Actions** en la página del repo,
o con GitHub CLI: `gh secret set NOMBRE`.

> Guarda una copia del `.jks` y su contraseña. Si se pierde, habrá que desinstalar la app una vez
> para instalar un APK firmado con una clave nueva.
