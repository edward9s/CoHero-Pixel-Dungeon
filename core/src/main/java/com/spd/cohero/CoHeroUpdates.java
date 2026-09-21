package com.spd.cohero;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight GitHub release checker for CoHero itself. */
public final class CoHeroUpdates {

    private static final String RELEASES_URL =
            "https://api.github.com/repos/edward9s/CoHero-Pixel-Dungeon/releases?per_page=30";
    private static final long CHECK_DELAY = 1000L * 60L * 60L;
    private static final Pattern RELEASE_VERSION = Pattern.compile(
            "\\\"tag_name\\\"\\s*:\\s*\\\"CoHero-v([0-9]+(?:\\.[0-9]+)*)-SPD-[^\\\"]+\\\"");

    private static long lastCheck = 0L;
    private static boolean checking = false;
    private static String latestVersion;

    private CoHeroUpdates() {
    }

    public static synchronized void checkForUpdate() {
        long now = System.currentTimeMillis();
        if (checking || (lastCheck != 0L && now - lastCheck < CHECK_DELAY)) {
            return;
        }
        checking = true;

        try {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    checkRemote();
                }
            }, "CoHero-Update-Check");
            worker.start();
        } catch (Throwable t) {
            finishFailedCheck();
        }
    }

    private static void checkRemote() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "CoHero-Pixel-Dungeon");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                finishFailedCheck();
                return;
            }

            String response;
            InputStream stream = connection.getInputStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                try {
                    StringBuilder json = new StringBuilder();
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        json.append(buffer, 0, read);
                    }
                    response = json.toString();
                } finally {
                    reader.close();
                }
            } finally {
                stream.close();
            }

            String bestVersion = null;
            Matcher matcher = RELEASE_VERSION.matcher(response);
            while (matcher.find()) {
                String version = matcher.group(1);
                if (bestVersion == null || compareVersions(version, bestVersion) > 0) {
                    bestVersion = version;
                }
            }

            synchronized (CoHeroUpdates.class) {
                if (bestVersion != null
                        && compareVersions(bestVersion, CoHeroVersion.version()) > 0) {
                    latestVersion = bestVersion;
                } else {
                    latestVersion = null;
                }
                lastCheck = System.currentTimeMillis();
                checking = false;
            }
        } catch (Throwable t) {
            finishFailedCheck();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static synchronized void finishFailedCheck() {
        checking = false;
        // Leave lastCheck unset so a later inventory opening can retry.
    }

    public static synchronized boolean updateAvailable() {
        return latestVersion != null;
    }

    public static synchronized String latestVersion() {
        return latestVersion;
    }

    static int compareVersions(String a, String b) {
        String[] left = a == null ? new String[0] : a.split("\\.");
        String[] right = b == null ? new String[0] : b.split("\\.");
        int count = Math.max(left.length, right.length);

        for (int i = 0; i < count; i++) {
            int l = i < left.length ? parsePart(left[i]) : 0;
            int r = i < right.length ? parsePart(right[i]) : 0;
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (Exception e) {
            return 0;
        }
    }
}
