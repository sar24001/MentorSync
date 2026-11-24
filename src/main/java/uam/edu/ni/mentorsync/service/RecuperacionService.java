package uam.edu.ni.mentorsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import uam.edu.ni.mentorsync.dao.RecuperacionDAO;
import uam.edu.ni.mentorsync.util.ClaveUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public class RecuperacionService {
    private static final SecureRandom AZAR = new SecureRandom();
    private final RecuperacionDAO dao = new RecuperacionDAO();
    private final ObjectMapper json = new ObjectMapper();

    public void enviar(String cif) throws IOException, InterruptedException {
        verificarCif(cif);
        String apiKey = System.getenv("MENTORSYNC_RESEND_API_KEY");
        String remitente = System.getenv("MENTORSYNC_CORREO_REMITENTE");
        if (apiKey == null || apiKey.isBlank() || remitente == null || remitente.isBlank()) {
            throw new IllegalStateException("El envío de correos de recuperación aún no está configurado.");
        }

        byte[] bytes = new byte[16];
        AZAR.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String correo = dao.solicitar(cif, hash(codigo));
        if (correo == null) {
            return;
        }
        String cuerpo = json.writeValueAsString(Map.of(
                "from", remitente,
                "to", List.of(correo),
                "subject", "Recuperación de contraseña de MentorSync",
                "text", "Tu código de recuperación es: " + codigo + "\nVence en 10 minutos y solo se puede usar una vez."
        ));
        HttpRequest solicitud = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        try (HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            int estado = cliente.send(solicitud, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (estado < 200 || estado >= 300) {
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                        "Resend rechazó el correo de recuperación (HTTP {0}).", estado);
            }
        }
    }

    public boolean restablecer(String cif, String codigo, String clave) {
        verificarCif(cif);
        if (!codigo.matches("[A-Za-z0-9_-]{22}") || clave.length() < 8 || clave.length() > 128) {
            throw new IllegalArgumentException("Revisa el código y usa una contraseña de 8 a 128 caracteres.");
        }
        return dao.cambiarClave(cif, hash(codigo), ClaveUtil.generar(clave));
    }

    private void verificarCif(String cif) {
        if (!cif.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El CIF debe tener 8 dígitos.");
        }
    }

    private String hash(String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("No se pudo procesar el código.", error);
        }
    }
}
