/**
 * Generates reference curves for the Kotlin port in pk-engine. Runs the UNMODIFIED upstream
 * sources in ./upstream (Transmtf-HRT-Tracker commit 8c9abdde, MIT) on synthetic regimens and
 * writes JSON fixtures to pk-engine/src/test/resources/reference/. Usage: see README.md.
 */
// Node built-ins without @types/node so the generator needs no npm install.
declare const require: any, __dirname: string;
const fs = require('fs');
const path = require('path');
import { Route, Ester, type DoseEvent, type LabResult } from './upstream/types';
import { runSimulation } from './upstream/pk';
import { replayPersonalModel, computeSimulationWithCI, ekfUpdatePersonalModel, initPersonalModel } from './upstream/personalModel';

const BASE_H = Date.UTC(2026, 0, 5, 8, 0, 0) / 3600000;
const SAMPLES = 300;
let seq = 0;

function series(route: Route, ester: Ester, dose: number, everyH: number, count: number, weight = 60, extras: any = {}, offsetH = 0): DoseEvent[] {
    return Array.from({ length: count }, (_, i) => ({ id: `e${++seq}`, route, timeH: BASE_H + offsetH + i * everyH, doseMG: dose, ester, weightKG: weight, extras: { ...extras } }));
}
function patches(rate: number | undefined, everyH: number, count: number, targeted: boolean): DoseEvent[] {
    const out: DoseEvent[] = [];
    for (let i = 0; i < count; i++) {
        const id = `p${++seq}`;
        out.push({ id, route: Route.patchApply, timeH: BASE_H + i * everyH, doseMG: 1, ester: Ester.E2, weightKG: 60, extras: rate === undefined ? {} : { releaseRateUGPerDay: rate } });
        out.push({ id: `r${++seq}`, route: Route.patchRemove, timeH: BASE_H + (i + 1) * everyH, doseMG: 0, ester: Ester.E2, weightKG: 60, extras: targeted ? { patchRemovalFor: id } : {} });
    }
    return out;
}
const lab = (dh: number, v: number, unit: 'pg/ml' | 'pmol/l' = 'pg/ml'): LabResult => ({ id: `l${++seq}`, timeH: BASE_H + dh, concValue: v, unit } as LabResult);

const cases: { name: string; events: DoseEvent[]; labs?: LabResult[]; mode?: 'retrospective' | 'causal'; aaLearn?: boolean; inhibit?: boolean }[] = [
    { name: 'oral_e2_2mg_q12h', events: series(Route.oral, Ester.E2, 2, 12, 28) },
    { name: 'oral_ev_2mg_q24h', events: series(Route.oral, Ester.EV, 2, 24, 14, 72) },
    ...[0, 1, 2, 3].map(t => ({ name: `sl_e2_tier${t}`, events: series(Route.sublingual, Ester.E2, 1, 12, 14, 60, { sublingualTier: t }) })),
    { name: 'sl_e2_theta_custom', events: series(Route.sublingual, Ester.E2, 2, 24, 7, 60, { sublingualTheta: 0.3 }) },
    { name: 'sl_ev_standard', events: series(Route.sublingual, Ester.EV, 2, 12, 14, 60, { sublingualTier: 2 }) },
    { name: 'inj_ev_5mg_q7d', events: series(Route.injection, Ester.EV, 5, 168, 8) },
    { name: 'inj_ec_5mg_q7d', events: series(Route.injection, Ester.EC, 5, 168, 8, 80) },
    { name: 'inj_eb_1mg_q3d', events: series(Route.injection, Ester.EB, 1, 72, 10) },
    { name: 'inj_en_10mg_q14d', events: series(Route.injection, Ester.EN, 10, 336, 6) },
    { name: 'inj_eu_100mg_q30d', events: series(Route.injection, Ester.EU, 100, 720, 6, 70) },
    ...[1, 2, 3, 4, 5].map(p => ({ name: `gel_product${p}_arm`, events: series(Route.gel, Ester.E2, 1.5, 24, 14, 60, { gelProductId: p, gelSite: 0 }) })),
    { name: 'gel_thigh_200cm2', events: series(Route.gel, Ester.E2, 1, 24, 10, 60, { gelProductId: 4, gelSite: 1, areaCM2: 200 }) },
    { name: 'gel_abdomen_palm2', events: series(Route.gel, Ester.E2, 2, 24, 10, 60, { gelProductId: 2, gelSite: 3, areaCM2: 350 }) },
    { name: 'gel_scrotal', events: series(Route.gel, Ester.E2, 0.75, 24, 10, 60, { gelProductId: 1, gelSite: 2 }) },
    { name: 'gel_wash_1h', events: series(Route.gel, Ester.E2, 1.5, 24, 10, 60, { gelProductId: 1, gelSite: 0, gelWashAfterH: 1 }) },
    { name: 'gel_sunscreen', events: series(Route.gel, Ester.E2, 1.5, 24, 10, 60, { gelProductId: 1, gelSite: 0, gelCoApplied: 1 }) },
    { name: 'gel_moisturizer', events: series(Route.gel, Ester.E2, 1.5, 24, 10, 60, { gelProductId: 1, gelSite: 0, gelCoApplied: 2 }) },
    { name: 'patch_50ug_targeted', events: patches(50, 84, 6, true) },
    { name: 'patch_100ug_legacy_removal', events: patches(100, 84, 6, false) },
    { name: 'patch_first_order', events: patches(undefined, 84, 4, true) },
    { name: 'mixed_oral_gel', events: [...series(Route.oral, Ester.E2, 2, 24, 10), ...series(Route.gel, Ester.E2, 1.5, 24, 10, 60, { gelProductId: 1 }, 12)] },
    { name: 'weight_change', events: [...series(Route.oral, Ester.E2, 2, 12, 10, 60), ...series(Route.oral, Ester.E2, 2, 12, 10, 75, {}, 120)] },
    { name: 'cpa_12_5mg_daily', events: [...series(Route.oral, Ester.CPA, 12.5, 24, 30), ...series(Route.oral, Ester.E2, 4, 24, 30)] },
    { name: 'bica_50mg_daily', events: series(Route.oral, Ester.BICA, 50, 24, 56, 65) },
    { name: 'ekf_oral_retro', events: series(Route.oral, Ester.E2, 2, 12, 60), labs: [lab(24 * 10 + 10, 180), lab(24 * 20 + 2, 95), lab(24 * 28 + 6, 620, 'pmol/l')] },
    { name: 'ekf_oral_causal', events: series(Route.oral, Ester.E2, 2, 12, 60), labs: [lab(24 * 10 + 10, 180), lab(24 * 20 + 2, 95), lab(24 * 28 + 6, 620, 'pmol/l')], mode: 'causal' },
    { name: 'ekf_baseline_and_outlier', events: series(Route.injection, Ester.EV, 5, 168, 8, 70, {}, 48), labs: [lab(0, 25), lab(24 * 9, 300), lab(24 * 30, 3000)] },
    { name: 'ekf_cpa_inhibition', events: [...series(Route.oral, Ester.CPA, 25, 24, 30), ...series(Route.sublingual, Ester.E2, 2, 12, 60, 60, { sublingualTier: 2 })], labs: [lab(24 * 14 + 3, 210)], inhibit: true },
    { name: 'ekf_cpa_no_learning', events: [...series(Route.oral, Ester.CPA, 25, 24, 30), ...series(Route.gel, Ester.E2, 1.5, 24, 30, 60, { gelProductId: 1 })], labs: [lab(24 * 14 + 3, 120)], aaLearn: false },
];

function pick(n: number): number[] {
    const idx = new Set<number>([0, n - 1]);
    for (let k = 0; k < SAMPLES; k++) idx.add(Math.round(k * (n - 1) / (SAMPLES - 1)));
    return [...idx].sort((a, b) => a - b);
}
const at = (arr: number[], idx: number[]) => idx.map(i => arr[i]);

const outDir = path.resolve(__dirname, '../../../pk-engine/src/test/resources/reference');
fs.mkdirSync(outDir, { recursive: true });
for (const c of cases) {
    const sim = runSimulation(c.events)!;
    const idx = pick(sim.timeH.length);
    const out: any = {
        name: c.name, events: c.events, labs: c.labs ?? [], mode: c.mode ?? 'retrospective', aaLearn: c.aaLearn ?? true, inhibit: c.inhibit ?? false,
        sim: { steps: sim.timeH.length, start: sim.timeH[0], end: sim.timeH[sim.timeH.length - 1], auc: sim.auc, idx,
               conc: at(sim.concPGmL, idx), e2: at(sim.concPGmL_E2, idx), cpa: at(sim.concPGmL_CPA, idx),
               by: Object.fromEntries(Object.entries(sim.byCompound).map(([k, v]) => [k, at(v!.values, idx)])) },
    };
    if (c.labs) {
        const model = replayPersonalModel(c.events, c.labs);
        const ci = computeSimulationWithCI(sim, c.events, model, c.aaLearn ?? true, c.labs, 'ekf', c.inhibit ?? false, c.mode ?? 'retrospective');
        const sorted = [...c.labs].sort((a, b) => a.timeH - b.timeH);
        const prior = sorted.length > 1 ? replayPersonalModel(c.events, sorted.slice(0, -1)) : initPersonalModel();
        const { diagnostics } = ekfUpdatePersonalModel(c.events, prior, sorted[sorted.length - 1], sorted.length > 1 ? sorted[sorted.length - 2].timeH : undefined);
        out.model = { thetaS: model.thetaMean[0], thetaK: model.thetaMean[1], cov: [model.thetaCov[0][0], model.thetaCov[0][1], model.thetaCov[1][0], model.thetaCov[1][1]],
                      obs: model.observationCount, post: model.postDoseObservationCount, baseline: model.baselinePGmL ?? null };
        out.diag = diagnostics;
        out.ci = { e2: at(ci.e2Adjusted, idx), lo95: at(ci.ci95Low, idx), hi95: at(ci.ci95High, idx), lo68: at(ci.ci68Low, idx), hi68: at(ci.ci68High, idx),
                   aa: Object.fromEntries(Object.entries(ci.antiandrogen).map(([k, v]) => [k, { adj: at(v!.adjusted, idx), lo: at(v!.ci95Low, idx), hi: at(v!.ci95High, idx) }])) };
    }
    fs.writeFileSync(path.join(outDir, `${c.name}.json`), JSON.stringify(out));
}
console.log(`wrote ${cases.length} fixtures to ${outDir}`);
