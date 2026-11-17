# Voice Chat E2EE (Fabric, Minecraft 26.3)

A client-side mod for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that encrypts
voice chat with a shared **key**, or only for the players on your **whitelist**. Only players who have entered the
same key (or whom you whitelisted) can hear you. The server (and its admin) only sees ciphertext.

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

## Whitelist mode: choose who hears you

With a shared key, everybody who has the key hears you, and you cannot take the key back from one person. In
**whitelist mode** you pick the players who may hear you, and you can remove them again at any time.

1. Everybody involved needs this mod, as before, and an online-mode account (see Limitations).
2. Turn it on: `/vcwl mode whitelist`.
3. Add the players who may hear you: `/vcwl add <player>`. They must be online when you add them.
4. They add you the same way. **You hear someone only if they whitelisted you, and they hear you only if you
   whitelisted them.** Players you did not add (and strangers) hear silence.

The whitelist does not need a shared key, so nothing has to be copied around. The incognito icon shows up next to
everybody you can hear this way.

| Command | Effect |
| --- | --- |
| `/vcwl` or `/vcwl list` | mode, and for every player whether your key grant is queued locally (delivery is not confirmed) and whether you have theirs |
| `/vcwl add <player>` | the player (online) may hear you from now on; their key is exchanged automatically |
| `/vcwl remove <name>` | they cannot hear you anymore. Your voice key is replaced and the others get the new one. You stop hearing them, too |
| `/vcwl mode whitelist` / `key` | send to your whitelist only / send with the shared key (the original behavior, still the default) |
| `/vcwl resend` | hand your key to everybody on the list again |

The mode and the list are stored in `config/voicechat-e2ee-whitelist.txt`. The encryption switch (incognito button)
turns whitelist encryption on and off just like it does for the shared key.

### How whitelist mode works

- Every player has their own random voice key, which exists only in memory. Your voice is encrypted with it
  (AES-256-GCM, same packet format as before). It is replaced whenever you remove somebody.
- To hand the key to a whitelisted player, the mod sends them **two whispers** (`/w`) with some unreadable text in
  them, and hides these whispers in your chat and theirs. The key inside is encrypted (RSA-OAEP) with the **Minecraft
  profile key** of the receiver, the public part of which every client gets from the server and which Mojang has
  certified for that account. Only the receiver can open it.
- The whisper is also signed with the profile key of the sender. So the receiver knows it is really you and that it
  is meant for them, and a server cannot slip in a key of its own. There is nothing to compare by hand.
- Packets carry a counter, and the receiver rejects duplicate packets and packets outside a 64-packet reordering
  window. It also rejects grants for epochs older than the newest accepted epoch. This prevents ordinary captured
  packets and old grants from being replayed, but cannot stop a server from delaying a never-before-delivered valid
  packet until it is first accepted inside the window. The speaker's UUID is authenticated, so packets cannot be
  passed off as another player's audio.
- If you hear a whitelisted player but have no key from them (for example after restarting your game), the mod asks
  them for it with a short whisper and they answer automatically.
- Whispers are paced (a burst of a few, then one per second) so servers do not kick you for chat spam.

### Whitelist limitations

- **The server can see the whispers.** It cannot read them, but it can see that you exchange keys with a player, so
  it can tell who is on your whitelist. It also still sees who speaks when.
- **Whispers must work.** A server that blocks `/w`, a chat plugin that removes unusual characters, or a plugin that
  delivers whispers as system messages (instead of player chat) stops the key exchange. Nothing breaks, the other
  player just does not hear you.
- **A whitelisted player without this mod** sees two lines of strange characters in their chat. Only add players who
  have the mod.
- **Online-mode accounts only.** The keys come from the Minecraft profile key, which offline-mode accounts do not have.
- **No forward secrecy.** The profile keys change about every day, but a key grant that was recorded can be opened by
  anybody who later gets that old profile private key.
- **A whitelisted player can record you** and tell others. Removing somebody only stops them from hearing what you say
  from then on.
- **The whitelist has no buttons in the settings window yet.** Use `/vcwl`.

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
- In whitelist mode the key is not derived from a passphrase but is random per player and handed out as described
  above (`voicechate2ee.whitelist`). A mixin (`ChatListenerMixin`) takes the key whispers out of the chat and another
  one (`KeyPairAccessor`) gives access to your profile key; both are optional like the UI mixins.
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
- **Replay:** Shared-key mode does not have replay protection. Whitelist mode rejects duplicate packets and stale
  epochs, but a valid packet that was captured and delayed before its first delivery may still be accepted once if
  it arrives within the receiver's 64-packet window. The server can also delay or drop key whispers; queued grants
  are retried, but vanilla whispers provide no end-to-end delivery acknowledgement.
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
players, and the logic behind the settings window and the quick toggle, all without Minecraft.
`WhitelistSelfTest.java` (called by `SelfTest`) covers the whitelist: the chat encoding, the key grants with real
RSA keys (forgery, wrong recipient, tampering), packet encryption, replay protection, the whitelist file, and the
whole voice path from encoder to decoder including removing a player.
