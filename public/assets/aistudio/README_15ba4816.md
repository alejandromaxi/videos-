# POWER PAPILO para Android

App nativa de Android para descargar video y audio, hermana de la versión de
Windows (`POWER_PAPILO.ps1`), pero **independiente**: no comparte código con la
de escritorio y no la modifica.

---

## Por qué no se puede "portar" la de Windows

La app de escritorio es PowerShell + WinForms. Eso **no existe en Android**:
no hay PowerShell de escritorio, ni WinForms, ni procesos `cmd.exe`, ni
`yt-dlp.exe`. Un APK es código compilado (Kotlin/Java) que corre sobre el
runtime de Android.

La forma realista de tener yt-dlp en Android es empaquetar **Python + yt-dlp +
ffmpeg** dentro del APK. Eso es exactamente lo que hace la librería
[youtubedl-android](https://github.com/yausername/youtubedl-android), la misma
que usan apps como Seal. Esta app la usa como motor.

---

## Qué incluye

- Interfaz **Jetpack Compose + Material 3** (color dinámico en Android 12+).
- **Cola de descargas** secuencial con progreso, ETA y cancelación.
- Presets: **Mejor**, **1080p**, **720p**, **Audio MP3**.
- **Servicio en primer plano**: sigue descargando con la app en segundo plano y
  muestra el progreso en la notificación.
- **Compartir desde otras apps**: manda un enlace desde YouTube o el navegador
  a POWER PAPILO y se rellena solo.
- Archivos en la carpeta privada de la app (`Android/data/com.powerpapilo.app/files/Download`),
  con **Abrir** y **Compartir** (FileProvider). Sin permisos de almacenamiento.
- Icono adaptativo propio y tema claro/oscuro según el sistema.

### Diferencias con la versión de Windows

| Función | Windows | Android |
| --- | --- | --- |
| Cola, progreso, cancelar | Sí | Sí |
| Presets de calidad / MP3 | Sí | Sí |
| Playlists | Sí (rango) | No (solo video individual) |
| Cookies del navegador | Sí | No (pendiente: importar `cookies.txt`) |
| Archivo histórico | Sí | No |
| Subtítulos / miniatura | Sí | Solo miniatura al extraer audio |
| Límite de velocidad | Sí | No |

---

## Cómo compilar

### Opción A — Android Studio (recomendada)

1. Instala Android Studio (incluye JDK y SDK).
2. `File → Open` y elige esta carpeta `POWER-PAPILO-Android`.
3. Espera a que Gradle sincronice (descargará yt-dlp, Compose, etc.).
4. `Run ▶` en un emulador o teléfono con depuración USB.

Android Studio genera el Gradle wrapper que falte (`gradlew`).

### Opción B — línea de comandos

Con JDK 17 y Gradle 8.7 instalados:

```bash
cd POWER-PAPILO-Android
gradle assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

### Opción C — GitHub Actions (sin instalar nada)

Sube esta carpeta como repositorio y el workflow
`.github/workflows/android.yml` compila el APK en la nube y lo deja como
artefacto descargable (`power-papilo-apk`).

---

## Estado de esta entrega

**No se compiló aquí**: en este equipo no hay JDK, ni Gradle, ni Android SDK, y
la unidad `F:` está sin espacio libre. Por eso no puedo entregar un `.apk`
verificado. Lo que sí está es el proyecto completo y listo para compilar en
Android Studio o en la nube.

Sobre el motor: la librería se fijó en **0.14.0** porque las versiones 0.15+ de
`youtubedl-android` fallan al compilarse en JitPack (comprobado en su API). Al
arrancar, la app **actualiza yt-dlp a la última estable en segundo plano**, así
que el motor empaquetado antiguo no debería ser un problema, pero es lo primero
que conviene revisar si una descarga falla.

Si al compilar aparece un error de versiones, los dos puntos a revisar son:

- `app/build.gradle.kts` → versión de `com.github.yausername.youtubedl-android`
  (JitPack) y `kotlinCompilerExtensionVersion`.
- `compileSdk`/`targetSdk` según el SDK que tengas instalado.

---

## Estructura

```
app/src/main/java/com/powerpapilo/app/
├── PowerPapiloApp.kt          Inicializa yt-dlp + ffmpeg
├── MainActivity.kt            Compose, permiso de notificaciones, "Compartir"
├── data/DownloadItem.kt       Modelo y estados
├── download/
│   ├── YtDlp.kt               Argumentos de yt-dlp y ejecución
│   ├── DownloadManager.kt     Cola y estado compartido
│   └── DownloadService.kt     Servicio en primer plano + notificación
├── ui/
│   ├── HomeScreen.kt          Interfaz
│   ├── HomeViewModel.kt       Estado de la pantalla
│   └── theme/Theme.kt         Material 3
└── util/Files.kt              Abrir y compartir archivos
```

---

## Aviso de uso

Uso personal. Respeta los términos de cada sitio y los derechos de autor del
contenido que descargues.
