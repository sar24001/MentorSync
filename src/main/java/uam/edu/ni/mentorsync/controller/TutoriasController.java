package uam.edu.ni.mentorsync.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Datos;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Fila;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Opcion;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.TutoriaService;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public class TutoriasController {
    @FXML private ComboBox<Opcion> cmbTutor;
    @FXML private ComboBox<Opcion> cmbEstudiante;
    @FXML private ComboBox<Opcion> cmbAsignatura;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtHoraInicio;
    @FXML private TextField txtHoraFin;
    @FXML private TableView<Fila> tblTutorias;
    @FXML private TableColumn<Fila, LocalDate> colFecha;
    @FXML private TableColumn<Fila, String> colHorario;
    @FXML private TableColumn<Fila, String> colAsignatura;
    @FXML private TableColumn<Fila, String> colTutor;
    @FXML private TableColumn<Fila, String> colEstudiante;
    @FXML private TableColumn<Fila, String> colEstado;
    @FXML private Button btnAgendar;
    @FXML private Button btnCancelar;
    @FXML private Button btnMarcarRealizada;
    @FXML private Button btnValidarHoras;

    private final TutoriaService servicio = new TutoriaService();
    private Usuario actor;
    private boolean ocupado;

    @FXML
    private void initialize() {
        actor = SesionActual.getUsuario();
        boolean coordinador = "COORDINADOR".equals(actor.getRol());
        boolean tutor = "TUTOR".equals(actor.getRol());
        mostrarControl(btnAgendar.getParent(), coordinador);
        mostrarControl(btnCancelar, coordinador);
        mostrarControl(btnValidarHoras, coordinador);
        mostrarControl(btnMarcarRealizada, tutor);
        mostrarControl(btnMarcarRealizada.getParent(), coordinador || tutor);
        tblTutorias.getSelectionModel().selectedItemProperty().addListener(
                (observable, anterior, actual) -> actualizarBotones(ocupado));
        colFecha.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().fecha()));
        colHorario.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().horario()));
        colAsignatura.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().asignatura()));
        colTutor.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().tutor()));
        colEstudiante.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().estudiante()));
        colEstado.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().estado()));
        cargar();
    }

    @FXML
    private void handleAgendar() {
        Opcion tutor = cmbTutor.getValue();
        Opcion estudiante = cmbEstudiante.getValue();
        Opcion asignatura = cmbAsignatura.getValue();
        LocalDate fecha = dpFecha.getValue();
        if (tutor == null || estudiante == null || asignatura == null || fecha == null) {
            mostrar("Selecciona tutor, estudiante, asignatura y fecha.");
            return;
        }
        try {
            LocalTime inicio = leerHora(txtHoraInicio);
            LocalTime fin = leerHora(txtHoraFin);
            ejecutarCambio(() -> servicio.agendar(actor, tutor.id(), estudiante.id(), asignatura.id(), fecha, inicio, fin));
        } catch (DateTimeParseException e) {
            mostrar("Escribe las horas con formato HH:mm.");
        }
    }

    private LocalTime leerHora(TextField campo) {
        String hora = campo.getText().trim();
        if (!hora.matches("[0-9]{2}:[0-9]{2}")) {
            throw new DateTimeParseException("Formato HH:mm requerido", hora, 0);
        }
        return LocalTime.parse(hora);
    }

    @FXML
    private void handleCancelar() {
        cambiarSeleccionada(id -> servicio.cancelar(actor, id));
    }

    @FXML
    private void handleMarcarRealizada() {
        cambiarSeleccionada(id -> servicio.marcarRealizada(actor, id));
    }

    @FXML
    private void handleValidarHoras() {
        cambiarSeleccionada(id -> servicio.validarHoras(actor, id));
    }

    private void cambiarSeleccionada(LongFunction<String> cambio) {
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrar("Selecciona una tutoría en la tabla.");
            return;
        }
        ejecutarCambio(() -> cambio.apply(seleccionada.id()));
    }

    private void ejecutarCambio(Callable<String> operacion) {
        ejecutar(operacion, mensaje -> {
            mostrar(mensaje);
            cargar();
        });
    }

    private void cargar() {
        tblTutorias.setPlaceholder(new Label("Cargando sesiones..."));
        ejecutar(() -> servicio.cargar(actor), this::mostrarDatos);
    }

    private void mostrarDatos(Datos datos) {
        cmbTutor.getItems().setAll(datos.tutores());
        cmbEstudiante.getItems().setAll(datos.estudiantes());
        cmbAsignatura.getItems().setAll(datos.asignaturas());
        tblTutorias.getItems().setAll(datos.sesiones());
        tblTutorias.setPlaceholder(new Label("No hay sesiones para mostrar."));
        actualizarBotones(false);
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> completado) {
        actualizarBotones(true);
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operacion.call();
            }
        };
        tarea.setOnSucceeded(e -> completado.accept(tarea.getValue()));
        tarea.setOnFailed(e -> {
            tblTutorias.setPlaceholder(new Label("No se pudieron cargar las sesiones."));
            actualizarBotones(false);
            mostrar("No se pudo completar la operación de tutorías.");
        });
        Thread hilo = new Thread(tarea, "mentorsync-tutorias");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void mostrarControl(Node control, boolean visible) {
        control.setVisible(visible);
        control.setManaged(visible);
    }

    private void actualizarBotones(boolean ocupado) {
        this.ocupado = ocupado;
        boolean coordinador = "COORDINADOR".equals(actor.getRol());
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        boolean programada = seleccionada != null && "PROGRAMADA".equals(seleccionada.estado());
        boolean realizada = seleccionada != null && "REALIZADA".equals(seleccionada.estado());
        boolean puedeMarcar = "TUTOR".equals(actor.getRol()) && programada
                && seleccionada.tutorId() == actor.getId()
                && !seleccionada.fecha().atTime(seleccionada.horaFin()).isAfter(LocalDateTime.now());

        btnAgendar.setDisable(ocupado || !coordinador);
        btnCancelar.setDisable(ocupado || !coordinador || !programada);
        btnValidarHoras.setDisable(ocupado || !coordinador || !realizada);
        btnMarcarRealizada.setDisable(ocupado || !puedeMarcar);
    }

    private void mostrar(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alerta.initOwner(tblTutorias.getScene().getWindow());
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }
}
