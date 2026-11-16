# Voice Chat E2EE (Fabric, Minecraft 26.3)

A client-side mod for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that encrypts
voice chat with a shared **key**. Only players who have entered the same key can hear each other.
The server (and its admin) only sees ciphertext.

The mod runs **on the client only**. The server only needs Simple Voice Chat, just like before.

## Requirements

- Minecraft 26.3 with Fabric Loader (0.19.3 or newer) and Fabric API
- Simple Voice Chat 2.6.24 or newer (Fabric) on client and server
- Everyone who should hear each other needs this mod and the same key

## Usage

Everything is set in the game. You never need a command or the config file.

1. Put the jar in the `mods` folder and start Minecraft. On the first start a random key is created for you.
2. Open the Simple Voice Chat window (default key `V`). Next to it there is a small panel with two buttons:
   - **Incognito button (top):** switches the encryption on or off with one click. It shows the incognito icon
     with a green dot while you send encrypted, and grayed out and crossed out while encryption is off.
   - **Gear button (bottom):** opens the settings of this mod.
3. In the settings, press **Show** to see your key, select it (Ctrl+A) and copy it (Ctrl+C), and send it to your
   friends over a private channel, not publicly.
4. Your friends open the settings, press **Show**, paste the key (Ctrl+V) into the key field and press
   **Apply key** (or Enter). It takes effect immediately, without a restart.
5. Comparison: the settings show a key code (e.g. `A1B2-C3D4`). Same code means same key.

Anyone who does not have the key cannot hear you, and you cannot hear them (default).

### The settings window

| Element | What it does |
| --- | --- |
| **Encryption: ON/OFF** | how you send (see below). ON needs a key. |
| **Only hear encrypted players: ON/OFF** | "strict" (see below) |
| **Key field + Show/Hide** | your key. It is hidden until you press **Show**, so it does not show up on streams by accident |
| **Apply key** | uses the typed key. A typed key is only used after this button (or Enter), so a half-typed key can never replace a working one |
| **Generate new key** | creates a new random key. Because the old key is gone afterwards, the button asks for a second click |
| **Done** / Esc | back to the Simple Voice Chat window |

The two lines under the switches always tell you what you send and who you hear right now. Below the key field
you see the key code, or a warning (no key, unsaved key, very short key).

## Encryption on/off

The encryption switch (the incognito button, or **Encryption** in the settings) controls **how you send**:

| | You send | You hear |
| --- | --- | --- |
| **ON** (default) | encrypted | players with your key. With strict off, also players without the mod |
| **OFF** | unencrypted, as if this mod were not installed | players with your key (encrypted) **and** all unencrypted players |

Even with encryption switched off, you still hear everyone who speaks encrypted with your key.
They just cannot hear you while they have strict on, because you are sending unencrypted.
Strict (**Only hear encrypted players**) only applies while encryption is switched on.

## Incognito icon

A small incognito icon (hat with glasses) shows who is encrypted. It appears next to the skin of
**yourself** (as long as you send encrypted) and of **every player who speaks encrypted with your
key**, in these places in Simple Voice Chat:

- **Group display in the HUD** (the heads at the edge of the screen): at the head
- **Group window** (member list): after the name
- **Volume window** (player list): in the corner of the skin
- **Join group window** (list with the heads of the members): at the head

When the icon appears:

- For other players, as soon as you have **decrypted a packet from them with your key**, that is, after
  they have spoken for the first time. It stays even if they are silent.
- It disappears when the player speaks unencrypted or with a different key again, when you change
  your key, and when you switch servers.
- Players with a **different** key get no icon, because you cannot hear them.
- Without a key of your own there are no icons.

## Optional commands

Commands are not needed any more, but they still work as shortcuts:

| Command | Effect |
| --- | --- |
| `/vckey gui` | opens the settings window (a fallback in case the buttons are missing after a Simple Voice Chat update) |
| `/vckey` | status, key code |
| `/vckey generate` | generate and save a new random key |
| `/vckey show` | show your own key in chat |
| `/vckey on` / `off` | encryption on/off |
| `/vckey strict on` / `off` | the same as the strict switch |
| `/vckey reload` | re-read the config file immediately |

There is deliberately no command that takes a key as an argument. A typo in the command name would otherwise
send the key to the server as a server command. Keys are entered in the settings window.

The settings are stored in `config/voicechat-e2ee.properties`. You can edit that file by hand (changes are picked
up within one second), but you do not have to.

## Building it yourself

Install JDK 25, then in the project folder:

```
./gradlew build          (Windows: gradlew.bat build)
```

The finished file is in `build/libs/voicechat-e2ee-1.2.0.jar`. To test with a development client:
`./gradlew runClient`.

## How it works

- The mod hooks into the Opus encoder (`MicThread`) and the Opus decoders (`AudioChannel`) of
  Simple Voice Chat via mixins. Every encoded audio packet is encrypted with AES-256-GCM before it is
  sent, and decrypted after it is received, before it is played. Decryption always happens as soon as a
  key is set, even with encryption switched off.
- While decrypting, the mod remembers which player (audio channel ID = player UUID) speaks encrypted.
  That is where the incognito icon comes from. It needs no mod on the server and does not send anything
  extra.
- The icon is drawn into the Simple Voice Chat UI via mixins (`GroupChatManager`, `GroupEntry`,
  `JoinGroupEntry`, `PlayerVolumeEntry`) and is generated from rectangles, without a texture.
- The two buttons are added to the Simple Voice Chat window by one more mixin (`VoiceChatScreen`). The settings
  window and the panel behind the buttons are drawn from rectangles as well, in the same look as the Simple Voice
  Chat windows.
- The AES key is derived from the key via PBKDF2-HMAC-SHA256 (150,000 rounds).
- GCM detects wrong keys and tampered packets. Such packets are dropped.
- The ciphertext is hidden in the padding of a valid, empty Opus packet. Players without the mod
  therefore hear silence instead of noise. Verified with the real libopus.

## Limitations

- **Metadata is visible:** The server sees who speaks when and how large the packets are.
  The content is encrypted, not the fact that someone is speaking.
- **The key is only as safe as the way you hand it out.** Anyone with the key hears everything.
  If in doubt, press **Generate new key** in the settings and distribute the new key.
- **Short echo for players without the mod:** If a player without the mod has just heard real audio,
  they may still hear about 60 ms of reverb after switching, then silence.
- **Server plugins that process audio** (radio, recording, voice filters) only see ciphertext and
  do not work with encrypted speakers. Proximity, groups and whispering keep working.
- **Replay:** A server admin could resend recorded packets (replay). They cannot read or modify
  them.
- **Mixins on internals:** The mod accesses private fields of Simple Voice Chat (`MicThread.encoder`,
  `AudioChannel.decoder`, `AudioChannel.uuid`, `AudioChannel.client`). After major updates of Simple
  Voice Chat, adjustments may be necessary.
- **The icons and buttons are display only:** The UI mixins (`voicechat_e2ee.ui.mixins.json`) are deliberately
  optional. If a spot no longer fits after a Simple Voice Chat update, only the icon or the buttons are missing
  there (an entry in the log), the game still starts and encryption keeps working. `/vckey gui` still opens
  the settings then.
- **The buttons sit next to the window:** The Simple Voice Chat window is a fixed image with no free space, so
  the two buttons have a small panel of their own on its right (on the left, or below it, on very narrow screens).
- **Icon only after the first speech:** Whether another player is encrypted can only be seen from
  their voice packets. Someone who has never spoken has no icon yet.

## Self-test

`src/test/java/voicechate2ee/SelfTest.java` checks encryption, packet format, configuration, the
encoder/decoder wrappers, hearing with encryption switched off, the detection of encrypted
players, and the logic behind the settings window and the quick toggle, all without Minecraft (120 checks).
