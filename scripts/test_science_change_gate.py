import unittest
from science_change_gate import needs_deep


class TriggerMatrixTest(unittest.TestCase):
    def test_five_change_classes(self):
        scenarios = {
            'ui_only': (['app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt',
                         'app/src/main/res/values/strings_unified_concentration.xml'], False),
            'pk_core': (['pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt'], True),
            'research_data': (['docs/pk-research/p2/evidence-catalog.json'], True),
            'workflow': (['.github/workflows/science-validation.yml'], True),
            'docs_only': (['docs/HANDOFF.md'], False),
        }
        for name, (files, expected) in scenarios.items():
            with self.subTest(name=name):
                self.assertEqual(needs_deep(files), expected)

    def test_science_tool_and_new_pk_file_route_deep(self):
        for file in ('tools/pk-research/new_model.py',
                     'pk-engine/src/main/kotlin/net/plainnotes/app/pk/NewHelper.kt',
                     'scripts/check_unified_concentration.py'):
            self.assertTrue(needs_deep([file]), file)
