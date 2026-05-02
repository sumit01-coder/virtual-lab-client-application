package com.virtuallab.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ImageUrlSanitizer {

    private static final Pattern HTTP_URL = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    private ImageUrlSanitizer() {}

    public static List<String> keepDecodable(List<String> urls) {
        List<String> out = new ArrayList<>();
        if (urls == null) return out;
        for (String url : urls) {
            String s = url == null ? "" : url.trim();
            if (s.isEmpty()) continue;
            if (!isLikelyUnsupported(s) && !isLikelyBrokenUrl(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean isLikelyUnsupported(String url) {
        String clean = url.toLowerCase(Locale.US);
        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        return clean.endsWith(".heic")
                || clean.endsWith(".heif")
                || clean.endsWith(".avif");
    }

    private static boolean isLikelyBrokenUrl(String url) {
        String clean = url.toLowerCase(Locale.US).trim();
        if (!HTTP_URL.matcher(clean).matches()) return true;

        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        int h = clean.indexOf('#');
        if (h >= 0) clean = clean.substring(0, h);

        int slash = clean.lastIndexOf('/');
        String file = slash >= 0 ? clean.substring(slash + 1) : clean;
        if (file.isEmpty() || !file.contains(".")) return true;

        int dot = file.lastIndexOf('.');
        String ext = dot >= 0 ? file.substring(dot + 1) : "";
        if (ext.length() < 3) return true;

        return !(ext.equals("jpg")
                || ext.equals("jpeg")
                || ext.equals("png")
                || ext.equals("webp")
                || ext.equals("gif")
                || ext.equals("bmp"));
    }
}
