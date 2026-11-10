-- DDL COMPLETO: crea la base taxidb y las 3 tablas
-- Generado a partir de los archivos de database/ddl/ (mismo orden).

-- DDL 00: base de datos
CREATE DATABASE IF NOT EXISTS taxidb CHARACTER SET utf8mb4;

-- DDL 01: tabla taxistas
-- Dos banderas independientes controladas por el Superadmin:
--   estado_habilitacion (PENDIENTE -> HABILITADO)  y  cuenta_activa (0/1).
-- La contrasena se guarda solo como hash BCrypt.
USE taxidb;

CREATE TABLE IF NOT EXISTS taxistas (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    nombres                   VARCHAR(80)   NOT NULL,
    apellidos                 VARCHAR(80)   NOT NULL,
    tipo_documento            VARCHAR(25)   NOT NULL,   -- DNI | PASAPORTE | CARNET_EXTRANJERIA
    numero_documento          VARCHAR(20)   NOT NULL,
    fecha_nacimiento          DATE          NOT NULL,
    correo                    VARCHAR(120)  NOT NULL,
    telefono                  VARCHAR(20)   NOT NULL,
    password_hash             VARCHAR(100)  NOT NULL,   -- BCrypt, nunca texto plano
    domicilio                 VARCHAR(200)  NOT NULL,
    foto_url                  VARCHAR(500)  NULL,       -- URL en Cloudinary
    placa_vehiculo            VARCHAR(10)   NOT NULL,
    foto_vehiculo_url         VARCHAR(500)  NULL,       -- URL en Cloudinary
    estado_habilitacion       VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',   -- PENDIENTE | HABILITADO
    cuenta_activa             TINYINT(1)    NOT NULL DEFAULT 1,
    valoracion_promedio       DECIMAL(3,2)  NOT NULL DEFAULT 0.00,
    total_valoraciones        INT           NOT NULL DEFAULT 0,
    latitud                   DOUBLE        NULL,
    longitud                  DOUBLE        NULL,
    ubicacion_actualizada_en  DATETIME(6)   NULL,
    disponibilidad            VARCHAR(15)   NOT NULL DEFAULT 'DISPONIBLE',  -- DISPONIBLE | OCUPADO
    fecha_registro            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_taxista_correo    (correo),
    UNIQUE KEY uk_taxista_documento (tipo_documento, numero_documento),
    UNIQUE KEY uk_taxista_placa     (placa_vehiculo),
    KEY idx_taxista_busqueda (estado_habilitacion, cuenta_activa, disponibilidad)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- DDL 02: tabla servicios_taxi  (requiere haber ejecutado 01_taxistas.sql)
-- Flujo: SOLICITADO -> ASIGNADO -> EN_CAMINO -> EN_TRASLADO -> FINALIZADO
-- taxista_id es NULL mientras el servicio esta SOLICITADO.
USE taxidb;

CREATE TABLE IF NOT EXISTS servicios_taxi (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    taxista_id             BIGINT         NULL,
    cliente_id             VARCHAR(60)    NULL,
    nombre_cliente         VARCHAR(120)   NOT NULL,
    telefono_cliente       VARCHAR(20)    NULL,
    direccion_origen       VARCHAR(200)   NOT NULL,
    latitud_origen         DOUBLE         NOT NULL,
    longitud_origen        DOUBLE         NOT NULL,
    direccion_destino      VARCHAR(200)   NOT NULL,
    latitud_destino        DOUBLE         NULL,
    longitud_destino       DOUBLE         NULL,
    tarifa                 DECIMAL(10,2)  NULL,
    estado                 VARCHAR(20)    NOT NULL DEFAULT 'SOLICITADO',
    codigo_qr              VARCHAR(40)    NOT NULL,
    fecha_solicitud        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    fecha_asignacion       DATETIME(6)    NULL,
    fecha_inicio_traslado  DATETIME(6)    NULL,
    fecha_finalizacion     DATETIME(6)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_servicio_codigo_qr (codigo_qr),
    KEY idx_servicio_estado (estado),
    CONSTRAINT fk_servicio_taxista FOREIGN KEY (taxista_id) REFERENCES taxistas (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- DDL 03: tabla valoraciones  (requiere 01_taxistas.sql y 02_servicios_taxi.sql)
-- Una valoracion por servicio finalizado (uk_valoracion_servicio).
-- El promedio y el contador se mantienen en la tabla taxistas.
USE taxidb;

CREATE TABLE IF NOT EXISTS valoraciones (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    taxista_id      BIGINT        NOT NULL,
    servicio_id     BIGINT        NULL,
    puntuacion      INT           NOT NULL,
    comentario      VARCHAR(300)  NULL,
    fecha_registro  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_valoracion_servicio (servicio_id),
    CONSTRAINT fk_valoracion_taxista  FOREIGN KEY (taxista_id)  REFERENCES taxistas (id),
    CONSTRAINT fk_valoracion_servicio FOREIGN KEY (servicio_id) REFERENCES servicios_taxi (id),
    CONSTRAINT chk_puntuacion CHECK (puntuacion BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
