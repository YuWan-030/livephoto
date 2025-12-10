package cn.alini.livephoto.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MotionPhotoPlaceholder {
    private static final byte[] SOI = new byte[]{(byte)0xFF, (byte)0xD8};
    private static final byte[] APP1 = new byte[]{(byte)0xFF, (byte)0xE1};
    private static final byte[] XMP_HEADER = "http://ns.adobe.com/xap/1.0/\0".getBytes();

    public static void build(Path coverJpg, Path mp4, Path outJpg) throws IOException {
        byte[] jpg = Files.readAllBytes(coverJpg);
        if (jpg.length < 2 || jpg[0] != (byte)0xFF || jpg[1] != (byte)0xD8) {
            throw new IOException("not a jpeg");
        }

        // 先写原始 JPG SOI
        var bos = new java.io.ByteArrayOutputStream();
        bos.write(SOI);

        // 写 XMP APP1 段（长度 = header + xmp）
        String xmp = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description xmlns:GCamera="http://ns.google.com/photos/1.0/camera/">
                      <GCamera:MicroVideo>1</GCamera:MicroVideo>
                      <GCamera:MicroVideoVersion>1</GCamera:MicroVideoVersion>
                      <GCamera:MicroVideoOffset>%d</GCamera:MicroVideoOffset>
                      <GCamera:MicroVideoPresentationTimestampUs>0</GCamera:MicroVideoPresentationTimestampUs>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
        // 先占位 offset，后面替换
        xmp = xmp.formatted(0);
        byte[] xmpBytes = xmp.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = XMP_HEADER.length + xmpBytes.length + 2; // +2 for length bytes themselves? Actually length excludes APP1 marker itself, includes size bytes; see below
        // 写 APP1 marker + length
        bos.write(APP1);
        bos.write((len >> 8) & 0xFF);
        bos.write(len & 0xFF);
        bos.write(XMP_HEADER);
        bos.write(xmpBytes);

        // 写剩余 JPG（去掉 SOI 即可）
        bos.write(jpg, 2, jpg.length - 2);

        // 计算真实偏移：当前 bos 长度
        int offset = bos.size();
        // 重新生成 XMP 段，带真实 offset
        xmp = xmp.formatted(offset);
        xmpBytes = xmp.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        len = XMP_HEADER.length + xmpBytes.length + 2;
        var bos2 = new java.io.ByteArrayOutputStream();
        bos2.write(SOI);
        bos2.write(APP1);
        bos2.write((len >> 8) & 0xFF);
        bos2.write(len & 0xFF);
        bos2.write(XMP_HEADER);
        bos2.write(xmpBytes);
        bos2.write(jpg, 2, jpg.length - 2);

        // 追加 MP4
        bos2.write(Files.readAllBytes(mp4));

        Files.write(outJpg, bos2.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}