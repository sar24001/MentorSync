package uam.edu.ni.mentorsync.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import uam.edu.ni.mentorsync.dao.UsuarioDAO.RegistroTutor;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.EstudianteService;
import uam.edu.ni.mentorsync.service.UsuarioService;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class TutoresController {
    @FXML private TextField txtCarnetBusqueda;
    @FXML private TextField txtNombre;
    @FXML private TextField txtCorreo;
    @FXML private TextField txtCarrera;
    @FXML private TextField txtPromedio;
    @FXML private PasswordField txtPasswordTutor;
    @FXML private Label lblEstadoElegibilidad;
    @FXML private Button btnBuscarApi;
    @FXML private Button btnRegistrarTutor;
    @FXML private Button btnLimpiar;
    @FXML private TableView<TutorResumen> tblTutores;
    @FXML private TableColumn<TutorResumen, String> colCarnet;
    @FXML private TableColumn<TutorResumen, String> colNombre;
    @FXML private TableColumn<TutorResumen, String> colCarrera;
    @FXML private TableColumn<TutorResumen, Double> colPromedio;
    @FXML private TableColumn<TutorResumen, Double> colHoras;

    private final UsuarioService usuarioService = new UsuarioService();
    private final EstudianteService estudianteService = new EstudianteService();
    private EstudianteApiDTO candidato;

    @FXML
    private void initialize() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !"COORDINADOR".equals(usuario.getRol())) {
            return;
        }
        colCarnet.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().cif()));
        colNombre.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().nombre()));
        colCarrera.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().carrera()));
        colPromedio.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().promedio()));
        colHoras.setCellValueFactory(fila -> new ReadOnlyObjectWrapper<>(fila.getValue().horasValidadas()));
        txtCarnetBusqueda.textProperty().addListener((observable, anterior, nuevo) -> limpiarCandidato());
        actualizarBotones(false);
        cargarTutores();
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
                () -> lblEstadoElegibilidad.setText("No se pudo consultar la API académica."));
    }

    @FXML
    private void handleRegistrarTutor() {
        EstudianteApiDTO estudiante = candidato;
        String clave = txtPasswordTutor.getText();
        if (estudiante == null || !usuarioService.esElegible(estudiante)) {
            return;
        }
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
        }, () -> lblEstadoElegibilidad.setText("No se pudo registrar el tutor en PostgreSQL."));
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
        txtPromedio.setText(Double.toString(estudiante.promedio()));
        lblEstadoElegibilidad.setText(usuarioService.esElegible(estudiante)
                ? "Estudiante elegible."
                : "Se requiere un estudiante activo con promedio de 85 o más.");
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
        txtCarnetBusqueda.setDisable(ocupado);
        btnBuscarApi.setDisable(ocupado);
        btnLimpiar.setDisable(ocupado);
        btnRegistrarTutor.setDisable(ocupado || candidato == null || !usuarioService.esElegible(candidato));
    }

    private void cargarTutores() {
        tblTutores.setPlaceholder(new Label("Cargando tutores..."));
        Task<List<TutorResumen>> tarea = tarea(usuarioService::listarTutoresActivos);
        tarea.setOnSucceeded(event -> {
            tblTutores.getItems().setAll(tarea.getValue());
            tblTutores.setPlaceholder(new Label("No hay tutores activos."));
        });
        tarea.setOnFailed(event ->
                tblTutores.setPlaceholder(new Label("No se pudo cargar el listado de tutores.")));
        iniciar(tarea);
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> exito, Runnable fallo) {
        actualizarBotones(true);
        Task<T> tarea = tarea(operacion);
        tarea.setOnSucceeded(event -> {
            exito.accept(tarea.getValue());
            actualizarBotones(false);
        });
        tarea.setOnFailed(event -> {
            fallo.run();
            actualizarBotones(false);
        });
        iniciar(tarea);
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
