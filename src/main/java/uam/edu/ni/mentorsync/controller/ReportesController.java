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
import uam.edu.ni.mentorsync.util.SesionActual;

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
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !"COORDINADOR".equals(usuario.getRol())) {
            return;
        }
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

            if ((notaIni != null && (!Double.isFinite(notaIni) || notaIni < 0 || notaIni > 100)) ||
                    (notaFin != null && (!Double.isFinite(notaFin) || notaFin < 0 || notaFin > 100))) {
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
        Optional<List<Tutoria>> seleccion = elegirFiltrosRendimiento();
        if (seleccion.isEmpty()) return;
        List<Tutoria> resultados = seleccion.get();
        if (resultados.isEmpty()) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Sin resultados",
                    "No hay tutorías que coincidan con los filtros.");
            return;
        }

        Optional<String> formatoOpt = elegirFormato("Reporte de Rendimiento Académico");
        if (formatoOpt.isEmpty()) return;

        String formato = formatoOpt.get();
        boolean esPdf = formato.startsWith("PDF");

        File destino = mostrarSaveDialog(((Node) event.getSource()).getScene().getWindow(), "Reporte_Rendimiento_MentorSync", esPdf);
        if (destino == null) return;

        try {
            if (esPdf) {
                reporteService.exportarRendimientoPDF(resultados, destino);
            } else {
                reporteService.exportarRendimientoExcel(resultados, destino);
            }
            mostrarAlerta(Alert.AlertType.INFORMATION, "Reporte Generado",
                    "Archivo guardado exitosamente en:\n" + destino.getAbsolutePath());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error al exportar", e.getMessage());
        }
    }

    private Optional<List<Tutoria>> elegirFiltrosRendimiento() {
        ComboBox<String> semestre = filtro("Todos los semestres", listaTutorias.stream()
                .map(this::semestreDe).distinct().sorted().toList());
        ComboBox<String> carrera = filtro("Todas las carreras", listaTutorias.stream()
                .map(tutoria -> tutoria.getEstudiante().getCarrera())
                .filter(nombre -> nombre != null && !nombre.isBlank()).distinct().sorted().toList());
        ComboBox<String> asignatura = filtro("Todas las asignaturas", listaTutorias.stream()
                .map(tutoria -> tutoria.getAsignatura().toString()).distinct().sorted().toList());

        Dialog<List<Tutoria>> dialogo = new Dialog<>();
        dialogo.setTitle("Reporte de rendimiento");
        dialogo.setHeaderText("Filtrar las tutorías para exportar");
        dialogo.initOwner(tblRendimiento.getScene().getWindow());

        var campos = new javafx.scene.layout.GridPane();
        campos.setHgap(12);
        campos.setVgap(8);
        campos.addRow(0, new Label("Semestre"), semestre);
        campos.addRow(1, new Label("Carrera del estudiante"), carrera);
        campos.addRow(2, new Label("Asignatura"), asignatura);
        dialogo.getDialogPane().setContent(campos);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.setResultConverter(boton -> boton == ButtonType.OK ? listaTutorias.stream()
                .filter(tutoria -> coincide(semestre, semestreDe(tutoria)))
                .filter(tutoria -> coincide(carrera, tutoria.getEstudiante().getCarrera()))
                .filter(tutoria -> coincide(asignatura, tutoria.getAsignatura().toString()))
                .toList() : null);
        return dialogo.showAndWait();
    }

    private ComboBox<String> filtro(String opcionGeneral, List<String> opciones) {
        ComboBox<String> filtro = new ComboBox<>();
        filtro.getItems().add(opcionGeneral);
        filtro.getItems().addAll(opciones);
        filtro.getSelectionModel().selectFirst();
        return filtro;
    }

    private boolean coincide(ComboBox<String> filtro, String valor) {
        return filtro.getSelectionModel().getSelectedIndex() == 0 || filtro.getValue().equals(valor);
    }

    private String semestreDe(Tutoria tutoria) {
        var fecha = tutoria.getFecha();
        return fecha.getYear() + (fecha.getMonthValue() <= 6 ? " - I" : " - II");
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