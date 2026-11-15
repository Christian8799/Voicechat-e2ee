# Voice Chat E2EE (Fabric, Minecraft 26.3)

A client-side mod for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that encrypts voice chat using a shared **key**. Only players who have entered the same key can hear each other. The server (including its admins) only sees ciphertext.

The mod runs **entirely client-side**. The server only needs Simple Voice Chat, just as before.

## Requirements

- Minecraft 26.3 with Fabric Loader (0.19.3 or newer) and Fabric API
- Simple Voice Chat 2.6.24 or newer (Fabric) on both the client and server
- Everyone who wants to communicate must have this mod installed and use the same key

## Usage

1. Place the JAR file in your `mods` folder and launch Minecraft. On the first launch, the file `config/voicechat-e2ee.properties` will be created with a randomly generated key.
2. View your key: run `/vckey show` in-game or open the configuration file (look for the `key=...` line).
3. Share the key with your friends (preferably through a private channel, not publicly).
4. Your friends must enter the key in the `key=` field of their own configuration file and save it. The change takes effect within one second at most, without requiring a restart.
5. Verify that the keys match: `/vckey` displays a key code (e.g. `A1B2-C3D4`). If the codes match, the keys are identical.

Anyone who does not have the key cannot hear you, and you cannot hear them either (by default).

| Command | Description |
| --- | --- |
| `/vckey` | Displays the current status and key code |
| `/vckey generate` | Generates and saves a new random key (everyone must then update theirs) |
| `/vckey show` | Displays your key in the chat |
| `/vckey on` / `off` | Enables or disables encryption |
| `/vckey strict on` / `off` | On: only encrypted speakers are played. Off: players without the mod remain audible as well |
| `/vckey reload` | Immediately reloads the configuration file |

There is intentionally no command that accepts a key as an argument. Otherwise, a typo in the command name could accidentally send the key to the server as a server command. Other people's keys must be entered directly into the configuration file.

## Building from Source

Install JDK 25, then run the following command in the project directory:

```bash
./gradlew build          (Windows: gradlew.bat build)
```

The compiled JAR file will be located at `build/libs/voicechat-e2ee-1.0.0.jar`.

To test it using the development client, run:

```bash
./gradlew runClient
```

## How It Works

- The mod uses Mixins to hook into Simple Voice Chat's Opus encoder (`MicThread`) and Opus decoder (`AudioChannel`). Every encoded audio packet is encrypted using AES-256-GCM before being sent and decrypted after reception, before playback.
- The AES key is derived from the shared key using PBKDF2-HMAC-SHA256 (150,000 iterations).
- GCM detects incorrect keys and tampered packets. Such packets are discarded.
- The ciphertext is embedded in the padding of a valid, empty Opus packet. Players without the mod therefore hear silence instead of noise. This has been tested with the actual libopus implementation.

## Limitations

- **Metadata is still visible:** The server can see who is speaking, when they are speaking, and the size of the packets. The audio content is encrypted, but the fact that someone is speaking is not hidden.
- **The key is only as secure as the way you share it.** Anyone who has the key can listen in. If you suspect it has been compromised, run `/vckey generate` and distribute the new key.
- **Brief audio echo for players without the mod:** If a player without the mod was just hearing actual audio, they may still hear approximately 60 ms of residual audio after switching. After that, they will hear silence.
- **Server plugins that process audio** (radio, recording, voice filters) will only see ciphertext and will not work with encrypted speakers. Proximity chat, groups, and whispering will continue to work.
- **Replay attacks:** A server admin could resend previously recorded packets (replay). However, they cannot read or modify them.
- **Mixins accessing internal fields:** The mod accesses private fields in Simple Voice Chat (`MicThread.encoder`, `AudioChannel.decoder`). Major Simple Voice Chat updates may require adjustments to the mod.

## Self-Test

`src/test/java/voicechate2ee/SelfTest.java` tests encryption, packet formatting, configuration, and encoder/decoder wrappers without requiring Minecraft (48 tests).

German:
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
