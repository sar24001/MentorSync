package uam.edu.ni.mentorsync.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import uam.edu.ni.mentorsync.model.MateriaTutor;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.service.AsignaturaService;
import uam.edu.ni.mentorsync.util.SesionActual;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MateriasTutorController {
    @FXML private VBox listaMaterias;
    @FXML private Label lblMensaje;
    @FXML private Button btnGuardar;
    @FXML private Button btnActualizar;
    private AsignaturaService servicio;
    private final Map<MateriaTutor, CheckBox> seleccion = new LinkedHashMap<>();
    private TutorResumen tutor;
    private boolean cargado;

    public void cargar(TutorResumen tutor, AsignaturaService servicio) {
        this.tutor = tutor;
        this.servicio = java.util.Objects.requireNonNull(servicio);
        handleActualizar();
    }

    @FXML
    private void handleActualizar() {
        cargado = false;
        lblMensaje.setText("Consultando las materias aprobadas en la API académica...");
        ejecutar(() -> servicio.materiasTutor(SesionActual.getUsuario(), tutor.cif()), datos -> {
            seleccion.clear();
            listaMaterias.getChildren().clear();
            for (MateriaTutor materia : datos.materias()) {
                CheckBox casilla = new CheckBox(materia.codigo() + " · " + materia.nombre() + " · Nota: " + materia.nota());
                casilla.setSelected(materia.habilitada());
                casilla.setWrapText(true);
                seleccion.put(materia, casilla);
                listaMaterias.getChildren().add(casilla);
            }
            cargado = true;
            btnGuardar.setDisable(false);
            lblMensaje.setText(datos.materias().size() + " materias aprobadas disponibles. "
                    + (datos.sinCatalogo() > 0 ? datos.sinCatalogo() + " materias de la API no existen o están inactivas en el catálogo. " : "")
                    + "Marca las materias que este tutor puede impartir.");
        });
    }

    @FXML
    private void handleGuardar() {
        Set<Long> ids = seleccion.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(e -> e.getKey().asignaturaId()).collect(Collectors.toSet());
        lblMensaje.setText("Verificando y guardando materias...");
        ejecutar(() -> servicio.guardarMaterias(SesionActual.getUsuario(), tutor.cif(), ids), resultado -> lblMensaje.setText(resultado.mensaje()));
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> completado) {
        btnGuardar.setDisable(true);
        btnActualizar.setDisable(true);
        listaMaterias.setDisable(true);
        Task<T> tarea = new Task<>() {
            @Override protected T call() throws Exception { return operacion.call(); }
        };
        tarea.setOnSucceeded(event -> {
            btnActualizar.setDisable(false);
            listaMaterias.setDisable(false);
            btnGuardar.setDisable(!cargado);
            completado.accept(tarea.getValue());
        });
        tarea.setOnFailed(event -> {
            btnActualizar.setDisable(false);
            listaMaterias.setDisable(false);
            btnGuardar.setDisable(!cargado);
            Throwable error = tarea.getException();
            lblMensaje.setText(error.getMessage() == null ? "No se pudo consultar o guardar las materias." : error.getMessage());
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "Error en materias del tutor.", error);
        });
        Thread hilo = new Thread(tarea, "mentorsync-materias-tutor");
        hilo.setDaemon(true);
        hilo.start();
    }
}
