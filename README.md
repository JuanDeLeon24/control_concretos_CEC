# Control Concreto – PS-TUNEL 011

App Android para diligenciar el **Control de llegadas de concreto a obra** en los vaciados
de los módulos de revestimiento del túnel. Funciona **100 % sin internet**: no tiene permiso
de red, guarda todo en una base de datos interna del teléfono y genera el PDF en el mismo equipo.

## Cómo obtener el APK (elige una opción)

### Opción A – Sin instalar nada, usando GitHub (recomendada)
1. Crea una cuenta gratuita en github.com y un repositorio nuevo (privado si prefieres).
2. Sube todo el contenido de esta carpeta (botón "Add file" > "Upload files"; arrastra las
   carpetas). Importante: incluir la carpeta oculta `.github`.
3. Entra a la pestaña **Actions** y ejecuta **Compilar APK Control Concreto** con **Run workflow**. También compila automáticamente al subir cambios a `main` o `master`.
4. Descarga **ControlConcreto-APK-debug** para probar. Si están configurados los cuatro secretos de firma, también aparecerá **ControlConcreto-APK-release**, firmado para actualizar instalaciones previas.
5. Pasa el .apk al celular, ábrelo y acepta "Instalar apps de origen desconocido".

### Opción B – Con Android Studio en un computador
1. Instala Android Studio (gratis).
2. File > Open > selecciona la carpeta raíz del proyecto (la que contiene `settings.gradle.kts` y `app/`, no `android/` ni `ControlConcreto/`). Espera que termine de sincronizar.
3. Build > Build App Bundle(s) / APK(s) > Build APK(s).
4. Para depuración, el archivo queda en `app/build/outputs/apk/debug/app-debug.apk`. La compilación release requiere configurar `keystore.properties` con la llave original de firma.

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
La llave `app/condor.jks` se conserva para versiones release compatibles con instalaciones anteriores. Un APK debug sirve para pruebas, pero no reemplaza una instalación firmada con la llave release. El flujo de GitHub compila debug sin secretos y compila release solo si encuentra configurados `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD`.

## Catálogo de frentes y tramos (versión 1.3)
Menú ⋮ > "Catálogo de frentes y tramos".
1. Descarga la **plantilla** (Excel o CSV). Columnas: Frente | Tramo | Abscisa | Ubicación (módulo) |
   Elemento vaciado. La columna Elemento vaciado es una lista aparte: un elemento por fila.
   Cada fila es un lugar de vaciado. Si varias filas seguidas son del mismo frente, puedes dejar
   el Frente en blanco y la app repite el de arriba.
2. **Cargar archivo**: acepta .xlsx y .csv (separado por ; o por ,).
3. Al crear una jornada eliges **Lista cargada** (frente y tramo solo se escogen de la lista, y la
   localización de cada mixer también; al final de la lista está "Otro..." para escribir un lugar
   que no está en el catálogo) o **Escribir libre** (como siempre).
4. Para cambiar el catálogo: **Eliminar catálogo cargado** y luego cargar el nuevo.
   Las jornadas ya registradas nunca se modifican.
Sin catálogo cargado la app funciona exactamente igual que antes.

## Pasar una jornada completa a otro teléfono (versión 1.3)
- En la jornada: Exportar > **Jornada completa con fotos**. Sale un archivo .zip con los datos,
  todos los mixers y las fotos de las remisiones. Envíalo por WhatsApp o correo.
- En el otro teléfono: menú ⋮ > **Importar jornada de otro teléfono** y eliges el archivo.
- Si esa jornada ya existe (mismo identificador interno, o misma fecha, turno, frente y tramo),
  la app pregunta: **Reemplazar la que ya tengo** o **Conservar las dos**.
- No abras el .zip con otra app: se importa tal cual desde Control Concreto.

## Elemento vaciado
La lista de elementos (HD, HI, bóveda, placa, lo que necesites) se carga en la columna
"Elemento vaciado" de la plantilla. En las jornadas con lista cargada, cada mixer muestra esos
botones más "Otro..." (abre un recuadro para escribirlo). Queda junto a la localización
(ej. "Módulo 15 – K0+180 · BV-HD") y así sale en el PDF y en el Excel.

## Acceso de administrador
Se pide usuario y contraseña de administrador para: cargar catálogo, eliminar catálogo,
restaurar copia de seguridad e importar jornada. Descargar la plantilla es libre.
La app no guarda el usuario ni la contraseña: los pide cada vez.

## Logo
Menú ⋮ > "Agregar logo de la empresa" y eliges la imagen del logo oficial (PNG). Queda guardado
y sale en la app y en el encabezado del PDF. También puedes poner `logo.png` en
`app/src/main/assets/` antes de compilar para que venga incluido.

## Copias de seguridad
Los datos viven en el teléfono. Haz de vez en cuando: menú ⋮ > "Guardar copia de seguridad"
(archivo .json) y guárdalo en otro lado. Para recuperarlos: "Restaurar copia de seguridad".
Si desinstalas la app sin copia, los datos se pierden.
