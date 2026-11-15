# Caja Panda

Punto de venta para Abarrotes Panda: ventas con escáner, inventario, fiado por cliente, usuarios con permisos, corte de caja por turno e impresión de tickets en impresoras térmicas Bluetooth.

- **Celular (Android):** descarga `Caja-Panda.apk` de la sección *Releases*. La app avisa sola cuando hay una versión nueva.
- **Computadora:** se usa desde el navegador en la página de GitHub Pages del repositorio.
- **Datos:** viven en Firebase (proyecto `caja-panda`); las reglas de seguridad están en `firestore.rules`.

## Cómo se actualiza

1. Se edita `web/caja.html` (la pantalla) o el código de Android en `app/`.
2. Al subir los cambios a `main`, GitHub Actions compila la APK, la publica como versión nueva (`v1.0.N`) y actualiza la versión web.
3. Las apps instaladas detectan la versión nueva y ofrecen actualizar.
