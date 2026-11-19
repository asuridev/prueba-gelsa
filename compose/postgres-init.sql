-- Una base y un usuario por servicio, como si cada uno tuviera su propio servidor.
-- Solo se ejecuta la primera vez que arranca el volumen (docker-entrypoint-initdb.d).
-- "order" va entre comillas porque es palabra reservada en SQL.

CREATE ROLE "order" LOGIN PASSWORD 'changeme';
CREATE DATABASE "order" OWNER "order";

CREATE ROLE payment LOGIN PASSWORD 'changeme';
CREATE DATABASE payment OWNER payment;

CREATE ROLE notifications LOGIN PASSWORD 'changeme';
CREATE DATABASE notifications OWNER notifications;
