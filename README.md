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

1. Put the jar in the `mods` folder and start Minecraft. On the first start, the file
   `config/voicechat-e2ee.properties` is created with a random key.
2. Show your key: in game `/vckey show`, or open the file (line `key=...`).
3. Send the key to your friends (preferably over a private channel, not publicly).
4. Your friends enter it in the same file after `key=` and save. It takes effect within
   one second at the latest, without a restart.
5. Comparison: `/vckey` shows a key code (e.g. `A1B2-C3D4`). Same code means same key.

Anyone who does not have the key cannot hear you, and you cannot hear them (default).

## Encryption on/off

`/vckey on` / `off` controls **how you send**:

| | You send | You hear |
| --- | --- | --- |
| **ON** (default) | encrypted | players with your key. With `strict off`, also players without the mod |
| **OFF** | unencrypted, as if this mod were not installed | players with your key (encrypted) **and** all unencrypted players |

Even with encryption switched off, you still hear everyone who speaks encrypted with your key.
They just cannot hear you while they have `strict` on, because you are sending unencrypted.
`strict` only applies while encryption is switched on.

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

| Command | Effect |
| --- | --- |
| `/vckey` | status, key code |
| `/vckey generate` | generate and save a new random key (everyone then needs the new one) |
| `/vckey show` | show your own key in chat |
| `/vckey on` / `off` | encryption on/off (controls how you send, see above) |
| `/vckey strict on` / `off` | on: only encrypted speakers are played. off: players without the mod stay audible as well |
| `/vckey reload` | re-read the config file immediately |

There is deliberately no command that takes a key as an argument. A typo in the command name would
otherwise send the key to the server as a server command. You enter other people's keys in the file.

## Building it yourself

Install JDK 25, then in the project folder:

```
./gradlew build          (Windows: gradlew.bat build)
```

The finished file is in `build/libs/voicechat-e2ee-1.1.0.jar`. To test with a development client:
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
- The AES key is derived from the key via PBKDF2-HMAC-SHA256 (150,000 rounds).
- GCM detects wrong keys and tampered packets. Such packets are dropped.
- The ciphertext is hidden in the padding of a valid, empty Opus packet. Players without the mod
  therefore hear silence instead of noise. Verified with the real libopus.

## Limitations

- **Metadata is visible:** The server sees who speaks when and how large the packets are.
  The content is encrypted, not the fact that someone is speaking.
- **The key is only as safe as the way you hand it out.** Anyone with the key hears everything.
  If in doubt, run `/vckey generate` and distribute the new key.
- **Short echo for players without the mod:** If a player without the mod has just heard real audio,
  they may still hear about 60 ms of reverb after switching, then silence.
- **Server plugins that process audio** (radio, recording, voice filters) only see ciphertext and
  do not work with encrypted speakers. Proximity, groups and whispering keep working.
- **Replay:** A server admin could resend recorded packets (replay). They cannot read or modify
  them.
- **Mixins on internals:** The mod accesses private fields of Simple Voice Chat (`MicThread.encoder`,
  `AudioChannel.decoder`, `AudioChannel.uuid`, `AudioChannel.client`). After major updates of Simple
  Voice Chat, adjustments may be necessary.
- **The icon is display only:** The icon mixins (`voicechat_e2ee.ui.mixins.json`) are deliberately
  optional. If a spot no longer fits after a Simple Voice Chat update, only the icon is missing there
  (an entry in the log), the game still starts and encryption keeps working.
- **Icon only after the first speech:** Whether another player is encrypted can only be seen from
  their voice packets. Someone who has never spoken has no icon yet.

## Self-test

`src/test/java/voicechate2ee/SelfTest.java` checks encryption, packet format, configuration, the
encoder/decoder wrappers, hearing with encryption switched off, and the detection of encrypted
players, all without Minecraft (76 checks).
