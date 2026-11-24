package uam.edu.ni.mentorsync.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Datos;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Detalle;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Fila;
import uam.edu.ni.mentorsync.dao.TutoriaDAO.Opcion;
import uam.edu.ni.mentorsync.dao.UsuarioDAO;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.TutoriaService;
import uam.edu.ni.mentorsync.service.UsuarioService;
import uam.edu.ni.mentorsync.service.AsignaturaService;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.time.LocalDate;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public class TutoriasController {
    private static final System.Logger LOGGER = System.getLogger(TutoriasController.class.getName());
    private static final String TODOS_ESTADOS = "Todos los estados";
    private static final Opcion TODAS_ASIGNATURAS = new Opcion(0, "Todas las asignaturas");

    @FXML private Node panelAgendar;

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
    @FXML private Button btnVerDetalle;
    @FXML private Button btnReprogramar;
    @FXML private Button btnEliminar;
    @FXML private Button btnActualizar;
    @FXML private ComboBox<String> cmbFiltroEstado;
    @FXML private ComboBox<Opcion> cmbFiltroAsignatura;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Label lblFiltros;
    @FXML private Label lblMateriasTutor;
    @FXML private Button btnCatalogo;
    @FXML private Button btnImportarEstudiante;

    private final TutoriaService servicio = new TutoriaService();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final UsuarioService servicioUsuarios = new UsuarioService();
    private final AsignaturaService servicioAsignaturas = new AsignaturaService();

    private final Button btnConfirmarAsistencia = new Button("Confirmar asistencia");
    private Usuario actor;
    private Usuario tutorSeleccionado;
    private Usuario estudianteSeleccionado;
    private List<Opcion> estudiantesDisponibles = List.of();
    private boolean ocupado;
    private int operacionesEnCurso;
    private final ObservableList<Fila> sesiones = FXCollections.observableArrayList();
    private final FilteredList<Fila> sesionesFiltradas = new FilteredList<>(sesiones);

    @FXML
    private void initialize() {
        actor = SesionActual.getUsuario();
        boolean coordinador = "COORDINADOR".equals(actor.getRol());
        boolean tutor = "TUTOR".equals(actor.getRol());
        boolean estudiante = "ESTUDIANTE".equals(actor.getRol());

        ((Pane) btnMarcarRealizada.getParent()).getChildren().add(btnConfirmarAsistencia);
        btnConfirmarAsistencia.setOnAction(event ->
                cambiarSeleccionada(id -> servicio.confirmarAsistencia(actor, id)));
        mostrarControl(btnConfirmarAsistencia, estudiante || tutor);

        mostrarControl(panelAgendar, coordinador);
        mostrarControl(btnCancelar, coordinador);
        mostrarControl(btnValidarHoras, coordinador);
        mostrarControl(btnReprogramar, coordinador);
        mostrarControl(btnEliminar, coordinador);
        mostrarControl(btnCatalogo, coordinador);
        mostrarControl(btnMarcarRealizada, tutor);
        mostrarControl(btnSolicitarCancelacion, estudiante);
        mostrarControl(btnCalificarTutor, estudiante);
        mostrarControl(btnMarcarRealizada.getParent(), coordinador || tutor || estudiante);

        tblTutorias.getSelectionModel().selectedItemProperty().addListener(
                (observable, anterior, actual) -> actualizarBotones(ocupado));

        colFecha.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().fecha()));
        colHorario.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().horario() + " · " + fila.getValue().modalidad() + (fila.getValue().grupal() ? " · Grupal" : "")));
        colAsignatura.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().asignatura()));
        colTutor.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().tutor()));
        colEstudiante.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().estudiante()));
        colEstado.setCellValueFactory(fila -> {
            Fila sesion = fila.getValue();
            String estado = sesion.estado();
            if ("REALIZADA".equals(estado)) {
                estado += sesion.asistenciaConfirmada() ? " · Asistencia confirmada" : " · Asistencia pendiente";
            }
            return new ReadOnlyObjectWrapper<>(estado);
        });

        SortedList<Fila> ordenadas = new SortedList<>(sesionesFiltradas);
        ordenadas.comparatorProperty().bind(tblTutorias.comparatorProperty());
        tblTutorias.setItems(ordenadas);
        tblTutorias.setRowFactory(tabla -> {
            TableRow<Fila> fila = new TableRow<>();
            fila.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                        && !fila.isEmpty() && !ocupado) {
                    tblTutorias.getSelectionModel().select(fila.getItem());
                    handleVerDetalle();
                }
            });
            return fila;
        });
        cmbFiltroEstado.getItems().setAll(TODOS_ESTADOS, "PROGRAMADA", "REALIZADA", "VALIDADA",
                "CANCELADA", "SOLICITUD_CANCELACION");
        cmbFiltroEstado.getSelectionModel().selectFirst();
        cmbFiltroAsignatura.getItems().setAll(TODAS_ASIGNATURAS);
        cmbFiltroAsignatura.getSelectionModel().selectFirst();
        cmbFiltroEstado.valueProperty().addListener((o, anterior, nuevo) -> filtrar());
        cmbFiltroAsignatura.valueProperty().addListener((o, anterior, nuevo) -> filtrar());
        dpDesde.valueProperty().addListener((o, anterior, nuevo) -> filtrar());
        dpHasta.valueProperty().addListener((o, anterior, nuevo) -> filtrar());

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
        cmbAsignatura.getItems().clear();
        long tutorId = tutor.getId();
        ejecutar(() -> servicio.materiasTutor(actor, tutorId), materias -> {
            if (tutorSeleccionado == null || tutorSeleccionado.getId() != tutorId) return;
            cmbAsignatura.getItems().setAll(materias);
            lblMateriasTutor.setText(materias.isEmpty()
                    ? "Este tutor no tiene materias habilitadas. Configúralas en Gestión de Tutores."
                    : "Solo se muestran materias activas habilitadas para este tutor.");
        });
    }

    private void seleccionarEstudiante(Usuario estudiante) {
        this.estudianteSeleccionado = estudiante;
        txtEstudianteSeleccionado.setText(estudiante.getNombre() + " (" + estudiante.getCif() + ")");
    }

    @FXML
    private void handleImportarEstudiante() {
        if (ocupado || !"COORDINADOR".equals(actor.getRol())) return;
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.initOwner(tblTutorias.getScene().getWindow());
        dialogo.setTitle("Buscar estudiante por carnet");
        dialogo.setHeaderText("Consultar o registrar estudiante desde la API académica");
        TextField cif = new TextField(txtBuscarEstudiante.getText().trim().matches("[0-9]{8}")
                ? txtBuscarEstudiante.getText().trim() : "");
        cif.setId("txtCarnetApi");
        cif.setPromptText("Carnet de 8 dígitos");
        PasswordField clave = new PasswordField();
        clave.setId("txtClaveEstudiante");
        clave.setPromptText("Contraseña inicial, solo para una cuenta nueva");
        Label mensaje = new Label("Si ya existe una cuenta activa, se conservará su contraseña.");
        mensaje.setWrapText(true);
        VBox formulario = new VBox(8, new Label("Carnet"), cif, new Label("Contraseña inicial"), clave, mensaje);
        formulario.setPadding(new Insets(12));
        dialogo.getDialogPane().setContent(formulario);
        dialogo.getDialogPane().setPrefWidth(470);
        ButtonType buscar = new ButtonType("Buscar y seleccionar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(buscar, ButtonType.CANCEL);
        SimpleBooleanProperty guardando = new SimpleBooleanProperty();
        cif.disableProperty().bind(guardando);
        clave.disableProperty().bind(guardando);
        dialogo.getDialogPane().lookupButton(buscar).disableProperty().bind(guardando);
        dialogo.getDialogPane().lookupButton(ButtonType.CANCEL).disableProperty().bind(guardando);
        dialogo.setOnCloseRequest(event -> { if (guardando.get()) event.consume(); });
        dialogo.getDialogPane().lookupButton(buscar).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            String carnet = cif.getText();
            String password = clave.getText();
            guardando.set(true);
            mensaje.setText("Buscando estudiante...");
            ejecutar(() -> servicioUsuarios.importarEstudiante(carnet, password), usuario -> {
                guardando.set(false);
                clave.clear();
                seleccionarEstudiante(usuario);
                dialogo.close();
            }, error -> {
                guardando.set(false);
                mensaje.setText(error instanceof IllegalArgumentException || error instanceof IllegalStateException
                        ? error.getMessage() : "No se pudo consultar la API o guardar el estudiante. Inténtalo de nuevo.");
                LOGGER.log(System.Logger.Level.ERROR, "Error al importar estudiante.", error);
            });
        });
        dialogo.show();
    }

    @FXML
    private void handleCatalogo() {
        if (ocupado || !"COORDINADOR".equals(actor.getRol())) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/uam/edu/ni/mentorsync/catalogo-asignaturas.fxml"));
            loader.setControllerFactory(tipo -> new AsignaturasController(servicioAsignaturas));
            Parent contenido = loader.load();
            Dialog<Void> dialogo = new Dialog<>();
            dialogo.initOwner(tblTutorias.getScene().getWindow());
            dialogo.setTitle("Catálogo de materias");
            dialogo.setHeaderText("Administración de asignaturas");
            dialogo.getDialogPane().setContent(contenido);
            dialogo.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
            dialogo.setResizable(true);
            dialogo.setOnHidden(event -> cargar());
            dialogo.show();
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.ERROR, "No se pudo abrir el catálogo.", error);
            mostrar("No se pudo abrir el catálogo de materias.");
        }
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

            ChoiceDialog<String> dialogo = new ChoiceDialog<>("PRESENCIAL", "PRESENCIAL", "VIRTUAL");
            dialogo.setTitle("Agendar tutoría");
            dialogo.setHeaderText("Selecciona la modalidad");
            dialogo.initOwner(tblTutorias.getScene().getWindow());
            var modalidad = dialogo.showAndWait();
            if (modalidad.isEmpty()) {
                return;
            }

            var participantes = elegirParticipantes(tutorId, estudianteId);
            if (participantes.isEmpty()) {
                return;
            }
            ejecutar(() -> servicio.agendar(actor, tutorId, participantes.get(), asignatura.id(), fecha, inicio, fin,
                            modalidad.get()),
                    mensaje -> {
                        mostrar(mensaje);
                        limpiarFormularioAgendar();
                        cargar();
                    });
        } catch (DateTimeParseException e) {
            mostrar("Escribe las horas con formato HH:mm.");
        }
    }

    private java.util.Optional<List<Long>> elegirParticipantes(long tutor, long principal) {
        var adicionales = new ListView<Opcion>();
        adicionales.getItems().setAll(estudiantesDisponibles.stream()
                .filter(estudiante -> estudiante.id() != tutor && estudiante.id() != principal).toList());
        adicionales.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        adicionales.setPrefHeight(180);

        Dialog<List<Long>> dialogo = new Dialog<>();
        dialogo.setTitle("Participantes de la tutoría");
        dialogo.setHeaderText("Selecciona estudiantes adicionales si la tutoría es grupal");
        dialogo.initOwner(tblTutorias.getScene().getWindow());
        dialogo.getDialogPane().setContent(adicionales);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.setResultConverter(boton -> {
            if (boton != ButtonType.OK) {
                return null;
            }
            var participantes = new java.util.ArrayList<Long>();
            participantes.add(principal);
            adicionales.getSelectionModel().getSelectedItems().forEach(opcion -> participantes.add(opcion.id()));
            return List.copyOf(participantes);
        });
        return dialogo.showAndWait();
    }

    private void limpiarFormularioAgendar() {
        tutorSeleccionado = null;
        estudianteSeleccionado = null;
        txtBuscarTutor.clear();
        txtTutorSeleccionado.clear();
        txtBuscarEstudiante.clear();
        txtEstudianteSeleccionado.clear();
        cmbAsignatura.getSelectionModel().clearSelection();
        cmbAsignatura.getItems().clear();
        lblMateriasTutor.setText("Selecciona un tutor para ver sus materias habilitadas.");
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
    private void handleVerDetalle() {
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        if (seleccionada == null || ocupado) {
            return;
        }
        ejecutar(() -> servicio.buscarDetalle(actor, seleccionada.id()), detalle -> detalle.ifPresentOrElse(
                valor -> mostrarDetalle(seleccionada, valor),
                () -> mostrar("La sesión ya no existe o no tienes acceso a su detalle.")));
    }

    private void mostrarDetalle(Fila fila, Detalle detalle) {
        Dialog<Void> dialogo = new Dialog<>();
        dialogo.initOwner(tblTutorias.getScene().getWindow());
        dialogo.setTitle("Detalle de tutoría");
        dialogo.setHeaderText(fila.asignatura() + " · " + fila.fecha() + " · " + fila.horario());
        Label resumen = new Label("Tutor: " + fila.tutor() + "\nEstudiante: " + fila.estudiante()
                + "\nEstado: " + fila.estado());
        resumen.setWrapText(true);
        VBox contenido = new VBox(12, resumen,
                campoDetalle("Observación del tutor", detalle.observacionTutor()),
                new Label("Calificación del estudiante: " + (detalle.calificacionTutor() == null
                        ? "Sin calificación" : detalle.calificacionTutor() + " / 5")),
                campoDetalle("Comentario del estudiante", detalle.comentarioEstudiante()),
                campoDetalle("Motivo de cancelación", detalle.motivoCancelacion()));
        contenido.setPadding(new Insets(12));
        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.setPrefViewportWidth(580);
        desplazable.setPrefViewportHeight(440);
        dialogo.getDialogPane().setContent(desplazable);
        dialogo.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialogo.setResizable(true);
        dialogo.show();
    }

    private VBox campoDetalle(String titulo, String valor) {
        Label etiqueta = new Label(titulo);
        etiqueta.setStyle("-fx-font-weight: bold;");
        TextArea texto = new TextArea(valor == null || valor.isBlank() ? "Sin información registrada." : valor);
        texto.setEditable(false);
        texto.setWrapText(true);
        texto.setPrefRowCount(3);
        return new VBox(5, etiqueta, texto);
    }

    @FXML
    private void handleReprogramar() {
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        if (ocupado || seleccionada == null || !"COORDINADOR".equals(actor.getRol())
                || !"PROGRAMADA".equals(seleccionada.estado())) {
            return;
        }
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.initOwner(tblTutorias.getScene().getWindow());
        dialogo.setTitle("Reprogramar sesión");
        dialogo.setHeaderText(seleccionada.asignatura() + " · " + seleccionada.tutor()
                + "\nEstudiante: " + seleccionada.estudiante());
        DatePicker fecha = new DatePicker(seleccionada.fecha());
        fecha.setId("dpNuevaFecha");
        TextField inicio = new TextField(seleccionada.horaInicio().toString());
        inicio.setId("txtNuevaHoraInicio");
        TextField fin = new TextField(seleccionada.horaFin().toString());
        fin.setId("txtNuevaHoraFin");
        inicio.setPromptText("HH:mm");
        fin.setPromptText("HH:mm");
        GridPane formulario = new GridPane();
        formulario.setHgap(12);
        formulario.setVgap(10);
        formulario.addRow(0, new Label("Fecha"), fecha);
        formulario.addRow(1, new Label("Hora de inicio"), inicio);
        formulario.addRow(2, new Label("Hora de fin"), fin);
        Label mensaje = new Label();
        mensaje.setId("lblErrorReprogramacion");
        mensaje.setWrapText(true);
        VBox contenido = new VBox(12, formulario, mensaje);
        contenido.setPadding(new Insets(12));
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setPrefWidth(460);
        ButtonType guardar = new ButtonType("Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);
        SimpleBooleanProperty guardando = new SimpleBooleanProperty();
        formulario.disableProperty().bind(guardando);
        Node botonGuardar = dialogo.getDialogPane().lookupButton(guardar);
        botonGuardar.disableProperty().bind(guardando);
        dialogo.getDialogPane().lookupButton(ButtonType.CANCEL).disableProperty().bind(guardando);
        dialogo.setOnCloseRequest(event -> {
            if (guardando.get()) {
                event.consume();
            }
        });
        botonGuardar.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            try {
                fecha.commitValue();
                LocalDate nuevaFecha = fecha.getValue();
                LocalTime nuevaHoraInicio = leerHora(inicio);
                LocalTime nuevaHoraFin = leerHora(fin);
                guardando.set(true);
                mensaje.setText("Comprobando disponibilidad...");
                ejecutar(() -> servicio.reprogramar(actor, seleccionada.id(), nuevaFecha, nuevaHoraInicio, nuevaHoraFin),
                        resultado -> {
                            guardando.set(false);
                            if (resultado.exito()) {
                                dialogo.close();
                                cargar();
                                mostrar(resultado.mensaje());
                            } else {
                                mensaje.setText(resultado.mensaje());
                            }
                        }, error -> {
                            guardando.set(false);
                            mensaje.setText("No se pudo reprogramar la sesión. Inténtalo de nuevo.");
                            LOGGER.log(System.Logger.Level.ERROR, "Error al reprogramar tutoría.", error);
                        });
            } catch (DateTimeParseException error) {
                mensaje.setText("Selecciona una fecha válida y escribe las horas con formato HH:mm.");
            }
        });
        dialogo.show();
    }

    @FXML
    private void handleEliminar() {
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        if (ocupado || seleccionada == null || !"COORDINADOR".equals(actor.getRol())
                || !("PROGRAMADA".equals(seleccionada.estado()) || "CANCELADA".equals(seleccionada.estado()))) {
            return;
        }
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.initOwner(tblTutorias.getScene().getWindow());
        confirmacion.setTitle("Eliminar sesión");
        confirmacion.setHeaderText("¿Eliminar esta sesión de " + seleccionada.asignatura() + "?");
        confirmacion.setContentText(seleccionada.fecha() + " · " + seleccionada.horario()
                + "\nTutor: " + seleccionada.tutor() + "\nEstudiante: " + seleccionada.estudiante());
        ButtonType eliminar = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        confirmacion.getButtonTypes().setAll(eliminar, ButtonType.CANCEL);
        ((Button) confirmacion.getDialogPane().lookupButton(eliminar)).setDefaultButton(false);
        ((Button) confirmacion.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        confirmacion.showAndWait().filter(respuesta -> respuesta == eliminar).ifPresent(respuesta ->
                ejecutar(() -> servicio.eliminar(actor, seleccionada.id()), resultado -> {
                    if (resultado.exito()) {
                        cargar();
                    }
                    mostrar(resultado.mensaje());
                }));
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

    @FXML
    private void cargar() {
        tblTutorias.setPlaceholder(new Label("Cargando sesiones..."));
        lblFiltros.setText("Cargando sesiones...");
        ejecutar(() -> servicio.cargar(actor), this::mostrarDatos);
    }

    private void mostrarDatos(Datos datos) {
        estudiantesDisponibles = datos.estudiantes();
        Fila seleccionada = tblTutorias.getSelectionModel().getSelectedItem();
        Opcion filtroAnterior = cmbFiltroAsignatura.getValue();
        if (tutorSeleccionado == null) {
            cmbAsignatura.getItems().clear();
        } else {
            seleccionarTutor(tutorSeleccionado);
        }
        Map<Long, Opcion> materias = new HashMap<>();
        datos.asignaturas().forEach(opcion -> materias.put(opcion.id(), opcion));
        datos.sesiones().forEach(fila -> materias.put(fila.asignaturaId(), new Opcion(fila.asignaturaId(), fila.asignatura())));
        cmbFiltroAsignatura.getItems().setAll(TODAS_ASIGNATURAS);
        cmbFiltroAsignatura.getItems().addAll(materias.values().stream()
                .sorted(Comparator.comparing(Opcion::nombre)).toList());
        cmbFiltroAsignatura.getSelectionModel().select(cmbFiltroAsignatura.getItems().stream()
                .filter(opcion -> filtroAnterior != null && opcion.id() == filtroAnterior.id())
                .findFirst().orElse(TODAS_ASIGNATURAS));
        sesiones.setAll(datos.sesiones());
        tblTutorias.setPlaceholder(new Label("No hay sesiones para mostrar."));
        filtrar();
        if (seleccionada != null) {
            tblTutorias.getItems().stream().filter(fila -> fila.id() == seleccionada.id()).findFirst()
                    .ifPresent(fila -> tblTutorias.getSelectionModel().select(fila));
        }
        actualizarBotones(operacionesEnCurso > 0);
    }

    private void filtrar() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            sesionesFiltradas.setPredicate(fila -> false);
            lblFiltros.setText("La fecha inicial no puede ser posterior a la fecha final.");
            return;
        }
        String estado = cmbFiltroEstado.getValue();
        Opcion materia = cmbFiltroAsignatura.getValue();
        sesionesFiltradas.setPredicate(fila -> (estado == null || TODOS_ESTADOS.equals(estado) || estado.equals(fila.estado()))
                && (materia == null || materia.id() == 0 || materia.id() == fila.asignaturaId())
                && (desde == null || !fila.fecha().isBefore(desde))
                && (hasta == null || !fila.fecha().isAfter(hasta)));
        lblFiltros.setText("Sesiones: " + sesionesFiltradas.size() + " de " + sesiones.size());
    }

    @FXML
    private void handleLimpiarFiltros() {
        cmbFiltroEstado.getSelectionModel().selectFirst();
        cmbFiltroAsignatura.getSelectionModel().selectFirst();
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        filtrar();
    }

    private void buscarUsuariosAsync(Button boton, Callable<List<Usuario>> consulta, Consumer<List<Usuario>> alCompletar) {
        boton.setDisable(true);
        ejecutar(consulta, alCompletar);
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> completado) {
        ejecutar(operacion, completado, error -> {
            LOGGER.log(System.Logger.Level.ERROR, "Error en una operación de tutorías.", error);
            mostrar("No se pudo completar la operación de tutorías. Revisa la conexión e inténtalo nuevamente.");
        });
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> completado, Consumer<Throwable> fallo) {
        operacionesEnCurso++;
        actualizarBotones(true);
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operacion.call();
            }
        };
        tarea.setOnSucceeded(e -> {
            terminarOperacion();
            completado.accept(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            terminarOperacion();
            fallo.accept(tarea.getException());
        });
        tarea.setOnCancelled(e -> terminarOperacion());
        Thread hilo = new Thread(tarea, "mentorsync-tutorias");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void terminarOperacion() {
        operacionesEnCurso--;
        actualizarBotones(operacionesEnCurso > 0);
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
        btnValidarHoras.setDisable(ocupado || !coordinador || !realizada || !seleccionada.asistenciaConfirmada());
        btnConfirmarAsistencia.setDisable(ocupado || !realizada
                || seleccionada.estudianteId() != actor.getId() || seleccionada.asistenciaConfirmada());
        btnMarcarRealizada.setDisable(ocupado || !puedeMarcar);
        btnSolicitarCancelacion.setDisable(ocupado || !estudiante || !programada);
        btnCalificarTutor.setDisable(ocupado || !estudiante || !completada);
        btnReprogramar.setDisable(ocupado || !coordinador || !programada);
        btnEliminar.setDisable(ocupado || !coordinador || seleccionada == null
                || !(programada || "CANCELADA".equals(seleccionada.estado())));
        btnVerDetalle.setDisable(ocupado || seleccionada == null);
        btnActualizar.setDisable(ocupado);
        btnBuscarTutor.setDisable(ocupado || !coordinador);
        btnBuscarEstudiante.setDisable(ocupado || !coordinador);
        btnImportarEstudiante.setDisable(ocupado || !coordinador);
        btnCatalogo.setDisable(ocupado || !coordinador);
    }

    private void mostrar(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alerta.initOwner(tblTutorias.getScene().getWindow());
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }
}
