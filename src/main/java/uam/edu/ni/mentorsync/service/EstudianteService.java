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
            "https://gist.githubusercontent.com/sar24001/55b0fea24d086178d4a268b1550a8814/raw/c31ba0b6dc3371bb2eaf5e07eb99b5ff3ca7ed94/estudiantes.json";

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

        HttpRequest solicitud = HttpRequest.newBuilder(URI.create(URL_API))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<byte[]> respuesta = cliente().send(solicitud, HttpResponse.BodyHandlers.ofByteArray());

        return switch (respuesta.statusCode()) {
            case 200 -> Arrays.stream(json.readValue(respuesta.body(), EstudianteApiDTO[].class))
                    .filter(estudiante -> cifBuscado.equals(estudiante.cif()))
                    .findFirst();
            case 404 -> Optional.empty();
            default -> throw new IOException("Respuesta inesperada de la API académica (HTTP " + respuesta.statusCode() + ").");
        };
    }
}
