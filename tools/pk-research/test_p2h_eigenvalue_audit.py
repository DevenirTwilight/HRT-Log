import math
import unittest
import p2h_eigenvalue_audit as a

class PrimaryPDFHalfLife(unittest.TestCase):
    def setUp(self):
        self.p=a.rates(1550,1258,1930,62261,.632)
    def test_original_reported_half_life_reproduces_total_volume_formula(self):
        self.assertAlmostEqual(self.p["reported_half_life_formula_match_h"],28.40517145934656)
    def test_terminal_eigenmode_is_not_same_as_total_volume_shortcut(self):
        self.assertAlmostEqual(self.p["conditional_terminal_two_comp_half_life_h"],50.516798727187,places=6)
        self.assertGreater(self.p["conditional_terminal_two_comp_half_life_h"],self.p["reported_half_life_formula_match_h"])
    def test_eigenvalue_sum_and_product(self):
        self.assertAlmostEqual(self.p["alpha"]+self.p["beta"],
          self.p["k10"]+self.p["k12"]+self.p["k21"],places=10)
        self.assertAlmostEqual(self.p["alpha"]*self.p["beta"],
          self.p["k10"]*self.p["k21"],places=10)
    def test_absorption_half_life_is_not_clearance(self):
        self.assertAlmostEqual(self.p["conditional_absorption_exponential_half_life_h"],math.log(2)/.632)
        self.assertNotAlmostEqual(self.p["conditional_absorption_exponential_half_life_h"],28.4,places=1)
    def test_invalid_physiological_parameters_fail(self):
        with self.assertRaises(ValueError):a.rates(1550,0,1930,62261,.632)
        with self.assertRaises(ValueError):a.rates(1550,1258,float("nan"),62261,.632)
        with self.assertRaises(ValueError):a.rates(1550,1258,1930,-1,.632)
    def test_source_role_and_pdf_provenance(self):
        data=__import__("json").loads(a.SOURCE.read_text())
        self.assertFalse(data["clinical_accuracy_established"])
        d=next(x for x in data["pdf_sources"] if x["id"]=="Doll2022")
        self.assertEqual(d["classification"],"PUBLISHER_WEBPAGE_PRINT_NOT_FULLTEXT")
        thesis=next(x for x in data["pdf_sources"] if x["id"]=="Abdelmawla2023")
        self.assertFalse(thesis["oral_or_sublingual_individually_disambiguated"])
        self.assertEqual(thesis["final_observations"],168)

if __name__=="__main__":unittest.main()
