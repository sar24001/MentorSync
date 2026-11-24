package uam.edu.ni.mentorsync.dao;

import jakarta.persistence.EntityManager;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.HibernateUtil;

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
                    transaccion.commit();
                    return RegistroTutor.HABILITADO;
                }
                if (clave == null || clave.isBlank()) {
                    transaccion.commit();
                    return RegistroTutor.REQUIERE_CLAVE;
                }
                em.persist(Usuario.crearTutor(estudiante, clave));
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
        try (EntityManager em = HibernateUtil.getEntityManager()) {
            return em.createQuery("""
                    select new uam.edu.ni.mentorsync.model.TutorResumen(
                        u.cif, u.nombre, u.carrera, u.promedio, u.horasAcumuladas)
                    from Usuario u
                    where u.rol = 'TUTOR' and u.activo = true
                    order by u.nombre, u.cif
                    """, TutorResumen.class).getResultList();
        }
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
