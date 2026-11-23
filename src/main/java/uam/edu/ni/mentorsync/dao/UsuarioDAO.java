package uam.edu.ni.mentorsync.dao;

import jakarta.persistence.EntityManager;
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
