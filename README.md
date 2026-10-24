# News Ticker — barra notizie scorrevole per Android TV

Add-on indipendente dal launcher: una barra in overlay sul fondo dello schermo
con i titoli di ANSA (cronaca, politica, sport) e di Google News (Juventus,
Formula 1, MotoGP, Tennis), aggiornati ogni 15 minuti. Non modifica il launcher,
non tocca nulla se la barra è spenta, e nasconde la barra mentre un'app video
(Stremio, YouTube, Prime, Plex...) è in riproduzione.

## Permessi

- `INTERNET`: legge i feed RSS di ANSA e Google News.
- `SYSTEM_ALERT_WINDOW`: la barra in overlay sul fondo. Da concedere con
  `appops set io.github.newsticker SYSTEM_ALERT_WINDOW allow`.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`: tiene vivo
  l'aggiornamento ogni 15 min; il tipo `specialUse` dichiara l'uso dell'overlay.
- `RECEIVE_BOOT_COMPLETED`: riporta la barra dopo un riavvio se era accesa.
- `POST_NOTIFICATIONS`: notifica minima che tiene il servizio a chânito...

## Setup via ADB dopo l'install

1. Grantee overlay:
   `adb shell appops set io.github.newsticker SYSTEM_ALERT_WINDOW allow`
2. Grantee ricading del top app (auto-hide dei video):
   `adb shell appops set io.github.newsticker GET_USAGE_STATS allow`
3. Accendi la barra: apri l'app "News Ticker" dal launcher e premi OK.
