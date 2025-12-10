package cn.alini.livephoto.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class LivePhotoPlaceholder {
    private static final byte[] SOI = new byte[]{(byte) 0xFF, (byte) 0xD8};
    private static final byte[] APP1 = new byte[]{(byte) 0xFF, (byte) 0xE1};
    private static final byte[] XMP_HEADER = "http://ns.adobe.com/xap/1.0/\0".getBytes();

    /**
     * 写入 XMP（包含 Live Photo UUID）并输出封面 + MOV。
     * 注意：此实现针对 JPG；如需 HEIC 需另行处理。
     */
    public static void build(Path coverJpg, Path movSrc, Path outJpg, Path outMov, String uuid) throws IOException {
        byte[] jpg = Files.readAllBytes(coverJpg);
        if (jpg.length < 2 || jpg[0] != (byte) 0xFF || jpg[1] != (byte) 0xD8) {
            throw new IOException("not a jpeg");
        }

        String xmp = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description xmlns:apple="http://ns.apple.com/namespace/1.0/">
                      <apple:ContentIdentifier>%s</apple:ContentIdentifier>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """.formatted(uuid);
        byte[] xmpBytes = xmp.getBytes(StandardCharsets.UTF_8);
        int len = XMP_HEADER.length + xmpBytes.length + 2;

        var bos = new java.io.ByteArrayOutputStream();
        bos.write(SOI);
        bos.write(APP1);
        bos.write((len >> 8) & 0xFF);
        bos.write(len & 0xFF);
        bos.write(XMP_HEADER);
        bos.write(xmpBytes);
        bos.write(jpg, 2, jpg.length - 2);

        Files.write(outJpg, bos.toByteArray(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.copy(movSrc, outMov, StandardCopyOption.REPLACE_EXISTING);
    }
}