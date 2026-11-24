package uam.edu.ni.mentorsync.controller;

import jakarta.persistence.PersistenceException;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import uam.edu.ni.mentorsync.MentorSyncApplication;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.service.UsuarioService;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.io.IOException;
import java.util.Optional;

public class LoginController {
    @FXML private TextField txtCarnet;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnIngresar;

    private final UsuarioService usuarioService = new UsuarioService();

    @FXML
    private void handleIngresar() {
        String cif = txtCarnet.getText().trim();
        String password = txtPassword.getText();
        if (!cif.matches("[0-9]{8}")) {
            lblError.setText("El CIF debe tener 8 dígitos.");
            return;
        }
        if (password.isEmpty()) {
            lblError.setText("Ingresa la contraseña.");
            return;
        }

        lblError.setText("");
        btnIngresar.setDisable(true);
        Task<Optional<Usuario>> tarea = new Task<>() {
            @Override
            protected Optional<Usuario> call() {
                return usuarioService.autenticar(cif, password);
            }
        };
        tarea.setOnSucceeded(event -> {
            btnIngresar.setDisable(false);
            txtPassword.clear();
            tarea.getValue().ifPresentOrElse(this::abrirPrincipal,
                    () -> lblError.setText("CIF o contraseña incorrectos."));
        });
        tarea.setOnFailed(event -> {
            btnIngresar.setDisable(false);
            Throwable error = tarea.getException();
            if (error instanceof IllegalStateException) {
                lblError.setText(error.getMessage());
            } else if (error instanceof PersistenceException) {
                lblError.setText("No se pudo consultar PostgreSQL. Revisa la conexión y la tabla de usuarios.");
            } else {
                lblError.setText("No se pudo iniciar sesión.");
            }
        });

        Thread hilo = new Thread(tarea, "mentorsync-login");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void abrirPrincipal(Usuario usuario) {
        SesionActual.iniciar(usuario);
        try {
            FXMLLoader loader = new FXMLLoader(MentorSyncApplication.class.getResource("principal-view.fxml"));
            Scene escena = new Scene(loader.load());
            Stage ventana = (Stage) btnIngresar.getScene().getWindow();
            ventana.setScene(escena);
            ventana.sizeToScene();
        } catch (IOException e) {
            SesionActual.cerrar();
            lblError.setText("No se pudo abrir la pantalla principal.");
        }
    }
}
