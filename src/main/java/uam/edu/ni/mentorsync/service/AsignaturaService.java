package uam.edu.ni.mentorsync.service;

import uam.edu.ni.mentorsync.dao.AsignaturaDAO;
import uam.edu.ni.mentorsync.dao.UsuarioDAO;
import uam.edu.ni.mentorsync.model.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AsignaturaService {
    private final AsignaturaDAO dao = new AsignaturaDAO();
    private final UsuarioDAO usuarios = new UsuarioDAO();
    private final EstudianteService academico = new EstudianteService();

    public record Resultado(boolean exito, String mensaje) {}
    public record MateriasDisponibles(List<MateriaTutor> materias, int sinCatalogo) {}

    public List<MateriaCatalogo> listar(Usuario actor, boolean inactivas) {
        exigirCoordinador(actor);
        return dao.listar(inactivas);
    }

    public Resultado guardar(Usuario actor, Long id, String codigo, String nombre) {
        exigirCoordinador(actor);
        String codigoLimpio = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (nombreLimpio.isEmpty() || nombreLimpio.length() > 100
                || (id == null && (codigoLimpio.isEmpty() || codigoLimpio.length() > 20))) {
            return new Resultado(false, "Escribe un código de hasta 20 caracteres y un nombre de hasta 100 caracteres.");
        }
        boolean exito = id == null ? dao.crear(codigoLimpio, nombreLimpio) : dao.renombrar(id, nombreLimpio);
        return new Resultado(exito, exito ? "Materia guardada." : "El código ya existe o la materia ya no está disponible.");
    }

    public Resultado cambiarEstado(Usuario actor, long id, boolean activo) {
        exigirCoordinador(actor);
        boolean exito = dao.cambiarEstado(id, activo);
        return new Resultado(exito, exito ? (activo ? "Materia reactivada." : "Materia desactivada.") : "La materia ya no existe.");
    }

    public MateriasDisponibles materiasTutor(Usuario actor, String cif) throws IOException, InterruptedException {
        exigirCoordinador(actor);
        usuarios.buscarPorCif(cif).filter(u -> "TUTOR".equals(u.getRol()))
                .orElseThrow(() -> new IllegalArgumentException("El tutor ya no está registrado."));
        EstudianteApiDTO estudiante = academico.buscar(cif)
                .orElseThrow(() -> new IllegalStateException("No se encontró al tutor en la API académica."));
        Map<String, MateriaCatalogo> catalogo = dao.listar(false).stream()
                .collect(Collectors.toMap(m -> m.codigo().toUpperCase(Locale.ROOT), m -> m));
        Set<Long> habilitadas = dao.habilitadas(cif);
        Map<Long, MateriaTutor> aptas = new LinkedHashMap<>();
        int sinCatalogo = 0;
        for (ClaseCursadaDTO clase : estudiante.clasesElegibles()) {
            if (clase.codigo() == null || !Double.isFinite(clase.nota()) || clase.nota() > 100) {
                continue;
            }
            MateriaCatalogo materia = catalogo.get(clase.codigo().trim().toUpperCase(Locale.ROOT));
            if (materia == null) {
                sinCatalogo++;
            } else {
                aptas.put(materia.id(), new MateriaTutor(materia.id(), materia.codigo(), materia.nombre(),
                        clase.nota(), habilitadas.contains(materia.id())));
            }
        }
        return new MateriasDisponibles(List.copyOf(aptas.values()), sinCatalogo);
    }

    public Resultado guardarMaterias(Usuario actor, String cif, Set<Long> seleccionadas) throws IOException, InterruptedException {
        MateriasDisponibles disponibles = materiasTutor(actor, cif);
        List<MateriaTutor> materias = disponibles.materias().stream()
                .filter(m -> seleccionadas.contains(m.asignaturaId())).toList();
        if (materias.size() != seleccionadas.size()) {
            return new Resultado(false, "Una materia no está aprobada en la API o ya no está activa en el catálogo.");
        }
        boolean exito = dao.guardarMaterias(cif, materias);
        return new Resultado(exito, exito ? "Materias habilitadas actualizadas." : "El tutor ya no está registrado.");
    }

    private void exigirCoordinador(Usuario actor) {
        if (actor == null || !"COORDINADOR".equals(actor.getRol())) {
            throw new IllegalStateException("Solo coordinación puede administrar las materias.");
        }
    }
}
