package uam.edu.ni.mentorsync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios", schema = "mentorsync_seguridad")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "carnet")
    private String cif;
    private String nombre;
    private String correo;
    private String rol;
    private String password;
    private boolean activo;
    private String carrera;
    private Double promedio;
    @Column(name = "horas_acumuladas")
    private Double horasAcumuladas;

    protected Usuario() {
    }

    public static Usuario crearTutor(EstudianteApiDTO estudiante, String password) {
        Usuario usuario = new Usuario();
        usuario.cif = estudiante.cif();
        usuario.password = password;
        usuario.horasAcumuladas = 0.0;
        usuario.habilitarComoTutor(estudiante);
        return usuario;
    }

    public static Usuario crearEstudiante(EstudianteApiDTO estudiante, String password) {
        Usuario usuario = crearTutor(estudiante, password);
        usuario.rol = "ESTUDIANTE";
        return usuario;
    }

    public void habilitarComoTutor(EstudianteApiDTO estudiante) {
        nombre = estudiante.nombre();
        correo = estudiante.correo();
        carrera = estudiante.carrera();
        promedio = Math.round(estudiante.calcularPromedioGeneral() * 100.0) / 100.0;
        rol = "TUTOR";
        activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    public String getPassword() {
        return password;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getCif() {
        return cif;
    }
}
