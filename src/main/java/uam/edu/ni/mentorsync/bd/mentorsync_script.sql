CREATE DATABASE mentorsync_db;

-- Creación de los esquemas
CREATE SCHEMA mentorsync_seguridad;
CREATE SCHEMA mentorsync_academico;

-- Tabla de usuarios
CREATE TABLE IF NOT EXISTS mentorsync_seguridad.usuarios (
                                                             id BIGSERIAL PRIMARY KEY,
                                                             carnet VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    correo VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL, -- 'COORDINADOR', 'TUTOR', 'ESTUDIANTE'
    carrera VARCHAR(80),
    promedio DOUBLE PRECISION,
    horas_acumuladas DOUBLE PRECISION DEFAULT 0.0,
    activo BOOLEAN DEFAULT TRUE
    );

-- Tabla de las asignaturas
CREATE TABLE IF NOT EXISTS mentorsync_academico.asignaturas (
                                                                id BIGSERIAL PRIMARY KEY,
                                                                codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL
    );

-- Tabla de tutorías
CREATE TABLE IF NOT EXISTS mentorsync_academico.tutorias (
                                                             id BIGSERIAL PRIMARY KEY,
                                                             tutor_id BIGINT NOT NULL,
                                                             estudiante_id BIGINT NOT NULL,
                                                             asignatura_id BIGINT NOT NULL,
                                                             fecha DATE NOT NULL,
                                                             hora_inicio TIME NOT NULL,
                                                             hora_fin TIME NOT NULL,
                                                             estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA', -- 'PROGRAMADA', 'REALIZADA', 'VALIDADA', 'CANCELADA'
    nota_inicial DOUBLE PRECISION,
    nota_final DOUBLE PRECISION,
    observaciones VARCHAR(255),

    -- Llaves foráneas cruzadas entre schemas
    CONSTRAINT fk_tutoria_tutor
    FOREIGN KEY (tutor_id) REFERENCES mentorsync_seguridad.usuarios(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tutoria_estudiante
    FOREIGN KEY (estudiante_id) REFERENCES mentorsync_seguridad.usuarios(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tutoria_asignatura
    FOREIGN KEY (asignatura_id) REFERENCES mentorsync_academico.asignaturas(id) ON DELETE RESTRICT
    );

-- Inserciones de datos en las tablas
INSERT INTO mentorsync_seguridad.usuarios (carnet, nombre, correo, password, rol, carrera, promedio, horas_acumuladas, activo) VALUES
                                                                                                                                   ('10000001', 'Ing. Elena Martínez', 'elena.martinez@uamv.edu.ni', '1234', 'COORDINADOR', 'Ingeniería en Sistemas', NULL, 0.0, TRUE),
                                                                                                                                   ('20220101', 'Carlos Mendoza', 'carlos.mendoza@uamv.edu.ni', '1234', 'TUTOR', 'Ingeniería en Sistemas', 91.5, 4.0, TRUE),
                                                                                                                                   ('20220102', 'Sofía Castellón', 'sofia.castellon@uamv.edu.ni', '1234', 'TUTOR', 'Ingeniería Industrial', 88.0, 2.0, TRUE),
                                                                                                                                   ('20240201', 'Diego Morales', 'diego.morales@uamv.edu.ni', '1234', 'ESTUDIANTE', 'Ingeniería en Sistemas', 72.0, 0.0, TRUE),
                                                                                                                                   ('20240202', 'Valeria Gómez', 'valeria.gomez@uamv.edu.ni', '1234', 'ESTUDIANTE', 'Ingeniería Civil', 68.5, 0.0, TRUE);

INSERT INTO mentorsync_academico.asignaturas (codigo, nombre) VALUES
                                                                  ('SIS-201', 'Programación Orientada a Objetos'),
                                                                  ('MAT-101', 'Cálculo I'),
                                                                  ('FIS-305', 'Física Aplicada'),
                                                                  ('MAT-103', 'Álgebra Lineal');

INSERT INTO mentorsync_academico.tutorias (tutor_id, estudiante_id, asignatura_id, fecha, hora_inicio, hora_fin, estado, nota_inicial, nota_final, observaciones) VALUES
                                                                                                                                                                      (2, 4, 1, '2026-11-18', '14:00:00', '16:00:00', 'VALIDADA', 58.0, 84.0, 'Excelente avance en herencia y polimorfismo'),
                                                                                                                                                                      (3, 5, 2, '2026-11-20', '10:00:00', '12:00:00', 'REALIZADA', 62.0, NULL, 'Pendiente de aprobación de horas por coordinación'),
                                                                                                                                                                      (2, 5, 3, '2026-11-25', '15:00:00', '17:00:00', 'PROGRAMADA', NULL, NULL, 'Repaso previo al examen parcial');
ALTER TABLE mentorsync_academico.tutorias
    ADD COLUMN calificacion_tutor INTEGER,
ADD COLUMN comentario_estudiante VARCHAR(255),
ADD COLUMN motivo_cancelacion VARCHAR(255),
ADD COLUMN confirmada_estudiante BOOLEAN DEFAULT FALSE;