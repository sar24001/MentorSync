package uam.edu.ni.mentorsync.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import uam.edu.ni.mentorsync.dao.SeguimientoDAO;
import uam.edu.ni.mentorsync.model.Tutoria;
import uam.edu.ni.mentorsync.model.Usuario;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReporteService {

    private final SeguimientoDAO seguimientoDAO = new SeguimientoDAO();
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<Tutoria> obtenerTutoriasEvaluables() {
        return seguimientoDAO.listarTutoriasEvaluables();
    }

    public List<Usuario> obtenerTutoresActivos() {
        return seguimientoDAO.listarTutoresActivos();
    }

    public void guardarEvaluacion(Long tutoriaId, Double notaInicial, Double notaFinal, String observaciones) {
        seguimientoDAO.actualizarCalificaciones(tutoriaId, notaInicial, notaFinal, observaciones);
    }

    // =========================================================================
    // 1. CONSTANCIA DE HORAS POR TUTOR (PDF & EXCEL)
    // =========================================================================

    public void exportarConstanciaHorasPDF(Usuario tutor, File destino) throws Exception {
        List<Tutoria> sesiones = seguimientoDAO.listarTutoriasValidadasPorTutor(tutor.getId());

        Document doc = new Document(PageSize.LETTER, 45, 45, 50, 50);
        try (FileOutputStream fos = new FileOutputStream(destino)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            Font fTitulo = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(30, 58, 138));
            Font fSubtitulo = new Font(Font.HELVETICA, 11, Font.BOLD, Color.DARK_GRAY);
            Font fNormal = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font fNegrita = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);

            Paragraph header = new Paragraph("UNIVERSIDAD AMERICANA (UAM)\nCOORDINACIÓN DE TUTORÍAS ACADÉMICAS - MENTORSYNC", fSubtitulo);
            header.setAlignment(Element.ALIGN_CENTER);
            doc.add(header);

            Paragraph titulo = new Paragraph("\nCONSTANCIA OFICIAL DE HORAS LABORALES", fTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingAfter(18);
            doc.add(titulo);

            String cuerpo = String.format(
                    "Por medio de la presente, la Coordinación Académica hace constar que el/la estudiante %s, " +
                            "con carnet %s, de la carrera de %s, ha participado activamente como TUTOR ACADÉMICO en el programa " +
                            "de tutorías entre pares, acumulando un total de %.2f horas validadas según el siguiente detalle:",
                    tutor.getNombre(),
                    tutor.getCif(),
                    tutor.getCarrera() != null ? tutor.getCarrera() : "No especificada",
                    calcularTotalHoras(tutor, sesiones)
            );
            Paragraph pCuerpo = new Paragraph(cuerpo, fNormal);
            pCuerpo.setAlignment(Element.ALIGN_JUSTIFIED);
            pCuerpo.setSpacingAfter(16);
            doc.add(pCuerpo);

            PdfPTable tabla = new PdfPTable(new float[]{1.8f, 3.2f, 3.0f, 2.0f, 1.5f});
            tabla.setWidthPercentage(100);
            agregarCeldaEncabezado(tabla, "Fecha");
            agregarCeldaEncabezado(tabla, "Asignatura");
            agregarCeldaEncabezado(tabla, "Estudiante Atendido");
            agregarCeldaEncabezado(tabla, "Horario");
            agregarCeldaEncabezado(tabla, "Horas");

            double sumaHoras = 0.0;
            for (Tutoria t : sesiones) {
                double duracion = t.getDuracionHoras();
                sumaHoras += duracion;
                agregarCeldaDato(tabla, t.getFecha().format(FMT_FECHA), fNormal);
                agregarCeldaDato(tabla, t.getAsignatura().toString(), fNormal);
                agregarCeldaDato(tabla, t.getEstudiante().getNombre(), fNormal);
                agregarCeldaDato(tabla, t.getHoraInicio() + " - " + t.getHoraFin(), fNormal);
                agregarCeldaDato(tabla, String.format("%.2f", duracion), fNormal);
            }

            if (sesiones.isEmpty()) {
                PdfPCell vacia = new PdfPCell(new Phrase("No se registran sesiones validadas en el historial detallado.", fNormal));
                vacia.setColspan(5);
                vacia.setPadding(8);
                tabla.addCell(vacia);
            }

            doc.add(tabla);

            Paragraph resumen = new Paragraph(
                    String.format("\nTotal Horas en Sesiones Validadas: %.2f hrs  |  Saldo Acumulado en Sistema: %.2f hrs",
                            sumaHoras, tutor.getHorasAcumuladas()),
                    fNegrita
            );
            resumen.setSpacingBefore(10);
            doc.add(resumen);

            Paragraph pie = new Paragraph(
                    "\n\nEmitido el " + LocalDate.now().format(FMT_FECHA) + ".\n\n\n_________________________________________\nFirma y Sello - Coordinación Académica",
                    fNormal
            );
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.setSpacingBefore(30);
            doc.add(pie);

            doc.close();
        }
    }

    public void exportarConstanciaHorasExcel(Usuario tutor, File destino) throws IOException {
        List<Tutoria> sesiones = seguimientoDAO.listarTutoriasValidadasPorTutor(tutor.getId());

        try (FileOutputStream fos = new FileOutputStream(destino);
             Workbook wb = new Workbook(fos, "MentorSync", "1.0")) {

            Worksheet ws = wb.newWorksheet("Constancia de Horas");

            ws.value(0, 0, "CONSTANCIA DE HORAS LABORALES - MENTORSYNC");
            ws.style(0, 0).bold().fontSize(14).set();

            ws.value(2, 0, "Tutor:");
            ws.value(2, 1, tutor.getNombre());
            ws.value(3, 0, "Carnet:");
            ws.value(3, 1, tutor.getCif());
            ws.value(4, 0, "Carrera:");
            ws.value(4, 1, tutor.getCarrera() != null ? tutor.getCarrera() : "N/A");
            ws.value(5, 0, "Horas Acumuladas:");
            ws.value(5, 1, tutor.getHorasAcumuladas());
            ws.style(2, 0).bold().set();
            ws.style(3, 0).bold().set();
            ws.style(4, 0).bold().set();
            ws.style(5, 0).bold().set();

            int headerRow = 7;
            String[] headers = {"ID Sesión", "Fecha", "Asignatura", "Estudiante", "Hora Inicio", "Hora Fin", "Duración (Hrs)", "Estado"};
            for (int c = 0; c < headers.length; c++) {
                ws.value(headerRow, c, headers[c]);
                ws.style(headerRow, c).bold().fillColor("1E3A8A").fontColor("FFFFFF").set();
            }

            int row = headerRow + 1;
            double totalHoras = 0.0;
            for (Tutoria t : sesiones) {
                double hrs = t.getDuracionHoras();
                totalHoras += hrs;
                ws.value(row, 0, t.getId());
                ws.value(row, 1, t.getFecha().format(FMT_FECHA));
                ws.value(row, 2, t.getAsignatura().toString());
                ws.value(row, 3, t.getEstudiante().getNombre());
                ws.value(row, 4, t.getHoraInicio().toString());
                ws.value(row, 5, t.getHoraFin().toString());
                ws.value(row, 6, hrs);
                ws.value(row, 7, t.getEstado());
                row++;
            }

            ws.value(row + 1, 5, "TOTAL HORAS VALIDADAS:");
            ws.style(row + 1, 5).bold().set();
            ws.value(row + 1, 6, totalHoras);
            ws.style(row + 1, 6).bold().set();
        }
    }

    // =========================================================================
    // 2. REPORTE DE RENDIMIENTO ACADÉMICO (PDF & EXCEL)
    // =========================================================================

    public void exportarRendimientoPDF(List<Tutoria> tutorias, File destino) throws Exception {
        Document doc = new Document(PageSize.LETTER.rotate(), 36, 36, 40, 40);
        try (FileOutputStream fos = new FileOutputStream(destino)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            Font fTitulo = new Font(Font.HELVETICA, 15, Font.BOLD, new Color(30, 58, 138));
            Font fNormal = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
            Font fNegrita = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);

            Paragraph titulo = new Paragraph("REPORTE DE IMPACTO Y RENDIMIENTO ACADÉMICO - MENTORSYNC", fTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingAfter(6);
            doc.add(titulo);

            Paragraph fechaGen = new Paragraph("Fecha de generación: " + LocalDate.now().format(FMT_FECHA), fNormal);
            fechaGen.setAlignment(Element.ALIGN_RIGHT);
            fechaGen.setSpacingAfter(14);
            doc.add(fechaGen);

            PdfPTable tabla = new PdfPTable(new float[]{2.5f, 2.5f, 2.3f, 1.3f, 1.3f, 1.4f, 3.2f});
            tabla.setWidthPercentage(100);
            agregarCeldaEncabezado(tabla, "Estudiante");
            agregarCeldaEncabezado(tabla, "Asignatura");
            agregarCeldaEncabezado(tabla, "Tutor");
            agregarCeldaEncabezado(tabla, "Nota Inicial");
            agregarCeldaEncabezado(tabla, "Nota Final");
            agregarCeldaEncabezado(tabla, "Variación");
            agregarCeldaEncabezado(tabla, "Observaciones");

            int evaluados = 0;
            int aprobados = 0;
            double sumaMejora = 0.0;

            for (Tutoria t : tutorias) {
                agregarCeldaDato(tabla, t.getEstudiante().getNombre(), fNormal);
                agregarCeldaDato(tabla, t.getAsignatura().toString(), fNormal);
                agregarCeldaDato(tabla, t.getTutor().getNombre(), fNormal);
                agregarCeldaDato(tabla, t.getNotaInicial() != null ? String.format("%.1f", t.getNotaInicial()) : "-", fNormal);
                agregarCeldaDato(tabla, t.getNotaFinal() != null ? String.format("%.1f", t.getNotaFinal()) : "-", fNormal);

                if (t.getNotaInicial() != null && t.getNotaFinal() != null) {
                    double diff = t.getNotaFinal() - t.getNotaInicial();
                    evaluados++;
                    sumaMejora += diff;
                    if (t.getNotaFinal() >= 70.0) aprobados++;
                    agregarCeldaDato(tabla, String.format("%+.1f pts", diff), fNormal);
                } else {
                    agregarCeldaDato(tabla, "Pendiente", fNormal);
                }

                agregarCeldaDato(tabla, t.getObservaciones() != null ? t.getObservaciones() : "", fNormal);
            }

            doc.add(tabla);

            double promedioMejora = evaluados > 0 ? sumaMejora / evaluados : 0.0;
            Paragraph indicadores = new Paragraph(
                    String.format("\nResumen Estadístico:  Total sesiones: %d  |  Con nota final: %d  |  Aprobados (>=70): %d  |  Variación promedio: %+.2f pts",
                            tutorias.size(), evaluados, aprobados, promedioMejora),
                    fNegrita
            );
            indicadores.setSpacingBefore(12);
            doc.add(indicadores);

            doc.close();
        }
    }

    public void exportarRendimientoExcel(List<Tutoria> tutorias, File destino) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destino);
             Workbook wb = new Workbook(fos, "MentorSync", "1.0")) {

            Worksheet ws = wb.newWorksheet("Rendimiento Académico");

            ws.value(0, 0, "REPORTE DE IMPACTO Y RENDIMIENTO ACADÉMICO - MENTORSYNC");
            ws.style(0, 0).bold().fontSize(14).set();
            ws.value(1, 0, "Generado el: " + LocalDate.now().format(FMT_FECHA));

            int headerRow = 3;
            String[] headers = {"Fecha", "Estudiante", "Carnet Est.", "Asignatura", "Tutor", "Estado", "Nota Inicial", "Nota Final", "Variación", "Condición", "Observaciones"};
            for (int c = 0; c < headers.length; c++) {
                ws.value(headerRow, c, headers[c]);
                ws.style(headerRow, c).bold().fillColor("16A34A").fontColor("FFFFFF").set();
            }

            int row = headerRow + 1;
            for (Tutoria t : tutorias) {
                ws.value(row, 0, t.getFecha().format(FMT_FECHA));
                ws.value(row, 1, t.getEstudiante().getNombre());
                ws.value(row, 2, t.getEstudiante().getCif());
                ws.value(row, 3, t.getAsignatura().toString());
                ws.value(row, 4, t.getTutor().getNombre());
                ws.value(row, 5, t.getEstado());

                if (t.getNotaInicial() != null) ws.value(row, 6, t.getNotaInicial());
                if (t.getNotaFinal() != null) ws.value(row, 7, t.getNotaFinal());

                if (t.getNotaInicial() != null && t.getNotaFinal() != null) {
                    double diff = t.getNotaFinal() - t.getNotaInicial();
                    ws.value(row, 8, diff);
                    ws.value(row, 9, t.getNotaFinal() >= 70.0 ? "APROBADO" : "EN RIESGO");
                } else {
                    ws.value(row, 9, "SIN EVALUAR");
                }

                ws.value(row, 10, t.getObservaciones() != null ? t.getObservaciones() : "");
                row++;
            }
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private double calcularTotalHoras(Usuario tutor, List<Tutoria> sesiones) {
        double sum = sesiones.stream().mapToDouble(Tutoria::getDuracionHoras).sum();
        return sum > 0 ? sum : tutor.getHorasAcumuladas();
    }

    private void agregarCeldaEncabezado(PdfPTable tabla, String texto) {
        Font font = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(new Color(30, 58, 138));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.addCell(cell);
    }

    private void agregarCeldaDato(PdfPTable tabla, String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        cell.setPadding(5);
        tabla.addCell(cell);
    }
}