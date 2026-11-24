package uam.edu.ni.mentorsync.dao;

import org.hibernate.Session;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
                       String tutor, String estudiante, String estado, long tutorId, LocalTime horaFin) {
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
                           a.nombre, tutor.nombre, estudiante.nombre, t.estado, t.tutor_id, cast(t.hora_fin as text)
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
                            ((Number) f[7]).longValue(), LocalTime.parse((String) f[8])))
                    .toList();
            if (!coordinador) {
                return new Datos(List.of(), List.of(), List.of(), sesiones);
            }
            return new Datos(
                    opciones(sesion, "select id, nombre from " + USUARIOS + " where activo and rol = 'TUTOR' order by nombre, id"),
                    opciones(sesion, "select id, nombre from " + USUARIOS + " where activo and rol in ('ESTUDIANTE', 'TUTOR') order by nombre, id"),
                    opciones(sesion, "select id, nombre from mentorsync_academico.asignaturas order by nombre, id"),
                    sesiones);
        }
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
                      and tu.id <> es.id and a.id = :materia
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
                        "update " + TUTORIAS + " set estado = 'CANCELADA' where id = :id and estado = 'PROGRAMADA'")
                .setParameter("id", id).executeUpdate() == 1);
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
