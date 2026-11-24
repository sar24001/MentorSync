package uam.edu.ni.mentorsync.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import uam.edu.ni.mentorsync.model.MateriaCatalogo;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.AsignaturaService;
import uam.edu.ni.mentorsync.util.SesionActual;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class AsignaturasController {
    @FXML private TableView<MateriaCatalogo> tblMaterias;
    @FXML private TableColumn<MateriaCatalogo, String> colCodigo;
    @FXML private TableColumn<MateriaCatalogo, String> colNombre;
    @FXML private TableColumn<MateriaCatalogo, String> colEstado;
    @FXML private TextField txtCodigo;
    @FXML private TextField txtNombre;
    @FXML private CheckBox chkInactivas;
    @FXML private Button btnGuardar;
    @FXML private Button btnEstado;
    @FXML private Button btnNueva;
    @FXML private Button btnActualizar;
    @FXML private Label lblMensaje;

    private final AsignaturaService servicio;
    private Usuario actor;
    private boolean ocupado;

    public AsignaturasController() {
        this(new AsignaturaService());
    }

    public AsignaturasController(AsignaturaService servicio) {
        this.servicio = java.util.Objects.requireNonNull(servicio);
    }

    @FXML
    private void initialize() {
        actor = SesionActual.getUsuario();
        colCodigo.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue().codigo()));
        colNombre.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue().nombre()));
        colEstado.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue().activo() ? "Activa" : "Inactiva"));
        tblMaterias.getSelectionModel().selectedItemProperty().addListener((o, anterior, materia) -> {
            txtCodigo.setText(materia == null ? "" : materia.codigo());
            txtNombre.setText(materia == null ? "" : materia.nombre());
            actualizarControles();
        });
        chkInactivas.selectedProperty().addListener((o, a, n) -> cargar());
        cargar();
    }

    @FXML
    private void cargar() {
        boolean inactivas = chkInactivas.isSelected();
        ejecutar(() -> servicio.listar(actor, inactivas), materias -> {
            tblMaterias.getItems().setAll(materias);
            lblMensaje.setText("Materias: " + materias.size());
        });
    }

    @FXML
    private void handleNueva() {
        tblMaterias.getSelectionModel().clearSelection();
        txtCodigo.clear();
        txtNombre.clear();
        actualizarControles();
    }

    @FXML
    private void handleGuardar() {
        MateriaCatalogo seleccionada = tblMaterias.getSelectionModel().getSelectedItem();
        Long id = seleccionada == null ? null : seleccionada.id();
        String codigo = txtCodigo.getText();
        String nombre = txtNombre.getText();
        ejecutar(() -> servicio.guardar(actor, id, codigo, nombre), resultado -> {
            lblMensaje.setText(resultado.mensaje());
            if (resultado.exito()) {
                handleNueva();
                cargar();
            }
        });
    }

    @FXML
    private void handleEstado() {
        MateriaCatalogo materia = tblMaterias.getSelectionModel().getSelectedItem();
        if (materia == null || ocupado) return;
        ejecutar(() -> servicio.cambiarEstado(actor, materia.id(), !materia.activo()), resultado -> {
            lblMensaje.setText(resultado.mensaje());
            if (resultado.exito()) cargar();
        });
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> completado) {
        ocupado = true;
        actualizarControles();
        Task<T> tarea = new Task<>() {
            @Override protected T call() throws Exception { return operacion.call(); }
        };
        tarea.setOnSucceeded(event -> {
            ocupado = false;
            actualizarControles();
            completado.accept(tarea.getValue());
        });
        tarea.setOnFailed(event -> {
            ocupado = false;
            actualizarControles();
            lblMensaje.setText("No se pudo completar la operación. Revisa la conexión e inténtalo nuevamente.");
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "Error en el catálogo.", tarea.getException());
        });
        Thread hilo = new Thread(tarea, "mentorsync-catalogo");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void actualizarControles() {
        MateriaCatalogo materia = tblMaterias.getSelectionModel().getSelectedItem();
        txtCodigo.setDisable(ocupado || materia != null);
        txtNombre.setDisable(ocupado);
        chkInactivas.setDisable(ocupado);
        btnGuardar.setDisable(ocupado);
        btnNueva.setDisable(ocupado);
        btnActualizar.setDisable(ocupado);
        tblMaterias.setDisable(ocupado);
        btnEstado.setDisable(ocupado || materia == null);
        btnEstado.setText(materia != null && !materia.activo() ? "Reactivar materia" : "Desactivar materia");
    }
}
