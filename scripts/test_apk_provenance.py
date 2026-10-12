import unittest
from apk_provenance import expect_source, package_fields, source_fields

DOC = """<api>
<package name="net.plainnotes.app">
<class name="BuildConfig" extends="java.lang.Object" final="true">
<field name="SOURCE_SHA" type="java.lang.String" static="true" final="true" value="0123456789abcdef0123456789abcdef01234567"></field>
<field name="SOURCE_ORIGIN" type="java.lang.String" static="true" final="true" value="git"></field>
<field name="SOURCE_DIRTY" type="boolean" static="true" final="true" value="false"></field>
</class>
</package>
<package name="other.lib">
<class name="BuildConfig" extends="java.lang.Object" final="true">
<field name="SOURCE_SHA" type="java.lang.String" static="true" final="true" value="ffffffffffffffffffffffffffffffffffffffff"></field>
</class>
</package>
</api>"""


class SourceFieldsTest(unittest.TestCase):
    def test_reads_only_the_app_build_config(self):
        self.assertEqual(source_fields(DOC), {"SOURCE_SHA": "0123456789abcdef0123456789abcdef01234567", "SOURCE_ORIGIN": "git", "SOURCE_DIRTY": "false"})

    def test_ignores_a_same_named_string_outside_the_build_config(self):
        self.assertEqual(source_fields("<api><package name=\"net.plainnotes.app\"><class name=\"Other\">"
                                       "<field name=\"SOURCE_SHA\" static=\"true\" final=\"true\" value=\"x\"></field></class></package></api>"), {})


class PackageFieldsTest(unittest.TestCase):
    def test_reads_application_id_and_version(self):
        line = "package: name='net.plainnotes.app' versionCode='26' versionName='0.2.1' platformBuildVersionName='17'\nsdkVersion:'26'"
        self.assertEqual(package_fields(line), {"application_id": "net.plainnotes.app", "version_code": 26, "version_name": "0.2.1"})

    def test_real_aapt2_line_with_codename_suffix(self):
        # Copied from aapt2 37.0.0 output for this app; "compileSdkVersionCodename" once overwrote the package name.
        line = ("package: name='net.plainnotes.app' versionCode='25' versionName='0.2.0' platformBuildVersionName='17' "
                "platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'")
        self.assertEqual(package_fields(line), {"application_id": "net.plainnotes.app", "version_code": 25, "version_name": "0.2.0"})

    def test_missing_line_is_not_guessed(self):
        self.assertEqual(package_fields(""), {"application_id": None, "version_code": None, "version_name": None})


class ExpectSourceTest(unittest.TestCase):
    SHA = "0123456789abcdef0123456789abcdef01234567"

    def report(self, **source):
        return {"source": {"SOURCE_SHA": self.SHA, "SOURCE_ORIGIN": "git", "SOURCE_DIRTY": "false", **source}}

    def test_exact_clean_git_identity_passes(self):
        expect_source(self.report(), self.SHA)

    def test_other_commit_dirty_declared_or_absent_fail(self):
        for bad in (self.report(SOURCE_SHA="f" * 40), self.report(SOURCE_DIRTY="true"), self.report(SOURCE_ORIGIN="declared-unverified"),
                    {"source": "absent (built before audit B1 or BuildConfig stripped)"}):
            with self.assertRaises(SystemExit):
                expect_source(bad, self.SHA)


if __name__ == "__main__":
    unittest.main()
