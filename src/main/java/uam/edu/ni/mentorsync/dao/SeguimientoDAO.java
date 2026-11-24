package uam.edu.ni.mentorsync.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import uam.edu.ni.mentorsync.model.Tutoria;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.util.List;

public class SeguimientoDAO {

    public List<Tutoria> listarTutoriasEvaluables() {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            return em.createQuery(
                    "FROM Tutoria t WHERE t.estado IN ('REALIZADA', 'VALIDADA') ORDER BY t.fecha DESC",
                    Tutoria.class
            ).getResultList();
        } finally {
            em.close();
        }
    }

    public List<Tutoria> listarTutoriasValidadasPorTutor(Long tutorId) {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            return em.createQuery(
                    "FROM Tutoria t WHERE t.tutor.id = :tutorId AND t.estado = 'VALIDADA' ORDER BY t.fecha ASC",
                    Tutoria.class
            ).setParameter("tutorId", tutorId).getResultList();
        } finally {
            em.close();
        }
    }

    public List<Usuario> listarTutoresActivos() {
        EntityManager em = HibernateUtil.getEntityManager();
        try {
            return em.createQuery(
                    "FROM Usuario u WHERE u.rol = 'TUTOR' AND u.activo = true ORDER BY u.nombre ASC",
                    Usuario.class
            ).getResultList();
        } finally {
            em.close();
        }
    }

    public void actualizarCalificaciones(Long tutoriaId, Double notaInicial, Double notaFinal, String observaciones) {
        EntityManager em = HibernateUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Tutoria tutoria = em.find(Tutoria.class, tutoriaId);
            if (tutoria != null) {
                tutoria.setNotaInicial(notaInicial);
                tutoria.setNotaFinal(notaFinal);
                tutoria.setObservaciones(observaciones);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
