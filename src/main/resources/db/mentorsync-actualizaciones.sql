ALTER TABLE mentorsync_academico.asignaturas
    ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS mentorsync_academico.tutor_asignaturas (
    tutor_id BIGINT NOT NULL REFERENCES mentorsync_seguridad.usuarios(id) ON DELETE CASCADE,
    asignatura_id BIGINT NOT NULL REFERENCES mentorsync_academico.asignaturas(id) ON DELETE RESTRICT,
    nota_aprobacion NUMERIC(5,2) NOT NULL CHECK (nota_aprobacion BETWEEN 85 AND 100),
    PRIMARY KEY (tutor_id, asignatura_id)
);

ALTER TABLE mentorsync_academico.tutorias
    ADD COLUMN IF NOT EXISTS calificacion_tutor INTEGER,
    ADD COLUMN IF NOT EXISTS comentario_estudiante VARCHAR(255),
    ADD COLUMN IF NOT EXISTS motivo_cancelacion VARCHAR(255),
    ADD COLUMN IF NOT EXISTS confirmada_estudiante BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS modalidad VARCHAR(12) NOT NULL DEFAULT 'PRESENCIAL'
        CHECK (modalidad IN ('PRESENCIAL', 'VIRTUAL')),
    ADD COLUMN IF NOT EXISTS grupo_id UUID;

CREATE INDEX IF NOT EXISTS idx_tutorias_grupo
    ON mentorsync_academico.tutorias (grupo_id) WHERE grupo_id IS NOT NULL;

ALTER TABLE mentorsync_seguridad.usuarios
    ADD COLUMN IF NOT EXISTS recuperacion_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recuperacion_expira TIMESTAMPTZ;
