-- --------------------------------------------------------
-- Host:                         192.168.178.112
-- Server Version:               8.0.34 - MySQL Community Server - GPL
-- Server Betriebssystem:        Linux
-- HeidiSQL Version:             12.2.0.6576
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Exportiere Datenbank Struktur für livestreamirc
CREATE DATABASE IF NOT EXISTS `livestreamirc` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `livestreamirc`;

-- Exportiere Struktur von Tabelle livestreamirc.channel
CREATE TABLE IF NOT EXISTS `channel` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `Link` varchar(256) NOT NULL DEFAULT '',
  `Key` varchar(256) NOT NULL DEFAULT '',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.event
CREATE TABLE IF NOT EXISTS `event` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `Title` varchar(256) DEFAULT NULL,
  `Description` varchar(1000) DEFAULT NULL,
  `Thumbnail` blob,
  `ChannelID` int DEFAULT NULL,
  `StarttimeUTC` bigint DEFAULT NULL,
  `Duration` bigint DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Prozedur livestreamirc.getActiveStreams
DELIMITER //
CREATE PROCEDURE `getActiveStreams`()
BEGIN

SELECT `Channel` 			AS `Channel`
                              , p.Name   			AS `Platform`
                              , e.StarttimeUTC	AS `Timestamp`
                              , e.Duration		AS `Duration`
                        FROM `keys` k
                        JOIN platform p ON p.ID = k.PlatformID
                        JOIN user_keys uk ON uk.KeyID = k.ID
                        JOIN user_event ue ON ue.UserID = uk.UserID
                        JOIN `event` e ON e.ID = ue.EventID;
                        
               

END//
DELIMITER ;

-- Exportiere Struktur von Tabelle livestreamirc.group
CREATE TABLE IF NOT EXISTS `group` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `Parent` int NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.grouppermissions
CREATE TABLE IF NOT EXISTS `grouppermissions` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `GroupID` int DEFAULT NULL,
  `Permission` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.keys
CREATE TABLE IF NOT EXISTS `keys` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `PlatformID` int NOT NULL DEFAULT '0',
  `Key` varchar(512) NOT NULL DEFAULT '0',
  `Channel` varchar(256) NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.mediaitem
CREATE TABLE IF NOT EXISTS `mediaitem` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `MediaListID` int NOT NULL DEFAULT '0',
  `Type` int NOT NULL DEFAULT '0',
  `SystemPath` varchar(256) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.medialist
CREATE TABLE IF NOT EXISTS `medialist` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `MediasetID` int DEFAULT NULL,
  `ListNo` int DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.mediaset
CREATE TABLE IF NOT EXISTS `mediaset` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `EventID` int NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.platform
CREATE TABLE IF NOT EXISTS `platform` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `Name` varchar(256) NOT NULL DEFAULT '0',
  `RTMP_Prefix` varchar(256) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.user
CREATE TABLE IF NOT EXISTS `user` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `GroupID` int NOT NULL,
  `Username` varchar(2048) NOT NULL,
  `Password` varchar(2048) NOT NULL,
  `Channel` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.user_event
CREATE TABLE IF NOT EXISTS `user_event` (
  `UserID` int NOT NULL,
  `EventID` int NOT NULL,
  PRIMARY KEY (`UserID`,`EventID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

-- Exportiere Struktur von Tabelle livestreamirc.user_keys
CREATE TABLE IF NOT EXISTS `user_keys` (
  `UserID` int NOT NULL,
  `KeyID` int NOT NULL,
  PRIMARY KEY (`UserID`,`KeyID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Daten Export vom Benutzer nicht ausgewählt

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
