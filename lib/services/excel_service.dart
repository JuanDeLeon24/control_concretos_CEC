import 'dart:io';

import 'package:excel/excel.dart';

import '../models/activity.dart';
import '../models/daily_report.dart';
import '../models/tunnel_module.dart';

class ExcelService {
  static const String HOJA_MATRIZ = 'Matriz cant.';
  static const String HOJA_AVANCES = 'Avances_Diarios';
  static const String HOJA_ACTIVIDADES = 'BD_Actividades';
  static const String HOJA_MODULOS = 'Módulos_VB';
  static const String HOJA_FORMATO_DIA = 'Formato Túnel_Dia';
  static const String HOJA_FORMATO_NOCHE = 'Formato Túnel_Noche';
  static const String HOJA_RESUMEN = 'Resumen';

  Future<List<TunnelModule>> leerModulosTunel0(
    String filePath,
  ) async {
    final file = File(filePath);

    if (!await file.exists()) {
      throw Exception('Archivo no encontrado: $filePath');
    }

    final bytes = await file.readAsBytes();
    final excel = Excel.decodeBytes(bytes);

    final modulos = <TunnelModule>[];
    final sheetModulos = excel.tables[HOJA_MODULOS];

    if (sheetModulos == null) {
      return modulos;
    }

    for (int i = 4; i < sheetModulos.maxRows; i++) {
      final row = sheetModulos.rows[i];

      if (row.isEmpty) {
        continue;
      }

      final moduloValue = _celda(row, 0);
      final moduloNum = _parseInt(moduloValue);

      if (moduloNum == null) {
        continue;
      }

      try {
        final abscisaIni = _parseDouble(_celda(row, 1));
        final abscisaFin = _parseDouble(_celda(row, 2));
        final longitud = _parseDouble(_celda(row, 3));

        modulos.add(
          TunnelModule(
            id: moduloNum,
            moduloNumber: moduloNum,
            abscisaInicial: abscisaIni,
            abscisaFinal: abscisaFin,
            longitud: longitud,
            galeria: 'Túnel 0',
            frente: 'Frente 1',
            estado: 'En ejecución',
            avanceTotal: 0,
          ),
        );
      } catch (_) {
        continue;
      }
    }

    return modulos;
  }

  Future<List<DailyReport>> leerAvancesDiarios(
    String filePath,
  ) async {
    final file = File(filePath);

    if (!await file.exists()) {
      throw Exception('Archivo no encontrado: $filePath');
    }

    final bytes = await file.readAsBytes();
    final excel = Excel.decodeBytes(bytes);

    final reportes = <DailyReport>[];
    final sheet = excel.tables[HOJA_AVANCES];

    if (sheet == null) {
      return reportes;
    }

    final actividades = <ReportActivity>[];
    DateTime? fechaActual;

    for (int i = 3; i < sheet.maxRows; i++) {
      final row = sheet.rows[i];

      if (row.isEmpty) {
        continue;
      }

      final actividad = _parseTexto(_celda(row, 0));

      if (actividad.isEmpty) {
        continue;
      }

      final actividadNormalizada = actividad.toUpperCase();

      if (actividadNormalizada.startsWith('AVANCE') ||
          actividadNormalizada.startsWith('TÚNEL') ||
          actividadNormalizada.startsWith('TUNEL')) {
        if (actividades.isNotEmpty && fechaActual != null) {
          reportes.add(
            DailyReport(
              fecha: fechaActual,
              turno: 'Día',
              actividades: List<ReportActivity>.from(actividades),
            ),
          );

          actividades.clear();
        }

        continue;
      }

      try {
        final fechaLeida = _parseFecha(_celda(row, 2));

        if (fechaLeida != null) {
          fechaActual = fechaLeida;
        }

        final pkIni = _parseDouble(_celda(row, 4));
        final pkFin = _parseDouble(_celda(row, 5));
        final cantidad = _parseDouble(_celda(row, 7));
        final unidad = _parseTexto(_celda(row, 8));
        final avance = _parseDouble(_celda(row, 10));
        final codigo = _parseTexto(_celda(row, 12));

        actividades.add(
          ReportActivity(
            codigo: codigo,
            nombre: actividad,
            frente: 'TUNEL 0',
            grupo: '',
            pkInicial: pkIni,
            pkFinal: pkFin,
            cantidad: cantidad,
            unidad: unidad,
            avancePercent: avance,
          ),
        );
      } catch (_) {
        continue;
      }
    }

    if (actividades.isNotEmpty && fechaActual != null) {
      reportes.add(
        DailyReport(
          fecha: fechaActual,
          turno: 'Día',
          actividades: List<ReportActivity>.from(actividades),
        ),
      );
    }

    return reportes;
  }

  Future<List<Activity>> leerActividades(
    String filePath,
  ) async {
    final file = File(filePath);

    if (!await file.exists()) {
      throw Exception('Archivo no encontrado: $filePath');
    }

    final bytes = await file.readAsBytes();
    final excel = Excel.decodeBytes(bytes);

    final actividades = <Activity>[];
    final sheet = excel.tables[HOJA_ACTIVIDADES];

    if (sheet == null) {
      return actividades;
    }

    for (int i = 5; i < sheet.maxRows; i++) {
      final row = sheet.rows[i];

      if (row.isEmpty) {
        continue;
      }

      final actividad = _parseTexto(_celda(row, 2));

      if (actividad.isEmpty) {
        continue;
      }

      try {
        final fechaInicio = _parseFecha(_celda(row, 0));
        final turno = _parseTexto(
          _celda(row, 1),
          valorPredeterminado: 'Día',
        );
        final grupo = _parseTexto(_celda(row, 4));
        final unidad = _parseTexto(_celda(row, 5));

        final dato1 = _celda(row, 9);
        final dato2 = _celda(row, 11);

        final cantidadEjecutada = !_esCeldaVacia(dato1)
            ? _parseDouble(dato1)
            : _parseDouble(dato2);

        actividades.add(
          Activity(
            id: '$i',
            nombre: actividad,
            estado: 'En ejecución',
            fechaInicio: fechaInicio,
            cantidadEjecutada: cantidadEjecutada,
            unidad: unidad,
            grupo: grupo,
            turno: turno,
          ),
        );
      } catch (_) {
        continue;
      }
    }

    return actividades;
  }

  Map<int, double> calcularAvancePorModulo(
    List<Activity> actividades,
  ) {
    final avancePorModulo = <int, double>{};

    for (final actividad in actividades) {
      final match = RegExp(
        r'(\d+)(?::(\d+))?',
      ).firstMatch(actividad.nombre);

      if (match == null) {
        continue;
      }

      final modulo = int.tryParse(match.group(1) ?? '');

      if (modulo == null) {
        continue;
      }

      avancePorModulo[modulo] =
          (avancePorModulo[modulo] ?? 0) +
          actividad.avancePercent;
    }

    return avancePorModulo;
  }

  CellValue? _celda(List<Data?> row, int index) {
    if (index < 0 || index >= row.length) {
      return null;
    }

    return row[index]?.value;
  }

  DateTime? _parseFecha(CellValue? value) {
    if (value == null) {
      return null;
    }

    if (value is DateTimeCellValue) {
      return value.asDateTimeLocal();
    }

    if (value is DateCellValue) {
      return value.asDateTimeLocal();
    }

    if (value is TextCellValue) {
      return _parseFechaTexto(value.value.toString());
    }

    if (value is IntCellValue) {
      return _parseFechaSerialExcel(value.value.toDouble());
    }

    if (value is DoubleCellValue) {
      return _parseFechaSerialExcel(value.value);
    }

    return null;
  }

  DateTime? _parseFechaTexto(String value) {
    final texto = value.trim();

    if (texto.isEmpty) {
      return null;
    }

    final fechaIso = DateTime.tryParse(texto);

    if (fechaIso != null) {
      return fechaIso;
    }

    final coincidencia = RegExp(
      r'^(\d{1,2})\d{1,2}\d{2}|\d{4}$',
    ).firstMatch(texto);

    if (coincidencia == null) {
      return null;
    }

    final dia = int.tryParse(coincidencia.group(1) ?? '');
    final mes = int.tryParse(coincidencia.group(2) ?? '');
    var anio = int.tryParse(coincidencia.group(3) ?? '');

    if (dia == null || mes == null || anio == null) {
      return null;
    }

    if (anio < 100) {
      anio += anio >= 70 ? 1900 : 2000;
    }

    if (mes < 1 || mes > 12 || dia < 1 || dia > 31) {
      return null
