package uam.edu.ni.mentorsync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios", schema = "mentorsync_seguridad")
public class Usuario {
    @Id
    private Long id;
    @Column(name = "carnet")
    private String cif;
    private String nombre;
    private String rol;
    private String password;
    private boolean activo;
    private String carrera;
    private Double promedio;
    @Column(name = "horas_acumuladas")
    private Double horasAcumuladas;

    protected Usuario() {
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
    return cif; }
}
