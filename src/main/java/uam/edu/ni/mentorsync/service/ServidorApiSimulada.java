package uam.edu.ni.mentorsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

public final class ServidorApiSimulada {
        private static final Map<String, EstudianteApiDTO> ESTUDIANTES = Map.of(
            "20240202", new EstudianteApiDTO("20240202", "Valeria Gómez",
                    "valeria.gomez@uamv.edu.ni", "Ingeniería Civil", 87, true),
            "25001234", new EstudianteApiDTO("25001234", "Ana López",
                    "ana.lopez@example.test", "Ingeniería en Sistemas", 92, true),
            "25001235", new EstudianteApiDTO("25001235", "Luis Pérez",
                    "luis.perez@example.test", "Ingeniería Industrial", 80, true),
            "25001236", new EstudianteApiDTO("25001236", "Sara Ruiz",
                    "sara.ruiz@example.test", "Ingeniería Civil", 91, false)
    );

    private ServidorApiSimulada() {
    }

    public static HttpServer iniciar() throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectMapper json = new ObjectMapper();
        servidor.createContext("/estudiantes/", intercambio -> {
            try (intercambio) {
                String cif = intercambio.getRequestURI().getPath().substring("/estudiantes/".length());
                EstudianteApiDTO estudiante = ESTUDIANTES.get(cif);
                if (!"GET".equals(intercambio.getRequestMethod())) {
                    intercambio.sendResponseHeaders(405, -1);
                } else if (estudiante == null) {
                    intercambio.sendResponseHeaders(404, -1);
                } else {
                    byte[] respuesta = json.writeValueAsBytes(estudiante);
                    intercambio.getResponseHeaders().set("Content-Type", "application/json");
                    intercambio.sendResponseHeaders(200, respuesta.length);
                    intercambio.getResponseBody().write(respuesta);
                }
            }
        });
        servidor.start();
        return servidor;
    }
}
