package uam.edu.ni.mentorsync.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EstudianteApiDTO(
        String cif,
        String nombre,
        String correo,
        String carrera,
        int anioCarrera,
        int semestre,
        boolean activo,
        List<ClaseCursadaDTO> clasesCursadas
) {
    // Cambia a (anioCarrera >= 2) si exiges segundo año completo (3er semestre en adelante)
    public boolean cumpleNivelAcademico() {
        return semestre >= 2;
    }

    public double calcularPromedioGeneral() {
        if (clasesCursadas == null || clasesCursadas.isEmpty()) {
            return 0.0;
        }
        return clasesCursadas.stream()
                .mapToDouble(ClaseCursadaDTO::nota)
                .average()
                .orElse(0.0);
    }

    public List<ClaseCursadaDTO> clasesElegibles() {
        if (clasesCursadas == null) {
            return List.of();
        }
        return clasesCursadas.stream()
                .filter(ClaseCursadaDTO::esAptaParaTutoria)
                .toList();
    }

    public boolean puedeSerTutor() {
        return activo && cumpleNivelAcademico() && !clasesElegibles().isEmpty();
    }
}