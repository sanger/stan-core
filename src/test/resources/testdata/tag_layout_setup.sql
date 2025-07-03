INSERT INTO tag_layout (name) VALUES ('layout1');

SET @layout=LAST_INSERT_ID();

INSERT INTO reagent_plate_type_tag_layout (plate_type, tag_layout_id)
VALUES ('Dual Index TS Set A', @layout)
;