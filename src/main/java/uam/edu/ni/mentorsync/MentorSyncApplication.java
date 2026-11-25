package uam.edu.ni.mentorsync;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.Scene;
import javafx.stage.Stage;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.io.IOException;

public class MentorSyncApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        if (!HibernateUtil.tienePassword()) {
            var password = solicitarPassword();
            if (password.isEmpty()) {
                Platform.exit();
                return;
            }
            HibernateUtil.configurarPassword(password.get());
        }

        FXMLLoader fxmlLoader = new FXMLLoader(MentorSyncApplication.class.getResource("login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("MentorSync");
        stage.setScene(scene);
        stage.show();
    }

    private java.util.Optional<String> solicitarPassword() {
        Dialog<String> dialogo = new Dialog<>();
        dialogo.setTitle("MentorSync");
        dialogo.setHeaderText("Conectar a PostgreSQL");

        PasswordField campo = new PasswordField();
        campo.setPromptText("Contraseña de PostgreSQL");
        var panel = dialogo.getDialogPane();
        panel.setContent(campo);
        panel.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        panel.lookupButton(ButtonType.OK).disableProperty().bind(campo.textProperty().isEmpty());
        dialogo.setOnShown(event -> campo.requestFocus());
        dialogo.setResultConverter(boton -> boton == ButtonType.OK ? campo.getText() : null);
        return dialogo.showAndWait();
    }

    @Override
    public void stop() {
        HibernateUtil.cerrar();
    }
}
