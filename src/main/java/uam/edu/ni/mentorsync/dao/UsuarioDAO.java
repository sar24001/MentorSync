package uam.edu.ni.mentorsync.dao;

import jakarta.persistence.EntityManager;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.HibernateUtil;

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

}
