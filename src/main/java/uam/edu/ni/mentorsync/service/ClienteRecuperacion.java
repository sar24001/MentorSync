package uam.edu.ni.mentorsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class ClienteRecuperacion {
    private final ObjectMapper json = new ObjectMapper();

    public boolean solicitar(String cif) throws IOException, InterruptedException {
        return consultar("/recuperacion/solicitar", Map.of("cif", cif));
    }

    public boolean restablecer(String cif, String codigo, String clave) throws IOException, InterruptedException {
        return consultar("/recuperacion/restablecer", Map.of("cif", cif, "codigo", codigo, "clave", clave));
    }

    private boolean consultar(String ruta, Map<String, String> datos) throws IOException, InterruptedException {
        String direccion = System.getenv("MENTORSYNC_RECUPERACION_URL");
        if (direccion == null || direccion.isBlank()) {
            throw new IllegalStateException("Configura la dirección del servidor de recuperación.");
        }
        URI base = URI.create(direccion.trim());
        boolean accesoLocal = "http".equalsIgnoreCase(base.getScheme()) && "127.0.0.1".equals(base.getHost());
        if (base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null || (!accesoLocal && !"https".equalsIgnoreCase(base.getScheme()))) {
            throw new IllegalStateException("El servidor de recuperación debe usar HTTPS o 127.0.0.1 en la prueba local.");
        }
        HttpRequest peticion = HttpRequest.newBuilder(base.resolve(ruta))
                .timeout(Duration.ofSeconds(18))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(datos)))
                .build();
        try (HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofByteArray());
            if (respuesta.statusCode() == 400) {
                throw new IllegalArgumentException("Revisa los datos de recuperación.");
            }
            if (respuesta.statusCode() != 200 && respuesta.statusCode() != 202) {
                throw new IOException("El servidor de recuperación no está disponible.");
            }
            return json.readTree(respuesta.body()).path("aceptado").asBoolean(false);
        }
    }
}
