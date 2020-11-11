CREATE TEMPORARY TABLE _range7 (
    number INT NOT NULL PRIMARY KEY
);
INSERT INTO _range7 (number) VALUES (0), (1), (2), (3), (4), (5), (6);

INSERT INTO spatial_location (tissue_type_id, name, code)
SELECT id, IF(number=0, 'Unknown', CONCAT('Location ', number)), number
FROM tissue_type, _range7
WHERE name!='Unknown' OR number=0
ORDER BY id, number;

DROP TEMPORARY TABLE _range7;
