package uam.edu.ni.mentorsync.util;

import uam.edu.ni.mentorsync.model.Usuario;

public final class SesionActual {
    private static Usuario usuario;

    private SesionActual() {
    }

    public static void iniciar(Usuario autenticado) {
        usuario = autenticado;
    }

    public static Usuario getUsuario() {
        return usuario;
    }

    public static void cerrar() {
        usuario = null;
    }
}
