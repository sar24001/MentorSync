package uam.edu.ni.mentorsync.dao;

import org.hibernate.Session;
import uam.edu.ni.mentorsync.model.MateriaCatalogo;
import uam.edu.ni.mentorsync.model.MateriaTutor;
import uam.edu.ni.mentorsync.util.HibernateUtil;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AsignaturaDAO {
    public List<MateriaCatalogo> listar(boolean incluirInactivas) {
        try (var em = HibernateUtil.getEntityManager()) {
            return em.unwrap(Session.class).createNativeQuery("""
                    select id, codigo, nombre, activo from mentorsync_academico.asignaturas
                    where :inactivas = true or activo = true
                    order by nombre, codigo
                    """, Object[].class).setParameter("inactivas", incluirInactivas).getResultList().stream()
                    .map(f -> new MateriaCatalogo(((Number) f[0]).longValue(), (String) f[1], (String) f[2], (Boolean) f[3]))
                    .toList();
        }
    }

    public boolean crear(String codigo, String nombre) {
        return cambiar(s -> s.createNativeMutationQuery("""
                insert into mentorsync_academico.asignaturas(codigo, nombre, activo)
                select :codigo, :nombre, true
                where not exists (select 1 from mentorsync_academico.asignaturas where upper(codigo) = :codigo)
                on conflict (codigo) do nothing
                """).setParameter("codigo", codigo).setParameter("nombre", nombre).executeUpdate() == 1);
    }

    public boolean renombrar(long id, String nombre) {
        return cambiar(s -> s.createNativeMutationQuery("""
                update mentorsync_academico.asignaturas set nombre = :nombre where id = :id
                """).setParameter("id", id).setParameter("nombre", nombre).executeUpdate() == 1);
    }

    public boolean cambiarEstado(long id, boolean activo) {
        return cambiar(s -> s.createNativeMutationQuery("""
                update mentorsync_academico.asignaturas set activo = :activo where id = :id
                """).setParameter("id", id).setParameter("activo", activo).executeUpdate() == 1);
    }

    public Set<Long> habilitadas(String cif) {
        try (var em = HibernateUtil.getEntityManager()) {
            return em.unwrap(Session.class).createNativeQuery("""
                    select ta.asignatura_id from mentorsync_academico.tutor_asignaturas ta
                    join mentorsync_seguridad.usuarios u on u.id = ta.tutor_id
                    where u.carnet = :cif and u.rol = 'TUTOR'
                    """, Long.class).setParameter("cif", cif).getResultStream().collect(Collectors.toSet());
        }
    }

    public boolean guardarMaterias(String cif, List<MateriaTutor> materias) {
        return cambiar(s -> {
            Long tutor = s.createNativeQuery("""
                    select id from mentorsync_seguridad.usuarios where carnet = :cif and rol = 'TUTOR' for update
                    """, Long.class).setParameter("cif", cif).getSingleResultOrNull();
            if (tutor == null) {
                return false;
            }
            s.createNativeMutationQuery("delete from mentorsync_academico.tutor_asignaturas where tutor_id = :tutor")
                    .setParameter("tutor", tutor).executeUpdate();
            for (MateriaTutor materia : materias) {
                int guardadas = s.createNativeMutationQuery("""
                        insert into mentorsync_academico.tutor_asignaturas(tutor_id, asignatura_id, nota_aprobacion)
                        select :tutor, id, :nota from mentorsync_academico.asignaturas
                        where id = :asignatura and activo = true
                        """).setParameter("tutor", tutor).setParameter("asignatura", materia.asignaturaId())
                        .setParameter("nota", materia.nota()).executeUpdate();
                if (guardadas != 1) {
                    throw new IllegalStateException("Una materia dejó de estar activa. Actualiza el catálogo e inténtalo de nuevo.");
                }
            }
            return true;
        });
    }

    private boolean cambiar(Function<Session, Boolean> operacion) {
        try (var em = HibernateUtil.getEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();
            try {
                boolean exito = operacion.apply(em.unwrap(Session.class));
                tx.commit();
                return exito;
            } catch (RuntimeException error) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw error;
            }
        }
    }
}
