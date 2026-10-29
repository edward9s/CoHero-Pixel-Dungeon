#!/usr/bin/env python3
"""Regression checks for manual tests and the CoHero/SPD official Release policy."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ReleaseWorkflowsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        cls.build = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")

    def test_release_daily_and_manual(self):
        trigger = self.release.split("permissions:", 1)[0]
        self.assertIn("  schedule:", trigger)
        self.assertIn("  workflow_dispatch:", trigger)

    def test_official_tag_includes_cohero_and_spd_only(self):
        self.assertIn('RELEASE_TAG="CoHero-v${COHERO_VER}-SPD-${SPD_TAG}"', self.release)
        self.assertNotIn('RELEASE_TAG="CoHero-v${COHERO_VER}-SPD-${SPD_TAG}-SMM', self.release)

    def test_existing_pair_skips_only_schedule(self):
        self.assertIn('if [ "${{ github.event_name }}" = "schedule" ]; then', self.release)
        self.assertIn('echo "skip=true" >> "$GITHUB_OUTPUT"', self.release)
        self.assertIn('echo "existing=true" >> "$GITHUB_OUTPUT"', self.release)
        self.assertIn("if: needs.prepare.outputs.skip == 'false'", self.release)

    def test_manual_release_can_replace_official_assets(self):
        self.assertIn("overwrite_files: true", self.release)
        self.assertIn("github.event_name == 'workflow_dispatch'", self.release)

    def test_build_test_is_manual_only(self):
        trigger = self.build.split("jobs:", 1)[0]
        self.assertIn("  workflow_dispatch:", trigger)
        self.assertNotIn("  schedule:", trigger)

    def test_build_uploads_rolling_prerelease(self):
        self.assertIn("TEST_TAG: build-test", self.build)
        self.assertIn("Publish rolling test release", self.build)
        self.assertIn("--clobber", self.build)
        self.assertIn("--prerelease", self.build)

    def test_build_requires_four_verified_signed_assets(self):
        self.assertEqual(2, len(re.findall(r'apksigner" verify --verbose', self.build)))
        self.assertIn("Missing required signing secret:", self.build)
        self.assertIn("Expected exactly four APK/JAR files", self.build)

    def test_manual_test_build_keeps_smm_main(self):
        self.assertIn("ref: main", self.build)
        self.assertIn("SMM main commit:", self.build)


if __name__ == "__main__":
    unittest.main()
