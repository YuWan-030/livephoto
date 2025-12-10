package cn.alini.livephoto.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 枚举录音设备（Windows WASAPI）。失败返回空列表。 */
public class AudioDeviceLister {
    private static volatile List<String> cached = List.of();

    public static List<String> listWasapiDevices(Path ffmpeg) {
        if (cached != null && !cached.isEmpty()) return cached;
        List<String> result = new ArrayList<>();
        try {
            Process p = new ProcessBuilder(
                    ffmpeg.toString(),
                    "-list_devices", "true",
                    "-f", "wasapi",
                    "-i", "dummy"
            ).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    int q1 = line.indexOf('"');
                    int q2 = line.lastIndexOf('"');
                    if (q1 >= 0 && q2 > q1) {
                        String name = line.substring(q1 + 1, q2);
                        if (!name.isBlank()) result.add(name);
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        cached = Collections.unmodifiableList(result);
        return cached;
    }
}