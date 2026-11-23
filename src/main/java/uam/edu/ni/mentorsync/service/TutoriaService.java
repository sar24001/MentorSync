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
        return dao.cancelar(id)
                ? "Tutoría cancelada." : "Solo se puede cancelar una tutoría programada o con solicitud pendiente.";
    }

    public String solicitarCancelacion(Usuario actor, long id, String motivo) {
        exigirRol(actor, "ESTUDIANTE");
        if (motivo == null || motivo.trim().length() < 5) {
            return "Escribe un motivo válido (mínimo 5 caracteres).";
        }
        return dao.solicitarCancelacion(id, actor.getId(), motivo)
                ? "Solicitud de cancelación enviada al coordinador."
                : "Solo puedes solicitar la cancelación de una tutoría programada asignada a ti.";
    }

    public String calificarTutor(Usuario actor, long id, int calificacion, String comentario) {
        exigirRol(actor, "ESTUDIANTE");
        if (calificacion < 1 || calificacion > 5) {
            return "La calificación debe estar entre 1 y 5 estrellas.";
        }
        if (comentario == null || comentario.trim().isEmpty()) {
            return "Escribe un comentario o reseña sobre la tutoría.";
        }
        return dao.calificarTutor(id, actor.getId(), calificacion, comentario)
                ? "Reseña registrada. Gracias por calificar al tutor."
                : "Esta tutoría ya fue calificada o aún no ha sido impartida.";
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
        if (actor == null || !rol.equals(actor.getRol())) {
            throw new IllegalStateException("El usuario no tiene permiso para esta acción.");
        }
    }
}