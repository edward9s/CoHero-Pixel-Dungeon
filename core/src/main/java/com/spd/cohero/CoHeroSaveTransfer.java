package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Android save-file transfer for CoHero builds.
 *
 * <p>This intentionally mirrors SMM's transfer contract without depending on
 * SMM: export flushes the live run before replacing the external snapshot;
 * import validates a non-empty snapshot before deleting any live files.</p>
 */
public final class CoHeroSaveTransfer {

    private static final String LOG_PREFIX = "CoHero save transfer: ";

    private CoHeroSaveTransfer() {
    }

    public static void exportSave() {
        try {
            exportSnapshot();
        } catch (Exception e) {
            logFailure("export", e);
            GLog.w(CoHeroMessages.get("save_transfer.export_failed"), new Object[0]);
        }
    }

    public static void importSave() {
        try {
            importSnapshot();
        } catch (Exception e) {
            logFailure("import", e);
            GLog.w(CoHeroMessages.get("save_transfer.import_failed"), new Object[0]);
        }
    }

    private static void exportSnapshot() throws Exception {
        Object context = androidContext();
        if (!ensureAllFilesAccess(context)) {
            return;
        }

        // Flush the active run before taking the external snapshot.
        Dungeon.saveAll();

        File sourceDir = (File) context.getClass()
                .getMethod("getFilesDir")
                .invoke(context);
        File targetDir = externalSaveDirectory(context);

        if (targetDir.exists()) {
            if (!targetDir.isDirectory()) {
                throw new IOException(
                        "Export path is not a directory: " + targetDir.getAbsolutePath());
            }
            deleteContents(targetDir);
        } else if (!targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IOException(
                    "Unable to create export directory: " + targetDir.getAbsolutePath());
        }

        copyRecursively(sourceDir, targetDir, false);
        GLog.h(CoHeroMessages.get("save_transfer.exported"), new Object[0]);
    }

    private static void importSnapshot() throws Exception {
        Object context = androidContext();
        if (!ensureAllFilesAccess(context)) {
            return;
        }

        File sourceDir = externalSaveDirectory(context);
        File targetDir = (File) context.getClass()
                .getMethod("getFilesDir")
                .invoke(context);

        // Never delete live files until a real external snapshot has been
        // validated. Import is intentionally replacement, not merge.
        File[] sourceFiles = sourceDir.listFiles();
        if (!sourceDir.exists()
                || !sourceDir.isDirectory()
                || sourceFiles == null
                || sourceFiles.length == 0) {
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

    private static File externalSaveDirectory(Object context) throws Exception {
        String packageName = (String) context.getClass()
                .getMethod("getPackageName")
                .invoke(context);
        return new File("/sdcard/Download/" + packageName);
    }

    private static Object androidContext() throws Exception {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread
                    .getMethod("currentApplication")
                    .invoke(null);
            if (application == null) {
                throw new IllegalStateException("Android application context is unavailable");
            }
            return application;
        } catch (ClassNotFoundException notAndroid) {
            throw new UnsupportedOperationException("save transfer is Android-only", notAndroid);
        }
    }

    private static boolean ensureAllFilesAccess(Object context) throws Exception {
        Class<?> buildVersionClass = Class.forName("android.os.Build$VERSION");
        int sdkInt = buildVersionClass
                .getField("SDK_INT")
                .getInt(null);

        if (sdkInt < 30) {
            return true;
        }

        Class<?> environmentClass = Class.forName("android.os.Environment");
        boolean manager = ((Boolean) environmentClass
                .getMethod("isExternalStorageManager")
                .invoke(null)).booleanValue();

        if (manager) {
            return true;
        }

        Class<?> intentClass = Class.forName("android.content.Intent");
        Object intent = intentClass
                .getConstructor(String.class)
                .newInstance("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");

        Class<?> uriClass = Class.forName("android.net.Uri");
        String packageName = (String) context.getClass()
                .getMethod("getPackageName")
                .invoke(context);
        Object uri = uriClass
                .getMethod("fromParts", String.class, String.class, String.class)
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

    private static void deleteContents(File directory) throws IOException {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            throw new IOException(
                    "Unable to list directory while clearing: " + directory.getAbsolutePath());
        }

        for (File file : files) {
            deleteRecursively(file);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException(
                        "Unable to list directory while deleting: " + file.getAbsolutePath());
            }
            for (File child : children) {
                deleteRecursively(child);
            }
        }

        if (!file.delete() && file.exists()) {
            throw new IOException("Unable to delete: " + file.getAbsolutePath());
        }
    }

    private static void copyRecursively(
            File source,
            File target,
            boolean syncFiles) throws IOException {

        if (!source.exists()) {
            throw new IOException("Copy source disappeared: " + source.getAbsolutePath());
        }

        if (source.isDirectory()) {
            if (target.exists() && target.isFile() && !target.delete()) {
                throw new IOException(
                        "Unable to replace file with directory: " + target.getAbsolutePath());
            }
            if (!target.exists() && !target.mkdirs() && !target.isDirectory()) {
                throw new IOException(
                        "Unable to create directory: " + target.getAbsolutePath());
            }

            File[] files = source.listFiles();
            if (files == null) {
                throw new IOException(
                        "Unable to list copy source: " + source.getAbsolutePath());
            }

            for (File file : files) {
                copyRecursively(file, new File(target, file.getName()), syncFiles);
            }
            return;
        }

        File parent = target.getParentFile();
        if (parent != null
                && !parent.exists()
                && !parent.mkdirs()
                && !parent.isDirectory()) {
            throw new IOException(
                    "Unable to create directory: " + parent.getAbsolutePath());
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
