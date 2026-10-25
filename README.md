[![GitHub Release](https://img.shields.io/github/v/release/giuanpadan74/news-ticker?label=beta&display_name=release&logo=github)](https://github.com/giuanpadan74/news-ticker/releases/latest)

# News Ticker — barra notizie scorrevole per NVIDIA Shield TV / Android TV

Add-on indipendente: una **barra in overlay sul fondo dello schermo** con notizie in
continuo, ora e meteo, e una torta degli argomenti che stai seguendo. Aggiornata ogni
15 minuti, senza modificare il launcher: quando la barra è spenta l'app non tocca
nulla, e mentre un'app video (Stremio, YouTube, Prime, Plex...) è in riproduzione la
barra si nasconde da sola.

## Scarica la beta

⬇️ **[news-ticker-v0.9.5.apk](https://github.com/giuanpadan74/news-ticker/releases/latest)** — APK release (~80 KB)

### Installazione su Shield TV
1. Scarica l'APK (dal browser del TV o da PC)
2. Copia sul TV via USB, file manager o [Send Files to TV](https://play.google.com/store/apps/details?id=com.yablio.sendfilestotv)
3. Apri il file e conferma; se richiesto, consenti le **origini sconosciute**
   (Impostazioni → Preferenze dispositivo → Sicurezza e restrizioni)
4. Al primo avvio concedi la **sovrimpressione**: l'app ti guida al setting

Da PC, con debug USB attivo:
```
adb install news-ticker-v0.9.5.apk
```

## Cosa fa

- **Barra notizie in overlay** sulla home: titoli in continuo con frequenza di aggiornamento di 15 minuti
- **Argomenti personalizzati** (pulsante "+ Modifica categorie" su schermo):
  - *Google News*: qualsiasi termine (INTER, NBA, MotoGP, la tua squadra...)
  - *ANSA*: sezioni (Ultim'ora, Sport, Politica...) da scorrere con OK
  - *RSS*: indirizzo di un feed qualsiasi, con verifica al salvataggio ("Feed non raggiungibile" se non risponde)
  - *Goliardia*: frasi a tema
- **RSS ITALIANI**: elenco di fonti italiane (built-in verificate + elenco remoto aggiornato all'apertura) con navigazione a telecomando: GIÙ entra nell'elenco, OK usa la fonte, ‹ due volte la elimina; le tue fonti verificate restano anche quando l'elenco remoto cambia
- **Torta & legenda**: ripartizione degli argomenti, colori stabili per feed
- **Auto-riavvio** dopo il boot se la barra era accesa

### Compatibilità e requisiti
- NVIDIA Shield TV (2015-2019, Pro/Tube) e Android TV/Google TV generico
- Android 8.0+ (minSdk 26), nessuna dipendenza esterna
- Navigazione a telecomando D-pad; non richiede launcher modificati

## Permessi (e perché)

| Permesso | Uso |
|---|---|
| `INTERNET` | legge i feed RSS/ANSA/Google News e l'elenco fonti RSS ITALIANI |
| `SYSTEM_ALERT_WINDOW` | la barra in overlay sul fondo: è il cuore dell'app |
| `FOREGROUND_SERVICE` + `specialUse` | aggiornamento ogni 15 min; il tipo `specialUse` dichiara l'uso dell'overlay |
| `RECEIVE_BOOT_COMPLETED` | riporta la barra dopo un riavvio se era accesa |
| `POST_NOTIFICATIONS` | notifica minima che tiene vivo il servizio in foreground |
| `GET_USAGE_STATS` (via appops) | sapere quale app è in primo piano (modalità "solo home" e nascondere la barra nell'app stessa) |

Nota: i servizi sono pensati per usare l'overlay solo se la barra è esplicitamente
accesa dall'utente; l'app non tocca nulla quando la barra è spenta.

L'auto-hide è una funzione scegliibile nelle impostazioni: riga "**Auto-hide**"
(subito dopo "Barra notizie") con interruttore on/off. Quando è ON la barra esce con
una piccola animazione mentre la TV sta davvero riproducendo audio/video
(`AudioManager.getActivePlaybackConfigurations()`, nessun permesso extra) e rientra
quando la riproduzione finisce — vale per Stremio, Plex, Prime Video, Tivimate, Rai
Play e qualunque player. Quando è OFF la barra resta sopra qualunque app, anche
durante i video. La riga "**Posizione**" (Sopra/Sotto, default Sotto) sposta il bordo
dove compare la barra. Nei menu delle app (senza riproduzione) la barra continua a
scorrere invece di restare congelata sopra l'app.

## Compilare il progetto

```
gradle assembleRelease          # APK in app/build/outputs/apk/release/
```

L'app non usa librerie di terzi né SDK Android a pagamento: solo framework.

## Beta: cosa aspettarsi

È una beta viva: mancano rifiniture e vengono aggiunte novità di continuo.
Segnala difetti o idee nelle [Issues](https://github.com/giuanpadan74/news-ticker/issues).