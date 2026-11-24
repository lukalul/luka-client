package dev.subframe;

import java.util.concurrent.TimeUnit;

/**
 * Picks the ffmpeg output settings. With "auto" (the default) the graphics card's NVENC encoder is used when ffmpeg
 * has it and a short test encode works: it is several times faster than x264 for raw high-fps output and does not
 * compete with the game for the CPU. Settings the user changed in the config always win.
 */
final class Encoders {
    static final String CPU_BLENDED = "-c:v libx264 -preset medium -crf 12 -pix_fmt yuv420p";
    static final String CPU_RAW = "-c:v libx264 -preset veryfast -qp 0 -pix_fmt yuv444p";
    /**
     * GPU encodes use constant QP (the same quantiser for every frame, like OBS's "CQP") plus NVENC's quality tuning:
     * high-quality tuning, two-pass, look-ahead and adaptive quantisation. A constant-quality VBR target (-cq) spends far
     * fewer bits for the same number: measured on real footage at 60 fps, -cq 8 gave 41 Mbit/s where QP 17 gave 65.
     */
    private static final String TUNING = "-preset p5 -tune hq -multipass qres -rc-lookahead 20 -spatial-aq 1 -temporal-aq 1 -bf 2";
    private static final String TUNING_PLAIN = "-preset p5";

    private static String gpuBlended(boolean tuned) {
        return "-c:v h264_nvenc " + (tuned ? TUNING : TUNING_PLAIN) + " -rc constqp -qp " + SubFrameMod.gpuQp + " -pix_fmt yuv420p";
    }

    /** 4:4:4 keeps full colour resolution (sharp HUD text and crosshairs) and is what editors expect for raw high-fps frames. */
    private static String gpuRaw(boolean tuned) {
        return "-c:v h264_nvenc " + (tuned ? TUNING : TUNING_PLAIN) + " -rc constqp -qp " + SubFrameMod.gpuQp
                + " -profile:v high444p -pix_fmt yuv444p";
    }

    /**
     * The highest frame rate the GPU encoder's level allows at this size: H.264 about 4.18 M macroblocks per second (the
     * level NVENC accepts), HEVC level 6.2 about 4.28 G luma samples per second. Both with a little margin.
     */
    static int gpuMaxFps(String args, int w, int h) {
        double perSecond, perFrame;
        if (args.contains("hevc_nvenc")) {
            perSecond = 4.0e9;
            perFrame = (double) ((w + 7) / 8 * 8) * ((h + 7) / 8 * 8);
        } else {
            perSecond = 4.0e6;
            perFrame = (double) ((w + 15) / 16) * ((h + 15) / 16);
        }
        return (int) Math.max(1, Math.floor(perSecond / perFrame));
    }

    private static final java.util.Map<String, Boolean> rates = new java.util.concurrent.ConcurrentHashMap<String, Boolean>();

    /** True if the encoder takes this size and frame rate: a short test encode of four frames (each case tried once). */
    static boolean acceptsRate(String outputArgs, int w, int h, int fps) {
        int ew = w / 2 * 2, eh = h / 2 * 2;
        String key = outputArgs + "|" + ew + "x" + eh + "@" + fps;
        Boolean cached = rates.get(key);
        if (cached != null) {
            return cached;
        }
        boolean ok = false;
        try {
            java.util.List<String> cmd = new java.util.ArrayList<String>(java.util.Arrays.asList(SubFrameMod.ffmpegPath, "-hide_banner",
                    "-loglevel", "error", "-f", "lavfi", "-i", "color=c=black:s=" + ew + "x" + eh + ":r=" + fps, "-frames:v", "4"));
            cmd.addAll(java.util.Arrays.asList(outputArgs.trim().split("\\s+")));
            cmd.addAll(java.util.Arrays.asList("-f", "null", "-"));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            java.io.InputStream in = p.getInputStream();
            byte[] b = new byte[1024];
            while (in.read(b) > 0) {
                // discard
            }
            ok = p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (!ok) {
                p.destroyForcibly();
            }
        } catch (Exception e) {
            ok = false;
        }
        rates.put(key, ok);
        return ok;
    }

    private static volatile Boolean gpuWorks;

    /** HEVC on the GPU: its level limits allow far higher frame rates than H.264's (e.g. 960 fps at 1080p). Null when ffmpeg or the driver refuse it. */
    static String hevcGpuArgs() {
        // the same family of settings the "blur" tool's h265 preset and common high-quality NVENC setups use: slow preset, quality-targeted
        // VBR with no bitrate cap, adaptive quantisation and 10-bit colour; the quality number is gpuQuality (lower = better)
        if (SubFrameMod.hevcLossless) {
            String lossless = "-c:v hevc_nvenc -preset p7 -tune lossless -profile:v rext -pix_fmt yuv444p -tag:v hvc1";
            if (probe(lossless)) {
                return lossless;
            }
        }
        String best = "-c:v hevc_nvenc -preset p7 -tune hq -rc vbr -cq " + SubFrameMod.gpuQp + " -b:v 0 -spatial-aq 1 -aq-strength 8 -temporal-aq 1"
                + " -rc-lookahead 32 -profile:v main10 -pix_fmt p010le -tag:v hvc1";
        if (probe(best)) {
            return best;
        }
        String plain = "-c:v hevc_nvenc -preset p5 -rc constqp -qp " + SubFrameMod.gpuQp + " -pix_fmt yuv420p -tag:v hvc1";
        return probe(plain) ? plain : null;
    }

    private static final java.util.Map<String, Boolean> probes = new java.util.concurrent.ConcurrentHashMap<String, Boolean>();

    /** True if ffmpeg can encode a tiny test clip with these output arguments. Each distinct set is tried once. */
    private static boolean probe(String outputArgs) {
        Boolean cached = probes.get(outputArgs);
        if (cached != null) {
            return cached;
        }
        boolean ok = false;
        try {
            java.util.List<String> cmd = new java.util.ArrayList<String>(java.util.Arrays.asList(SubFrameMod.ffmpegPath, "-hide_banner",
                    "-loglevel", "error", "-f", "lavfi", "-i", "color=c=black:s=256x256:d=0.5:r=30", "-frames:v", "8"));
            cmd.addAll(java.util.Arrays.asList(outputArgs.trim().split("\\s+")));
            cmd.addAll(java.util.Arrays.asList("-f", "null", "-"));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            final java.io.InputStream in = p.getInputStream();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        byte[] b = new byte[1024];
                        while (in.read(b) > 0) {
                            // discard
                        }
                    } catch (java.io.IOException ignored) {
                    }
                }
            }).start();
            if (p.waitFor(15, TimeUnit.SECONDS)) {
                ok = p.exitValue() == 0;
            } else {
                p.destroyForcibly();
            }
        } catch (Exception e) {
            ok = false;
        }
        probes.put(outputArgs, ok);
        return ok;
    }

    /** True if ffmpeg can encode with NVENC on this machine. Tested once with a tiny encode. */
    static boolean gpuAvailable() {
        Boolean cached = gpuWorks;
        if (cached != null) {
            return cached;
        }
        boolean ok = probe("-c:v h264_nvenc");
        gpuWorks = ok;
        SubFrameMod.LOG.info("GPU (NVENC) encoding " + (ok ? "is available" : "is not available; using the CPU encoder"));
        return ok;
    }

    /** The GPU arguments for this render: the tuned set if this ffmpeg and driver accept it, else plain constant QP, else null. */
    private static String gpuArgs(boolean blended) {
        String tuned = blended ? gpuBlended(true) : gpuRaw(true);
        if (probe(tuned)) {
            return tuned;
        }
        String plain = blended ? gpuBlended(false) : gpuRaw(false);
        if (probe(plain)) {
            SubFrameMod.LOG.info("NVENC quality tuning is not supported here; using plain constant QP");
            return plain;
        }
        return null;
    }

    private static boolean customised(boolean blended) {
        return blended ? !CPU_BLENDED.equals(SubFrameMod.encoderArgs) : !CPU_RAW.equals(SubFrameMod.rawEncoderArgs);
    }

    /** The ffmpeg output arguments for this render. */
    static String args(boolean blended) {
        if (customised(blended)) {
            return blended ? SubFrameMod.encoderArgs : SubFrameMod.rawEncoderArgs;
        }
        String mode = SubFrameMod.encoderMode;
        boolean gpu = "gpu".equals(mode) || ("auto".equals(mode) && gpuAvailable());
        if (gpu) {
            if (SubFrameMod.hevcLossless) {
                // lossless HEVC for every GPU render (blended or raw), not only the very high frame rates
                String lossless = hevcGpuArgs();
                if (lossless != null) {
                    return lossless;
                }
            }
            String g = gpuArgs(blended);
            if (g != null) {
                return g;
            }
            SubFrameMod.LOG.warn("The GPU encoder rejected these settings; using the CPU encoder");
        }
        return blended ? CPU_BLENDED : CPU_RAW;
    }

    static String label() {
        String mode = SubFrameMod.encoderMode;
        if ("cpu".equals(mode)) {
            return "CPU (x264)";
        }
        if ("gpu".equals(mode)) {
            return "GPU (NVENC)";
        }
        return "Auto";
    }

    static String nextMode() {
        String mode = SubFrameMod.encoderMode;
        return "auto".equals(mode) ? "cpu" : "cpu".equals(mode) ? "gpu" : "auto";
    }
}
