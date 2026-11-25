package uam.edu.ni.mentorsync.controller;

import jakarta.persistence.PersistenceException;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.VBox;
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
import uam.edu.ni.mentorsync.service.RecuperacionService;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class LoginController {
    @FXML private TextField txtCarnet;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnIngresar;

    private final RecuperacionService recuperacionService = new RecuperacionService();
    private final Hyperlink btnRecuperar = new Hyperlink("Olvidé mi contraseña");

    @FXML
    private void initialize() {
        ((VBox) btnIngresar.getParent()).getChildren().add(btnRecuperar);
        btnRecuperar.setOnAction(event -> abrirRecuperacion());
        btnRecuperar.disableProperty().bind(btnIngresar.disableProperty());
    }

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

    private void abrirRecuperacion() {
        Dialog<ButtonType> solicitud = new Dialog<>();
        solicitud.initOwner(btnIngresar.getScene().getWindow());
        solicitud.setTitle("Recuperar contraseña");
        solicitud.setHeaderText("Ingresa tu CIF");
        TextField cifIngresado = new TextField(txtCarnet.getText().trim());
        cifIngresado.setPromptText("CIF de 8 dígitos");
        solicitud.getDialogPane().setContent(cifIngresado);
        ButtonType enviar = new ButtonType("Solicitar código", ButtonBar.ButtonData.OK_DONE);
        ButtonType usar = new ButtonType("Ya tengo un código", ButtonBar.ButtonData.OTHER);
        solicitud.getDialogPane().getButtonTypes().addAll(enviar, usar, ButtonType.CANCEL);
        var cifInvalido = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> !cifIngresado.getText().trim().matches("[0-9]{8}"), cifIngresado.textProperty());
        solicitud.getDialogPane().lookupButton(enviar).disableProperty().bind(cifInvalido);
        solicitud.getDialogPane().lookupButton(usar).disableProperty().bind(cifInvalido);
        solicitud.showAndWait().ifPresent(boton -> {
            String cif = cifIngresado.getText().trim();
            if (boton == usar) {
                pedirCodigo(cif);
            } else if (boton == enviar) {
                ejecutarRecuperacion(() -> {
                    recuperacionService.enviar(cif);
                    return true;
                }, enviado -> pedirCodigo(cif));
            }
        });
    }

    private void pedirCodigo(String cif) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.initOwner(btnIngresar.getScene().getWindow());
        dialogo.setTitle("Restablecer contraseña");
        dialogo.setHeaderText("Si la cuenta está habilitada, recibirás un código en su correo registrado.");
        TextField codigo = new TextField();
        codigo.setPromptText("Código del correo");
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Nueva contraseña (mínimo 8 caracteres)");
        PasswordField repetir = new PasswordField();
        repetir.setPromptText("Repite la nueva contraseña");
        Label error = new Label();
        dialogo.getDialogPane().setContent(new VBox(10, codigo, nueva, repetir, error));
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, evento -> {
            String valor = codigo.getText().trim();
            String clave = nueva.getText();
            if (!valor.matches("[A-Za-z0-9_-]{22}") || clave.length() < 8 || clave.length() > 128
                    || !clave.equals(repetir.getText())) {
                error.setText("Revisa el código y que ambas contraseñas coincidan.");
                evento.consume();
            }
        });
        dialogo.showAndWait().filter(ButtonType.OK::equals).ifPresent(boton -> {
            String valor = codigo.getText().trim();
            String clave = nueva.getText();
            ejecutarRecuperacion(() -> recuperacionService.restablecer(cif, valor, clave), resultado -> {
                if (resultado) {
                    txtCarnet.setText(cif);
                    txtPassword.clear();
                    lblError.setText("Contraseña actualizada. Ya puedes iniciar sesión.");
                } else {
                    lblError.setText("El código es incorrecto, ya fue usado o ha vencido.");
                }
            });
        });
    }

    private <T> void ejecutarRecuperacion(Callable<T> operacion, Consumer<T> alCompletar) {
        btnIngresar.setDisable(true);
        lblError.setText("");
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operacion.call();
            }
        };
        tarea.setOnSucceeded(event -> {
                btnIngresar.setDisable(false);
            alCompletar.accept(tarea.getValue());
        });
        tarea.setOnFailed(event -> {
                btnIngresar.setDisable(false);
            Throwable error = tarea.getException();
            lblError.setText(error instanceof IllegalArgumentException
                    || error instanceof IllegalStateException
                    ? error.getMessage() : "No se pudo procesar la recuperación. Intenta de nuevo.");
        });
        Thread hilo = new Thread(tarea, "mentorsync-recuperacion");
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
