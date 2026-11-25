package uam.edu.ni.mentorsync.model;

public record TutorResumen(String cif, String nombre, String carrera, Double promedio, Double horasValidadas,
                           Double puntuacion, long cantidadResenas, boolean activo) {
}
