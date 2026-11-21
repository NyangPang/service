package com.commicom.itda.infra.video.storyboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** FFmpeg / ffprobe 실행 도우미 */
final class Ffmpeg {

    private final String ffmpeg;
    private final String ffprobe;

    Ffmpeg(String ffmpegPath) {
        this.ffmpeg = ffmpegPath;
        this.ffprobe = ffmpegPath.endsWith("ffmpeg")
                ? ffmpegPath.substring(0, ffmpegPath.length() - "ffmpeg".length()) + "ffprobe"
                : "ffprobe";
    }

    void run(List<String> args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y"));
        cmd.addAll(args);
        exec(cmd, "FFmpeg");
    }

    /** 미디어 길이(초) */
    double duration(Path file) throws IOException, InterruptedException {
        String out = exec(List.of(ffprobe, "-v", "error", "-show_entries", "format=duration",
                "-of", "csv=p=0", file.toString()), "ffprobe");
        return Double.parseDouble(out.strip());
    }

    static String exec(List<String> cmd, String name) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException(name + " 실패 (exit=" + exit + ")\n" + String.join(" ", cmd) + "\n" + out);
        }
        return out;
    }
}
