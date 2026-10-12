import unittest
from apk_provenance import source_fields

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


if __name__ == "__main__":
    unittest.main()
