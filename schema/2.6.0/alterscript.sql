ALTER TABLE `logistimo`.`INVNTRY` ADD UON DATETIME NULL;
CREATE TABLE `MATERIALMANUFACTURERS` (
  `KEY` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `MATERIAL_ID` BIGINT(20) NOT NULL,
  `MFR_CODE` BIGINT(20) NOT NULL,
  `MFR_NAME` VARCHAR(255) NOT NULL,
  `MATERIAL_CODE` BIGINT(20) NOT NULL,
  `QTY` FLOAT NULL,
  PRIMARY KEY (`KEY`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

UPDATE TRANSACTION SET TOT = "ts" WHERE TID IN ( SELECT ID FROM `ORDER` WHERE OTY = 0);
UPDATE TRANSACTION SET TOT = "os" WHERE TOT = "s";;

DROP TABLE `logistimo`.`DASHBOARD`;

CREATE TABLE `logistimo`.`DASHBOARD` (
  `DBID` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `DID` BIGINT(20) NULL DEFAULT NULL,
  `DESC` VARCHAR(255) NULL DEFAULT NULL,
  `CONF` VARCHAR(8192) NULL DEFAULT NULL,
  `NAME` VARCHAR(255) NULL DEFAULT NULL,
  `TITLE` VARCHAR(255) NULL DEFAULT NULL,
  `CON` DATETIME NULL DEFAULT NULL,
  `CBY` VARCHAR(255) NULL DEFAULT NULL,
  `UON` DATETIME NULL DEFAULT NULL,
  `UBY` VARCHAR(255) NULL DEFAULT NULL,
  `INFO` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`DBID`))ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `logistimo`.`BULLETINBOARD` (
  `BBID` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `DID` BIGINT(20) NULL DEFAULT NULL,
  `DESC` VARCHAR(255) NULL DEFAULT NULL,
  `CONF` VARCHAR(4096) NULL DEFAULT NULL,
  `NAME` VARCHAR(255) NULL DEFAULT NULL,
  `MIN` BIGINT(20) NULL DEFAULT NULL,
  `MAX` BIGINT(20) NULL DEFAULT NULL,
  `UBY` VARCHAR(255) NULL DEFAULT NULL,
  `UON` DATETIME NULL DEFAULT NULL,
  `CBY` VARCHAR(255) NULL DEFAULT NULL,
  `CON` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`BBID`))ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

alter table USERTOKEN add column ACCESSKEY VARCHAR(255);

ALTER TABLE `logistimo`.`ORDER` ADD CVT DATETIME NULL, ADD VVT DATETIME NULL, ADD PART BIGINT(20) NULL, ADD SART BIGINT(20) NULL, ADD TART BIGINT(20) NULL;

create index INVNTRYEVENTLOG_DOMAINS_DOMAINID on INVNTRYEVENTLOG_DOMAINS (DOMAIN_ID);

ALTER TABLE SHIPMENT ADD COLUMN RID VARCHAR(100) NULL;

ALTER TABLE `ORDER` CHANGE COLUMN RID RID VARCHAR(100) DEFAULT NULL;

create index IDX_ASSETSTATUS_ASSETID_TYPE on ASSETSTATUS(ASSETID,TYPE);
create index IDX_ASSET_DOMAINS_DOMAINID on ASSET_DOMAINS (DOMAIN_ID);
create INDEX IDX_TAG_NAME_TYPE ON TAG(NAME,TYPE);
create INDEX IDX_SHIPMENTITEM_SID on SHIPMENTITEM (SID);
create INDEX IDX_SHIPMENT_OID on SHIPMENT(ORDERID);
create INDEX IDX_KIOSK_DOMAINS on KIOSK_DOMAINS(DOMAIN_ID);
create INDEX IDX_SHIPMENTITEMBATCH_SIID on SHIPMENTITEMBATCH(SIID);