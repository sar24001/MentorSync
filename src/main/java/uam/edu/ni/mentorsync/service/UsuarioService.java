package uam.edu.ni.mentorsync.service;

import uam.edu.ni.mentorsync.dao.UsuarioDAO;
import uam.edu.ni.mentorsync.dao.UsuarioDAO.RegistroTutor;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.ClaveUtil;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.util.List;
import java.util.Optional;

public class UsuarioService {
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    public Optional<Usuario> autenticar(String cif, String password) {
        return usuarioDAO.buscarPorCif(cif)
                .filter(Usuario::isActivo)
                .filter(usuario -> ClaveUtil.coincide(password, usuario.getPassword()));
    }

    public boolean esElegible(EstudianteApiDTO estudiante) {
        return estudiante.activo() && estudiante.promedio() >= 85;
    }

    public RegistroTutor registrarTutor(EstudianteApiDTO estudiante, String clave) {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !"COORDINADOR".equals(usuario.getRol())) {
            throw new IllegalStateException("Solo coordinación puede registrar tutores.");
        }
        if (!esElegible(estudiante)) {
            throw new IllegalArgumentException("El estudiante no cumple los requisitos para ser tutor.");
        }
        return usuarioDAO.registrarTutor(estudiante, clave.isBlank() ? null : ClaveUtil.generar(clave));
    }

    public List<TutorResumen> listarTutoresActivos() {
        return usuarioDAO.listarTutoresActivos();
    }
}
