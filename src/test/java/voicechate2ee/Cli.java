package voicechate2ee;

import voicechate2ee.crypto.OpusEnvelope;
import voicechate2ee.crypto.PacketCipher;
import voicechate2ee.crypto.VoiceKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Helper program for cross-checks with a real libopus: encrypts/decrypts all *.bin files
 * of a folder. Usage: {@code Cli seal|open <passphrase> <input folder> <output folder>}
 */
public final class Cli {

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        VoiceKey key = VoiceKey.fromPassphrase(args[1]);
        Path in = Path.of(args[2]);
        Path out = Path.of(args[3]);
        Files.createDirectories(out);
        PacketCipher cipher = new PacketCipher();
        List<Path> files;
        try (Stream<Path> s = Files.list(in)) {
            files = s.filter(p -> p.toString().endsWith(".bin")).sorted().toList();
        }
        for (Path f : files) {
            byte[] data = Files.readAllBytes(f);
            byte[] result;
            if (mode.equals("seal")) {
                result = OpusEnvelope.wrap(cipher.seal(data, key));
            } else {
                byte[] sealed = OpusEnvelope.unwrap(data);
                result = sealed == null ? null : PacketCipher.open(sealed, key);
            }
            if (result == null) {
                System.out.println("rejected: " + f.getFileName());
                continue;
            }
            Files.write(out.resolve(f.getFileName()), result);
        }
    }

    private Cli() throws IOException {
    }
}
