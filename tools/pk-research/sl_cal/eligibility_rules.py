"""SL-CAL-01 reference for the PROPOSED sublingual add-on to CalibrationEligibility. Research only: it is not wired to production and
the production CalibrationEligibility.kt is unchanged. SL-CAL-02 would port these rules to Kotlin behind the existing owner approvals.

Layering: a lab first has to pass the existing history gate (P1-B CalibrationEligibility: complete saved read, known doses/times, no omitted
or unmodelled exposure, treatment evidence). The rules here are the ADDITIONAL, sublingual-specific checks plus the set-level refusal.
Reason texts are meant to be shown to the user as they are: they describe the data, never blame the lab, never give dose advice.
"""
from dataclasses import dataclass, field

PEAK_WINDOW_H = 2.5            # hours since the last sublingual dose in which the M2 curve is steep (peak ~1 h)
MIN_INFORMATION_GAIN = 0.5     # set-level: the labs must remove at least half of the prior variance of the sublingual coefficient
MIN_HISTORY_H = 24.0           # model tail: a single-dose increment is <0.1 % of its peak after ~22.5 h (external_diagnostic.py)
PEAK_POLICY = 'DOWNWEIGHT'     # chosen from the synthetic comparison, see the report: 'DOWNWEIGHT' (delta method) or 'EXCLUDE'

REASON_TEXT = {
    'HISTORY_NOT_ELIGIBLE': {
        'zh': '这次化验之前的用药记录不完整或无法确认，所以不能用它来校准。已保存的化验结果不受影响。',
        'en': 'The dose records before this test are incomplete or cannot be confirmed, so it cannot be used for calibration. The saved result is unchanged.'},
    'LAB_TIME_NOT_PRECISE': {
        'zh': '这次化验没有记录具体到分钟的抽血时间。舌下雌二醇在服药后 1–2 小时内变化很快，所以只有日期、没有时间的化验不能用来校准。',
        'en': 'This test has no sampling time to the minute. Sublingual estradiol changes quickly in the first 1–2 hours after a dose, so a test with only a date cannot be used for calibration.'},
    'SL_LAST_DOSE_UNKNOWN': {
        'zh': '无法确定这次抽血距上一次舌下服药过去了多久（缺少上一次舌下服药的时间），所以不能用它来校准。',
        'en': 'The time since the last sublingual dose cannot be worked out (the last sublingual dose has no recorded time), so this test cannot be used for calibration.'},
    'NO_USABLE_LAB': {
        'zh': '目前没有可用于校准的化验。校准需要至少一次满足条件的化验。',
        'en': 'There is no test that can be used for calibration yet. Calibration needs at least one test that meets the conditions.'},
    'INSUFFICIENT_INFORMATION': {
        'zh': '现有化验提供的信息不足以校准舌下部分。例如化验都抽在两次服药之间的低谷，或抽血时间相近。曲线仍显示未校准的参考结果。',
        'en': 'The available tests do not carry enough information to calibrate the sublingual part, for example when all samples were taken at low points between doses. The curve still shows the uncalibrated reference.'},
    'PEAK_WINDOW_DOWNWEIGHTED': {
        'zh': '这次化验抽在服药后 2.5 小时内，浓度变化很快，时间差 30 分钟读数就可能差好几倍，所以它在校准中的权重较低。',
        'en': 'This test was taken within 2.5 hours of a sublingual dose, when concentration changes fast; a 30-minute timing difference can change the reading several-fold, so it carries less weight in calibration.'},
    'PEAK_WINDOW_EXCLUDED': {
        'zh': '这次化验抽在服药后 2.5 小时内，浓度变化很快，时间稍有偏差读数就会差很多，所以没有用于校准。',
        'en': 'This test was taken within 2.5 hours of a sublingual dose, when concentration changes fast and small timing differences change the reading a lot, so it was not used for calibration.'},
}


@dataclass
class LabDecision:
    decision: str                      # USE | DOWNWEIGHT | REJECT
    reasons: list = field(default_factory=list)


@dataclass
class SetDecision:
    decision: str                      # CALIBRATE | REFUSE
    reasons: list = field(default_factory=list)


def assess_lab(time_known_to_minute, last_sl_dose_known, hours_since_last_sl, history_gate_passed, peak_policy=None):
    policy = peak_policy or PEAK_POLICY
    if not history_gate_passed:
        return LabDecision('REJECT', ['HISTORY_NOT_ELIGIBLE'])
    if not time_known_to_minute:
        return LabDecision('REJECT', ['LAB_TIME_NOT_PRECISE'])
    if not last_sl_dose_known or hours_since_last_sl is None or hours_since_last_sl != hours_since_last_sl:
        return LabDecision('REJECT', ['SL_LAST_DOSE_UNKNOWN'])
    if hours_since_last_sl < PEAK_WINDOW_H:
        if policy == 'EXCLUDE':
            return LabDecision('REJECT', ['PEAK_WINDOW_EXCLUDED'])
        return LabDecision('DOWNWEIGHT', ['PEAK_WINDOW_DOWNWEIGHTED'])
    return LabDecision('USE')


def assess_set(usable_labs, information_gain_sl):
    """Set-level refusal after the per-lab decisions. information_gain_sl = 1 - posterior variance / prior variance of ln k_sl."""
    if usable_labs <= 0:
        return SetDecision('REFUSE', ['NO_USABLE_LAB'])
    if information_gain_sl < MIN_INFORMATION_GAIN:
        return SetDecision('REFUSE', ['INSUFFICIENT_INFORMATION'])
    return SetDecision('CALIBRATE')
