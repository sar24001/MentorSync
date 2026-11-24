module uam.edu.ni.mentorsync {
    requires javafx.controls;
    requires javafx.fxml;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires org.postgresql.jdbc;
    requires java.net.http;
    requires jdk.httpserver;
    requires com.fasterxml.jackson.databind;
    requires com.github.librepdf.openpdf;
    requires org.dhatim.fastexcel;
    requires java.desktop;

    opens uam.edu.ni.mentorsync to javafx.fxml;
    opens uam.edu.ni.mentorsync.controller to javafx.fxml;
    opens uam.edu.ni.mentorsync.model to org.hibernate.orm.core, com.fasterxml.jackson.databind;
    exports uam.edu.ni.mentorsync;
}
