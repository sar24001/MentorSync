package uam.edu.ni.mentorsync.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import uam.edu.ni.mentorsync.MentorSyncApplication;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.io.IOException;

public class PrincipalController {
    @FXML private Label lblUsuarioInfo;
    @FXML private TabPane tabPanePrincipal;
    @FXML private Tab tabTutores;
    @FXML private Tab tabReportes;

    @FXML
    private void initialize() {
        Usuario usuario = SesionActual.getUsuario();
        lblUsuarioInfo.setText(usuario.getNombre() + " · " + usuario.getRol());
        if (!"COORDINADOR".equals(usuario.getRol())) {
            tabPanePrincipal.getTabs().removeAll(tabTutores, tabReportes);
        }
    }

    @FXML
    private void handleCerrarSesion(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(MentorSyncApplication.class.getResource("login-view.fxml"));
        Scene escena = new Scene(loader.load());
        SesionActual.cerrar();
        Stage ventana = (Stage) ((Node) event.getSource()).getScene().getWindow();
        ventana.setScene(escena);
        ventana.sizeToScene();
    }
}
