package uam.edu.ni.mentorsync.dao;

import org.hibernate.Session;
import org.hibernate.Transaction; // en rama josue lo marca como que no tengo la libreria
import uam.edu.ni.mentorsync.model.Tutoria;
import uam.edu.ni.mentorsync.model.Usuario;
import uam.edu.ni.mentorsync.util.HibernateUtil;

import java.util.List;

public class SeguimientoDAO {

    public List<Tutoria> listarTutoriasEvaluables() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Tutoria t WHERE t.estado IN ('REALIZADA', 'VALIDADA') ORDER BY t.fecha DESC",
                    Tutoria.class
            ).list();
        }
    }

    public List<Tutoria> listarTutoriasValidadasPorTutor(Long tutorId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Tutoria t WHERE t.tutor.id = :tutorId AND t.estado = 'VALIDADA' ORDER BY t.fecha ASC",
                    Tutoria.class
            ).setParameter("tutorId", tutorId).list();
        }
    }

    public List<Usuario> listarTutoresActivos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Usuario u WHERE u.rol = 'TUTOR' AND u.activo = true ORDER BY u.nombre ASC",
                    Usuario.class
            ).list();
        }
    }

    public void actualizarCalificaciones(Long tutoriaId, Double notaInicial, Double notaFinal, String observaciones) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Tutoria tutoria = session.find(Tutoria.class, tutoriaId);
            if (tutoria != null) {
                tutoria.setNotaInicial(notaInicial);
                tutoria.setNotaFinal(notaFinal);
                tutoria.setObservaciones(observaciones);
                session.merge(tutoria);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        }
    }
}