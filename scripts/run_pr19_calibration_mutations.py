"""Run real Kotlin source mutations serially, require assertion failures, restore exact bytes.
Local/explicit tool: do not run concurrently with any other Gradle invocation.
Never commits mutant code or edits the frozen protocol/approval chain.
"""
import argparse,hashlib,json,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--gradle-runner',required=True);p.add_argument('--out',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
preview=root/'app/src/main/java/net/plainnotes/app/experimental/ConcentrationPreview.kt'
protocol=root/'pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/ResearchDisplayProtocol.kt'
ui=root/'app/src/main/java/net/plainnotes/app/experimental/UnifiedConcentrationScreen.kt'
original={x:x.read_bytes() for x in (preview,protocol,ui)}
app_task=':app:testFullDebugUnitTest';app_xml=root/'app/build/test-results/testFullDebugUnitTest'
core_task=':pk-engine:test';core_xml=root/'pk-engine/build/test-results/test'
def replace(s,old,new):
 if s.count(old)!=1:raise ValueError('Mutation anchor not unique: '+old)
 return s.replace(old,new)
def remove_peaks(s):
 s=replace(s,'ResearchExtrema.peaks(start,end,stepH=1.0/12,value=::exact).forEach { times+=it.hour }','// MUTANT: omit refined extrema')
 s=replace(s,'(dose.atHour+single.hour).takeIf { it in from..to }?.let {times+=it}','// MUTANT: omit isolated peak')
 return replace(s,'times+=peak.hour','// MUTANT: omit history peak')
def leak_labs(s):
 old='''ConcentrationPreview.compute(audit!!,model,
                ConcentrationCalculator.hours(asOf))'''
 new='''ConcentrationPreview.compute(audit!!,model,
                ConcentrationCalculator.hours(asOf)).let { p ->
                // MUTANT: erroneously apply lab-derived amplitude to the research result.
                val scale=if(labs.any { it.value>1000.0 }) 2.0 else 1.0
                ConcentrationPreview(p.model,p.timeH,DoubleArray(p.values.size){p.values[it]*scale},
                    p.currentPgMl?.times(scale),p.candidateId,{hour -> p.valueAt(hour)!!*scale},
                    p.anchor,p.singleDosePeak,p.historyWindowPeak,p.historyOriginH)
            }'''
 return replace(s,old,new)
cases=[
 ('arbitrary_B25_A100',preview,lambda s:replace(s,'ResearchSublingualV01.studyScenario(hour,doses,selected,anchor)','CommonCalibration(25.0,100.0).concentration(ResearchSublingualV01.relativeHistory(hour,doses,selected))'),app_task,'net.plainnotes.app.ConcentrationPreviewTest.defaultProductPreviewUsesRealLiteratureFitInHeroChartAndExactReadout',app_xml/'TEST-net.plainnotes.app.ConcentrationPreviewTest.xml'),
 ('implicit_first_candidate',protocol,lambda s:replace(s,'ResearchSublingualV01.candidates.single { it.id == m2Id }','ResearchSublingualV01.candidates.first()'),core_task,'net.plainnotes.app.pk.experimental.ResearchDisplayProtocolTest.frozenProductChoiceIsExplicitTraceableAndReproducesTheSelectionRule',core_xml/'TEST-net.plainnotes.app.pk.experimental.ResearchDisplayProtocolTest.xml'),
 ('omitted_peak_points',preview,remove_peaks,app_task,'net.plainnotes.app.ConcentrationPreviewTest.defaultProductPreviewUsesRealLiteratureFitInHeroChartAndExactReadout',app_xml/'TEST-net.plainnotes.app.ConcentrationPreviewTest.xml'),
 ('lab_amplitude_leakage',ui,leak_labs,app_task,'net.plainnotes.app.UnifiedConcentrationUiTest.legacyCalibrationSwitchAndChangedObservedLabCannotScaleResearchHero',app_xml/'TEST-net.plainnotes.app.UnifiedConcentrationUiTest.xml')]
results=[]
try:
 for name,file,mutate,task,test,xml in cases:
  for f,b in original.items():f.write_bytes(b)
  mutant=mutate(original[file].decode());file.write_text(mutant)
  if xml.exists():xml.unlink()
  log=out/(name+'.log');cmd=[sys.executable,a.gradle_runner,str(root),str(log),task,'--tests',test]
  r=subprocess.run(cmd)
  if not xml.exists():raise AssertionError(name+' did not execute its test (compile/environment failure is not a killed mutant)')
  tree=ET.parse(xml).getroot();failures=[f.get('message') for f in tree.findall('.//failure')]
  if r.returncode==0 or not failures or int(tree.get('tests','0'))!=1:raise AssertionError(name+' survived or did not execute exactly one test')
  result={'name':name,'mutated_file':str(file.relative_to(root)),'mutant_sha256':hashlib.sha256(mutant.encode()).hexdigest(),'exit_code':r.returncode,'test':test,'tests':tree.get('tests'),'failures':failures,'status':'KILLED_BY_EXECUTED_ASSERTION'}
  results.append(result);(out/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,failures,flush=True)
finally:
 for f,b in original.items():f.write_bytes(b)
 for f,b in original.items():
  if f.read_bytes()!=b:raise AssertionError('Restore failed '+str(f))
print('All original source bytes restored; rerun the positive suite before building or committing.')
