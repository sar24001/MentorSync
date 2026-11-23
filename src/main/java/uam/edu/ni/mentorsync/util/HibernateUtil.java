package uam.edu.ni.mentorsync.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.Map;

public final class HibernateUtil {
    private static EntityManagerFactory factory;

    private HibernateUtil() {
    }

    public static synchronized EntityManager getEntityManager() {
        if (factory == null) {
            String password = System.getenv("MENTORSYNC_DB_PASSWORD");
            if (password == null || password.isEmpty()) {
                throw new IllegalStateException("Configura MENTORSYNC_DB_PASSWORD para conectar a PostgreSQL.");
            }
            Map<String, String> environment = System.getenv();
            factory = Persistence.createEntityManagerFactory("MentorSyncPU", Map.of(
                    "jakarta.persistence.jdbc.url", environment.getOrDefault("MENTORSYNC_DB_URL",
                            "jdbc:postgresql://localhost:5432/mentorsync_db"),
                    "jakarta.persistence.jdbc.user", environment.getOrDefault("MENTORSYNC_DB_USER", "postgres"),
                    "jakarta.persistence.jdbc.password", password
            ));
        }
        return factory.createEntityManager();
    }

    public static synchronized void cerrar() {
        if (factory != null) {
            factory.close();
            factory = null;
        }
    }
}
