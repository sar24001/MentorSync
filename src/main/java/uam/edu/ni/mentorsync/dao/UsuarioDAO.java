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
                int habilitados = em.createNativeQuery("""
                        update mentorsync_seguridad.usuarios
                        set rol = 'TUTOR', promedio = :promedio
                        where carnet = :cif and activo and rol = 'ESTUDIANTE'
                        """)
                        .setParameter("cif", estudiante.cif())
                        .setParameter("promedio", estudiante.promedio())
                        .executeUpdate();
                if (habilitados == 1) {
                    transaccion.commit();
                    return RegistroTutor.HABILITADO;
                }
                if (clave == null) {
                    boolean existe = em.createQuery(
                                    "select count(u) from Usuario u where u.cif = :cif", Long.class)
                            .setParameter("cif", estudiante.cif()).getSingleResult() > 0;
                    transaccion.commit();
                    return existe ? RegistroTutor.DUPLICADO : RegistroTutor.REQUIERE_CLAVE;
                }
                int insertados = em.createNativeQuery("""
                        insert into mentorsync_seguridad.usuarios
                            (carnet, nombre, correo, password, rol, carrera, promedio, horas_acumuladas, activo)
                        values (:cif, :nombre, :correo, :clave, 'TUTOR', :carrera, :promedio, 0, true)
                        on conflict do nothing
                        """)
                        .setParameter("cif", estudiante.cif())
                        .setParameter("nombre", estudiante.nombre())
                        .setParameter("correo", estudiante.correo())
                        .setParameter("clave", clave)
                        .setParameter("carrera", estudiante.carrera())
                        .setParameter("promedio", estudiante.promedio())
                        .executeUpdate();
                transaccion.commit();
                return insertados == 1 ? RegistroTutor.CREADO : RegistroTutor.DUPLICADO;
            } catch (RuntimeException e) {
                if (transaccion.isActive()) {
                    transaccion.rollback();
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
}
