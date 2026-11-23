package uam.edu.ni.mentorsync.model;

public record EstudianteApiDTO(String cif, String nombre, String correo, String carrera,
                               double promedio, boolean activo) {
}
