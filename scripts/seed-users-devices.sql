-- Local development seed: users 1-10, each owning the device with the same id.
--
-- Not a Flyway migration on purpose, so it never reaches another environment.
-- Safe to re-run: rows with ids 1-10 are overwritten back to these values.
--
--   docker exec -i energy-tracker-mysql mysql -uenergy -penergy energy_tracker < scripts/seed-users-devices.sql

START TRANSACTION;

INSERT INTO `user` (`id`, `name`, `surname`, `email`, `address`, `alerting`, `energy_alerting_threshold`) VALUES
    (1,  'Ana',      'Souza',     'ana.souza@seed.energy-tracker.dev',      'Rua das Flores, 120 - Sao Paulo/SP',          1, 1500),
    (2,  'Bruno',    'Lima',      'bruno.lima@seed.energy-tracker.dev',     'Av. Brasil, 845 - Rio de Janeiro/RJ',         0, 0),
    (3,  'Carla',    'Mendes',    'carla.mendes@seed.energy-tracker.dev',   'Rua XV de Novembro, 32 - Curitiba/PR',        1, 2000),
    (4,  'Diego',    'Alves',     'diego.alves@seed.energy-tracker.dev',    'Rua da Bahia, 1500 - Belo Horizonte/MG',      1, 1200),
    (5,  'Eduarda',  'Rocha',     'eduarda.rocha@seed.energy-tracker.dev',  'Av. Boa Viagem, 410 - Recife/PE',             0, 0),
    (6,  'Felipe',   'Carvalho',  'felipe.carvalho@seed.energy-tracker.dev','Rua dos Andradas, 77 - Porto Alegre/RS',      1, 1800),
    (7,  'Gabriela', 'Ferreira',  'gabriela.ferreira@seed.energy-tracker.dev','Av. Beira Mar, 2300 - Fortaleza/CE',        1, 2500),
    (8,  'Henrique', 'Barbosa',   'henrique.barbosa@seed.energy-tracker.dev','SQS 308 Bloco C - Brasilia/DF',              0, 0),
    (9,  'Isabela',  'Ribeiro',   'isabela.ribeiro@seed.energy-tracker.dev','Rua Chile, 18 - Salvador/BA',                 1, 1000),
    (10, 'Joao',     'Pereira',   'joao.pereira@seed.energy-tracker.dev',   'Av. Goias, 560 - Goiania/GO',                 1, 3000)
AS new
ON DUPLICATE KEY UPDATE
    `name`                      = new.`name`,
    `surname`                   = new.`surname`,
    `email`                     = new.`email`,
    `address`                   = new.`address`,
    `alerting`                  = new.`alerting`,
    `energy_alerting_threshold` = new.`energy_alerting_threshold`;

-- type must match the DeviceType enum: SPEAKER, GPS, CAMERA, THERMOSTAT, LIGHT, LOCK, DOORBELL
INSERT INTO `device` (`id`, `name`, `type`, `location`, `user_id`) VALUES
    (1,  'Termostato da sala',      'THERMOSTAT', 'Sala de estar',  1),
    (2,  'Lampada do quarto',       'LIGHT',      'Quarto',         2),
    (3,  'Camera do portao',        'CAMERA',     'Entrada',        3),
    (4,  'Caixa de som da cozinha', 'SPEAKER',    'Cozinha',        4),
    (5,  'Fechadura da porta',      'LOCK',       'Porta da frente',5),
    (6,  'Campainha',               'DOORBELL',   'Entrada',        6),
    (7,  'Rastreador do carro',     'GPS',        'Garagem',        7),
    (8,  'Lampada do escritorio',   'LIGHT',      'Escritorio',     8),
    (9,  'Termostato do quarto',    'THERMOSTAT', 'Quarto',         9),
    (10, 'Camera do quintal',       'CAMERA',     'Quintal',        10)
AS new
ON DUPLICATE KEY UPDATE
    `name`     = new.`name`,
    `type`     = new.`type`,
    `location` = new.`location`,
    `user_id`  = new.`user_id`;

COMMIT;
