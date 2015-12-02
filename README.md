---

![TU Dortmund Logo](http://www.ub.tu-dortmund.de/images/tu-logo.png)

![UB Dortmund Logo](http://www.ub.tu-dortmund.de/images/ub-schriftzug.jpg)

---

# PaiaService - Eine Implementierung der PAIA-Spezifikation

Die UB Dortmund benötigt einen nach aussen vom Bibliothekssystem (ILS) unabhängigen Service für Kontofunktionen aus Nutzersicht. Zu diesen zählen:

* Abrufen von Informationen zu ausgeliehenen Medien
* Abrufen von Informationen zu bestellten bzw. vorgemerkten Medien
* Anzeige von offenen Gebühren
* Aufgeben und Stornieren von Bestellungen bzw Vormerkungen
* Verlängerung von Ausleihfristen

Die Notwendigkeit für einen solchen Service ergibt sich aus einer Vielzahl von neuen vernetzten Dienstleistungen innerhalb der TU Dortmund.

Zentrales Szenario ist die Einbindung der Kontofunktion für Nutzende in das neue Recherchesystem der UB Dortmund [Katalog plus](http://www.ub.tu-dortmund.de/katalog/).
In Kombination mit der Frage nach der Ermittlung von Live-Informationen zur Verfügbarkeit von Medien in der UB Dortmund, ergab sich die Möglichkeit
mit [DAIA](https://gbv.github.io/daiaspec/daia.html) und [PAIA](https://gbv.github.io/paia/paia.html) diese Aufgabe zu erfüllen.

Für einige spezielle Funktionen wurde die originale PAIA-Spezifikation erweitert.

Die Authentifizierungs- und Autorisierungsfunktionen werden mittels OAuth 2.0 realisiert.

## Anwendung

PaiaService ist in Java 1.8 implementiert und stellt drei Interfaces für lokale Anpassungen zur Verfügung.

* `de.tu_dortmund.ub.api.paia.core.ils.IntegratedLibrarySystem` zur Implementierung der Anbindung an ein ILS
* `de.tu_dortmund.ub.api.paia.auth.AuthorizationInterface` zur Implementierung der Anbindung an einen OAuth-Token-Endpoint
* `de.tu_dortmund.ub.util.output.ObjectToHtmlTransformation` zur Implementierung einer HTML-Ausgabe der "Responses" des API - falls benötigt.

Die Konfiguration der Implementierung geschieht mittels `META-INF.service`.

## Kontakt

**api@ubdo - Application Programming Interfaces der Universitätsbibliothek Dortmund**

Technische Universität Dortmund // Universitätsbibliothek // Bibliotheks-IT // Vogelpothsweg 76 // 44227 Dortmund

[Webseite](https://api.ub.tu-dortmund.de) // [E-Mail](mailto:api@ub.tu-dortmund.de)

---

![Creative Commons License](http://i.creativecommons.org/l/by/4.0/88x31.png)

This work is licensed under a [Creative Commons Attribution 4.0 International License (CC BY 4.0)](http://creativecommons.org/licenses/by/4.0/)
