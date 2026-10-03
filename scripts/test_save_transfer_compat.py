import pathlib
import shutil
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SAVE_TRANSFER = ROOT / "core/src/main/java/com/spd/cohero/CoHeroSaveTransfer.java"


class CoHeroSaveTransferCompatTests(unittest.TestCase):

    @staticmethod
    def _tool(name: str) -> str:
        value = shutil.which(name)
        if value is None:
            raise unittest.SkipTest(f"{name} is unavailable")
        return value

    @staticmethod
    def _write(root: pathlib.Path, relative: str, text: str) -> pathlib.Path:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_save_transfer_compiles_against_minimal_desktop_api(self):
        javac = self._tool("javac")
        java = self._tool("java")

        with tempfile.TemporaryDirectory() as tmp:
            work = pathlib.Path(tmp)
            src = work / "src"
            classes = work / "classes"
            save_dir = work / "save"
            save_dir.mkdir()

            self._write(
                src,
                "com/shatteredpixel/shatteredpixeldungeon/Dungeon.java",
                """
package com.shatteredpixel.shatteredpixeldungeon;
public final class Dungeon {
    public static void saveAll() {
    }
}
""",
            )
            self._write(
                src,
                "com/shatteredpixel/shatteredpixeldungeon/utils/GLog.java",
                """
package com.shatteredpixel.shatteredpixeldungeon.utils;
public final class GLog {
    public static void h(String text, Object[] args) {
    }
    public static void w(String text, Object[] args) {
    }
}
""",
            )
            self._write(
                src,
                "com/watabou/noosa/Game.java",
                """
package com.watabou.noosa;
public class Game {
    public static void runOnRenderThread(Runnable runnable) {
        runnable.run();
    }
}
""",
            )
            self._write(
                src,
                "com/watabou/utils/DeviceCompat.java",
                """
package com.watabou.utils;
public final class DeviceCompat {
    public static boolean isDesktop() {
        return true;
    }
    public static boolean isAndroid() {
        return false;
    }
}
""",
            )
            self._write(
                src,
                "com/badlogic/gdx/files/FileHandle.java",
                """
package com.badlogic.gdx.files;
import java.io.File;
public class FileHandle {
    private final File file;
    public FileHandle(File file) {
        this.file = file;
    }
    public File file() {
        return file;
    }
}
""",
            )
            self._write(
                src,
                "com/watabou/utils/FileUtils.java",
                """
package com.watabou.utils;
import java.io.File;
import com.badlogic.gdx.files.FileHandle;
public final class FileUtils {
    private static File root;
    public static void configure(File directory) {
        root = directory;
    }
    public static FileHandle getFileHandle(String name) {
        return new FileHandle(name.isEmpty() ? root : new File(root, name));
    }
}
""",
            )
            self._write(
                src,
                "com/spd/cohero/CoHeroMessages.java",
                """
package com.spd.cohero;
public final class CoHeroMessages {
    public static String get(String key) {
        return key;
    }
}
""",
            )
            self._write(
                src,
                "com/spd/cohero/CoHeroSaveTransfer.java",
                SAVE_TRANSFER.read_text(encoding="utf-8"),
            )
            self._write(
                src,
                "com/spd/cohero/SavePathHarness.java",
                """
package com.spd.cohero;
import java.io.File;
import java.lang.reflect.Method;
public final class SavePathHarness {
    public static void main(String[] args) throws Exception {
        File expected = new File(args[0]).getCanonicalFile();
        com.watabou.utils.FileUtils.configure(expected);
        Method method = CoHeroSaveTransfer.class.getDeclaredMethod(
                "desktopSaveDirectory");
        method.setAccessible(true);
        File actual = (File) method.invoke(null);
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "expected=" + expected + " actual=" + actual);
        }
    }
}
""",
            )

            result = subprocess.run(
                [javac, "-d", str(classes), *map(str, src.rglob("*.java"))],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
            )
            self.assertEqual(0, result.returncode, result.stdout)

            run = subprocess.run(
                [
                    java,
                    "-cp",
                    str(classes),
                    "com.spd.cohero.SavePathHarness",
                    str(save_dir),
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
            )
            self.assertEqual(0, run.returncode, run.stdout)

    def test_transfer_paths_use_app_names_without_desktop_picker(self):
        source = SAVE_TRANSFER.read_text(encoding="utf-8")
        self.assertNotIn("org.lwjgl.util.tinyfd", source)
        self.assertNotIn("java.util.prefs.Preferences", source)
        self.assertIn('"/sdcard/Documents/spd_saves/"', source)
        self.assertIn('"Documents"', source)
        self.assertIn('"spd_saves"', source)
        self.assertIn("getSpecificationTitle()", source)
        self.assertIn('"android.content.pm.PackageManager"', source)
        self.assertIn("loadLabel", source)


if __name__ == "__main__":
    unittest.main()
