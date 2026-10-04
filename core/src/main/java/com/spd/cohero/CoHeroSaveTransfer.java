package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.Game;
import com.watabou.utils.DeviceCompat;
import com.watabou.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Save-file transfer for CoHero builds.
 *
 * <p>Android mirrors SMM's full-snapshot behavior without depending on SMM.
 * Android and Desktop store snapshots under Documents/spd_saves using the
 * current build's stable application name.</p>
 */
public final class CoHeroSaveTransfer {

    private static final String LOG_PREFIX = "CoHero save transfer: ";


    private CoHeroSaveTransfer() {
    }

    public static void exportSave() {
        if (DeviceCompat.isDesktop()) {
            runDesktopTransferLater(true);
            return;
        }

        try {
            if (DeviceCompat.isAndroid()) {
                exportAndroidSnapshot();
            } else {
                throw new UnsupportedOperationException(
                        "save export is not supported on this platform");
            }
        } catch (Exception e) {
            logFailure("export", e);
            GLog.w(CoHeroMessages.get("save_transfer.export_failed"), new Object[0]);
        }
    }

    public static void importSave() {
        if (DeviceCompat.isDesktop()) {
            runDesktopTransferLater(false);
            return;
        }

        try {
            if (DeviceCompat.isAndroid()) {
                importAndroidSnapshot();
            } else {
                throw new UnsupportedOperationException(
                        "save import is not supported on this platform");
            }
        } catch (Exception e) {
            logFailure("import", e);
            GLog.w(CoHeroMessages.get("save_transfer.import_failed"), new Object[0]);
        }
    }

    private static void runDesktopTransferLater(final boolean export) {
        // Button clicks run while PointerEvent is iterating its event queue.
        // Opening a native desktop dialog synchronously can enqueue focus/pointer
        // events into that same list and trigger ConcurrentModificationException.
        // postRunnable runs after the current input dispatch has returned.
        Game.runOnRenderThread(() -> {
            String operation = export ? "export" : "import";
            try {
                if (export) {
                    if (exportDesktopSnapshot()) {
                        System.out.println(LOG_PREFIX + "Save exported!");
                        GLog.h(
                                CoHeroMessages.get("save_transfer.exported"),
                                new Object[0]);
                    }
                } else {
                    importDesktopSnapshot();
                }
            } catch (Exception e) {
                logFailure(operation, e);
                GLog.w(
                        CoHeroMessages.get(
                                export
                                        ? "save_transfer.export_failed"
                                        : "save_transfer.import_failed"),
                        new Object[0]);
            }
        });
    }

    private static void exportAndroidSnapshot() throws Exception {
        Object context = androidContext();
        if (!ensureAllFilesAccess(context)) {
            return;
        }

        // Flush the active run before taking the external snapshot.
        Dungeon.saveAll();

        File sourceDir = (File) context.getClass()
                .getMethod("getFilesDir")
                .invoke(context);
        File targetDir = androidExternalSaveDirectory(context);

        replaceSnapshot(sourceDir, targetDir, false);
        GLog.h(CoHeroMessages.get("save_transfer.exported"), new Object[0]);
    }

    private static void importAndroidSnapshot() throws Exception {
        Object context = androidContext();
        if (!ensureAllFilesAccess(context)) {
            return;
        }

        File sourceDir = androidExternalSaveDirectory(context);
        File targetDir = (File) context.getClass()
                .getMethod("getFilesDir")
                .invoke(context);

        // Never delete live files until a real external snapshot has been
        // validated. Import is intentionally replacement, not merge.
        if (!hasAnyContent(sourceDir)) {
            GLog.w(CoHeroMessages.get("save_transfer.no_save"), new Object[0]);
            return;
        }

        deleteContents(targetDir);
        copyRecursively(sourceDir, targetDir, true);

        Class<?> processClass = Class.forName("android.os.Process");
        int pid = ((Integer) processClass
                .getMethod("myPid")
                .invoke(null)).intValue();
        processClass
                .getMethod("killProcess", int.class)
                .invoke(null, pid);
    }

    private static boolean exportDesktopSnapshot() throws Exception {
        File sourceDir = desktopSaveDirectory();
        File targetDir = desktopTransferDirectory();

        if (directoriesOverlap(sourceDir, targetDir)) {
            throw new IOException(
                    "Desktop transfer directory overlaps the active save directory");
        }

        if (hasAnyContent(targetDir)
                && !looksLikeSpdSaveDirectory(targetDir)) {
            throw new IOException(
                    "Desktop transfer directory does not look like SPD save data: "
                            + targetDir.getAbsolutePath());
        }

        Dungeon.saveAll();
        replaceSnapshot(sourceDir, targetDir, false);
        return true;
    }

    private static void importDesktopSnapshot() throws Exception {
        File sourceDir = desktopTransferDirectory();
        File targetDir = desktopSaveDirectory();

        if (directoriesOverlap(sourceDir, targetDir)) {
            throw new IOException(
                    "Desktop transfer directory overlaps the active save directory");
        }

        if (!hasAnyContent(sourceDir)) {
            System.out.println(
                    LOG_PREFIX + CoHeroMessages.get("save_transfer.no_save"));
            GLog.w(
                    CoHeroMessages.get("save_transfer.no_save"),
                    new Object[0]);
            return;
        }

        deleteContents(targetDir);
        copyRecursively(sourceDir, targetDir, true);

        // The imported preferences and saves are now on disk, while the current
        // process still has the old state in memory. Exit instead of mixing them.
        System.exit(0);
    }

    private static File desktopTransferDirectory() throws IOException {
        String appName = desktopAppName();
        File documents = new File(System.getProperty("user.home"), "Documents");
        return new File(new File(documents, "spd_saves"), appName)
                .getCanonicalFile();
    }

    private static String desktopAppName() throws IOException {
        Package packageInfo = CoHeroSaveTransfer.class.getPackage();
        String appName = packageInfo == null
                ? null
                : packageInfo.getSpecificationTitle();
        if (appName == null || appName.trim().isEmpty()) {
            appName = System.getProperty("Specification-Title");
        }
        return validateAppName(appName);
    }

    private static String validateAppName(String appName) throws IOException {
        if (appName == null || appName.isEmpty()) {
            throw new IOException("Application name is unavailable");
        }
        if (!appName.equals(appName.trim())
                || ".".equals(appName)
                || "..".equals(appName)
                || appName.endsWith(".")) {
            throw new IOException("Invalid application name: " + appName);
        }

        String invalid = "<>:\"/\\|?*";
        for (int i = 0; i < appName.length(); i++) {
            char c = appName.charAt(i);
            if (c < 32 || invalid.indexOf(c) >= 0) {
                throw new IOException("Invalid application name: " + appName);
            }
        }
        return appName;
    }

    private static File desktopSaveDirectory() throws IOException {
        File directory = FileUtils.getFileHandle("").file().getCanonicalFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IOException(
                    "Desktop save directory is unavailable: "
                            + directory.getAbsolutePath());
        }
        return directory;
    }

    private static void replaceSnapshot(
            File sourceDir,
            File targetDir,
            boolean syncFiles) throws IOException {

        if (targetDir.exists()) {
            if (!targetDir.isDirectory()) {
                throw new IOException(
                        "Export path is not a directory: "
                                + targetDir.getAbsolutePath());
            }
            deleteContents(targetDir);
        } else if (!targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IOException(
                    "Unable to create export directory: "
                            + targetDir.getAbsolutePath());
        }

        copyRecursively(sourceDir, targetDir, syncFiles);
    }

    private static File androidExternalSaveDirectory(Object context)
            throws Exception {

        return new File(
                "/sdcard/Documents/spd_saves/",
                androidAppName(context));
    }

    private static String androidAppName(Object context) throws Exception {
        Object applicationInfo = context.getClass()
                .getMethod("getApplicationInfo")
                .invoke(context);
        Object packageManager = context.getClass()
                .getMethod("getPackageManager")
                .invoke(context);
        Class<?> packageManagerClass =
                Class.forName("android.content.pm.PackageManager");
        Object label = applicationInfo.getClass()
                .getMethod("loadLabel", packageManagerClass)
                .invoke(applicationInfo, packageManager);

        return validateAppName(label == null ? null : label.toString());
    }

    private static Object androidContext() throws Exception {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread
                    .getMethod("currentApplication")
                    .invoke(null);
            if (application == null) {
                throw new IllegalStateException(
                        "Android application context is unavailable");
            }
            return application;
        } catch (ClassNotFoundException notAndroid) {
            throw new UnsupportedOperationException(
                    "Android context is unavailable",
                    notAndroid);
        }
    }

    private static boolean ensureAllFilesAccess(Object context)
            throws Exception {

        Class<?> buildVersionClass =
                Class.forName("android.os.Build$VERSION");
        int sdkInt = buildVersionClass
                .getField("SDK_INT")
                .getInt(null);

        if (sdkInt < 30) {
            return true;
        }

        Class<?> environmentClass =
                Class.forName("android.os.Environment");
        boolean manager = ((Boolean) environmentClass
                .getMethod("isExternalStorageManager")
                .invoke(null)).booleanValue();

        if (manager) {
            return true;
        }

        Class<?> intentClass = Class.forName("android.content.Intent");
        Object intent = intentClass
                .getConstructor(String.class)
                .newInstance(
                        "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");

        Class<?> uriClass = Class.forName("android.net.Uri");
        String packageName = (String) context.getClass()
                .getMethod("getPackageName")
                .invoke(context);
        Object uri = uriClass
                .getMethod(
                        "fromParts",
                        String.class,
                        String.class,
                        String.class)
                .invoke(null, "package", packageName, null);

        intentClass.getMethod("setData", uriClass).invoke(intent, uri);
        intentClass.getMethod("addFlags", int.class)
                .invoke(intent, 0x10000000);
        context.getClass()
                .getMethod("startActivity", intentClass)
                .invoke(context, intent);

        GLog.w(CoHeroMessages.get("save_transfer.permission"), new Object[0]);
        return false;
    }

    private static boolean hasAnyContent(File directory)
            throws IOException {

        return listFiles(directory).length > 0;
    }

    private static boolean looksLikeSpdSaveDirectory(File directory)
            throws IOException {

        // settings.xml is intentionally not sufficient by itself: many libGDX
        // applications use that generic name. These are SPD-specific save
        // artifacts that are stable across Shattered-derived forks.
        if (new File(directory, "rankings.dat").isFile()
                || new File(directory, "badges.dat").isFile()
                || new File(directory, "journal.dat").isFile()) {
            return true;
        }

        for (File file : listFiles(directory)) {
            if (!file.isDirectory()
                    || !file.getName().matches("game\\d+")) {
                continue;
            }
            if (new File(file, "game.dat").isFile()) {
                return true;
            }
        }

        return false;
    }

    private static File[] listFiles(File directory)
            throws IOException {

        if (directory == null
                || !directory.exists()
                || !directory.isDirectory()) {
            return new File[0];
        }

        File[] files = directory.listFiles();
        if (files == null) {
            throw new IOException(
                    "Unable to list directory: "
                            + directory.getAbsolutePath());
        }
        return files;
    }

    private static boolean directoriesOverlap(File first, File second)
            throws IOException {

        File firstCanonical = first.getCanonicalFile();
        File secondCanonical = second.getCanonicalFile();
        return containsDirectory(firstCanonical, secondCanonical)
                || containsDirectory(secondCanonical, firstCanonical);
    }

    private static boolean containsDirectory(File parent, File child) {
        File current = child;
        while (current != null) {
            if (parent.equals(current)) {
                return true;
            }
            current = current.getParentFile();
        }
        return false;
    }

    private static void deleteContents(File directory) throws IOException {
        if (directory == null
                || !directory.exists()
                || !directory.isDirectory()) {
            return;
        }

        File[] files = listFiles(directory);
        for (File file : files) {
            deleteRecursively(file);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = listFiles(file);
            for (File child : children) {
                deleteRecursively(child);
            }
        }

        if (!file.delete() && file.exists()) {
            throw new IOException(
                    "Unable to delete: " + file.getAbsolutePath());
        }
    }

    private static void copyRecursively(
            File source,
            File target,
            boolean syncFiles) throws IOException {

        if (!source.exists()) {
            throw new IOException(
                    "Copy source disappeared: "
                            + source.getAbsolutePath());
        }

        if (source.isDirectory()) {
            if (target.exists()
                    && target.isFile()
                    && !target.delete()) {
                throw new IOException(
                        "Unable to replace file with directory: "
                                + target.getAbsolutePath());
            }
            if (!target.exists()
                    && !target.mkdirs()
                    && !target.isDirectory()) {
                throw new IOException(
                        "Unable to create directory: "
                                + target.getAbsolutePath());
            }

            File[] files = listFiles(source);
            for (File file : files) {
                copyRecursively(
                        file,
                        new File(target, file.getName()),
                        syncFiles);
            }
            return;
        }

        File parent = target.getParentFile();
        if (parent != null
                && !parent.exists()
                && !parent.mkdirs()
                && !parent.isDirectory()) {
            throw new IOException(
                    "Unable to create directory: "
                            + parent.getAbsolutePath());
        }

        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }

            output.flush();
            if (syncFiles) {
                try {
                    output.getFD().sync();
                } catch (IOException ignored) {
                    // fsync is best effort; the copy itself has already succeeded.
                }
            }
        }
    }

    private static void logFailure(String operation, Exception error) {
        System.out.println(
                LOG_PREFIX + operation + " failed: "
                        + error.getClass().getSimpleName() + ": "
                        + error.getMessage());
        error.printStackTrace();
    }
}
