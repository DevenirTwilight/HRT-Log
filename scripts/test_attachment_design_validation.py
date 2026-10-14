"""CI entry point for synthetic design checks, intentionally outside production source sets."""
import sys
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DESIGN=ROOT/'tools'/'attachment-design-validation'
sys.path.insert(0,str(DESIGN))
def load_tests(loader, tests, pattern):
    return unittest.TestLoader().discover(str(DESIGN), pattern='test_*.py', top_level_dir=str(DESIGN))
if __name__=='__main__':unittest.main()
