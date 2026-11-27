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
        if (args.contains("hevc_")) {
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
                    "-loglevel", "error"));
            cmd.addAll(hwInput(outputArgs));
            cmd.addAll(java.util.Arrays.asList("-f", "lavfi", "-i", "color=c=black:s=" + ew + "x" + eh + ":r=" + fps, "-frames:v", "4"));
            if (!hwFilter(outputArgs).isEmpty()) {
                cmd.addAll(java.util.Arrays.asList("-vf", hwFilter(outputArgs).substring(1)));
            }
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
                    "-loglevel", "error"));
            cmd.addAll(hwInput(outputArgs));
            cmd.addAll(java.util.Arrays.asList("-f", "lavfi", "-i", "color=c=black:s=256x256:d=0.5:r=30", "-frames:v", "8"));
            if (!hwFilter(outputArgs).isEmpty()) {
                cmd.addAll(java.util.Arrays.asList("-vf", hwFilter(outputArgs).substring(1)));
            }
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

    /** Which GPU encoder family works here: "nvenc", "amf", "vaapi", "qsv", or "" for none. Found once with tiny test encodes. */
    private static volatile String gpuKind;

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");

    static String gpuKind() {
        String kind = gpuKind;
        if (kind != null) {
            return kind;
        }
        if (probe("-c:v h264_nvenc")) {
            kind = "nvenc";
        } else if (WINDOWS && probe("-c:v h264_amf")) {
            kind = "amf";
        } else if (!WINDOWS && vaapiDevice() != null && probe("-c:v h264_vaapi")) {
            kind = "vaapi";
        } else if (probe("-c:v h264_qsv")) {
            kind = "qsv";
        } else {
            kind = "";
        }
        gpuKind = kind;
        SubFrameMod.LOG.info(kind.isEmpty() ? "GPU encoding is not available (no NVIDIA, AMD or Intel encoder works with this ffmpeg); using the CPU encoder"
                : "GPU encoding: " + gpuName(kind));
        return kind;
    }

    private static String gpuName(String kind) {
        if ("nvenc".equals(kind)) {
            return "NVIDIA (NVENC)";
        }
        if ("amf".equals(kind)) {
            return "AMD (AMF)";
        }
        if ("vaapi".equals(kind)) {
            return "VAAPI (AMD or Intel)";
        }
        return "qsv".equals(kind) ? "Intel (Quick Sync)" : "none";
    }

    /** True if ffmpeg can encode on the graphics card here (NVIDIA, AMD or Intel). */
    static boolean gpuAvailable() {
        return !gpuKind().isEmpty();
    }

    /** True for output arguments that encode on a graphics card. */
    static boolean isGpu(String args) {
        return args.contains("_nvenc") || args.contains("_amf") || args.contains("_vaapi") || args.contains("_qsv");
    }

    /** Linux VAAPI needs the GPU's render node; the first one present (vaapiDevice in subframe.properties overrides it). */
    static String vaapiDevice() {
        String set = SubFrameMod.vaapiDevice;
        if (set != null && !set.isEmpty()) {
            return set;
        }
        for (int i = 128; i < 136; i++) {
            java.io.File f = new java.io.File("/dev/dri/renderD" + i);
            if (f.exists()) {
                return f.getPath();
            }
        }
        return null;
    }

    /** Options that go before ffmpeg's input for these output arguments (the VAAPI device). */
    static java.util.List<String> hwInput(String args) {
        if (args.contains("_vaapi") && vaapiDevice() != null) {
            return java.util.Arrays.asList("-vaapi_device", vaapiDevice());
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Colour. The game's frames are RGB and video is YUV. Left to itself, ffmpeg converts with the old SD matrix (BT.601)
     * and writes no colour information, so players and editors guess (most take BT.709 for HD video) and colours come out
     * slightly off: reds towards orange, greens towards yellow. The conversion is made explicit as BT.709 and written into
     * the file. Normal video uses limited range (16-235, what every player expects); lossless output keeps the full 0-255
     * range so no shades are merged. Left alone when the player's own encoder arguments already set colour.
     */
    static String colorFilter(String args) {
        if (ownColor(args)) {
            return "";
        }
        // setparams stamps the same values on every frame: newer ffmpeg takes the file's colour tags from the frames
        String range = lossless(args) ? "full" : "tv";
        return ",scale=out_color_matrix=bt709:out_range=" + range
                + ",setparams=range=" + range + ":color_primaries=bt709:color_trc=bt709:colorspace=bt709";
    }

    /** The colour tags written into the file, matching {@link #colorFilter}. */
    static java.util.List<String> colorTags(String args) {
        if (ownColor(args)) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.asList("-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
                "-color_range", lossless(args) ? "pc" : "tv");
    }

    /** Lossless output settings: NVENC's lossless tune, or x264/x265 at quantiser or CRF 0. */
    static boolean lossless(String args) {
        return args.contains("-tune lossless") || args.matches("(?s).*(^|\\s)-(qp|crf)\\s+0(\\s.*|$)");
    }

    private static boolean ownColor(String args) {
        return args.contains("-color") || args.contains("colorspace") || args.contains("out_color_matrix") || args.contains("zscale");
    }

    /** What the filter chain needs at its end for these output arguments: VAAPI encodes frames uploaded to the GPU. */
    static String hwFilter(String args) {
        return args.contains("_vaapi") ? ",format=nv12,hwupload" : "";
    }

    /**
     * Output arguments for AMD (AMF, Windows), VAAPI (Linux, AMD or Intel) or Intel Quick Sync: constant QP at gpuQuality,
     * HEVC where the card has it, else H.264. None of them has a lossless mode, so hevcLossless applies to NVENC only.
     */
    private static String otherGpuArgs(String kind) {
        int q = SubFrameMod.gpuQp;
        String[] choices;
        if ("amf".equals(kind)) {
            choices = new String[]{
                    "-c:v hevc_amf -usage transcoding -quality quality -rc cqp -qp_i " + q + " -qp_p " + q + " -pix_fmt yuv420p -tag:v hvc1",
                    "-c:v h264_amf -usage transcoding -quality quality -rc cqp -qp_i " + q + " -qp_p " + q + " -qp_b " + q + " -pix_fmt yuv420p"};
        } else if ("vaapi".equals(kind)) {
            choices = new String[]{"-c:v hevc_vaapi -rc_mode CQP -qp " + q + " -tag:v hvc1", "-c:v h264_vaapi -rc_mode CQP -qp " + q};
        } else {
            choices = new String[]{"-c:v hevc_qsv -preset slow -global_quality " + q + " -pix_fmt nv12 -tag:v hvc1",
                    "-c:v h264_qsv -preset slow -global_quality " + q + " -pix_fmt nv12"};
        }
        for (String c : choices) {
            if (probe(c)) {
                return c;
            }
        }
        return null;
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
        String kind = gpu ? gpuKind() : "";
        if (gpu && !kind.isEmpty() && !"nvenc".equals(kind)) {
            String other = otherGpuArgs(kind);
            if (other != null) {
                return other;
            }
            SubFrameMod.LOG.warn("The " + gpuName(kind) + " encoder rejected these settings; using the CPU encoder");
            return blended ? CPU_BLENDED : CPU_RAW;
        }
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
            return "CPU";
        }
        if ("gpu".equals(mode)) {
            String kind = gpuKind;
            return kind == null ? "GPU" : kind.isEmpty() ? "GPU (none)" : "GPU (" + kind.toUpperCase(java.util.Locale.ROOT) + ")";
        }
        return "Auto";
    }

    static String nextMode() {
        String mode = SubFrameMod.encoderMode;
        return "auto".equals(mode) ? "cpu" : "cpu".equals(mode) ? "gpu" : "auto";
    }
}
