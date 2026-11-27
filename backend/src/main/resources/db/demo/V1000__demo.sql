-- Demo data (dev profile only). Lot and product codes are made up; only the date inside the lot code is realistic.
-- Products and weights of some lots were assigned just for testing.
INSERT INTO product (code, description) VALUES
  ('900103','BAC DUC C/VID - G.MORHUA: P. ECO REST 200/300'),
  ('900010','BAC DUC C/VID - G.MORHUA: P. LONGA JUM AB'),
  ('900034','BAC DUC C/VID - G.MORHUA: RABOS PEQ (FRJ)');
INSERT INTO lot (code, product_id, weight_kg, production_date, notes)
SELECT v.code, p.id, v.kg, to_date(substr(v.code,2,6),'YYMMDD'), v.notes FROM (VALUES
  ('L250305419246','900103',264,''),
  ('L250305874642','900103',265,''),
  ('L250305114543','900103',328,''),
  ('L250506586529','900103',283,''),
  ('L250513898748','900010',310,''),
  ('L250602836819','900103',261,''),
  ('L250808321131','900034',295,''),
  ('L250925371447','900034',251,''),
  ('L250925240971','900034',256,''),
  ('L251028855838','900103',265,''),
  ('L251126421674','900034',320,''),
  ('L251205716322','900103',309,''),
  ('L251220105031','900103',272,''),
  ('L260114244776','900034',310,''),
  ('L260212487231','900010',324,''),
  ('L260312550604','900034',264,''),
  ('L260312568888','900034',264,''),
  ('L260326131282','900010',337,''),
  ('L260330288371','900034',313,''),
  ('L260511399503','900103',242,''),
  ('L260518826187','900010',320,''),
  ('L260521785077','900010',253,''),
  ('L260619586583','900103',257,''),
  ('L260624643317','900034',275,''),
  ('L260626782149','900010',245,''),
  ('L260630288264','900034',322,''),
  ('L260701740889','900010',233,''),
  ('L260701532159','900010',296,''),
  ('L260707745021','900010',270,''),
  ('L260709854452','900103',339,''),
  ('L260713830196','900010',319,''),
  ('L260716748110','900010',250,''),
  ('L260720428901','900034',317,''),
  ('L260721682646','900103',280,''),
  ('L260728740082','900010',304,''),
  ('L260730440188','900103',237,''),
  ('L260806722572','900010',248,''),
  ('L260807605844','900034',260,''),
  ('L260807817462','900034',323,''),
  ('L260807832560','900034',324,''),
  ('L260811611047','900103',329,''),
  ('L260811844690','900103',330,''),
  ('L260811513791','900103',331,''),
  ('L260814812391','900103',302,''),
  ('L260814641566','900103',303,''),
  ('L260821938783','900010',311,''),
  ('L260825415323','900034',306,''),
  ('L260825924531','900034',307,''),
  ('L260831233706','900034',338,''),
  ('L260831559959','900034',233,''),
  ('L260831109848','900034',234,''),
  ('L260902328849','900010',310,''),
  ('L260908247675','900010',322,''),
  ('L260908878201','900010',250,''),
  ('L260909583257','900034',256,''),
  ('L260910556329','900103',241,''),
  ('L260910271456','900103',266,''),
  ('L260915637770','900034',250,''),
  ('L261007814182','900010',233,''),
  ('L261009403916','900103',319,''),
  ('L261009360292','900103',236,''),
  ('L261012544806','900103',299,''),
  ('L261012865473','900103',322,''),
  ('L261013759226','900010',238,''),
  ('L261014553041','900034',329,''),
  ('L261015725132','900103',330,''),
  ('L261016736758','900010',232,''),
  ('L261020278474','900034',267,''),
  ('L261020800117','900034',268,''),
  ('L261020785565','900034',270,''),
  ('L261020932162','900034',271,''),
  ('L261020876582','900034',272,''),
  ('L261020686346','900034',273,''),
  ('L261020345776','900034',295,''),
  ('L261020892855','900034',296,''),
  ('L261020395126','900034',314,''),
  ('L261020762210','900034',315,''),
  ('L261021490794','900103',302,''),
  ('L261022527508','900010',303,'Marcado a laranja na app antiga'),
  ('L261024635446','900103',332,''),
  ('L261024644090','900103',333,''),
  ('L261024322099','900103',335,''),
  ('L261024636258','900103',337,''),
  ('L261027482295','900103',277,''),
  ('L261028249859','900010',335,''),
  ('L261028115064','900010',273,''),
  ('L261029105249','900034',240,''),
  ('L261029165614','900034',333,''),
  ('L261029575722','900034',242,''),
  ('L261029820099','900034',244,''),
  ('L261029446387','900034',246,''),
  ('L261029136383','900034',248,''),
  ('L261029175043','900034',250,''),
  ('L261029196674','900034',304,''),
  ('L261030123509','900103',293,''),
  ('L261030890699','900103',332,''),
  ('L261030472617','900103',333,''),
  ('L261030520727','900103',335,''),
  ('L261102111029','900103',271,''),
  ('L261102992974','900103',272,''),
  ('L261102589906','900103',274,''),
  ('L261102550981','900103',299,''),
  ('L261102113526','900103',289,''),
  ('L261103523160','900010',248,''),
  ('L261103372104','900010',249,''),
  ('L261103703437','900010',250,''),
  ('L261103165952','900010',237,''),
  ('L261104825060','900034',292,''),
  ('L261104818660','900034',295,''),
  ('L261104502101','900034',296,''),
  ('L261104585411','900034',299,''),
  ('L261104950111','900034',324,''),
  ('L261104221445','900034',325,''),
  ('L261107502738','900034',316,''),
  ('L261109123047','900010',317,''),
  ('L261109779636','900010',318,''),
  ('L261109221313','900010',319,''),
  ('L261109989789','900010',320,''),
  ('L261109520163','900010',233,''),
  ('L261109600915','900010',235,''),
  ('L261109442351','900010',265,''),
  ('L261109168934','900010',266,''),
  ('L261109590537','900010',270,''),
  ('L261111526778','900103',245,''),
  ('L261111162514','900103',301,''),
  ('L261113652501','900034',339,''),
  ('L261118339889','900034',310,''),
  ('L261118680826','900034',331,''),
  ('L261118731687','900034',314,''),
  ('L261118841825','900034',321,''),
  ('L261118358498','900034',311,''),
  ('L261118990795','900034',336,''),
  ('L261118648286','900034',295,''),
  ('L261118222007','900034',316,''),
  ('L261118847055','900034',315,''),
  ('L261118813705','900034',305,''),
  ('L261118905221','900034',295,''),
  ('L261118142115','900034',320,''),
  ('L261118342268','900034',305,''),
  ('L261118747196','900034',302,''),
  ('L261118938259','900034',297,''),
  ('L261118992162','900034',286,''),
  ('L261118842534','900034',157,''),
  ('L261121311697','900010',235,''),
  ('L261121523948','900010',244,''),
  ('L261121247643','900103',298,''),
  ('L261121962780','900103',312,''),
  ('L261121611587','900103',305,''),
  ('L261121993817','900103',307,''),
  ('L261121229196','900103',297,''),
  ('L261121555667','900103',296,''),
  ('L261121290066','900103',299,''),
  ('L261121344319','900103',296,''),
  ('L261121691275','900103',312,''),
  ('L261121468240','900103',306,''),
  ('L261121297804','900103',306,''),
  ('L261121804447','900103',311,''),
  ('L261121737767','900103',314,''),
  ('L261121261613','900103',317,''),
  ('L261121808897','900103',316,''),
  ('L261121325678','900103',313,''),
  ('L261121478003','900103',300,''),
  ('L261121794856','900103',271,'')
) AS v(code, prod, kg, notes) JOIN product p ON p.code = v.prod;
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260518826187'), updated_by='demo' WHERE code='R114a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260312568888'), updated_by='demo' WHERE code='R214a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L251126421674'), updated_by='demo' WHERE code='R314a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L251205716322'), updated_by='demo' WHERE code='R414a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261022527508'), updated_by='demo' WHERE code='R514a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250602836819'), updated_by='demo' WHERE code='R614a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250925371447'), updated_by='demo' WHERE code='R714a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260619586583'), updated_by='demo' WHERE code='R814a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260521785077'), updated_by='demo' WHERE code='R114b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L251220105031'), updated_by='demo' WHERE code='R214b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250808321131'), updated_by='demo' WHERE code='R314b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261014553041'), updated_by='demo' WHERE code='R414b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029446387'), updated_by='demo' WHERE code='R514b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250506586529'), updated_by='demo' WHERE code='R614b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250925240971'), updated_by='demo' WHERE code='R714b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260626782149'), updated_by='demo' WHERE code='R814b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261009403916'), updated_by='demo' WHERE code='R113a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L251028855838'), updated_by='demo' WHERE code='R213a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260713830196'), updated_by='demo' WHERE code='R313a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020892855'), updated_by='demo' WHERE code='R413a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029820099'), updated_by='demo' WHERE code='R513a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260910271456'), updated_by='demo' WHERE code='R613a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260825924531'), updated_by='demo' WHERE code='R713a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260721682646'), updated_by='demo' WHERE code='R813a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261007814182'), updated_by='demo' WHERE code='R113b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260114244776'), updated_by='demo' WHERE code='R213b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261009360292'), updated_by='demo' WHERE code='R313b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020345776'), updated_by='demo' WHERE code='R413b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029575722'), updated_by='demo' WHERE code='R513b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260910556329'), updated_by='demo' WHERE code='R613b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260825415323'), updated_by='demo' WHERE code='R713b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260811513791'), updated_by='demo' WHERE code='R813b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260326131282'), updated_by='demo' WHERE code='R112a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261012865473'), updated_by='demo' WHERE code='R212a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261015725132'), updated_by='demo' WHERE code='R312a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261021490794'), updated_by='demo' WHERE code='R412a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029175043'), updated_by='demo' WHERE code='R512a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260511399503'), updated_by='demo' WHERE code='R612a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261107502738'), updated_by='demo' WHERE code='R712a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260709854452'), updated_by='demo' WHERE code='R812a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261027482295'), updated_by='demo' WHERE code='R112b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261012544806'), updated_by='demo' WHERE code='R212b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261102550981'), updated_by='demo' WHERE code='R312b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261016736758'), updated_by='demo' WHERE code='R412b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029136383'), updated_by='demo' WHERE code='R512b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260728740082'), updated_by='demo' WHERE code='R612b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260707745021'), updated_by='demo' WHERE code='R712b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260716748110'), updated_by='demo' WHERE code='R812b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260212487231'), updated_by='demo' WHERE code='R111a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260701740889'), updated_by='demo' WHERE code='R211a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261028249859'), updated_by='demo' WHERE code='R311a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261030123509'), updated_by='demo' WHERE code='R411a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029196674'), updated_by='demo' WHERE code='R511a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261111526778'), updated_by='demo' WHERE code='R611a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261102113526'), updated_by='demo' WHERE code='R711a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260720428901'), updated_by='demo' WHERE code='R111b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260624643317'), updated_by='demo' WHERE code='R211b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261024644090'), updated_by='demo' WHERE code='R311b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260730440188'), updated_by='demo' WHERE code='R411b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250513898748'), updated_by='demo' WHERE code='R511b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261111162514'), updated_by='demo' WHERE code='R611b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029165614'), updated_by='demo' WHERE code='R711b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250305874642'), updated_by='demo' WHERE code='R224a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260909583257'), updated_by='demo' WHERE code='R324a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260831233706'), updated_by='demo' WHERE code='R424a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109590537'), updated_by='demo' WHERE code='R524a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261013759226'), updated_by='demo' WHERE code='R624a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020762210'), updated_by='demo' WHERE code='R724a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250305114543'), updated_by='demo' WHERE code='R224b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260908247675'), updated_by='demo' WHERE code='R324b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260831559959'), updated_by='demo' WHERE code='R424b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109168934'), updated_by='demo' WHERE code='R524b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260908878201'), updated_by='demo' WHERE code='R624b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260312550604'), updated_by='demo' WHERE code='R724b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L250305419246'), updated_by='demo' WHERE code='R224c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260902328849'), updated_by='demo' WHERE code='R324c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260831109848'), updated_by='demo' WHERE code='R424c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109442351'), updated_by='demo' WHERE code='R524c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260330288371'), updated_by='demo' WHERE code='R624c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020395126'), updated_by='demo' WHERE code='R724c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020686346'), updated_by='demo' WHERE code='R223a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260807605844'), updated_by='demo' WHERE code='R323a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260821938783'), updated_by='demo' WHERE code='R423a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261103165952'), updated_by='demo' WHERE code='R523a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261030520727'), updated_by='demo' WHERE code='R723a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260630288264'), updated_by='demo' WHERE code='R823a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020876582'), updated_by='demo' WHERE code='R223b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260807832560'), updated_by='demo' WHERE code='R323b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260811844690'), updated_by='demo' WHERE code='R423b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260806722572'), updated_by='demo' WHERE code='R523b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261030472617'), updated_by='demo' WHERE code='R723b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020932162'), updated_by='demo' WHERE code='R223c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260807817462'), updated_by='demo' WHERE code='R323c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260811611047'), updated_by='demo' WHERE code='R423c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261113652501'), updated_by='demo' WHERE code='R523c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261030890699'), updated_by='demo' WHERE code='R723c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261104221445'), updated_by='demo' WHERE code='R222a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109221313'), updated_by='demo' WHERE code='R322a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260814812391'), updated_by='demo' WHERE code='R422a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261121962780'), updated_by='demo' WHERE code='R522a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261024636258'), updated_by='demo' WHERE code='R722a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261103703437'), updated_by='demo' WHERE code='R822a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261104818660'), updated_by='demo' WHERE code='R222b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109779636'), updated_by='demo' WHERE code='R322b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260814641566'), updated_by='demo' WHERE code='R422b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261121993817'), updated_by='demo' WHERE code='R522b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261024635446'), updated_by='demo' WHERE code='R722b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261103372104'), updated_by='demo' WHERE code='R822b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261104950111'), updated_by='demo' WHERE code='R222c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109123047'), updated_by='demo' WHERE code='R322c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260915637770'), updated_by='demo' WHERE code='R422c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261121247643'), updated_by='demo' WHERE code='R522c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261024322099'), updated_by='demo' WHERE code='R722c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261103523160'), updated_by='demo' WHERE code='R822c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261104585411'), updated_by='demo' WHERE code='R221a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109600915'), updated_by='demo' WHERE code='R321a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020278474'), updated_by='demo' WHERE code='R421a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261102111029'), updated_by='demo' WHERE code='R621a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261029105249'), updated_by='demo' WHERE code='R721a';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261104825060'), updated_by='demo' WHERE code='R221b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109520163'), updated_by='demo' WHERE code='R321b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020800117'), updated_by='demo' WHERE code='R421b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261102992974'), updated_by='demo' WHERE code='R621b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L260701532159'), updated_by='demo' WHERE code='R721b';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261104502101'), updated_by='demo' WHERE code='R221c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261109989789'), updated_by='demo' WHERE code='R321c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261020785565'), updated_by='demo' WHERE code='R421c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261102589906'), updated_by='demo' WHERE code='R621c';
UPDATE location SET lot_id = (SELECT id FROM lot WHERE code='L261028115064'), updated_by='demo' WHERE code='R721c';
UPDATE location SET note = 'Cliente A', updated_by='demo' WHERE code='R824b';
UPDATE location SET note = 'Postas partidas', updated_by='demo' WHERE code='R623b';
UPDATE location SET note = 'Cliente A', updated_by='demo' WHERE code='R823b';
UPDATE location SET note = 'Postas partidas', updated_by='demo' WHERE code='R622b';
UPDATE location SET note = 'Eco rest 200/300', updated_by='demo' WHERE code='R521b';
-- A few lots in the corridor and leftovers lists, to test them
UPDATE lot SET holding = 'CORRIDOR' WHERE code IN ('L261121794856','L261121478003','L261121325678','L261121808897');
UPDATE lot SET holding = 'LEFTOVERS', weight_kg = 157 WHERE code = 'L261118842534';

-- Demo lots with no location and no list go to the corridor (a lot is never left unplaced)
UPDATE lot SET holding = 'CORRIDOR'
WHERE status = 'IN_STOCK' AND holding IS NULL AND NOT EXISTS (SELECT 1 FROM location l WHERE l.lot_id = lot.id);
