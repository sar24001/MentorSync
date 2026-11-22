module uam.edu.ni.mentorsync {
    requires javafx.controls;
    requires javafx.fxml;


    opens uam.edu.ni.mentorsync to javafx.fxml;
    exports uam.edu.ni.mentorsync;
}