ALTER TABLE INVNTRY ADD COLUMN IAT DATETIME DEFAULT NULL;

UPDATE INVNTRY AS I LEFT JOIN (SELECT MIN(T) AS UT, KID, MID FROM TRANSACTION GROUP BY KID, MID) AS T ON I.KID = T.KID AND I.MID = T.MID
SET I.IAT = T.UT;