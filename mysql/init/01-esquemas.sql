-- Se ejecuta automaticamente la PRIMERA vez que se crea el volumen de MySQL.
-- Un esquema por microservicio: cada servicio es dueno de sus datos.
CREATE DATABASE IF NOT EXISTS grip_usuarios  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS grip_productos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS grip_ordenes   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- El usuario grip_app lo crea la imagen de MySQL (MYSQL_USER). Aqui solo se le dan permisos.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON grip_usuarios.*  TO 'grip_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON grip_productos.* TO 'grip_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON grip_ordenes.*   TO 'grip_app'@'%';
FLUSH PRIVILEGES;
