module uam.edu.ni.mentorsync {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.github.librepdf.openpdf;
    requires org.dhatim.fastexcel;
    requires java.desktop;

    opens uam.edu.ni.mentorsync to javafx.fxml;
    exports uam.edu.ni.mentorsync;
}