package uam.edu.ni.mentorsync.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class ClaveUtil {
    private static final String PREFIJO = "pbkdf2:";
    private static final int ITERACIONES = 600_000;

    private ClaveUtil() {
    }

    public static String generar(String clave) {
        byte[] sal = new byte[16];
        new SecureRandom().nextBytes(sal);
        return PREFIJO + HexFormat.of().formatHex(sal) + ":" + HexFormat.of().formatHex(derivar(clave, sal));
    }

    public static boolean coincide(String clave, String almacenada) {
        if (!almacenada.startsWith(PREFIJO)) {
            return MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8),
                    almacenada.getBytes(StandardCharsets.UTF_8));
        }
        String[] partes = almacenada.split(":", -1);
        if (partes.length != 3 || partes[1].length() != 32 || partes[2].length() != 64) {
            return false;
        }
        try {
            HexFormat hex = HexFormat.of();
            return MessageDigest.isEqual(derivar(clave, hex.parseHex(partes[1])), hex.parseHex(partes[2]));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String clave, byte[] sal) {
        PBEKeySpec especificacion = new PBEKeySpec(clave.toCharArray(), sal, ITERACIONES, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo procesar la contraseña.", e);
        } finally {
            especificacion.clearPassword();
        }
    }
}
