package uam.edu.ni.mentorsync.service;

import uam.edu.ni.mentorsync.dao.TutoriaDAO;
import uam.edu.ni.mentorsync.model.Usuario;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.List;
import java.util.Set;

public class TutoriaService {
    private final TutoriaDAO dao = new TutoriaDAO();

    public record Resultado(boolean exito, String mensaje) {
    }

    public TutoriaDAO.Datos cargar(Usuario actor) {
        exigirUsuario(actor);
        return dao.cargar(actor.getId(), actor.getRol());
    }

    public Optional<TutoriaDAO.Detalle> buscarDetalle(Usuario actor, long id) {
        exigirUsuario(actor);
        return dao.buscarDetalle(id, actor.getId(), actor.getRol());
    }

    public java.util.List<TutoriaDAO.Opcion> materiasTutor(Usuario actor, long tutor) {
        exigirRol(actor, "COORDINADOR");
        return dao.materiasTutor(tutor);
    }

    public Resultado reprogramar(Usuario actor, long id, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        exigirRol(actor, "COORDINADOR");
        if (!horarioValido(fecha, inicio, fin)) {
            return new Resultado(false, "Selecciona una fecha vigente y una hora de fin posterior al inicio.");
        }
        return dao.reprogramar(id, fecha, inicio, fin)
                ? new Resultado(true, "Tutoría reprogramada.")
                : new Resultado(false, "No se pudo reprogramar: hay un choque de horario del tutor o estudiante, "
                        + "o la sesión ya no está PROGRAMADA.");
    }

    public Resultado eliminar(Usuario actor, long id) {
        exigirRol(actor, "COORDINADOR");
        return dao.eliminar(id)
                ? new Resultado(true, "Sesión eliminada.")
                : new Resultado(false, "Solo se pueden eliminar sesiones PROGRAMADAS o CANCELADAS. "
                        + "La sesión puede haber cambiado de estado o ya no existir.");
    }

    public String agendar(Usuario actor, long tutor, List<Long> estudiantes, long materia,
                          LocalDate fecha, LocalTime inicio, LocalTime fin, String modalidad) {
        exigirRol(actor, "COORDINADOR");
        if (!"PRESENCIAL".equals(modalidad) && !"VIRTUAL".equals(modalidad)) {
            return "Selecciona una modalidad válida.";
        }
        if (estudiantes.isEmpty() || estudiantes.stream().distinct().count() != estudiantes.size()) {
            return "Selecciona estudiantes distintos para la tutoría.";
        }
        if (estudiantes.contains(tutor)) {
            return "El tutor y el estudiante deben ser personas distintas.";
        }
        if (!horarioValido(fecha, inicio, fin)) {
            return "Revisa la fecha y asegúrate de que el fin sea posterior al inicio.";
        }
        return dao.agendar(tutor, estudiantes, materia, fecha, inicio, fin, modalidad)
                ? "Tutoría agendada." : "Hay un choque de horario o algún dato ya no está disponible.";
    }

    private boolean horarioValido(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        return fecha != null && inicio != null && fin != null
                && !fecha.isBefore(LocalDate.now()) && inicio.isBefore(fin);
    }

    private void exigirUsuario(Usuario actor) {
        if (actor == null || actor.getRol() == null
                || !Set.of("COORDINADOR", "TUTOR", "ESTUDIANTE").contains(actor.getRol())) {
            throw new IllegalStateException("El usuario no tiene permiso para consultar tutorías.");
        }
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

    public String confirmarAsistencia(Usuario actor, long id) {
        exigirUsuario(actor);
        if (!Set.of("ESTUDIANTE", "TUTOR").contains(actor.getRol())) {
            throw new IllegalStateException("Solo el estudiante de la tutoría puede confirmar su asistencia.");
        }
        return dao.confirmarAsistencia(id, actor.getId()) ? "Asistencia confirmada."
                : "La tutoría debe estar realizada, asignada a ti y pendiente de confirmación.";
    }

    public String validarHoras(Usuario actor, long id) {
        exigirRol(actor, "COORDINADOR");
        return dao.validarHoras(id) ? "Horas validadas." : "Para validar las horas, todos los participantes de la sesión deben haber confirmado su asistencia.";
    }

    private void exigirRol(Usuario actor, String rol) {
        if (actor == null || !rol.equals(actor.getRol())) {
            throw new IllegalStateException("El usuario no tiene permiso para esta acción.");
        }
    }
}
