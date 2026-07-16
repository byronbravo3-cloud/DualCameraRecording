# DualCameraRecorder 🎥

**DualCameraRecorder** es una aplicación de Android avanzada diseñada para creadores de contenido que necesitan capturar video en múltiples formatos simultáneamente. Con un solo toque, la aplicación graba dos archivos de video independientes: uno en formato panorámico (16:9) y otro en formato vertical (9:16), ambos optimizados y con la orientación correcta. Probado en un Samsung S24+ (Procesador Exynos).

## ✨ Características Principales

*   **Grabación Dual Simultánea**: Genera un archivo `video_horizontal.mp4` (16:9) y un `video_vertical.mp4` (9:16) al mismo tiempo.
*   **Previsualización a Pantalla Completa**: Interfaz limpia sin barras de acción, con una previsualización de cámara que llena toda la pantalla (Center Crop).
*   **Rotación Inteligente**: Soporte completo para cambios de orientación en tiempo real sin interrumpir la cámara ni la grabación.
*   **Botón de Grabación Profesional**: Interfaz intuitiva que cambia de estado (Espera/Grabando) inspirada en cámaras profesionales.
*   **Guardado Automático**: Los videos se exportan directamente a la galería del dispositivo (Carpeta `Movies/DualCamera`).
*   **Alta Resolución**: Configurado para capturar video en alta calidad utilizando codificación de hardware.

## 🚀 Tecnologías Utilizadas

*   **Android SDK (API 35)**: Desarrollo nativo en Kotlin.
*   **Camera2 API**: Control avanzado del hardware de la cámara.
*   **OpenGL ES 2.0 (GLES)**: Motor de renderizado personalizado para procesamiento de frames y recortes en tiempo real.
*   **MediaCodec & MediaMuxer**: Codificación de video eficiente por hardware.
*   **ConstraintLayout**: Diseño de interfaz moderno y adaptativo.

## 🛠️ Requisitos

*   **Android**: Mínimo API 24 (Android 7.0).
*   **Permisos**: Requiere acceso a la Cámara y al Micrófono.

## 📂 Estructura del Proyecto

*   `MainActivity.kt`: Gestión del ciclo de vida de la UI y permisos.
*   `DualCameraRecorder.kt`: Núcleo del motor de procesamiento de video y OpenGL.
*   `VideoEncoder.kt`: Manejo de la codificación y generación de archivos MP4.
*   `OrientationDetector.kt`: Detección precisa de la posición del dispositivo.
*   `gles/`: Utilidades para el manejo de shaders y superficies de dibujo.

---
Desarrollado con ❤️ para Android.
