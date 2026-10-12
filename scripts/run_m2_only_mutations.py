"""Execute nine real source mutants across seven required fault categories, require failed executed assertions, restore exact bytes.
Run serially, with no other local Gradle invocation. Never changes frozen inputs or approval records.
"""
import argparse, hashlib, json, subprocess, xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PREVIEW=ROOT/'app/src/main/java/net/plainnotes/app/experimental/ConcentrationPreview.kt'
PROTOCOL=ROOT/'pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/ResearchDisplayProtocol.kt'
SCREEN=ROOT/'app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt'
SCOPE=ROOT/'app/src/main/java/net/plainnotes/app/conc/experimental/ResearchHistoryScope.kt'
def replace(s,old,new):
    if s.count(old)!=1:raise ValueError('Mutation anchor not unique: '+old)
    return s.replace(old,new)
def one_hour_peak(s):
    old='val single=ResearchExtrema.maximum(0.0,24.0) { ResearchSublingualV01.studyScenario(it,listOf(ExperimentalDose(0.0,1.0)),selected,anchor) }'
    new='val single=ResearchPeak(1.0,ResearchSublingualV01.studyScenario(1.0,listOf(ExperimentalDose(0.0,1.0)),selected,anchor))'
    return replace(s,old,new)
def remove_mixed_scope(s):return replace(s,'val partial get() = otherE2Records > 0 || unknownRecords > 0','val partial get() = false')
def leak_actual_legacy_amplitude(s):
    old='return PriceFigure1997.anchor(projected)'
    new="""val oldEvents=listOf(net.plainnotes.app.pk.DoseEvent("synthetic-mutant",net.plainnotes.app.pk.Route.SUBLINGUAL,0.0,1.0,net.plainnotes.app.pk.Ester.E2,70.0,net.plainnotes.app.pk.DoseExtras(sublingualTier=2.0)))
        val oldFit=net.plainnotes.app.pk.LabFit.fit(oldEvents,listOf(net.plainnotes.app.pk.LabResult("synthetic-mutant-lab",1.0,9000.0,net.plainnotes.app.pk.LabUnit.PG_ML)))
        return PriceFigure1997.anchor(projected).let { it.copy(oneMgIncrementAtOneHour=it.oneMgIncrementAtOneHour*kotlin.math.exp(oldFit.logAmplitude)) }"""
    return replace(s,old,new)
def claim_total_from_sl_only(s):
    SCREEN.write_text(replace(SCREEN.read_text(),'stringResource(R.string.uc_original_assumptions)','"Total E2"'))
    return remove_mixed_scope(s)

CASES=[
 ('amplitude_100',PREVIEW,lambda s:replace(s,'val anchor=ResearchDisplayProtocol.anchor(model,selected)','val anchor=ResearchDisplayProtocol.anchor(model,selected).copy(oneMgIncrementAtOneHour=100.0)'),':app:testFullDebugUnitTest','net.plainnotes.app.ConcentrationPreviewTest.defaultProductPreviewUsesRealLiteratureFitInHeroChartAndExactReadout'),
 ('implicit_first',PROTOCOL,lambda s:replace(s,'ResearchSublingualV01.candidates.single { it.id == m2Id }','ResearchSublingualV01.candidates.first()'),':pk-engine:test','net.plainnotes.app.pk.experimental.M2OnlyNumericalContractTest.fixedCandidateHashVersionAndPriceProjectionCannotRegressToExample'),
 ('one_hour_as_cmax',PREVIEW,one_hour_peak,':app:testFullDebugUnitTest','net.plainnotes.app.ConcentrationPreviewTest.defaultProductPreviewUsesRealLiteratureFitInHeroChartAndExactReadout'),
 ('legacy_calibration_on_m2',SCREEN,lambda s:replace(s,'if(m2Only && (preview!=null || compatibilityHasSl)) SectionCard','if(false) SectionCard'),':app:testFullDebugUnitTest','net.plainnotes.app.UnifiedConcentrationUiTest.personalLabsCannotScaleM2AndOldCalibrationIsNotPresentedAsM2'),
 ('mixed_routes_without_scope',SCOPE,remove_mixed_scope,':app:testFullDebugUnitTest','net.plainnotes.app.ResearchHistoryScopeTest.mixedScopesRetainOtherRouteAndUnknownFactsWithoutChangingRecords'),
 ('candidate_range_as_human_90',SCREEN,lambda s:replace(s,'stringResource(R.string.uc_original_assumptions)','"15 candidates are a human 90% prediction interval"'),':app:testFullDebugUnitTest','net.plainnotes.app.UnifiedConcentrationUiTest.originalCardsAndCalendarControlsRemainWithoutModelSelector'),
 ('legacy_value_relabelled_m2',SCREEN,lambda s:replace(s,'(if(preview==null) result.currentPgMl else preview.currentPgMl)','result.currentPgMl'),':app:testFullDebugUnitTest','net.plainnotes.app.UnifiedConcentrationUiTest.fixedM2KernelOwnsHeroWithoutOldPredictionFallback'),
 ('actual_legacy_amplitude_leak',PROTOCOL,leak_actual_legacy_amplitude,':pk-engine:test','net.plainnotes.app.pk.experimental.M2OnlyNumericalContractTest.fixedCandidateHashVersionAndPriceProjectionCannotRegressToExample'),
 ('sl_only_claimed_total_e2',SCOPE,claim_total_from_sl_only,':app:testFullDebugUnitTest','net.plainnotes.app.UnifiedConcentrationUiTest.mixedE2WithoutScopeCannotAppearAsTotal')]
def main():
    p=argparse.ArgumentParser();p.add_argument('--runner',required=True);p.add_argument('--out',required=True);p.add_argument('--cases',default='');a=p.parse_args()
    output=Path(a.out);output.mkdir(parents=True,exist_ok=True)
    original={f:f.read_bytes() for f in (PREVIEW,PROTOCOL,SCREEN,SCOPE)}
    results=[]
    try:
        for name,file,mutate,task,test in CASES:
            if a.cases and name not in a.cases.split(','):continue
            for f,b in original.items():f.write_bytes(b)
            mutant=mutate(original[file].decode());file.write_text(mutant)
            module='pk-engine' if task==':pk-engine:test' else 'app'
            folder='test' if module=='pk-engine' else 'testFullDebugUnitTest'
            cls=test.rsplit('.',1)[0];xml=ROOT/module/'build/test-results'/folder/('TEST-'+cls+'.xml')
            if xml.exists():xml.unlink()
            cmd=[a.runner]+(['-PjvmOnly'] if module=='pk-engine' else [])+[task,'--tests',test]
            with (output/(name+'.log')).open('w') as log:run=subprocess.run(cmd,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
            if not xml.exists():raise AssertionError(name+' did not execute its test; compilation/environment failure does not kill a mutant')
            tree=ET.parse(xml).getroot();failures=tree.findall('.//failure')
            if run.returncode==0 or int(tree.get('tests','0'))!=1 or not failures:raise AssertionError(name+' survived or did not execute exactly one failing test')
            messages=[{'type':f.get('type'),'message':f.get('message')} for f in failures]
            if not all('Assertion' in f['type'] for f in messages):raise AssertionError(name+' failed for a non-assertion reason: '+str(messages))
            (output/(name+'.xml')).write_bytes(xml.read_bytes())
            results.append({'mutation':name,'file':str(file.relative_to(ROOT)),'sha256':hashlib.sha256(mutant.encode()).hexdigest(),'task':task,'test':test,'exit_code':run.returncode,'tests':1,'failures':messages,'status':'KILLED_BY_EXECUTED_ASSERTION'})
            (output/'results.json').write_text(json.dumps(results,indent=2)+'\n')
            print(name,'KILLED_BY_EXECUTED_ASSERTION',flush=True)
    finally:
        for f,b in original.items():f.write_bytes(b)
        assert all(f.read_bytes()==b for f,b in original.items())
        (output/'source-restoration.json').write_text(json.dumps({str(f.relative_to(ROOT)):hashlib.sha256(b).hexdigest() for f,b in original.items()},indent=2)+'\n')
    print('All source bytes restored. Run the positive full suite before building or committing.',flush=True)
if __name__=='__main__':main()
