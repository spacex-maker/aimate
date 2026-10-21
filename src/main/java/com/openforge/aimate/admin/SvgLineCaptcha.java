package com.openforge.aimate.admin;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/** SVG line-noise captcha (no AWT; headless-safe). */
public final class SvgLineCaptcha {

    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RND = new SecureRandom();

    private SvgLineCaptcha() {}

    public static String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(RND.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    public static String toDataUrl(String code, int width, int height) {
        String svg = buildSvg(code, width, height);
        String b64 = java.util.Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + b64;
    }

    private static String buildSvg(String code, int width, int height) {
        String safe = escapeXml(code);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">",
                width, height, width, height));
        sb.append(String.format(Locale.ROOT,
                "<rect width=\"%d\" height=\"%d\" fill=\"#f4f6f9\"/>", width, height));
        int lines = 4 + RND.nextInt(4);
        for (int i = 0; i < lines; i++) {
            int x1 = RND.nextInt(width);
            int y1 = RND.nextInt(height);
            int x2 = RND.nextInt(width);
            int y2 = RND.nextInt(height);
            String color = String.format(Locale.ROOT, "#%02x%02x%02x",
                    140 + RND.nextInt(80), 140 + RND.nextInt(80), 140 + RND.nextInt(80));
            sb.append(String.format(Locale.ROOT,
                    "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.2\" opacity=\"0.55\"/>",
                    x1, y1, x2, y2, color));
        }
        int dots = 28 + RND.nextInt(20);
        for (int i = 0; i < dots; i++) {
            sb.append(String.format(Locale.ROOT,
                    "<circle cx=\"%d\" cy=\"%d\" r=\"1.1\" fill=\"#8899aa\" opacity=\"0.35\"/>",
                    RND.nextInt(width), RND.nextInt(height)));
        }
        double rot = (RND.nextDouble() - 0.5) * 18;
        int fontSize = Math.min(width, height) / 3;
        int textX = (width - code.length() * fontSize * 3 / 5) / 2;
        int textY = height / 2 + fontSize / 3;
        sb.append(String.format(Locale.ROOT,
                "<text x=\"%d\" y=\"%d\" font-family=\"Arial,Helvetica,sans-serif\" font-size=\"%d\" "
                        + "font-weight=\"600\" fill=\"#2d3748\" transform=\"rotate(%.2f %d %d)\">%s</text>",
                textX, textY, fontSize, rot, width / 2, height / 2, safe));
        sb.append("</svg>");
        return sb.toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
