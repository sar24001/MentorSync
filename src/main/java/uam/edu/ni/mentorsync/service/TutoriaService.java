package uam.edu.ni.mentorsync.service;

import uam.edu.ni.mentorsync.dao.TutoriaDAO;
import uam.edu.ni.mentorsync.model.Usuario;

import java.time.LocalDate;
import java.time.LocalTime;

public class TutoriaService {
    private final TutoriaDAO dao = new TutoriaDAO();

    public TutoriaDAO.Datos cargar(Usuario actor) {
        return dao.cargar(actor.getId(), actor.getRol());
    }

    public String agendar(Usuario actor, long tutor, long estudiante, long materia,
                          LocalDate fecha, LocalTime inicio, LocalTime fin) {
        exigirRol(actor, "COORDINADOR");
        if (tutor == estudiante) {
            return "El tutor y el estudiante deben ser personas distintas.";
        }
        if (fecha.isBefore(LocalDate.now()) || !inicio.isBefore(fin)) {
            return "Revisa la fecha y asegúrate de que el fin sea posterior al inicio.";
        }
        return dao.agendar(tutor, estudiante, materia, fecha, inicio, fin)
                ? "Tutoría agendada." : "Hay un choque de horario o algún dato ya no está disponible.";
    }

    public String cancelar(Usuario actor, long id) {
        exigirRol(actor, "COORDINADOR");
        return dao.cancelar(id) ? "Tutoría cancelada." : "Solo se puede cancelar una tutoría programada.";
    }

    public String marcarRealizada(Usuario actor, long id) {
        exigirRol(actor, "TUTOR");
        return dao.marcarRealizada(id, actor.getId())
                ? "Tutoría marcada como realizada." : "La tutoría debe estar programada, asignada a ti y haber terminado.";
    }

    public String validarHoras(Usuario actor, long id) {
        exigirRol(actor, "COORDINADOR");
        return dao.validarHoras(id) ? "Horas validadas." : "Solo se pueden validar tutorías realizadas una vez.";
    }

    private void exigirRol(Usuario actor, String rol) {
        if (!rol.equals(actor.getRol())) {
            throw new IllegalStateException("El usuario no tiene permiso para esta acción.");
        }
    }
}
