package uam.edu.ni.mentorsync.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import uam.edu.ni.mentorsync.model.ResenaTutor;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.service.UsuarioService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ResenasTutorController {
    private static final System.Logger LOGGER = System.getLogger(ResenasTutorController.class.getName());
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label lblResumen;
    @FXML private Label lblEstado;
    @FXML private ProgressIndicator progreso;
    @FXML private Button btnReintentar;
    @FXML private ListView<ResenaTutor> lstResenas;

    private TutorResumen tutor;
    private UsuarioService usuarioService;
    private Task<List<ResenaTutor>> cargaActual;

    @FXML
    private void initialize() {
        lstResenas.setPlaceholder(new Label("Este tutor todavía no tiene reseñas."));
        lstResenas.setCellFactory(lista -> new ListCell<>() {
            private final Label estudiante = new Label();
            private final Label fecha = new Label();
            private final Label puntuacion = new Label();
            private final Label comentario = new Label();
            private final HBox encabezado = new HBox(12, estudiante, fecha);
            private final VBox tarjeta = new VBox(8, encabezado, puntuacion, comentario);

            {
                estudiante.setWrapText(true);
                estudiante.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(estudiante, Priority.ALWAYS);
                estudiante.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
                fecha.setStyle("-fx-text-fill: #64748b;");
                puntuacion.setStyle("-fx-font-weight: bold; -fx-text-fill: #b45309;");
                comentario.setWrapText(true);
                comentario.setMinHeight(USE_PREF_SIZE);
                comentario.prefWidthProperty().bind(lista.widthProperty().subtract(64));
                tarjeta.setPadding(new Insets(14));
                tarjeta.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; "
                        + "-fx-background-radius: 8; -fx-border-radius: 8;");
                setStyle("-fx-background-color: transparent; -fx-padding: 0 0 12 0;");
            }

            @Override
            protected void updateItem(ResenaTutor resena, boolean vacia) {
                super.updateItem(resena, vacia);
                setText(null);
                if (vacia || resena == null) {
                    setGraphic(null);
                    return;
                }
                estudiante.setText(resena.estudiante());
                fecha.setText("Tutoría del " + FECHA.format(resena.fechaTutoria()));
                puntuacion.setText(resena.puntuacion() == null
                        ? "Sin puntuación" : resena.puntuacion() + " / 5");
                comentario.setText(resena.comentario() == null || resena.comentario().isBlank()
                        ? "Sin comentario." : resena.comentario().trim());
                setGraphic(tarjeta);
            }
        });
    }

    public void cargar(TutorResumen tutor, UsuarioService usuarioService) {
        this.tutor = tutor;
        this.usuarioService = usuarioService;
        cargarResenas();
    }

    @FXML
    private void cargarResenas() {
        cancelarCarga();
        String cif = tutor.cif();
        lstResenas.getItems().clear();
        lstResenas.setPlaceholder(new Label("Cargando reseñas..."));
        progreso.setVisible(true);
        progreso.setManaged(true);
        lblEstado.setText("Cargando reseñas...");
        lblEstado.setManaged(true);
        btnReintentar.setVisible(false);
        btnReintentar.setManaged(false);
        Task<List<ResenaTutor>> tarea = new Task<>() {
            @Override
            protected List<ResenaTutor> call() {
                return usuarioService.listarResenasTutor(cif);
            }
        };
        cargaActual = tarea;
        tarea.setOnSucceeded(event -> {
            if (cargaActual != tarea) {
                return;
            }
            List<ResenaTutor> resenas = tarea.getValue();
            lstResenas.getItems().setAll(resenas);
            lstResenas.setPlaceholder(new Label("Este tutor todavía no tiene reseñas."));
            var promedio = resenas.stream().filter(r -> r.puntuacion() != null)
                    .mapToInt(ResenaTutor::puntuacion).average();
            lblResumen.setText(resenas.size() + (resenas.size() == 1 ? " valoración" : " valoraciones")
                    + " · Puntuación promedio: " + (promedio.isPresent()
                    ? String.format(Locale.US, "%.1f / 5", promedio.getAsDouble()) : "Sin calificar"));
            progreso.setVisible(false);
            progreso.setManaged(false);
            lblEstado.setText("");
            lblEstado.setManaged(false);
        });
        tarea.setOnFailed(event -> {
            if (cargaActual != tarea) {
                return;
            }
            LOGGER.log(System.Logger.Level.ERROR, "No se pudieron cargar las reseñas del tutor.", tarea.getException());
            progreso.setVisible(false);
            progreso.setManaged(false);
            lblEstado.setText("No se pudieron cargar las reseñas. Puedes volver a intentarlo.");
            lstResenas.setPlaceholder(new Label("Reseñas no disponibles."));
            btnReintentar.setVisible(true);
            btnReintentar.setManaged(true);
        });
        Thread hilo = new Thread(tarea, "mentorsync-resenas");
        hilo.setDaemon(true);
        hilo.start();
    }

    public void cancelarCarga() {
        if (cargaActual != null) {
            cargaActual.cancel(true);
            cargaActual = null;
        }
    }
}
