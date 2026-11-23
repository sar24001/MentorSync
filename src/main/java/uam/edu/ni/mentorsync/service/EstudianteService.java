package uam.edu.ni.mentorsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class EstudianteService {
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper();

    public Optional<EstudianteApiDTO> buscar(String cif) throws IOException, InterruptedException {
        if (!cif.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El CIF debe tener 8 dígitos.");
        }
        HttpServer servidor = ServidorApiSimulada.iniciar();
        try {
            URI uri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/estudiantes/" + cif);
            HttpRequest solicitud = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<byte[]> respuesta = cliente.send(solicitud, HttpResponse.BodyHandlers.ofByteArray());
            return switch (respuesta.statusCode()) {
                case 200 -> Optional.of(json.readValue(respuesta.body(), EstudianteApiDTO.class));
                case 404 -> Optional.empty();
                default -> throw new IOException("Respuesta inesperada de la API académica.");
            };
        } finally {
            servidor.stop(0);
        }
    }
}
