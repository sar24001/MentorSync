package uam.edu.ni.mentorsync.dao;

import uam.edu.ni.mentorsync.model.ClaseCursadaDTO;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;

import java.io.IOException;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class CatalogoPrivadoDAO {
    public Optional<EstudianteApiDTO> buscar(String cif) throws IOException {
        String url = System.getenv("MENTORSYNC_CATALOGO_PRIVADO_JDBC");
        String usuario = System.getenv("MENTORSYNC_CATALOGO_PRIVADO_USUARIO");
        String clave = System.getenv("MENTORSYNC_CATALOGO_PRIVADO_PASSWORD");
        if (ausente(url) && ausente(usuario) && ausente(clave)) {
            return Optional.empty();
        }
        if (ausente(url) || ausente(usuario) || ausente(clave) || !url.startsWith("jdbc:postgresql://")) {
            throw new IOException("La conexión al catálogo privado está incompleta.");
        }
        URI destino = URI.create(url.substring("jdbc:".length()));
        if (destino.getHost() == null || destino.getUserInfo() != null || destino.getQuery() != null
                || destino.getFragment() != null || !"/postgres".equals(destino.getPath())
                || !(destino.getHost().endsWith(".supabase.co")
                    || destino.getHost().endsWith(".pooler.supabase.com"))) {
            throw new IOException("Configura la dirección JDBC de Supabase sin credenciales ni parámetros.");
        }

        Properties opciones = new Properties();
        opciones.setProperty("user", usuario);
        opciones.setProperty("password", clave);
        opciones.setProperty("sslmode", "verify-full");
        String certificado = System.getenv("MENTORSYNC_SUPABASE_SSLROOTCERT");
        if (ausente(certificado)) {
            opciones.setProperty("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory");
        } else {
            var archivo = java.nio.file.Path.of(certificado);
            if (!java.nio.file.Files.isRegularFile(archivo) || !java.nio.file.Files.isReadable(archivo)) {
                throw new IOException("No se pudo leer el certificado de Supabase configurado.");
            }
            opciones.setProperty("sslrootcert", archivo.toString());
        }
        opciones.setProperty("connectTimeout", "5");
        opciones.setProperty("socketTimeout", "8");
        try (var conexion = DriverManager.getConnection(url, opciones);
             var consulta = conexion.prepareStatement("""
                    SELECT e.cif, e.nombre, e.correo, e.carrera, e.semestre,
                           c.codigo, c.nombre AS materia, c.nota_simulada
                    FROM mentorsync_privado.estudiantes e
                    LEFT JOIN mentorsync_privado.clases_cursadas c ON c.estudiante_cif = e.cif
                    WHERE e.cif = ?
                    ORDER BY c.codigo
                    """)) {
            consulta.setQueryTimeout(5);
            consulta.setString(1, cif);
            try (var filas = consulta.executeQuery()) {
                if (!filas.next()) {
                    return Optional.empty();
                }
                String nombre = filas.getString("nombre");
                String correo = filas.getString("correo");
                String carrera = filas.getString("carrera");
                int semestre = filas.getInt("semestre");
                List<ClaseCursadaDTO> materias = new ArrayList<>();
                do {
                    String codigo = filas.getString("codigo");
                    if (codigo != null) {
                        materias.add(new ClaseCursadaDTO(codigo, filas.getString("materia"),
                                filas.getDouble("nota_simulada")));
                    }
                } while (filas.next());
                return Optional.of(new EstudianteApiDTO(cif, nombre, correo, carrera,
                        (semestre + 1) / 2, semestre, true, List.copyOf(materias)));
            }
        } catch (SQLException error) {
            throw new IOException("No se pudo consultar el catálogo privado de Supabase.", error);
        }
    }

    private boolean ausente(String valor) {
        return valor == null || valor.isBlank();
    }
}
