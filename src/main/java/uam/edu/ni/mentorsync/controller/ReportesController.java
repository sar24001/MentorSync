package uam.edu.ni.mentorsync.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;

public class ReportesController {
    @FXML
    private void handleGuardarNotas(ActionEvent event) {
        ((Node) event.getSource()).setDisable(true);
    }

    @FXML
    private void handleGenerarReporteHoras(ActionEvent event) {
        ((Node) event.getSource()).setDisable(true);
    }

    @FXML
    private void handleGenerarReporteRendimiento(ActionEvent event) {
        ((Node) event.getSource()).setDisable(true);
    }
}
