package uam.edu.ni.mentorsync.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.Map;

public final class HibernateUtil {
    private static EntityManagerFactory factory;
    private static String password = System.getenv("MENTORSYNC_DB_PASSWORD");

    private HibernateUtil() {
    }

    public static synchronized boolean tienePassword() {
        return password != null && !password.isEmpty();
    }

    public static synchronized void configurarPassword(String valor) {
        password = valor;
    }

    public static synchronized EntityManager getEntityManager() {
        if (factory == null) {
            if (!tienePassword()) {
                throw new IllegalStateException("Configura MENTORSYNC_DB_PASSWORD para conectar a PostgreSQL.");
            }
            Map<String, String> environment = System.getenv();
            EntityManagerFactory nuevaFactory = Persistence.createEntityManagerFactory("MentorSyncPU", Map.of(
                    "jakarta.persistence.jdbc.url", environment.getOrDefault("MENTORSYNC_DB_URL",
                            "jdbc:postgresql://localhost:5432/mentorsync_db"),
                    "jakarta.persistence.jdbc.user", environment.getOrDefault("MENTORSYNC_DB_USER", "postgres"),
                    "jakarta.persistence.jdbc.password", password,
                    "hibernate.connection.connectTimeout", "5",
                    "hibernate.connection.socketTimeout", "15",
                    "jakarta.persistence.query.timeout", 10_000
            ));
            try {
                EsquemaAcademico.actualizar(nuevaFactory);
                factory = nuevaFactory;
            } catch (RuntimeException error) {
                nuevaFactory.close();
                throw error;
            }
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
