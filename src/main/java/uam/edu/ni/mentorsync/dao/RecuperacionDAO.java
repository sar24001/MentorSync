package uam.edu.ni.mentorsync.dao;

import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.util.function.Function;
import jakarta.persistence.EntityManager;

public class RecuperacionDAO {
    public String solicitar(String cif, String hash) {
        return ejecutar(em -> em.unwrap(org.hibernate.Session.class).createNativeQuery("""
                with solicitud as (
                    update mentorsync_seguridad.usuarios
                    set recuperacion_hash = :hash, recuperacion_expira = now() + interval '10 minutes'
                    where carnet = :cif and activo and correo is not null and trim(correo) <> ''
                      and (recuperacion_expira is null or recuperacion_expira <= now() + interval '9 minutes')
                    returning correo
                )
                select correo from solicitud
                """, String.class).setParameter("hash", hash).setParameter("cif", cif).getSingleResultOrNull());
    }

    public boolean cambiarClave(String cif, String hash, String clave) {
        return ejecutar(em -> em.unwrap(org.hibernate.Session.class).createNativeQuery("""
                update mentorsync_seguridad.usuarios
                set password = :clave, recuperacion_hash = null, recuperacion_expira = null
                where carnet = :cif and activo and recuperacion_hash = :hash and recuperacion_expira > now()
                """).setParameter("clave", clave).setParameter("cif", cif).setParameter("hash", hash)
                .executeUpdate() == 1);
    }

    private <T> T ejecutar(Function<EntityManager, T> operacion) {
        try (var em = HibernateUtil.getEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                T resultado = operacion.apply(em);
                tx.commit();
                return resultado;
            } catch (RuntimeException error) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw error;
            }
        }
    }
}
