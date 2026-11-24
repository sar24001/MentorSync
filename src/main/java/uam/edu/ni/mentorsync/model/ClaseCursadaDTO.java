package uam.edu.ni.mentorsync.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaseCursadaDTO(
        String codigo,
        String nombre,
        double nota
) {
    public boolean esAptaParaTutoria() {
        return nota >= 85.0;
    }
}