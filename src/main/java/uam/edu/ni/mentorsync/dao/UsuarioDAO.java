package uam.edu.ni.mentorsync.dao;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;
import uam.edu.ni.mentorsync.model.ClaseCursadaDTO;
import uam.edu.ni.mentorsync.model.ResenaTutor;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class UsuarioDAO {

    public Optional<Usuario> buscarPorCif(String cif) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return Optional.ofNullable(em.createQuery(
                            "select u from Usuario u where u.cif = :cif", Usuario.class)
                    .setParameter("cif", cif)
                    .getSingleResultOrNull());
        }
    }

    public enum RegistroTutor { HABILITADO, CREADO, DUPLICADO, REQUIERE_CLAVE }

    public RegistroTutor registrarTutor(EstudianteApiDTO estudiante, String clave) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            var transaccion = em.getTransaction();
            try {
                transaccion.begin();
                boolean correoOcupado = em.createQuery("""
                        select count(u) from Usuario u
                        where u.correo = :correo and u.cif <> :cif
                        """, Long.class)
                        .setParameter("correo", estudiante.correo())
                        .setParameter("cif", estudiante.cif())
                        .getSingleResult() > 0;
                if (correoOcupado) {
                    transaccion.commit();
                    return RegistroTutor.DUPLICADO;
                }
                Usuario existente = em.createQuery(
                                "select u from Usuario u where u.cif = :cif", Usuario.class)
                        .setParameter("cif", estudiante.cif())
                        .getSingleResultOrNull();
                if (existente != null) {
                    if (!existente.isActivo() || !"ESTUDIANTE".equals(existente.getRol())) {
                        transaccion.commit();
                        return RegistroTutor.DUPLICADO;
                    }
                    existente.habilitarComoTutor(estudiante);
                    habilitarMateriasAprobadas(em, existente.getId(), estudiante);
                    transaccion.commit();
                    return RegistroTutor.HABILITADO;
                }
                if (clave == null || clave.isBlank()) {
                    transaccion.commit();
                    return RegistroTutor.REQUIERE_CLAVE;
                }
                Usuario nuevo = Usuario.crearTutor(estudiante, clave);
                em.persist(nuevo);
                habilitarMateriasAprobadas(em, nuevo.getId(), estudiante);
                transaccion.commit();
                return RegistroTutor.CREADO;
            } catch (RuntimeException e) {
                if (transaccion.isActive()) {
                    try {
                        transaccion.rollback();
                    } catch (RuntimeException rollbackError) {
                        e.addSuppressed(rollbackError);
                    }
                }
                throw e;
            }
        }
    }

    public List<TutorResumen> listarTutoresActivos() {
        return listarTutores(false);
    }

    public List<TutorResumen> listarTutores(boolean incluirInactivos) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.unwrap(Session.class).createNativeQuery("""
                    select u.carnet, u.nombre, u.carrera, u.promedio, u.horas_acumuladas,
                           avg(t.calificacion_tutor), count(t.id), u.activo
                    from mentorsync_seguridad.usuarios u
                    left join mentorsync_academico.tutorias t on t.tutor_id = u.id
                        and (t.calificacion_tutor is not null
                             or nullif(trim(t.comentario_estudiante), '') is not null)
                    where u.rol = 'TUTOR' and (:inactivos = true or u.activo = true)
                    group by u.id, u.carnet, u.nombre, u.carrera, u.promedio, u.horas_acumuladas, u.activo
                    order by u.nombre, u.carnet
                    """, Object[].class).setParameter("inactivos", incluirInactivos).getResultList().stream()
                    .map(fila -> new TutorResumen((String) fila[0], (String) fila[1], (String) fila[2],
                            numeroDecimal(fila[3]), numeroDecimal(fila[4]), numeroDecimal(fila[5]),
                            ((Number) fila[6]).longValue(), (Boolean) fila[7]))
                    .toList();
        }
    }

    public List<ResenaTutor> listarResenasTutor(String cif) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.unwrap(Session.class).createNativeQuery("""
                    select estudiante.nombre, cast(t.fecha as text),
                           t.calificacion_tutor, t.comentario_estudiante
                    from mentorsync_academico.tutorias t
                    join mentorsync_seguridad.usuarios tutor on tutor.id = t.tutor_id
                    join mentorsync_seguridad.usuarios estudiante on estudiante.id = t.estudiante_id
                    where tutor.carnet = :cif and tutor.rol = 'TUTOR'
                        and (t.calificacion_tutor is not null
                             or nullif(trim(t.comentario_estudiante), '') is not null)
                    order by t.fecha desc, t.id desc
                    """, Object[].class)
                    .setParameter("cif", cif)
                    .getResultList().stream()
                    .map(fila -> new ResenaTutor((String) fila[0], LocalDate.parse((String) fila[1]),
                            fila[2] == null ? null : ((Number) fila[2]).intValue(), (String) fila[3]))
                    .toList();
        }
    }

    private void habilitarMateriasAprobadas(EntityManager em, long tutor, EstudianteApiDTO estudiante) {
        for (ClaseCursadaDTO clase : estudiante.clasesElegibles()) {
            if (clase.codigo() == null || !Double.isFinite(clase.nota()) || clase.nota() > 100) {
                continue;
            }
            em.createNativeQuery("""
                    insert into mentorsync_academico.tutor_asignaturas(tutor_id, asignatura_id, nota_aprobacion)
                    select :tutor, id, :nota from mentorsync_academico.asignaturas
                    where upper(codigo) = :codigo and activo = true
                    on conflict (tutor_id, asignatura_id) do update set nota_aprobacion = excluded.nota_aprobacion
                    """).setParameter("tutor", tutor).setParameter("codigo", clase.codigo().trim().toUpperCase(java.util.Locale.ROOT))
                    .setParameter("nota", clase.nota()).executeUpdate();
        }
    }

    public boolean cambiarEstadoTutor(String cif, boolean activo) {
        try (var em = HibernateUtil.getEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                int cambios = em.createNativeQuery("""
                        update mentorsync_seguridad.usuarios set activo = :activo where carnet = :cif and rol = 'TUTOR'
                        """).setParameter("activo", activo).setParameter("cif", cif).executeUpdate();
                tx.commit();
                return cambios == 1;
            } catch (RuntimeException error) {
                if (tx.isActive()) tx.rollback();
                throw error;
            }
        }
    }

    public Usuario registrarEstudiante(EstudianteApiDTO estudiante, String clave) {
        try (var em = HibernateUtil.getEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                Usuario existente = em.createQuery("select u from Usuario u where u.cif = :cif", Usuario.class)
                        .setParameter("cif", estudiante.cif()).getSingleResultOrNull();
                if (existente != null) {
                    if (!existente.isActivo() || !("ESTUDIANTE".equals(existente.getRol()) || "TUTOR".equals(existente.getRol()))) {
                        throw new IllegalStateException("El carnet pertenece a una cuenta inactiva o a otro rol.");
                    }
                    tx.commit();
                    return existente;
                }
                boolean correoOcupado = em.createQuery("select count(u) from Usuario u where u.correo = :correo", Long.class)
                        .setParameter("correo", estudiante.correo()).getSingleResult() > 0;
                if (correoOcupado) {
                    throw new IllegalArgumentException("El correo de la API ya pertenece a otra cuenta local.");
                }
                Usuario nuevo = Usuario.crearEstudiante(estudiante, clave);
                em.persist(nuevo);
                tx.commit();
                return nuevo;
            } catch (RuntimeException error) {
                if (tx.isActive()) tx.rollback();
                throw error;
            }
        }
    }

    private static Double numeroDecimal(Object valor) {
        return valor == null ? null : ((Number) valor).doubleValue();
    }

    public List<Usuario> buscarTutoresActivosPorNombre(String criterio) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.createQuery("""
                    select u from Usuario u
                    where u.rol = 'TUTOR'
                      and u.activo = true
                      and lower(u.nombre) like lower(:patron)
                    order by u.nombre, u.cif
                    """, Usuario.class)
                    .setParameter("patron", "%" + criterio.trim() + "%")
                    .setMaxResults(15)
                    .getResultList();
        }
    }

    public List<Usuario> buscarEstudiantesActivosPorNombre(String criterio) {
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.createQuery("""
                    select u from Usuario u
                    where u.rol = 'ESTUDIANTE'
                      and u.activo = true
                      and lower(u.nombre) like lower(:patron)
                    order by u.nombre, u.cif
                    """, Usuario.class)
                    .setParameter("patron", "%" + criterio.trim() + "%")
                    .setMaxResults(15)
                    .getResultList();
        }
    }
}
