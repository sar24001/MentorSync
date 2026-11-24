package uam.edu.ni.mentorsync.service;

import uam.edu.ni.mentorsync.dao.UsuarioDAO;
import uam.edu.ni.mentorsync.dao.UsuarioDAO.RegistroTutor;
import uam.edu.ni.mentorsync.model.EstudianteApiDTO;
import uam.edu.ni.mentorsync.model.ResenaTutor;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.ClaveUtil;
import uam.edu.ni.mentorsync.util.SesionActual;

import java.util.List;
import java.util.Optional;
import java.io.IOException;

public class UsuarioService {
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final EstudianteService academico = new EstudianteService();

    public Optional<Usuario> autenticar(String cif, String password) {
        return usuarioDAO.buscarPorCif(cif)
                .filter(Usuario::isActivo)
                .filter(usuario -> ClaveUtil.coincide(password, usuario.getPassword()));
    }

    public boolean esElegible(EstudianteApiDTO estudiante) {
        return estudiante != null && estudiante.puedeSerTutor();
    }

    public RegistroTutor registrarTutor(EstudianteApiDTO estudiante, String clave) {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !"COORDINADOR".equals(usuario.getRol())) {
            throw new IllegalStateException("Solo coordinación puede registrar tutores.");
        }
        if (!esElegible(estudiante)) {
            throw new IllegalArgumentException("El estudiante no cumple los requisitos para ser tutor.");
        }
        return usuarioDAO.registrarTutor(estudiante,
                clave == null || clave.isBlank() ? null : ClaveUtil.generar(clave));
    }

    public List<TutorResumen> listarTutoresActivos() {
        return usuarioDAO.listarTutoresActivos();
    }

    public List<TutorResumen> listarTutores(boolean incluirInactivos) {
        exigirCoordinador();
        return usuarioDAO.listarTutores(incluirInactivos);
    }

    public boolean cambiarEstadoTutor(String cif, boolean activo) {
        exigirCoordinador();
        return usuarioDAO.cambiarEstadoTutor(cif, activo);
    }

    public Usuario importarEstudiante(String cif, String clave) throws IOException, InterruptedException {
        exigirCoordinador();
        if (cif == null || !cif.trim().matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El carnet debe tener 8 dígitos.");
        }
        String carnet = cif.trim();
        Optional<Usuario> local = usuarioDAO.buscarPorCif(carnet);
        if (local.isPresent()) {
            Usuario existente = local.get();
            if (!existente.isActivo() || !("ESTUDIANTE".equals(existente.getRol()) || "TUTOR".equals(existente.getRol()))) {
                throw new IllegalStateException("El carnet pertenece a una cuenta inactiva o a otro rol.");
            }
            return existente;
        }
        EstudianteApiDTO estudiante = academico.buscar(carnet)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró ese carnet en la API académica."));
        if (!estudiante.activo() || estudiante.nombre() == null || estudiante.nombre().isBlank()
                || estudiante.correo() == null || estudiante.correo().isBlank()) {
            throw new IllegalArgumentException("El estudiante debe estar activo y tener nombre y correo en la API.");
        }
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("Asigna una contraseña inicial para crear la cuenta del estudiante.");
        }
        return usuarioDAO.registrarEstudiante(estudiante, ClaveUtil.generar(clave));
    }

    private void exigirCoordinador() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !"COORDINADOR".equals(usuario.getRol())) {
            throw new IllegalStateException("Solo coordinación puede administrar usuarios y tutores.");
        }
    }

    public List<ResenaTutor> listarResenasTutor(String cif) {
        return usuarioDAO.listarResenasTutor(cif);
    }
}
