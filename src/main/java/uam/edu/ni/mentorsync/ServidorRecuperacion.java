package uam.edu.ni.mentorsync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import uam.edu.ni.mentorsync.service.RecuperacionService;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;

public final class ServidorRecuperacion {
    private static final int LIMITE_SOLICITUD = 4096;
    private final ObjectMapper json = new ObjectMapper();
    private final RecuperacionService recuperacion = new RecuperacionService();

    public static void main(String[] args) throws IOException {
        int puerto = args.length == 0 ? 8081 : Integer.parseInt(args[0]);
        var servicio = new ServidorRecuperacion();
        var hilos = Executors.newFixedThreadPool(4);
        var servidor = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), puerto), 16);
        servidor.createContext("/recuperacion/solicitar", intercambio -> servicio.atender(intercambio, true));
        servidor.createContext("/recuperacion/restablecer", intercambio -> servicio.atender(intercambio, false));
        servidor.setExecutor(hilos);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.stop(1);
            hilos.shutdownNow();
            HibernateUtil.cerrar();
        }));
        servidor.start();
        System.out.println("Servicio de recuperación en 127.0.0.1:" + servidor.getAddress().getPort());
    }

    private void atender(HttpExchange intercambio, boolean solicitar) throws IOException {
        try (intercambio) {
            if (!"POST".equals(intercambio.getRequestMethod())) {
                intercambio.getResponseHeaders().set("Allow", "POST");
                responder(intercambio, 405, false);
                return;
            }
            String ruta = solicitar ? "/recuperacion/solicitar" : "/recuperacion/restablecer";
            if (!ruta.equals(intercambio.getRequestURI().getPath())) {
                responder(intercambio, 404, false);
                return;
            }
            byte[] cuerpo = intercambio.getRequestBody().readNBytes(LIMITE_SOLICITUD + 1);
            if (cuerpo.length > LIMITE_SOLICITUD) {
                responder(intercambio, 413, false);
                return;
            }
            JsonNode datos;
            try {
                datos = json.readTree(cuerpo);
                if (datos == null || !datos.isObject()) {
                    responder(intercambio, 400, false);
                    return;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                responder(intercambio, 400, false);
                return;
            }
            boolean aceptado;
            try {
                String cif = datos.path("cif").asText("");
                if (solicitar) {
                    recuperacion.enviar(cif);
                    aceptado = true;
                } else {
                    aceptado = recuperacion.restablecer(cif, datos.path("codigo").asText(""),
                            datos.path("clave").asText(""));
                }
            } catch (IllegalArgumentException e) {
                responder(intercambio, 400, false);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                responder(intercambio, 503, false);
                return;
            } catch (IOException | RuntimeException e) {
                responder(intercambio, 503, false);
                return;
            }
            responder(intercambio, solicitar ? 202 : 200, aceptado);
        }
    }

    private void responder(HttpExchange intercambio, int estado, boolean aceptado) throws IOException {
        byte[] cuerpo = json.writeValueAsBytes(Map.of("aceptado", aceptado));
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
    }
}
