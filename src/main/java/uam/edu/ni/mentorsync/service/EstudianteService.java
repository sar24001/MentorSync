package uam.edu.ni.mentorsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

public class EstudianteService {
    private static final String URL_API =
            "https://gist.githubusercontent.com/sar24001/55b0fea24d086178d4a268b1550a8814/raw/4b24be81c6003bcb1e36ff045074de1cf8d67f66/estudiantes.json";

    private HttpClient cliente;
    private final ObjectMapper json = new ObjectMapper();

    private synchronized HttpClient cliente() {
        if (cliente == null) {
            cliente = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        }
        return cliente;
    }

    public Optional<EstudianteApiDTO> buscar(String cif) throws IOException, InterruptedException {
        if (cif == null || !cif.trim().matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El CIF debe tener 8 dígitos.");
        }
        String cifBuscado = cif.trim();

        String supabase = System.getenv("MENTORSYNC_SUPABASE_URL");
        String llave = System.getenv("MENTORSYNC_SUPABASE_PUBLISHABLE_KEY");
        boolean usarSupabase = supabase != null && !supabase.isBlank();
        if (usarSupabase && (llave == null || !llave.startsWith("sb_publishable_"))) {
            throw new IOException("Configura la clave publicable del catálogo de Supabase.");
        }
        if (!usarSupabase && llave != null && !llave.isBlank()) {
            throw new IOException("Configura la URL del catálogo de Supabase.");
        }

        URI url = URI.create(URL_API);
        if (usarSupabase) {
            URI proyecto = URI.create(supabase.trim());
            if (!"https".equalsIgnoreCase(proyecto.getScheme()) || proyecto.getHost() == null
                    || proyecto.getRawUserInfo() != null || proyecto.getRawQuery() != null
                    || proyecto.getRawFragment() != null || (proyecto.getPath() != null
                    && !proyecto.getPath().isEmpty() && !"/".equals(proyecto.getPath()))) {
                throw new IOException("Usa la URL HTTPS del proyecto Supabase, sin ruta adicional.");
            }
            String recurso = "/rest/v1/estudiantes?cif=eq." + cifBuscado
                    + "&select=cif,nombre,correo,carrera,anioCarrera,semestre,activo,"
                    + "clasesCursadas:clases_cursadas(codigo,nombre,nota)";
            url = proyecto.resolve(recurso);
        }

        HttpRequest.Builder solicitud = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET();

        if (usarSupabase) {
            solicitud.header("apikey", llave);
        }
        HttpResponse<byte[]> respuesta = cliente().send(solicitud.build(), HttpResponse.BodyHandlers.ofByteArray());

        return switch (respuesta.statusCode()) {
            case 200 -> Arrays.stream(json.readValue(respuesta.body(), EstudianteApiDTO[].class))
                    .filter(estudiante -> cifBuscado.equals(estudiante.cif()))
                    .findFirst();
            case 404 -> { if (usarSupabase) throw new IOException("No se encontró el catálogo en Supabase."); yield Optional.empty(); }
            default -> throw new IOException("Respuesta inesperada de la API académica (HTTP " + respuesta.statusCode() + ").");
        };
    }
}
