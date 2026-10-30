#!/usr/bin/env python3
"""Research-only positive PK convolution kernels for missing T routes.
NO clinical absolute-concentration prediction; amplitude is arbitrary.
Time in days, dose in parent-equivalent mg. Python standard library only.
"""
from __future__ import annotations
import math
from dataclasses import dataclass

@dataclass(frozen=True)
class Dose:
    day: float
    parent_mg: float

@dataclass(frozen=True)
class Kernel:
    absorption_per_day: float
    elimination_per_day: float
    lag_days: float = 0.0

    def __post_init__(self):
        if self.absorption_per_day <= 0 or self.elimination_per_day <= 0 or self.lag_days < 0:
            raise ValueError("positive rates and nonnegative lag required")

    def value(self, elapsed_days: float) -> float:
        """Unit input-rate convolved with first-order elimination (units arbitrary).
        Stable at equal or nearly equal rate constants.
        """
        t = elapsed_days - self.lag_days
        if t <= 0:
            return 0.0
        a, e = self.absorption_per_day, self.elimination_per_day
        # a*(exp(-e*t)-exp(-a*t))/(a-e)
        x = (a-e)*t
        if abs(x) < 1e-7:
            return a*t*math.exp(-e*t)*(-math.expm1(-x)/x if x != 0 else 1.0)
        if a > e:
            return a*math.exp(-e*t)*(-math.expm1(-x))/(a-e)
        return a*math.exp(-a*t)*math.expm1(x)/(a-e)

    def peak_day(self) -> float:
        a,e=self.absorption_per_day,self.elimination_per_day
        return self.lag_days+(1/a if abs(a-e)<1e-10 else math.log(a/e)/(a-e))

def curve(days: list[float], history: list[Dose], kernel: Kernel, baseline: float = 0.0, gain: float = 1.0) -> list[float]:
    """Synthetic total = explicitly provided baseline + nonnegative superposed input.
    gain is a synthetic scale, NEVER inferred from published total Cmax alone.
    """
    if gain < 0 or not math.isfinite(gain) or not math.isfinite(baseline):
        raise ValueError("bad baseline/gain")
    if any(d.parent_mg < 0 or not math.isfinite(d.parent_mg) for d in history):
        raise ValueError("bad dose")
    return [baseline+gain*sum(d.parent_mg*kernel.value(t-d.day) for d in history) for t in days]

def gel_application(days: list[float], applications: list[Dose], skin_absorption: float, elimination: float, baseline: float=0.0, gain:float=1.0):
    """Research proxy only; NOT ANDROGEL-calibrated. A gel wash/removal policy requires separate evidence."""
    return curve(days,applications,Kernel(skin_absorption,elimination),baseline,gain)

def injectable_parent_equivalent(ester_mg:float, testosterone_fraction:float)->float:
    if not 0 < testosterone_fraction <= 1 or ester_mg < 0: raise ValueError("bad ester units")
    return ester_mg*testosterone_fraction

# Official NEBIDO: 1000 mg undecanoate = 631.5 mg T equivalent.
NEBIDO_T_FRACTION=0.6315

if __name__=="__main__":
    import unittest
    class ResearchKernelTests(unittest.TestCase):
        def test_before_dose_zero(self):
            k=Kernel(.2,1.2)
            self.assertEqual(curve([-2,0],[Dose(0,1)],k),[0,0])
        def test_nonnegative(self):
            for a,e in ((.04,1.0),(1.,1.),(2.,.5),(.99999999,1)):
                k=Kernel(a,e)
                self.assertTrue(all(math.isfinite(k.value(t)) and k.value(t)>=0 for t in [0,.001,.1,1,7,30,90,365]))
        def test_peak_math(self):
            for a,e in ((.04,1.),(1.,1.),(2.,.5)):
                k=Kernel(a,e); t=k.peak_day()
                self.assertGreater(k.value(t),k.value(t*.9))
                self.assertGreater(k.value(t),k.value(t*1.1))
        def test_superposition(self):
            k=Kernel(.15,1.2)
            x=curve([2,10],[Dose(0,1),Dose(7,2)],k,5,3)
            y=curve([2,10],[Dose(0,1)],k,5,3)
            self.assertAlmostEqual(x[0],y[0])
            self.assertAlmostEqual(x[1]-y[1],6*k.value(3))
        def test_ester_conversion(self):
            self.assertAlmostEqual(injectable_parent_equivalent(1000,NEBIDO_T_FRACTION),631.5)
        def test_invalid(self):
            with self.assertRaises(ValueError): Kernel(0,1)
            with self.assertRaises(ValueError): curve([1],[Dose(0,-1)],Kernel(1,2))
        def test_stable_near_equal(self):
            self.assertAlmostEqual(Kernel(1,1+1e-10).value(2),2*math.exp(-2),places=7)
    unittest.main()
