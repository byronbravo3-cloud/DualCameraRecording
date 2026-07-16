# Resumen de Cambios - Corrección de Rotación "Original"

He corregido el problema por el cual ambos videos se grababan girados 90 grados a la derecha. Ahora los videos se guardan en su posición natural sin rotaciones innecesarias.

## Cambios Realizados

### 1. Eliminación de Rotación de Sensor Excedente
- Se eliminó el uso de `sensorOrientation` en el motor de grabación.
- **Razón**: El hardware de Android ya entrega la imagen corregida (vertical) a través de la matriz de transformación de la cámara. Al sumarle 90 grados adicionales por software, el video se "volteaba" hacia un lado. Al quitar este paso, el video vuelve a su estado "original" y derecho.

### 2. Corrección de Proporciones en Tiempo Real
- Se ajustó el cálculo de las dimensiones efectivas del sensor en `drawFrame`.
- Ahora el sistema identifica correctamente que la fuente es vertical por defecto y solo intercambia los ejes cuando el usuario gira físicamente el teléfono a modo paisaje. Esto evita que la imagen se vea estirada o con recortes extraños.

## Verificación
- **Sin Giros a la Derecha**: Al grabar en modo retrato, los videos panorámico y vertical se ven perfectamente "de pie" (upright).
- **Consistencia**: Al girar el teléfono a horizontal, el video se adapta a la nueva vista sin rotar el archivo de forma errónea.
- **Calidad**: Se mantiene el llenado de pantalla (Center Crop) y la alta resolución en ambos archivos.
