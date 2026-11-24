package uam.edu.ni.mentorsync.dao;

import uam.edu.ni.mentorsync.model.Tutoria;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.util.List;

public class SeguimientoDAO {
    public List<Tutoria> listarTutoriasEvaluables() {
        try (var em = HibernateUtil.getEntityManager()) {
            return em.createQuery("""
                    from Tutoria t
                    where t.estado in ('REALIZADA', 'VALIDADA')
                    order by t.fecha desc, t.id desc
                    """, Tutoria.class).getResultList();
        }
    }

    public List<Tutoria> listarTutoriasValidadasPorTutor(Long tutorId) {
        try (var em = HibernateUtil.getEntityManager()) {
            return em.createQuery("""
                    from Tutoria t
                    where t.tutor.id = :tutorId and t.estado = 'VALIDADA'
                      and (t.grupoId is null or not exists (
                          select 1 from Tutoria miembro
                          where miembro.grupoId = t.grupoId and miembro.estado = 'VALIDADA' and miembro.id < t.id
                      ))
                    order by t.fecha, t.id
                    """, Tutoria.class).setParameter("tutorId", tutorId).getResultList();
        }
    }

    public List<Usuario> listarTutoresActivos() {
        try (var em = HibernateUtil.getEntityManager()) {
            return em.createQuery("""
                    from Usuario u where u.rol = 'TUTOR' and u.activo = true
                    order by u.nombre, u.id
                    """, Usuario.class).getResultList();
        }
    }

    public void actualizarCalificaciones(Long tutoriaId, Double notaInicial, Double notaFinal, String observaciones) {
        try (var em = HibernateUtil.getEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                int filas = em.createQuery("""
                        update Tutoria t
                        set t.notaInicial = :inicial, t.notaFinal = :final, t.observaciones = :observaciones
                        where t.id = :id and t.estado in ('REALIZADA', 'VALIDADA')
                        """)
                        .setParameter("inicial", notaInicial)
                        .setParameter("final", notaFinal)
                        .setParameter("observaciones", observaciones)
                        .setParameter("id", tutoriaId).executeUpdate();
                if (filas != 1) {
                    throw new IllegalStateException("La tutoría ya no está disponible para evaluación.");
                }
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }
}
