package uam.edu.ni.mentorsync.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import uam.edu.ni.mentorsync.dao.UsuarioDAO.RegistroTutor;
import uam.edu.ni.mentorsync.model.ClaseCursadaDTO;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.EstudianteService;
import uam.edu.ni.mentorsync.service.UsuarioService;
import uam.edu.ni.mentorsync.service.AsignaturaService;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TutoresController {
    private static final System.Logger LOGGER = System.getLogger(TutoresController.class.getName());

    @FXML private TextField txtCarnetBusqueda;
    @FXML private TextField txtNombre;
    @FXML private TextField txtCorreo;
    @FXML private TextField txtCarrera;
    @FXML private TextField txtPromedio;
    @FXML private PasswordField txtPasswordTutor;
    @FXML private Label lblEstadoElegibilidad;
    @FXML private Label lblEstadoTutores;
    @FXML private Button btnBuscarApi;
    @FXML private Button btnRegistrarTutor;
    @FXML private Button btnLimpiar;
    @FXML private TableView<TutorResumen> tblTutores;
    @FXML private TableColumn<TutorResumen, String> colCarnet;
    @FXML private TableColumn<TutorResumen, String> colNombre;
    @FXML private TableColumn<TutorResumen, String> colCarrera;
    @FXML private TableColumn<TutorResumen, Double> colPromedio;
    @FXML private TableColumn<TutorResumen, Double> colHoras;
    @FXML private TableColumn<TutorResumen, Double> colPuntuacion;
    @FXML private TableColumn<TutorResumen, TutorResumen> colResenas;
    @FXML private TableColumn<TutorResumen, String> colEstado;
    @FXML private CheckBox chkMostrarInactivos;
    @FXML private Button btnEstadoTutor;
    @FXML private Button btnMateriasTutor;

    private final UsuarioService usuarioService = new UsuarioService();
    private final EstudianteService estudianteService = new EstudianteService();
    private final AsignaturaService asignaturaService = new AsignaturaService();
    private EstudianteApiDTO candidato;
    private long versionCargaTutores;
    private boolean ocupado;

    @FXML
    private void initialize() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !"COORDINADOR".equals(usuario.getRol())) {
            return;
        }
        lblEstadoElegibilidad.setWrapText(true);
        colCarnet.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().cif()));
        colNombre.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().nombre()));
        colCarrera.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().carrera()));
        colPromedio.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().promedio()));
        colHoras.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().horasValidadas()));
        colEstado.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().activo() ? "Activo" : "Inactivo"));
        colPuntuacion.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().puntuacion()));
        colPuntuacion.setCellFactory(columna -> new TableCell<>() {
            {
                setStyle("-fx-alignment: CENTER;");
            }

            @Override
            protected void updateItem(Double puntuacion, boolean vacia) {
                super.updateItem(puntuacion, vacia);
                setText(vacia ? null : puntuacion == null
                        ? "Sin calificar" : String.format(Locale.US, "%.1f / 5", puntuacion));
            }
        });
        colResenas.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue()));
        colResenas.setCellFactory(columna -> new TableCell<>() {
            private final Button verResenas = new Button();

            {
                setStyle("-fx-alignment: CENTER;");
                verResenas.setOnAction(event -> {
                    TutorResumen tutor = getItem();
                    if (tutor != null) {
                        mostrarResenas(tutor);
                    }
                });
            }

            @Override
            protected void updateItem(TutorResumen tutor, boolean vacia) {
                super.updateItem(tutor, vacia);
                boolean sinResenas = tutor == null || tutor.cantidadResenas() == 0;
                setText(vacia ? null : sinResenas ? "Sin reseñas" : null);
                if (!vacia && !sinResenas) {
                    verResenas.setText("Ver reseñas (" + tutor.cantidadResenas() + ")");
                    verResenas.setAccessibleText("Ver reseñas de " + tutor.nombre());
                }
                setGraphic(vacia || sinResenas ? null : verResenas);
            }
        });
        txtCarnetBusqueda.textProperty().addListener((observable, anterior, nuevo) -> limpiarCandidato());
        tblTutores.getSelectionModel().selectedItemProperty().addListener((o, anterior, actual) -> actualizarBotones(ocupado));
        chkMostrarInactivos.selectedProperty().addListener((o, anterior, actual) -> cargarTutores());
        actualizarBotones(false);
        cargarTutores();
    }

    @FXML
    private void handleEstadoTutor() {
        TutorResumen tutor = tblTutores.getSelectionModel().getSelectedItem();
        if (tutor == null || ocupado) return;
        String accion = tutor.activo() ? "Desactivar" : "Reactivar";
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.initOwner(tblTutores.getScene().getWindow());
        confirmacion.setTitle(accion + " tutor");
        confirmacion.setHeaderText(accion + " a " + tutor.nombre());
        confirmacion.setContentText(tutor.activo()
                ? "Dejará de estar disponible para agendar nuevas sesiones. Sus sesiones y horas se conservarán."
                : "Volverá a estar disponible para agendar sesiones con sus materias habilitadas.");
        confirmacion.showAndWait().filter(respuesta -> respuesta == ButtonType.OK).ifPresent(respuesta ->
                ejecutar(() -> usuarioService.cambiarEstadoTutor(tutor.cif(), !tutor.activo()), exito -> {
                    lblEstadoTutores.setText(exito ? "Estado del tutor actualizado." : "El tutor ya no está disponible.");
                    if (exito) cargarTutores();
                }, error -> mostrarAlertaError("No se pudo actualizar el estado del tutor.", error)));
    }

    @FXML
    private void handleMateriasTutor() {
        TutorResumen tutor = tblTutores.getSelectionModel().getSelectedItem();
        if (tutor == null || ocupado) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/uam/edu/ni/mentorsync/materias-tutor.fxml"));
            Parent contenido = loader.load();
            MateriasTutorController controller = loader.getController();
            Dialog<Void> dialogo = new Dialog<>();
            dialogo.initOwner(tblTutores.getScene().getWindow());
            dialogo.setTitle("Materias del tutor");
            dialogo.setHeaderText(tutor.nombre());
            dialogo.getDialogPane().setContent(contenido);
            dialogo.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
            dialogo.setResizable(true);
            dialogo.show();
            controller.cargar(tutor, asignaturaService);
        } catch (IOException error) {
            mostrarAlertaError("No se pudo abrir la gestión de materias.", error);
        }
    }

    private void mostrarResenas(TutorResumen tutor) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/uam/edu/ni/mentorsync/resenas-tutor.fxml"));
            Parent contenido = loader.load();
            ResenasTutorController controller = loader.getController();
            Dialog<Void> dialogo = new Dialog<>();
            if (tblTutores.getScene() != null) {
                dialogo.initOwner(tblTutores.getScene().getWindow());
            }
            dialogo.setTitle("Reseñas del tutor");
            dialogo.setHeaderText(tutor.nombre());
            dialogo.setResizable(true);
            dialogo.getDialogPane().setContent(contenido);
            dialogo.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
            dialogo.setOnHidden(event -> controller.cancelarCarga());
            dialogo.show();
            controller.cargar(tutor, usuarioService);
        } catch (IOException error) {
            mostrarAlertaError("No se pudo abrir el detalle de reseñas.", error);
        }
    }

    @FXML
    private void handleBuscarApi() {
        String cif = txtCarnetBusqueda.getText().trim();
        limpiarCandidato();
        if (!cif.matches("[0-9]{8}")) {
            lblEstadoElegibilidad.setText("El CIF debe tener 8 dígitos.");
            return;
        }
        lblEstadoElegibilidad.setText("Consultando el registro académico...");
        ejecutar(() -> estudianteService.buscar(cif), resultado -> resultado.ifPresentOrElse(
                        this::mostrarCandidato,
                        () -> lblEstadoElegibilidad.setText("No se encontró un estudiante con ese CIF.")),
                error -> mostrarError("No se pudo consultar la API académica.", error));
    }

    @FXML
    private void handleRegistrarTutor() {
        EstudianteApiDTO estudiante = candidato;
        String clave = txtPasswordTutor.getText();
        if (estudiante == null) {
            lblEstadoElegibilidad.setText("Consulta primero el CIF del estudiante.");
            return;
        }
        if (!usuarioService.esElegible(estudiante)) {
            lblEstadoElegibilidad.setText(construirMensajeElegibilidad(estudiante));
            return;
        }
        lblEstadoElegibilidad.setText("Registrando tutor...");
        ejecutar(() -> usuarioService.registrarTutor(estudiante, clave), resultado -> {
            switch (resultado) {
                case DUPLICADO -> lblEstadoElegibilidad.setText("El CIF o correo ya está registrado.");
                case REQUIERE_CLAVE -> lblEstadoElegibilidad.setText("Para crear una cuenta, escribe una contraseña.");
                case HABILITADO, CREADO -> {
                    handleLimpiar();
                    lblEstadoElegibilidad.setText(resultado == RegistroTutor.HABILITADO
                            ? "Estudiante habilitado como tutor. Conserva su contraseña."
                            : "Tutor registrado.");
                    cargarTutores();
                }
            }
        }, error -> mostrarError("No se pudo registrar el tutor en PostgreSQL.", error));
    }

    @FXML
    private void handleLimpiar() {
        txtCarnetBusqueda.clear();
        limpiarCandidato();
    }

    private void mostrarCandidato(EstudianteApiDTO estudiante) {
        candidato = estudiante;
        txtNombre.setText(estudiante.nombre());
        txtCorreo.setText(estudiante.correo());
        txtCarrera.setText(estudiante.carrera());
        txtPromedio.setText(String.format(Locale.US, "%.2f", estudiante.calcularPromedioGeneral()));
        lblEstadoElegibilidad.setText(construirMensajeElegibilidad(estudiante));
    }

    private String construirMensajeElegibilidad(EstudianteApiDTO estudiante) {
        if (!estudiante.activo()) {
            return "El estudiante no se encuentra activo en el registro académico.";
        }
        if (!estudiante.cumpleNivelAcademico()) {
            return "Debe cursar al menos el 2.º semestre (actual: " + estudiante.semestre() + ".º semestre).";
        }
        List<ClaseCursadaDTO> aptas = estudiante.clasesElegibles();
        if (aptas.isEmpty()) {
            return "No tiene clases aprobadas con nota de 85 o más.";
        }
        String detalleClases = aptas.stream()
                .map(c -> c.nombre() + " (" + c.nota() + ")")
                .collect(Collectors.joining(", "));
        return "Elegible (" + estudiante.anioCarrera() + ".º año · " + estudiante.semestre() + ".º sem.). Clases aptas: " + detalleClases;
    }

    private void limpiarCandidato() {
        candidato = null;
        txtNombre.clear();
        txtCorreo.clear();
        txtCarrera.clear();
        txtPromedio.clear();
        txtPasswordTutor.clear();
        btnRegistrarTutor.setDisable(true);
        lblEstadoElegibilidad.setText("Pendiente de consulta...");
    }

    private void actualizarBotones(boolean ocupado) {
        this.ocupado = ocupado;
        txtCarnetBusqueda.setDisable(ocupado);
        txtPasswordTutor.setDisable(ocupado);
        btnBuscarApi.setDisable(ocupado);
        btnLimpiar.setDisable(ocupado);
        btnRegistrarTutor.setDisable(ocupado || candidato == null || !usuarioService.esElegible(candidato));
        TutorResumen tutor = tblTutores.getSelectionModel().getSelectedItem();
        btnEstadoTutor.setDisable(ocupado || tutor == null);
        btnMateriasTutor.setDisable(ocupado || tutor == null);
        btnEstadoTutor.setText(tutor != null && !tutor.activo() ? "Reactivar Tutor" : "Desactivar Tutor");
    }

    @FXML
    private void cargarTutores() {
        long version = ++versionCargaTutores;
        boolean incluirInactivos = chkMostrarInactivos.isSelected();
        lblEstadoTutores.setText("Cargando tutores...");
        tblTutores.setPlaceholder(new Label("Cargando tutores..."));
        Task<List<TutorResumen>> tarea = tarea(() -> usuarioService.listarTutores(incluirInactivos));
        tarea.setOnSucceeded(event -> {
            if (version != versionCargaTutores) {
                return;
            }
            tblTutores.getItems().setAll(tarea.getValue());
            tblTutores.setPlaceholder(new Label("No hay tutores activos."));
            lblEstadoTutores.setText("Tutores mostrados: " + tarea.getValue().size());
        });
        tarea.setOnFailed(event -> {
            if (version != versionCargaTutores) {
                return;
            }
            String mensaje = "No se pudo cargar el listado de tutores.";
            lblEstadoTutores.setText(mensaje + " Puedes reintentar con Actualizar.");
            tblTutores.setPlaceholder(new Label(mensaje));
            mostrarAlertaError(mensaje, tarea.getException());
        });
        iniciar(tarea);
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> exito, Consumer<Throwable> fallo) {
        actualizarBotones(true);
        Task<T> tarea = tarea(operacion);
        tarea.setOnSucceeded(event -> {
            try {
                exito.accept(tarea.getValue());
            } finally {
                actualizarBotones(false);
            }
        });
        tarea.setOnFailed(event -> {
            try {
                fallo.accept(tarea.getException());
            } finally {
                actualizarBotones(false);
            }
        });
        tarea.setOnCancelled(event -> actualizarBotones(false));
        iniciar(tarea);
    }

    private void mostrarError(String mensaje, Throwable error) {
        lblEstadoElegibilidad.setText(mensaje);
        mostrarAlertaError(mensaje, error);
    }

    private void mostrarAlertaError(String mensaje, Throwable error) {
        LOGGER.log(System.Logger.Level.ERROR, mensaje, error);
        Throwable causa = error;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String detalle = causa.getMessage();
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        if (lblEstadoElegibilidad.getScene() != null) {
            alerta.initOwner(lblEstadoElegibilidad.getScene().getWindow());
        }
        alerta.setTitle("Gestión de tutores");
        alerta.setHeaderText(mensaje);
        alerta.setContentText(detalle == null || detalle.isBlank() ? causa.toString() : detalle);
        alerta.show();
    }

    private static <T> Task<T> tarea(Callable<T> operacion) {
        return new Task<>() {
            @Override
            protected T call() throws Exception {
                return operacion.call();
            }
        };
    }

    private static void iniciar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "mentorsync-tutores");
        hilo.setDaemon(true);
        hilo.start();
    }
}
