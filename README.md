# Voice Chat E2EE (Fabric, Minecraft 26.3)

Eine Client-Mod für [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), die den Voicechat
mit einem gemeinsamen **Key** verschlüsselt. Nur Spieler, die denselben Key eingetragen haben, können sich
hören. Der Server (und dessen Admin) sieht nur Chiffretext.

Die Mod läuft **nur auf dem Client**. Der Server braucht nur Simple Voice Chat, so wie bisher.

## Voraussetzungen

- Minecraft 26.3 mit Fabric Loader (0.19.3 oder neuer) und Fabric API
- Simple Voice Chat 2.6.24 oder neuer (Fabric) auf Client und Server
- Alle, die sich hören sollen, brauchen diese Mod und denselben Key

## Benutzung

1. Jar in den `mods`-Ordner legen und Minecraft starten. Beim ersten Start wird die Datei
   `config/voicechat-e2ee.properties` mit einem zufälligen Key angelegt.
2. Key anzeigen: im Spiel `/vckey show` oder die Datei öffnen (Zeile `key=...`).
3. Key an deine Freunde schicken (am besten über einen privaten Kanal, nicht öffentlich).
4. Freunde tragen ihn bei sich in dieselbe Datei hinter `key=` ein und speichern. Das gilt nach
   spätestens einer Sekunde, ohne Neustart.
5. Abgleich: `/vckey` zeigt einen Key-Code (z. B. `A1B2-C3D4`). Gleicher Code heißt gleicher Key.

Wer den Key nicht hat, hört dich nicht, und du hörst ihn auch nicht (Standard).

| Befehl | Wirkung |
| --- | --- |
| `/vckey` | Status, Key-Code |
| `/vckey generate` | neuen zufälligen Key erzeugen und speichern (alle brauchen dann den neuen) |
| `/vckey show` | eigenen Key im Chat anzeigen |
| `/vckey on` / `off` | Verschlüsselung an/aus |
| `/vckey strict on` / `off` | an: nur verschlüsselte Sprecher werden abgespielt. aus: Spieler ohne Mod bleiben zusätzlich hörbar |
| `/vckey reload` | Konfigdatei sofort neu einlesen |

Es gibt absichtlich keinen Befehl, der einen Key als Argument annimmt. Ein Tippfehler im Befehlsnamen
würde den Key sonst als Server-Befehl an den Server schicken. Fremde Keys trägst du in die Datei ein.

## Selbst bauen

JDK 25 installieren, dann im Projektordner:

```
./gradlew build          (Windows: gradlew.bat build)
```

Die fertige Datei liegt in `build/libs/voicechat-e2ee-1.0.0.jar`. Zum Testen mit Entwicklungsclient:
`./gradlew runClient`.

## Wie es funktioniert

- Die Mod hängt sich per Mixin in den Opus-Encoder (`MicThread`) und die Opus-Decoder (`AudioChannel`)
  von Simple Voice Chat ein. Jedes kodierte Audiopaket wird mit AES-256-GCM verschlüsselt, bevor es
  gesendet wird, und nach dem Empfang entschlüsselt, bevor es abgespielt wird.
- Der AES-Schlüssel wird aus dem Key per PBKDF2-HMAC-SHA256 (150.000 Runden) abgeleitet.
- GCM erkennt falsche Keys und manipulierte Pakete. Solche Pakete werden verworfen.
- Der Chiffretext steckt im Padding eines gültigen, leeren Opus-Pakets. Spieler ohne Mod hören dadurch
  Stille statt Rauschen. Geprüft mit der echten libopus.

## Grenzen

- **Metadaten sind sichtbar:** Der Server sieht, wer wann spricht und wie groß die Pakete sind.
  Verschlüsselt ist der Inhalt, nicht die Tatsache, dass gesprochen wird.
- **Der Key schützt nur so gut, wie du ihn weitergibst.** Wer den Key hat, hört alles mit.
  Bei Verdacht `/vckey generate` und neu verteilen.
- **Kurzes Echo bei Spielern ohne Mod:** Hat ein Spieler ohne Mod gerade echtes Audio gehört, kann er
  nach dem Umschalten noch etwa 60 ms Nachhall hören, danach Stille.
- **Server-Plugins, die Audio verarbeiten** (Radio, Aufnahme, Sprachfilter), sehen nur Chiffretext und
  funktionieren mit verschlüsselten Sprechern nicht. Nähe, Gruppen und Flüstern funktionieren weiter.
- **Wiederholung:** Ein Server-Admin könnte aufgezeichnete Pakete erneut senden (Replay). Er kann sie
  nicht lesen oder ändern.
- **Mixin auf Interna:** Die Mod greift auf private Felder von Simple Voice Chat zu (`MicThread.encoder`,
  `AudioChannel.decoder`). Nach größeren Updates von Simple Voice Chat kann ein Anpassen nötig sein.

## Selbsttest

`src/test/java/voicechate2ee/SelfTest.java` prüft Verschlüsselung, Paketformat, Konfiguration und die
Encoder/Decoder-Hüllen ohne Minecraft (48 Prüfungen).
