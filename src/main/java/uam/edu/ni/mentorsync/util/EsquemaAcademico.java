package uam.edu.ni.mentorsync.util;

import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class EsquemaAcademico {
    private EsquemaAcademico() {
    }

    static void actualizar(EntityManagerFactory factory) {
        String sql;
        try (var recurso = EsquemaAcademico.class.getResourceAsStream("/db/mentorsync-actualizaciones.sql")) {
            if (recurso == null) {
                throw new IllegalStateException("No se encontró la actualización del esquema académico.");
            }
            sql = new String(recurso.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo leer la actualización del esquema académico.", error);
        }
        try (var em = factory.createEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                for (String sentencia : sql.split(";")) {
                    if (!sentencia.isBlank()) {
                        em.createNativeQuery(sentencia).executeUpdate();
                    }
                }
                tx.commit();
            } catch (RuntimeException error) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw error;
            }
        }
    }
}
