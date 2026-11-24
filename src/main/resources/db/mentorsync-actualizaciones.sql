ALTER TABLE mentorsync_academico.asignaturas
    ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS mentorsync_academico.tutor_asignaturas (
    tutor_id BIGINT NOT NULL REFERENCES mentorsync_seguridad.usuarios(id) ON DELETE CASCADE,
    asignatura_id BIGINT NOT NULL REFERENCES mentorsync_academico.asignaturas(id) ON DELETE RESTRICT,
    nota_aprobacion NUMERIC(5,2) NOT NULL CHECK (nota_aprobacion BETWEEN 85 AND 100),
    PRIMARY KEY (tutor_id, asignatura_id)
);
