# Control Concreto – PS-TUNEL 011

App Android para diligenciar el **Control de llegadas de concreto a obra** en los vaciados
de los módulos de revestimiento del túnel. Funciona **100 % sin internet**: no tiene permiso
de red, guarda todo en una base de datos interna del teléfono y genera el PDF en el mismo equipo.

## Cómo obtener el APK (elige una opción)

### Opción A – Sin instalar nada, usando GitHub (recomendada)
1. Crea una cuenta gratuita en github.com y un repositorio nuevo (privado si prefieres).
2. Sube todo el contenido de esta carpeta (botón "Add file" > "Upload files"; arrastra las
   carpetas). Importante: incluir la carpeta oculta `.github`.
3. Entra a la pestaña **Actions**. Se ejecuta "Compilar APK" (si no arranca, pulsa
   "Run workflow"). Tarda unos 5 minutos.
4. Al terminar, abre la ejecución y descarga **ControlConcreto-APK** (viene en .zip; adentro está
   el .apk).
5. Pasa el .apk al celular, ábrelo y acepta "Instalar apps de origen desconocido".

### Opción B – Con Android Studio en un computador
1. Instala Android Studio (gratis).
2. File > Open > selecciona esta carpeta. Espera que termine de sincronizar.
3. Build > Build App Bundle(s) / APK(s) > Build APK(s).
4. El archivo queda en `app/build/outputs/apk/`.

Solo se necesita internet para compilar (una vez). Después, el teléfono no lo necesita nunca.

## Uso
- **Nueva jornada**: nombre, frente, fecha, turno y tramo. Recuerda los últimos datos.
- **Agregar mixer**: todos los campos del formato. Cada hora tiene botón **Ahora**.
- Toca cualquier mixer o la jornada para **corregir** o **eliminar**.
- **Exportar PDF**: planilla carta horizontal con encabezado, tabla, total, resumen y firmas.
  Luego puedes abrirlo, compartirlo (WhatsApp, correo) o guardarlo en el teléfono.
- El historial queda **agrupado por día de vaciado**.

## Fotos de las remisiones
En cada mixer hay un cuadro con una cámara. Tómale la foto a la remisión de planta, ajusta las
cuatro esquinas (con lupa) y al guardar se endereza y se le aplica un filtro de escaneo automático.
Ya tomada, el cuadro muestra la miniatura; tócalo para verla, cambiarla, compartirla o borrarla.
Las fotos salen como anexo al final del PDF de la jornada. Todo funciona sin internet.

## Exportar a Excel
"Exportar" en la jornada ofrece PDF o Excel. En el menú ⋮ está "Exportar historial a Excel"
(todos los mixers + resumen por jornada, con filtros).

## Actualizaciones
Desde la versión 1.2 la app se firma con `app/condor.jks`. No borres ese archivo: con él, cada
versión nueva se instala encima de la anterior sin perder datos.

## Logo
Menú ⋮ > "Agregar logo de la empresa" y eliges la imagen del logo oficial (PNG). Queda guardado
y sale en la app y en el encabezado del PDF. También puedes poner `logo.png` en
`app/src/main/assets/` antes de compilar para que venga incluido.

## Copias de seguridad
Los datos viven en el teléfono. Haz de vez en cuando: menú ⋮ > "Guardar copia de seguridad"
(archivo .json) y guárdalo en otro lado. Para recuperarlos: "Restaurar copia de seguridad".
Si desinstalas la app sin copia, los datos se pierden.
