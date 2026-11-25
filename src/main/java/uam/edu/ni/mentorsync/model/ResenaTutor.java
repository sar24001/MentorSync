package uam.edu.ni.mentorsync.model;

import java.time.LocalDate;

public record ResenaTutor(String estudiante, LocalDate fechaTutoria, Integer puntuacion, String comentario) {
}
