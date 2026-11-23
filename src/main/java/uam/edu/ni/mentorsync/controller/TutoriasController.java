package uam.edu.ni.mentorsync.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Datos;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Fila;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Opcion;
import uam.edu.ni.mentorsync.dao.UsuarioDAO;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.TutoriaService;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public class TutoriasController {

    @FXML private TextField txtBuscarTutor;
    @FXML private TextField txtTutorSeleccionado;
    @FXML private Button btnBuscarTutor;

    @FXML private TextField txtBuscarEstudiante;
    @FXML private TextField txtEstudianteSeleccionado;
    @FXML private Button btnBuscarEstudiante;

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
    @FXML private Button btnSolicitarCancelacion;
    @FXML private Button btnCalificarTutor;
    @FXML private Button btnCancelar;
    @FXML private Button btnMarcarRealizada;
    @FXML private Button btnValidarHoras;

    private final TutoriaService servicio = new TutoriaService();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    private Usuario actor;
    private Usuario tutorSeleccionado;
    private Usuario estudianteSeleccionado;
    private boolean ocupado;

    @FXML
    private void initialize() {
        actor = SesionActual.getUsuario();
        boolean coordinador = "COORDINADOR".equals(actor.getRol());
        boolean tutor = "TUTOR".equals(actor.getRol());
        boolean estudiante = "ESTUDIANTE".equals(actor.getRol());

        mostrarControl(btnAgendar.getParent(), coordinador);
        mostrarControl(btnCancelar, coordinador);
        mostrarControl(btnValidarHoras, coordinador);
        mostrarControl(btnMarcarRealizada, tutor);
        mostrarControl(btnSolicitarCancelacion, estudiante);
        mostrarControl(btnCalificarTutor, estudiante);
        mostrarControl(btnMarcarRealizada.getParent(), coordinador || tutor || estudiante);

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
    private void handleBuscarTutor() {
        String criterio = txtBuscarTutor.getText().trim();
        if (criterio.length() < 2) {
            mostrar("Ingresa al menos 2 letras del nombre del tutor.");
            return;
        }

        buscarUsuariosAsync(
                btnBuscarTutor,
                () -> usuarioDAO.buscarTutoresActivosPorNombre(criterio),
                resultados -> {
                    if (resultados.isEmpty()) {
                        tutorSeleccionado = null;
                        txtTutorSeleccionado.clear();
                        mostrar("No se encontró ningún tutor activo con: " + criterio);
                    } else if (resultados.size() == 1) {
                        seleccionarTutor(resultados.get(0));
                    } else {
                        mostrarMenuResultados(txtBuscarTutor, resultados, this::seleccionarTutor);
                    }
                }
        );
    }

    @FXML
    private void handleBuscarEstudiante() {
        String criterio = txtBuscarEstudiante.getText().trim();
        if (criterio.length() < 2) {
            mostrar("Ingresa al menos 2 letras del nombre del estudiante.");
            return;
        }

        buscarUsuariosAsync(
                btnBuscarEstudiante,
                () -> usuarioDAO.buscarEstudiantesActivosPorNombre(criterio),
                resultados -> {
                    if (resultados.isEmpty()) {
                        estudianteSeleccionado = null;
                        txtEstudianteSeleccionado.clear();
                        mostrar("No se encontró ningún estudiante activo con: " + criterio);
                    } else if (resultados.size() == 1) {
                        seleccionarEstudiante(resultados.get(0));
                    } else {
                        mostrarMenuResultados(txtBuscarEstudiante, resultados, this::seleccionarEstudiante);
                    }
                }
        );
    }

    private void seleccionarTutor(Usuario tutor) {
        this.tutorSeleccionado = tutor;
        txtTutorSeleccionado.setText(tutor.getNombre() + " (" + tutor.getCif() + ")");
    }

    private void seleccionarEstudiante(Usuario estudiante) {
        this.estudianteSeleccionado = estudiante;
        txtEstudianteSeleccionado.setText(estudiante.getNombre() + " (" + estudiante.getCif() + ")");
    }

    private void mostrarMenuResultados(TextField campoAncla, List<Usuario> resultados, Consumer<Usuario> alElegir) {
        ContextMenu menu = new ContextMenu();
        for (Usuario usuario : resultados) {
            MenuItem item = new MenuItem(usuario.getNombre() + " — CIF: " + usuario.getCif());
            item.setOnAction(e -> alElegir.accept(usuario));
            menu.getItems().add(item);
        }
        menu.show(campoAncla, Side.BOTTOM, 0, 0);
    }

    @FXML
    private void handleAgendar() {
        Opcion asignatura = cmbAsignatura.getValue();
        LocalDate fecha = dpFecha.getValue();

        if (tutorSeleccionado == null || estudianteSeleccionado == null || asignatura == null || fecha == null) {
            mostrar("Busca y selecciona tutor, estudiante, asignatura y fecha.");
            return;
        }

        try {
            LocalTime inicio = leerHora(txtHoraInicio);
            LocalTime fin = leerHora(txtHoraFin);
            long tutorId = tutorSeleccionado.getId();
            long estudianteId = estudianteSeleccionado.getId();

            ejecutar(() -> servicio.agendar(actor, tutorId, estudianteId, asignatura.id(), fecha, inicio, fin),
                    mensaje -> {
                        mostrar(mensaje);
                        limpiarFormularioAgendar();
                        cargar();
                    });
        } catch (DateTimeParseException e) {
            mostrar("Escribe las horas con formato HH:mm.");
        }
    }

    private void limpiarFormularioAgendar() {
        tutorSeleccionado = null;
        estudianteSeleccionado = null;
        txtBuscarTutor.clear();
        txtTutorSeleccionado.clear();
        txtBuscarEstudiante.clear();
        txtEstudianteSeleccionado.clear();
        cmbAsignatura.getSelectionModel().clearSelection();
        dpFecha.setValue(null);
        txtHoraInicio.clear();
        txtHoraFin.clear();
    }

    private LocalTime leerHora(TextField campo) {
        String hora = campo.getText().trim();
        if (!hora.matches("[0-9]{2}:[0-9]{2}")) {
            throw new DateTimeParseException("Formato HH:mm requerido", hora, 0);
        }
        return LocalTime.parse(hora);
    }

    @FXML
    private void handleSolicitarCancelacion() {
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrar("Selecciona una tutoría programada en la tabla.");
            return;
        }

        TextInputDialog dialogo = new TextInputDialog();
        dialogo.initOwner(tblTutorias.getScene().getWindow());
        dialogo.setTitle("Solicitar Cancelación");
        dialogo.setHeaderText("Tutoría de " + seleccionada.asignatura() + " (" + seleccionada.fecha() + ")");
        dialogo.setContentText("Motivo de la cancelación:");

        dialogo.showAndWait().ifPresent(motivo ->
                ejecutarCambio(() -> servicio.solicitarCancelacion(actor, seleccionada.id(), motivo))
        );
    }

    @FXML
    private void handleCalificarTutor() {
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrar("Selecciona una tutoría realizada o validada en la tabla.");
            return;
        }

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.initOwner(tblTutorias.getScene().getWindow());
        dialogo.setTitle("Calificar Tutor");
        dialogo.setHeaderText("Evalúa la sesión con " + seleccionada.tutor() + " (" + seleccionada.asignatura() + ")");
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<Integer> cmbEstrellas = new ComboBox<>();
        cmbEstrellas.getItems().addAll(5, 4, 3, 2, 1);
        cmbEstrellas.setValue(5);

        TextArea txtComentario = new TextArea();
        txtComentario.setPromptText("Escribe tu reseña sobre la claridad y puntualidad del tutor...");
        txtComentario.setPrefRowCount(3);
        txtComentario.setWrapText(true);

        VBox contenido = new VBox(8,
                new Label("Puntuación (1 a 5 estrellas):"), cmbEstrellas,
                new Label("Comentario o reseña:"), txtComentario);
        contenido.setPadding(new Insets(10));
        dialogo.getDialogPane().setContent(contenido);

        dialogo.showAndWait().ifPresent(respuesta -> {
            if (respuesta == ButtonType.OK) {
                ejecutarCambio(() -> servicio.calificarTutor(
                        actor, seleccionada.id(), cmbEstrellas.getValue(), txtComentario.getText()));
            }
        });
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
        cmbAsignatura.getItems().setAll(datos.asignaturas());
        tblTutorias.getItems().setAll(datos.sesiones());
        tblTutorias.setPlaceholder(new Label("No hay sesiones para mostrar."));
        actualizarBotones(false);
    }

    private void buscarUsuariosAsync(Button boton, Callable<List<Usuario>> consulta, Consumer<List<Usuario>> alCompletar) {
        boton.setDisable(true);
        Task<List<Usuario>> tarea = new Task<>() {
            @Override
            protected List<Usuario> call() throws Exception {
                return consulta.call();
            }
        };
        tarea.setOnSucceeded(e -> {
            boton.setDisable(false);
            alCompletar.accept(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            boton.setDisable(false);
            mostrar("No se pudo realizar la búsqueda en la base de datos.");
        });
        Thread hilo = new Thread(tarea, "mentorsync-busqueda-usuario");
        hilo.setDaemon(true);
        hilo.start();
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
        boolean estudiante = "ESTUDIANTE".equals(actor.getRol());
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();

        boolean programada = seleccionada != null && "PROGRAMADA".equals(seleccionada.estado());
        boolean solicitudCancelacion = seleccionada != null && "SOLICITUD_CANCELACION".equals(seleccionada.estado());
        boolean realizada = seleccionada != null && "REALIZADA".equals(seleccionada.estado());
        boolean completada = seleccionada != null
                && ("REALIZADA".equals(seleccionada.estado()) || "VALIDADA".equals(seleccionada.estado()));

        boolean puedeMarcar = "TUTOR".equals(actor.getRol()) && programada
                && seleccionada.tutorId() == actor.getId()
                && !seleccionada.fecha().atTime(seleccionada.horaFin()).isAfter(LocalDateTime.now());

        btnAgendar.setDisable(ocupado || !coordinador);
        btnCancelar.setDisable(ocupado || !coordinador || (!programada && !solicitudCancelacion));
        btnValidarHoras.setDisable(ocupado || !coordinador || !realizada);
        btnMarcarRealizada.setDisable(ocupado || !puedeMarcar);
        btnSolicitarCancelacion.setDisable(ocupado || !estudiante || !programada);
        btnCalificarTutor.setDisable(ocupado || !estudiante || !completada);
    }

    private void mostrar(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alerta.initOwner(tblTutorias.getScene().getWindow());
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }
}