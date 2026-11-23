package uam.edu.ni.mentorsync.service;

import uam.edu.ni.mentorsync.dao.UsuarioDAO;
import uam.edu.ni.mentorsync.model.TutorResumen;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.ClaveUtil;

import java.util.List;
import java.util.Optional;

public class UsuarioService {
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    public Optional<Usuario> autenticar(String cif, String password) {
        return usuarioDAO.buscarPorCif(cif)
                .filter(Usuario::isActivo)
                .filter(usuario -> ClaveUtil.coincide(password, usuario.getPassword()));
    }

    public List<TutorResumen> listarTutoresActivos() {
        return usuarioDAO.listarTutoresActivos();
    }
}
