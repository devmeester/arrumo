-- Cold-room layout and the initial packing-board columns.
-- To change the layout later, add a new migration; never edit this one.
-- Names in quotes are shown to users, so they stay in Portuguese.

INSERT INTO zone (id, name, sort_order, column_labels) VALUES
  ('f1', 'Primeiro andar',  1, 'Rack 1;Rack 2;Rack 3;Rack 4;Rack 5;Rack 6;Rack 7;Rack 8;Rack 9'),
  ('f2', 'Segundo andar',   2, 'Rack 1;Rack 2;Rack 3;Rack 4;Rack 5;Rack 6;Rack 7;Rack 8;Rack 9'),
  ('fg', 'Produto acabado', 3, 'Rack 1;Rack 2;Rack 3;Rack 4;Rack 5;Rack 6;Rack 7;Rack 8;Rack 9');

-- Raw material: 9 racks x 4 positions; floor 1 has 2 slots (a, b), floor 2 has 3 (a, b, c).
-- Code R{rack}{floor}{position}{slot}: R514a, R521c. Position 4 at the top, slot a first.
INSERT INTO location (code, zone_id, group_label, row_no, col_no)
SELECT format('R%s%s%s%s', rack, f.floor, pos, chr(96 + slot)),
       'f' || f.floor,
       f.floor::text || pos::text,
       (4 - pos) * f.slots + slot,
       rack
FROM (VALUES (1, 2), (2, 3)) AS f(floor, slots)
CROSS JOIN generate_series(1, 9) AS rack
CROSS JOIN generate_series(1, 4) AS pos
CROSS JOIN LATERAL generate_series(1, f.slots) AS slot;

-- Finished goods: 9 racks x 2 levels x 4 pallets. Code R{rack}{level}{pallet}: R324.
-- Level 2 at the top, pallets 4..1 from top to bottom.
INSERT INTO location (code, zone_id, group_label, row_no, col_no)
SELECT format('R%s%s%s', rack, level, slot), 'fg', level::text, (2 - level) * 4 + (5 - slot), rack
FROM generate_series(1, 9) AS rack,
     generate_series(1, 2) AS level,
     generate_series(1, 4) AS slot;

INSERT INTO packing_column (name, sort_order, daily_list, reports) VALUES
  ('Requisições',             1, FALSE, FALSE),
  ('Backlog próximo dia',     2, TRUE,  FALSE),
  ('Em progresso',            3, FALSE, FALSE),
  ('Terminados e reportados', 4, FALSE, TRUE),
  ('Outros',                  5, FALSE, FALSE),
  ('Flow',                    6, FALSE, FALSE);
