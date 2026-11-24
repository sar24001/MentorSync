package uam.edu.ni.mentorsync.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import uam.edu.ni.mentorsync.model.Tutoria;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.ReporteService;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class ReportesController {

    @FXML private ComboBox<Tutoria> cmbTutoriaEvaluar;
    @FXML private TextField txtNotaInicial;
    @FXML private TextField txtNotaFinal;
    @FXML private TextField txtObservaciones;
    @FXML private Button btnGuardarNotas;

    @FXML private ComboBox<Usuario> cmbTutorReporte;
    @FXML private Button btnReporteHoras;
    @FXML private Button btnReporteRendimiento;

    @FXML private TableView<Tutoria> tblRendimiento;
    @FXML private TableColumn<Tutoria, String> colEstudiante;
    @FXML private TableColumn<Tutoria, String> colAsignatura;
    @FXML private TableColumn<Tutoria, String> colTutor;
    @FXML private TableColumn<Tutoria, Double> colNotaInicial;
    @FXML private TableColumn<Tutoria, Double> colNotaFinal;
    @FXML private TableColumn<Tutoria, String> colObservaciones;

    private final ReporteService reporteService = new ReporteService();
    private final ObservableList<Tutoria> listaTutorias = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarTabla();
        configurarListeners();
        cargarDatos();
    }

    private void configurarTabla() {
        colEstudiante.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getEstudiante().getNombre()));
        colAsignatura.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getAsignatura().toString()));
        colTutor.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTutor().getNombre()));
        colNotaInicial.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getNotaInicial()));
        colNotaFinal.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getNotaFinal()));
        colObservaciones.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getObservaciones() != null ? cell.getValue().getObservaciones() : ""));

        tblRendimiento.setItems(listaTutorias);
    }

    private void configurarListeners() {
        // Habilita guardar notas al seleccionar una sesión y rellena campos existentes
        cmbTutoriaEvaluar.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, nueva) -> {
            btnGuardarNotas.setDisable(nueva == null);
            if (nueva != null) {
                txtNotaInicial.setText(nueva.getNotaInicial() != null ? String.valueOf(nueva.getNotaInicial()) : "");
                txtNotaFinal.setText(nueva.getNotaFinal() != null ? String.valueOf(nueva.getNotaFinal()) : "");
                txtObservaciones.setText(nueva.getObservaciones() != null ? nueva.getObservaciones() : "");
            }
        });

        // Formato legible del tutor en el ComboBox
        cmbTutorReporte.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Usuario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getCif() + " - " + item.getNombre());
            }
        });
        cmbTutorReporte.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Usuario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getCif() + " - " + item.getNombre());
            }
        });

        // Habilita descargar constancia al elegir un tutor
        cmbTutorReporte.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, tutor) ->
                btnReporteHoras.setDisable(tutor == null)
        );
    }

    private void cargarDatos() {
        try {
            List<Tutoria> tutorias = reporteService.obtenerTutoriasEvaluables();
            listaTutorias.setAll(tutorias);
            cmbTutoriaEvaluar.setItems(FXCollections.observableArrayList(tutorias));
            btnReporteRendimiento.setDisable(tutorias.isEmpty());

            List<Usuario> tutores = reporteService.obtenerTutoresActivos();
            cmbTutorReporte.setItems(FXCollections.observableArrayList(tutores));
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos",
                    "No se pudieron cargar los datos de reportes: " + e.getMessage());
        }
    }

    @FXML
    private void handleGuardarNotas(ActionEvent event) {
        Tutoria seleccionada = cmbTutoriaEvaluar.getValue();
        if (seleccionada == null) return;

        try {
            Double notaIni = txtNotaInicial.getText().isBlank() ? null : Double.parseDouble(txtNotaInicial.getText().trim());
            Double notaFin = txtNotaFinal.getText().isBlank() ? null : Double.parseDouble(txtNotaFinal.getText().trim());
            String obs = txtObservaciones.getText().trim();

            if ((notaIni != null && (notaIni < 0 || notaIni > 100)) ||
                    (notaFin != null && (notaFin < 0 || notaFin > 100))) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación", "Las notas deben estar entre 0 y 100.");
                return;
            }

            reporteService.guardarEvaluacion(seleccionada.getId(), notaIni, notaFin, obs);
            cargarDatos();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Éxito", "Calificaciones actualizadas correctamente.");
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Formato inválido", "Ingrese valores numéricos válidos para las notas.");
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudieron guardar las notas: " + e.getMessage());
        }
    }

    @FXML
    private void handleGenerarReporteHoras(ActionEvent event) {
        Usuario tutor = cmbTutorReporte.getValue();
        if (tutor == null) return;

        Optional<String> formatoOpt = elegirFormato("Constancia de Horas - " + tutor.getNombre());
        if (formatoOpt.isEmpty()) return;

        String formato = formatoOpt.get();
        boolean esPdf = formato.startsWith("PDF");
        String nombreBase = "Constancia_Horas_" + tutor.getCif();

        File destino = mostrarSaveDialog(((Node) event.getSource()).getScene().getWindow(), nombreBase, esPdf);
        if (destino == null) return;

        try {
            if (esPdf) {
                reporteService.exportarConstanciaHorasPDF(tutor, destino);
            } else {
                reporteService.exportarConstanciaHorasExcel(tutor, destino);
            }
            mostrarAlerta(Alert.AlertType.INFORMATION, "Reporte Generado",
                    "Archivo guardado exitosamente en:\n" + destino.getAbsolutePath());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error al exportar", e.getMessage());
        }
    }

    @FXML
    private void handleGenerarReporteRendimiento(ActionEvent event) {
        if (listaTutorias.isEmpty()) return;

        Optional<String> formatoOpt = elegirFormato("Reporte de Rendimiento Académico");
        if (formatoOpt.isEmpty()) return;

        String formato = formatoOpt.get();
        boolean esPdf = formato.startsWith("PDF");

        File destino = mostrarSaveDialog(((Node) event.getSource()).getScene().getWindow(), "Reporte_Rendimiento_MentorSync", esPdf);
        if (destino == null) return;

        try {
            if (esPdf) {
                reporteService.exportarRendimientoPDF(listaTutorias, destino);
            } else {
                reporteService.exportarRendimientoExcel(listaTutorias, destino);
            }
            mostrarAlerta(Alert.AlertType.INFORMATION, "Reporte Generado",
                    "Archivo guardado exitosamente en:\n" + destino.getAbsolutePath());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error al exportar", e.getMessage());
        }
    }

    private Optional<String> elegirFormato(String tituloReporte) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("PDF (.pdf)", "PDF (.pdf)", "Excel (.xlsx)");
        dialog.setTitle("Formato de Exportación");
        dialog.setHeaderText(tituloReporte);
        dialog.setContentText("Seleccione el formato de salida:");
        return dialog.showAndWait();
    }

    private File mostrarSaveDialog(Window owner, String nombreSugerido, boolean esPdf) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar Reporte");
        if (esPdf) {
            fileChooser.setInitialFileName(nombreSugerido + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
        } else {
            fileChooser.setInitialFileName(nombreSugerido + ".xlsx");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Libro de Excel (*.xlsx)", "*.xlsx"));
        }
        return fileChooser.showSaveDialog(owner);
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}