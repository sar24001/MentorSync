package uam.edu.ni.mentorsync.dao;

import org.hibernate.Session;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class TutoriaDAO {
    private static final String USUARIOS = "mentorsync_seguridad.usuarios";
    private static final String TUTORIAS = "mentorsync_academico.tutorias";

    public record Opcion(long id, String nombre) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    public record Fila(long id, LocalDate fecha, String horario, String asignatura,
                       String tutor, String estudiante, String estado, long tutorId, LocalTime horaFin,
                       LocalTime horaInicio, long estudianteId, long asignaturaId) {
    }

    public record Detalle(String observacionTutor, Integer calificacionTutor,
                          String comentarioEstudiante, String motivoCancelacion) {
    }

    public record Datos(List<Opcion> tutores, List<Opcion> estudiantes, List<Opcion> asignaturas,
                        List<Fila> sesiones) {
    }

    public Datos cargar(long usuario, String rol) {
        try (var em = HibernateUtil.getEntityManager()) {
            Session sesion = em.unwrap(Session.class);
            boolean coordinador = "COORDINADOR".equals(rol);
            List<Fila> sesiones = sesion.createNativeQuery("""
                    select t.id, cast(t.fecha as text),
                           to_char(t.hora_inicio, 'HH24:MI') || ' - ' || to_char(t.hora_fin, 'HH24:MI'),
                           a.nombre, tutor.nombre, estudiante.nombre, t.estado, t.tutor_id, cast(t.hora_fin as text),
                           cast(t.hora_inicio as text), t.estudiante_id, t.asignatura_id
                    from mentorsync_academico.tutorias t
                    join mentorsync_academico.asignaturas a on a.id = t.asignatura_id
                    join mentorsync_seguridad.usuarios tutor on tutor.id = t.tutor_id
                    join mentorsync_seguridad.usuarios estudiante on estudiante.id = t.estudiante_id
                    where :rol = 'COORDINADOR'
                        or (:rol = 'TUTOR' and t.tutor_id = :usuario)
                        or (:rol in ('ESTUDIANTE', 'TUTOR') and t.estudiante_id = :usuario)
                    order by t.fecha desc, t.hora_inicio, t.id
                    """, Object[].class)
                    .setParameter("rol", rol).setParameter("usuario", usuario).getResultList().stream()
                    .map(f -> new Fila(((Number) f[0]).longValue(), LocalDate.parse((String) f[1]),
                            (String) f[2], (String) f[3], (String) f[4], (String) f[5], (String) f[6],
                            ((Number) f[7]).longValue(), LocalTime.parse((String) f[8]),
                            LocalTime.parse((String) f[9]), ((Number) f[10]).longValue(), ((Number) f[11]).longValue()))
                    .toList();
            if (!coordinador) {
                return new Datos(List.of(), List.of(), List.of(), sesiones);
            }
            return new Datos(
                    opciones(sesion, "select id, nombre from " + USUARIOS + " where activo and rol = 'TUTOR' order by nombre, id"),
                    opciones(sesion, "select id, nombre from " + USUARIOS + " where activo and rol in ('ESTUDIANTE', 'TUTOR') order by nombre, id"),
                    opciones(sesion, "select id, nombre from mentorsync_academico.asignaturas where activo = true order by nombre, id"),
                    sesiones);
        }
    }

    public List<Opcion> materiasTutor(long tutor) {
        try (var em = HibernateUtil.getEntityManager()) {
            return em.unwrap(Session.class).createNativeQuery("""
                    select a.id, a.nombre from mentorsync_academico.asignaturas a
                    join mentorsync_academico.tutor_asignaturas ta on ta.asignatura_id = a.id
                    join mentorsync_seguridad.usuarios u on u.id = ta.tutor_id
                    where ta.tutor_id = :tutor and a.activo = true and u.activo = true and u.rol = 'TUTOR'
                    order by a.nombre, a.id
                    """, Object[].class).setParameter("tutor", tutor).getResultList().stream()
                    .map(f -> new Opcion(((Number) f[0]).longValue(), (String) f[1])).toList();
        }
    }

    public Optional<Detalle> buscarDetalle(long id, long usuario, String rol) {
        try (var em = HibernateUtil.getEntityManager()) {
            return em.unwrap(Session.class).createNativeQuery("""
                    select t.observaciones, t.calificacion_tutor,
                           t.comentario_estudiante, t.motivo_cancelacion
                    from mentorsync_academico.tutorias t
                    where t.id = :id and (
                        :rol = 'COORDINADOR'
                        or (:rol = 'TUTOR' and t.tutor_id = :usuario)
                        or (:rol in ('ESTUDIANTE', 'TUTOR') and t.estudiante_id = :usuario)
                    )
                    """, Object[].class)
                    .setParameter("id", id).setParameter("usuario", usuario).setParameter("rol", rol)
                    .getResultList().stream().findFirst()
                    .map(f -> new Detalle((String) f[0], f[1] == null ? null : ((Number) f[1]).intValue(),
                            (String) f[2], (String) f[3]));
        }
    }

    public boolean reprogramar(long id, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        return transaccion(sesion -> {
            sesion.createNativeMutationQuery("lock table " + TUTORIAS + " in share row exclusive mode")
                    .executeUpdate();
            return sesion.createNativeMutationQuery("""
                    update mentorsync_academico.tutorias objetivo
                    set fecha = :fecha, hora_inicio = :inicio, hora_fin = :fin
                    where objetivo.id = :id and objetivo.estado = 'PROGRAMADA'
                      and not exists (
                          select 1 from mentorsync_academico.tutorias otra
                          where otra.id <> objetivo.id
                            and otra.fecha = :fecha and otra.estado <> 'CANCELADA'
                            and (otra.tutor_id in (objetivo.tutor_id, objetivo.estudiante_id)
                                 or otra.estudiante_id in (objetivo.tutor_id, objetivo.estudiante_id))
                            and otra.hora_inicio < :fin and otra.hora_fin > :inicio
                      )
                    """).setParameter("id", id).setParameter("fecha", fecha)
                    .setParameter("inicio", inicio).setParameter("fin", fin).executeUpdate() == 1;
        });
    }

    public boolean eliminar(long id) {
        return transaccion(sesion -> sesion.createNativeMutationQuery("""
                        delete from mentorsync_academico.tutorias
                        where id = :id and estado in ('PROGRAMADA', 'CANCELADA')
                        """)
                .setParameter("id", id).executeUpdate() == 1);
    }

    private List<Opcion> opciones(Session sesion, String sql) {
        return sesion.createNativeQuery(sql, Object[].class).getResultList().stream()
                .map(f -> new Opcion(((Number) f[0]).longValue(), (String) f[1])).toList();
    }

    public boolean agendar(long tutor, long estudiante, long materia, LocalDate fecha,
                           LocalTime inicio, LocalTime fin) {
        return transaccion(sesion -> {
            sesion.createNativeMutationQuery("lock table " + TUTORIAS + " in share row exclusive mode")
                    .executeUpdate();
            return sesion.createNativeMutationQuery("""
                    insert into mentorsync_academico.tutorias
                        (tutor_id, estudiante_id, asignatura_id, fecha, hora_inicio, hora_fin, estado)
                    select tu.id, es.id, a.id, :fecha, :inicio, :fin, 'PROGRAMADA'
                    from mentorsync_seguridad.usuarios tu, mentorsync_seguridad.usuarios es,
                         mentorsync_academico.asignaturas a
                    where tu.id = :tutor and tu.activo and tu.rol = 'TUTOR'
                      and es.id = :estudiante and es.activo and es.rol in ('ESTUDIANTE', 'TUTOR')
                      and tu.id <> es.id and a.id = :materia and a.activo = true
                      and exists (
                          select 1 from mentorsync_academico.tutor_asignaturas ta
                          where ta.tutor_id = tu.id and ta.asignatura_id = a.id
                      )
                      and not exists (
                          select 1 from mentorsync_academico.tutorias t
                          where t.fecha = :fecha and t.estado <> 'CANCELADA'
                            and (t.tutor_id in (:tutor, :estudiante) or t.estudiante_id in (:tutor, :estudiante))
                            and t.hora_inicio < :fin and t.hora_fin > :inicio
                      )
                    """).setParameter("tutor", tutor).setParameter("estudiante", estudiante)
                    .setParameter("materia", materia).setParameter("fecha", fecha)
                    .setParameter("inicio", inicio).setParameter("fin", fin).executeUpdate() == 1;
        });
    }

    public boolean cancelar(long id) {
        return transaccion(sesion -> sesion.createNativeMutationQuery(
                        "update " + TUTORIAS + " set estado = 'CANCELADA' where id = :id and estado in ('PROGRAMADA', 'SOLICITUD_CANCELACION')")
                .setParameter("id", id).executeUpdate() == 1);
    }

    public boolean solicitarCancelacion(long id, long estudiante, String motivo) {
        return transaccion(sesion -> sesion.createNativeMutationQuery("""
                        update mentorsync_academico.tutorias
                        set estado = 'SOLICITUD_CANCELACION',
                            motivo_cancelacion = :motivo
                        where id = :id and estudiante_id = :estudiante and estado = 'PROGRAMADA'
                        """)
                .setParameter("id", id)
                .setParameter("estudiante", estudiante)
                .setParameter("motivo", motivo.trim())
                .executeUpdate() == 1);
    }

    public boolean calificarTutor(long id, long estudiante, int calificacion, String comentario) {
        return transaccion(sesion -> sesion.createNativeMutationQuery("""
                        update mentorsync_academico.tutorias
                        set calificacion_tutor = :calificacion,
                            comentario_estudiante = :comentario
                        where id = :id and estudiante_id = :estudiante
                          and estado in ('REALIZADA', 'VALIDADA')
                          and calificacion_tutor is null
                        """)
                .setParameter("id", id)
                .setParameter("estudiante", estudiante)
                .setParameter("calificacion", calificacion)
                .setParameter("comentario", comentario.trim())
                .executeUpdate() == 1);
    }

    public boolean marcarRealizada(long id, long tutor) {
        return transaccion(sesion -> sesion.createNativeMutationQuery("""
                        update mentorsync_academico.tutorias set estado = 'REALIZADA'
                        where id = :id and tutor_id = :tutor and estado = 'PROGRAMADA'
                          and fecha + hora_fin <= localtimestamp
                        """).setParameter("id", id).setParameter("tutor", tutor).executeUpdate() == 1);
    }

    public boolean validarHoras(long id) {
        return transaccion(sesion -> sesion.createNativeMutationQuery("""
                        with validada as (
                            update mentorsync_academico.tutorias set estado = 'VALIDADA'
                            where id = :id and estado = 'REALIZADA'
                            returning tutor_id, extract(epoch from (hora_fin - hora_inicio)) / 3600 as horas
                        )
                        update mentorsync_seguridad.usuarios u
                        set horas_acumuladas = coalesce(u.horas_acumuladas, 0) + validada.horas
                        from validada where u.id = validada.tutor_id
                        """).setParameter("id", id).executeUpdate() == 1);
    }

    private boolean transaccion(Function<Session, Boolean> operacion) {
        try (var em = HibernateUtil.getEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                boolean actualizado = operacion.apply(em.unwrap(Session.class));
                tx.commit();
                return actualizado;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }
}
