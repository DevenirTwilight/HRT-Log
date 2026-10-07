"""Build app/src/main/resources/symptom-sources.json from the downloaded originals; every quote must be found verbatim."""
import json, re, sys
W = sys.argv[1]; OUT = sys.argv[2]
P = W + "/dl-drugs/pages/"; C = W + "/dl-cn/"
def text(path):
    t = open(path, encoding="utf-8", errors="replace").read()
    return re.sub(r"[ \t ]+", " ", t)
def norm(s): return re.sub(r"\s+", "", s)
docs = {}
def doc(key, path): docs[key] = text(path)
def q(key, s):
    """Return s after checking it occurs verbatim (whitespace-insensitive) in the document."""
    assert norm(s) in norm(docs[key]), f"NOT FOUND in {key}: {s}"
    return s
for k, f in [("PROVAMES", "n_65421374.txt"), ("OESTRODOSE", "n_63071975.txt"), ("ESTREVA", "n_63216075.txt"), ("DERMESTRIL", "n_65662980.txt"),
             ("PROGYNOVA", "n_60940721.txt"), ("ANDROCUR", "n_61255738.txt"), ("ANSM", "ansm.txt"), ("TW2022", "tw2022.txt"), ("ALDACTONE", "n_67368301.txt")]:
    doc(k, P + f)
import html as _h
def htmltext(path):
    t = open(path, encoding="utf-8", errors="replace").read(); t = re.sub(r"<[^>]+>", " ", t)
    return re.sub(r"[ \t\u00a0]+", " ", _h.unescape(t))
docs["HAS"] = text(W + "/dl-has/has.txt")
docs["TW_ADR"] = htmltext(W + "/dl-region/tw/s4240.html"); docs["FR_ADR"] = htmltext(W + "/dl-region/fr/ansm21.html"); docs["CN_ADR"] = htmltext(W + "/dl-region/cdr/gov81.htm")
doc("BJL_A", C + "a1/yaozh_bjl.html.txt"); doc("BJL_B", C + "a1/zy91.json.txt")
doc("EST_A", C + "a7/yz_gel.html.txt"); doc("EST_B", C + "a7/39_gel.html.txt"); doc("EST_C", C + "a7/ypn_gel.html.txt")
def q2(keys, s):
    for k in keys: q(k, s)
    return s
BDPM = "https://base-donnees-publique.medicaments.gouv.fr/medicament/{}/extrait#tab-notice"
sources = [
 dict(id="FR_PROVAMES", region="FR", kind="label", title="PROVAMES 2 mg, comprimé pelliculé — notice", publisher="ANSM / Base de données publique des médicaments",
      url=BDPM.format(65421374), document_date="2023-12-05", section="Notice, rubrique 2 « Avertissements et précautions »", third_party=False),
 dict(id="FR_OESTRODOSE", region="FR", kind="label", title="OESTRODOSE 0,06 POUR CENT, gel — notice", publisher="ANSM / Base de données publique des médicaments",
      url=BDPM.format(63071975), document_date="2026-08-14", section="Notice, rubrique 2 « Avertissements et précautions »", third_party=False),
 dict(id="FR_ESTREVA", region="FR", kind="label", title="ESTREVA 0,1 %, gel — notice", publisher="ANSM / Base de données publique des médicaments",
      url=BDPM.format(63216075), document_date="2026-07-06", section="Notice, rubrique 2 « Avertissements et précautions »", third_party=False),
 dict(id="FR_DERMESTRIL", region="FR", kind="label", title="DERMESTRIL 50 microgrammes/24 heures, dispositif transdermique — notice", publisher="ANSM / Base de données publique des médicaments",
      url=BDPM.format(65662980), document_date="2025-07-11", section="Notice, rubrique 2 « Avertissements et précautions »", third_party=False),
 dict(id="FR_PROGYNOVA", region="FR", kind="label", title="PROGYNOVA 2 mg, comprimé enrobé — notice", publisher="ANSM / Base de données publique des médicaments",
      url=BDPM.format(60940721), document_date="2024-05-30", section="Notice, rubrique 2 « Avertissements et précautions »", third_party=False, note="ARCHIVED_FR"),
 dict(id="FR_ANDROCUR", region="FR", kind="label", title="ANDROCUR 50 mg, comprimé sécable — notice", publisher="ANSM / Base de données publique des médicaments",
      url=BDPM.format(61255738), document_date="2024-11-05", section="Notice, rubrique 2 « Avertissements et précautions »", third_party=False),
 dict(id="FR_ANSM_CPA", region="FR", kind="regulator", title="Androcur et risque de méningiome — Questions/réponses", publisher="ANSM",
      url="https://ansm.sante.fr/dossiers-thematiques/androcur-et-risque-de-meningiome/questions-reponses", document_date="2022-12-01",
      section="« Quels sont les symptômes d’un méningiome ? »", third_party=False, note="NON_EXHAUSTIVE"),
 dict(id="TW_MOHW_2022_CPA", region="TW", kind="regulator", title="公告含cyproterone成分藥品之臨床效益及風險再評估結果", publisher="衛生福利部",
      url="https://mohw.gov.tw/cp-16-71612-1.html", document_date="2022-09-15", section="公告正文", third_party=False),
 dict(id="CN_BUJIALE", region="CN", kind="label", title="补佳乐 戊酸雌二醇片 说明书", publisher="第三方转载（药智网；医院用药知识库 swin.zy91.com）",
      url="https://db.yaozh.com/instruct/2679625942310912.html", document_date="核准日期 2006-10-13", section="【注意事项】", third_party=True,
      sites=[{"site": "药智网", "url": "https://db.yaozh.com/instruct/2679625942310912.html", "revised": "2023-05-23"},
             {"site": "医院用药知识库", "url": "https://swin.zy91.com/pages/m-instruction/m.instruction.html?webHisId=26908&hospitalCode=1", "revised": "2025-05-30"}]),
 dict(id="CN_AISITUO", region="CN", kind="label", title="爱斯妥 雌二醇凝胶 说明书", publisher="第三方转载（药智网；39药品通；药源网）",
      url="https://db.yaozh.com/instruct/35999.html", document_date="核准日期 2007-10-17", section="【不良反应】", third_party=True,
      sites=[{"site": "药智网", "url": "https://db.yaozh.com/instruct/35999.html", "revised": "2016-01-06"},
             {"site": "39药品通", "url": "https://ypk.39.net/2309838/manual/", "revised": None},
             {"site": "药源网", "url": "https://www.yaopinnet.com/huayao/hy67164.htm", "revised": None}]),
]
for s in sources: s["viewed"] = "2026-10-07"
actions = {
 "FR_PROVAMES": q("PROVAMES", "Arrêtez de prendre PROVAMES 2 mg, comprimé pelliculé et prévenez immédiatement votre médecin si vous notez l’apparition des signes suivants lors de l’utilisation d’un THS :"),
 "FR_OESTRODOSE": q("OESTRODOSE", "Arrêtez de prendre OESTRODOSE 0,06 POUR CENT et prévenez immédiatement votre médecin si vous notez l’apparition des signes suivants lors de l’utilisation d’un THS :"),
 "FR_ESTREVA": q("ESTREVA", "Arrêtez votre traitement et prévenez immédiatement votre médecin : Si vous notez l’apparition des signes suivants :"),
 "FR_DERMESTRIL": q("DERMESTRIL", "Arrêtez votre traitement et prévenez immédiatement votre médecin Si vous notez l’apparition des signes suivants :"),
 "FR_PROGYNOVA": q("PROGYNOVA", "Arrêtez de prendre PROGYNOVA 2 mg et prévenez immédiatement votre médecin si vous notez l'apparition des signes suivants :"),
 "FR_ANDROCUR_LIVER": q("ANDROCUR", "Arrêtez votre traitement et consultez immédiatement votre médecin si vous présentez des symptômes de toxicité hépatique (ex : démangeaisons sur le corps entier, jaunissement de la peau, urines foncées, douleurs abdominales, troubles digestifs)."),
 "FR_ANDROCUR_MENINGIOMA": q("ANDROCUR", "Si vous remarquez des symptômes tels que des troubles de la vision (par exemple une vision double ou floue), une perte d’audition ou un sifflement dans les oreilles, une perte d’odorat, des maux de tête qui s’aggravent au fil du temps, des pertes de mémoire, des crises convulsives, une faiblesse dans les bras ou les jambes, vous devez en informer immédiatement votre médecin."),
 "FR_ANDROCUR_STOP": q("ANDROCUR", "Arrêtez le traitement et prévenez immédiatement votre médecin en cas de :"),
 "FR_ANSM_CPA": q("ANSM", "Si vous êtes ou vous avez été traitée par Androcur ou ses génériques et que vous présentez un ou plusieurs de ces symptômes, consultez votre médecin qui vous prescrira une IRM cérébrale de contrôle."),
 "TW_MOHW_2022_CPA": q("TW2022", "提醒病人若出現視力變化、聽力喪失或耳鳴、嗅覺喪失、隨時間惡化之頭痛、記憶力喪失、癲癇發作或四肢無力等不適症狀應儘速回診。"),
 "CN_BUJIALE_STOP": q2(["BJL_A", "BJL_B"], "如果患者有禁忌症或出现以下状况，应立即停止治疗："),
 "CN_BUJIALE_VTE": q2(["BJL_A", "BJL_B"], "如果患者出现血栓栓塞的可能症状(尤其是腿部痛性肿胀、突发性胸痛、呼吸急促)，必须立即联系医生。"),
 "CN_BUJIALE_BREAST": q2(["BJL_A", "BJL_B"], "必须告知女性患者，出现哪些乳房改变时必须报告医师或护士"),
 "CN_AISITUO": q2(["EST_A", "EST_B", "EST_C"], "当然，为慎重起见，若出现下列任何一种情况，最好停止用药："),
}
E = []
def e(src, group, quote, action): E.append(dict(source=src, group=group, quote=quote, action=action))
# French estradiol notices: list items quoted exactly per product.
fr = {
 "FR_PROVAMES": ("PROVAMES", [("JAUNDICE", "un jaunissement de votre peau ou du blanc de vos yeux (jaunisse). C’est peut être un signe d’une maladie du foie"),
   ("FACE_SWELLING", "gonflement du visage, de la langue et/ou de la gorge, et/ou difficultés à déglutir ou urticaire, accompagnés de difficultés à respirer qui sont des signes évocateurs d’angioœdème"),
   ("BLOOD_PRESSURE", "une augmentation importante de votre pression artérielle (les symptômes peuvent être un mal de tête, une fatigue, des sensations vertigineuses)"),
   ("HEADACHE", "des maux de tête de type migraine, qui apparaissent pour la première fois"),
   ("LEG", "gonflement douloureux et rougeur au niveau des jambes"), ("CHEST_PAIN", "douleur brutale à la poitrine"), ("BREATHING", "difficulté à respirer")]),
 "FR_OESTRODOSE": ("OESTRODOSE", [("JAUNDICE", "un jaunissement de votre peau ou du blanc de vos yeux. C’est peut être un signe d’une maladie du foie"),
   ("FACE_SWELLING", "un gonflement du visage, de la langue et/ou de la gorge, et/ou des difficultés à déglutir ou une urticaire accompagnée de difficultés à respirer, suggérant un angioedème"),
   ("BLOOD_PRESSURE", "une augmentation importante de votre pression artérielle (les symptômes peuvent être mal de tête, fatigue, sensations vertigineuses)"),
   ("HEADACHE", "des maux de tête tels qu’une migraine, qui apparaissent pour la première fois"),
   ("LEG", "Gonflement douloureux dans vos jambes"), ("CHEST_PAIN", "Douleur brutale à la poitrine"), ("BREATHING", "Difficulté à respirer")]),
 "FR_ESTREVA": ("ESTREVA", [("JAUNDICE", "un jaunissement de votre peau ou du blanc de vos yeux. C’est peut être un signe d’une maladie du foie"),
   ("FACE_SWELLING", "un gonflement du visage, de la langue et/ou de la gorge, et/ou difficultés à déglutir ou urticaire, accompagnés de difficultés à respirer ; des signes évocateurs d’angiœdème"),
   ("BLOOD_PRESSURE", "une augmentation importante de votre pression artérielle (les symptômes peuvent être mal de tête, fatigue, sensations vertigineuses)"),
   ("HEADACHE", "des maux de tête tels qu’une migraine, qui apparaissent pour la première fois"),
   ("LEG", "Gonflement douloureux dans vos jambes"), ("CHEST_PAIN", "Douleur brutale à la poitrine"), ("BREATHING", "Difficulté à respirer")]),
 "FR_DERMESTRIL": ("DERMESTRIL", [("JAUNDICE", "un jaunissement de votre peau ou du blanc de vos yeux. C’est peut-être un signe d’une maladie du foie"),
   ("FACE_SWELLING", "un gonflement du visage, de la langue et/ou de la gorge, et/ou des difficultés à déglutir ou une urticaire accompagnée de difficultés à respirer qui suggèrent un angioœdème"),
   ("BLOOD_PRESSURE", "une augmentation importante de votre pression artérielle (les symptômes peuvent être mal de tête, fatigue, sensations vertigineuses)"),
   ("HEADACHE", "des maux de tête tels qu’une migraine, qui apparaissent pour la première fois"),
   ("LEG", "Gonflement douloureux dans vos jambes"), ("CHEST_PAIN", "Douleur brutale à la poitrine"), ("BREATHING", "Difficulté à respirer")]),
 "FR_PROGYNOVA": ("PROGYNOVA", [("ALLERGY", "une réaction allergique (respiration difficile, oppression thoracique, éruption généralisée à type d'urticaire, démangeaisons)"),
   ("JAUNDICE", "jaunissement de votre peau ou du blanc des yeux ; c’est peut être un signe d’une maladie du foie"),
   ("FACE_SWELLING", "gonflement du visage, de la langue et/ou de la gorge, et/ou difficultés à déglutir ou urticaire, accompagnés de difficultés à respirer ; des signes évocateurs d’angioedème"),
   ("BLOOD_PRESSURE", "une augmentation importante de votre pression artérielle (les symptômes peuvent être mal de tête, fatigue, sensations vertigineuses)"),
   ("HEADACHE", "maux de tête tels qu’une migraine, qui apparaissent pour la première fois"),
   ("LEG", "gonflement douloureux et rougeur au niveau de vos jambes"), ("CHEST_PAIN", "douleur brutale dans la poitrine"), ("BREATHING", "difficulté à respirer")]),
}
for src, (dk, items) in fr.items():
    for g, s in items: e(src, g, q(dk, s), src)
# ANDROCUR: quote the whole sentence for each symptom it lists.
liver = actions["FR_ANDROCUR_LIVER"]; men = actions["FR_ANDROCUR_MENINGIOMA"]
for g in ["ITCHING", "JAUNDICE", "DARK_URINE", "ABDOMINAL_PAIN", "DIGESTIVE"]: e("FR_ANDROCUR", g, liver, "FR_ANDROCUR_LIVER")
for g in ["VISION", "HEARING", "SMELL", "HEADACHE", "MEMORY", "SEIZURE", "LIMB_WEAKNESS"]: e("FR_ANDROCUR", g, men, "FR_ANDROCUR_MENINGIOMA")
stop = {"Jaunisse (au niveau des yeux et de la peau), démangeaisons sur le corps entier.": ["JAUNDICE", "ITCHING"],
        "Douleur inhabituelle dans les jambes, faiblesse dans les membres.": ["LEG", "LIMB_WEAKNESS"],
        "Douleur de la poitrine, pouls irrégulier, essoufflement soudain.": ["CHEST_PAIN", "IRREGULAR_PULSE", "BREATHING"],
        "Perte de connaissance, confusion, maux de tête sévères inhabituels, vertiges, troubles visuels, élocution ralentie ou perte de la parole.": ["CONSCIOUSNESS", "CONFUSION", "HEADACHE", "DIZZINESS", "VISION", "SPEECH"]}
for s, gs in stop.items():
    for g in gs: e("FR_ANDROCUR", g, q("ANDROCUR", s), "FR_ANDROCUR_STOP")
ansm = q("ANSM", "maux de tête fréquents, troubles de l’audition, vertiges, troubles de la mémoire, troubles du langage, faiblesse, paralysie, troubles de la vision, perte d’odorat, convulsions, nausées...")
for g in ["HEADACHE", "HEARING", "DIZZINESS", "MEMORY", "SPEECH", "LIMB_WEAKNESS", "VISION", "SMELL", "SEIZURE", "NAUSEA"]: e("FR_ANSM_CPA", g, ansm, "FR_ANSM_CPA")
tw = actions["TW_MOHW_2022_CPA"]
for g in ["VISION", "HEARING", "SMELL", "HEADACHE", "MEMORY", "SEIZURE", "LIMB_WEAKNESS"]: e("TW_MOHW_2022_CPA", g, tw, "TW_MOHW_2022_CPA")
for g, s in [("JAUNDICE", "黄疸或肝功能恶化"), ("BLOOD_PRESSURE", "严重的血压升高"), ("HEADACHE", "新发的偏头痛型头痛"), ("VISION", "急性视觉障碍或其他损伤")]:
    e("CN_BUJIALE", g, q2(["BJL_A", "BJL_B"], s), "CN_BUJIALE_STOP")
vte = actions["CN_BUJIALE_VTE"]
for g in ["LEG", "CHEST_PAIN", "BREATHING"]: e("CN_BUJIALE", g, vte, "CN_BUJIALE_VTE")
e("CN_BUJIALE", "BREAST_CHANGES", actions["CN_BUJIALE_BREAST"], "CN_BUJIALE_BREAST")
for g, s in [("CARDIO_THROMBO_EVENT", "心血管或血栓栓塞性意外；"), ("JAUNDICE", "胆汁郁积性黄疸；"), ("NIPPLE_DISCHARGE", "乳头溢液：若出现此症状，应检查是否有垂体腺瘤。")]:
    e("CN_AISITUO", g, q2(["EST_A", "EST_B", "EST_C"], s).rstrip("；"), "CN_AISITUO")
groups = {  # neutral names (translations, not source text): en, zh, zh-Hant, fr
 "JAUNDICE": ("Yellowing of the skin or eyes", "皮肤或眼白发黄", "皮膚或眼白發黃", "Jaunissement de la peau ou des yeux"),
 "FACE_SWELLING": ("Swelling of the face, tongue or throat", "脸、舌头或喉咙肿胀", "臉、舌頭或喉嚨腫脹", "Gonflement du visage, de la langue ou de la gorge"),
 "BLOOD_PRESSURE": ("Large rise in blood pressure", "血压明显升高", "血壓明顯升高", "Forte hausse de la tension"),
 "HEADACHE": ("Headache", "头痛", "頭痛", "Maux de tête"),
 "LEG": ("Leg pain or swelling", "腿部疼痛或肿胀", "腿部疼痛或腫脹", "Douleur ou gonflement des jambes"),
 "CHEST_PAIN": ("Chest pain", "胸痛", "胸痛", "Douleur dans la poitrine"),
 "BREATHING": ("Difficulty breathing or sudden breathlessness", "呼吸困难或突然气短", "呼吸困難或突然氣短", "Difficulté à respirer ou essoufflement soudain"),
 "ALLERGY": ("Allergic reaction", "过敏反应", "過敏反應", "Réaction allergique"),
 "VISION": ("Changes in vision", "视力变化", "視力變化", "Troubles de la vision"),
 "BREAST_CHANGES": ("Breast changes", "乳房变化", "乳房變化", "Modifications des seins"),
 "CARDIO_THROMBO_EVENT": ("Cardiovascular or blood-clot event", "心血管或血栓事件", "心血管或血栓事件", "Accident cardiovasculaire ou thrombotique"),
 "NIPPLE_DISCHARGE": ("Nipple discharge", "乳头溢液", "乳頭溢液", "Écoulement du mamelon"),
 "HEARING": ("Hearing loss or ringing in the ears", "听力下降或耳鸣", "聽力下降或耳鳴", "Perte d’audition ou sifflements d’oreille"),
 "SMELL": ("Loss of smell", "嗅觉丧失", "嗅覺喪失", "Perte d’odorat"),
 "MEMORY": ("Memory problems", "记忆力问题", "記憶力問題", "Troubles de la mémoire"),
 "SEIZURE": ("Seizures", "抽搐或癫痫发作", "抽搐或癲癇發作", "Convulsions"),
 "LIMB_WEAKNESS": ("Weakness in the arms or legs", "四肢无力", "四肢無力", "Faiblesse des bras ou des jambes"),
 "SPEECH": ("Speech problems", "说话困难", "說話困難", "Troubles de la parole"),
 "DIZZINESS": ("Dizziness", "头晕", "頭暈", "Vertiges"),
 "NAUSEA": ("Nausea", "恶心", "噁心", "Nausées"),
 "ITCHING": ("Itching all over the body", "全身瘙痒", "全身搔癢", "Démangeaisons sur tout le corps"),
 "DARK_URINE": ("Dark urine", "尿色变深", "尿色變深", "Urines foncées"),
 "ABDOMINAL_PAIN": ("Abdominal pain", "腹痛", "腹痛", "Douleurs abdominales"),
 "DIGESTIVE": ("Digestive problems", "消化不适", "消化不適", "Troubles digestifs"),
 "IRREGULAR_PULSE": ("Irregular pulse", "心跳不规则", "心跳不規則", "Pouls irrégulier"),
 "CONSCIOUSNESS": ("Loss of consciousness", "意识丧失", "意識喪失", "Perte de connaissance"),
 "CONFUSION": ("Confusion", "意识混乱", "意識混亂", "Confusion"),
}
used = {x["group"] for x in E}; assert used <= set(groups), used - set(groups)
actions_out = {k: {"text": v, "urgent": ("immédiatement" in v or "立即" in v)} for k, v in actions.items()}
for src in fr: actions_out[src] = {"text": actions[src], "urgent": True}
monitoring = [
 dict(id="FR_HAS_R32", region="FR", when="SPI", publisher="HAS", title="Transidentité : prise en charge de l’adulte — R32", url="https://www.has-sante.fr/upload/docs/application/pdf/2025-07/transidentite_prise_en_charge_de_ladulte_-_recommandations.pdf",
      document_date="2025-07", section="Page 19, R32",
      quote=q("HAS", "R32. L’utilisation de spironolactone, en complément des œstrogènes, peut s’envisager en cas de prescription d’hormones féminisantes chez une personne trans (AE) sous réserve d’une surveillance de la pression artérielle, ionogramme sanguin et de la créatininémie en respectant les conditions habituelles de prescription.")),
 dict(id="FR_ANSM_IRM", region="FR", when="CPA", publisher="ANSM", title="Androcur et risque de méningiome — Questions/réponses", url="https://ansm.sante.fr/dossiers-thematiques/androcur-et-risque-de-meningiome/questions-reponses",
      document_date="2022-12-01", section="« Je suis traité(e) actuellement par Androcur ou ses génériques, que dois-je faire ? »",
      quote=q("ANSM", "En complément un suivi périodique par imagerie cérébrale (IRM) est à réaliser selon le schéma suivant : une IRM en début de traitement, à renouveler dans les 5 ans, puis tous les 2 ans tant que l’IRM est normale et que le traitement est poursuivi.")),
 dict(id="FR_ANSM_ATTESTATION", region="FR", when="CPA", publisher="ANSM", title="Androcur et risque de méningiome — Questions/réponses", url="https://ansm.sante.fr/dossiers-thematiques/androcur-et-risque-de-meningiome/questions-reponses",
      document_date="2022-12-01", section="« Je suis traité(e) actuellement par Androcur ou ses génériques, que dois-je faire ? »",
      quote=q("ANSM", "Au-delà d’un an de traitement, votre médecin doit vous remettre chaque année une attestation d’information que vous devez compléter et signer ensemble.")),
 dict(id="FR_HAS_R40", region="FR", when="ANY", publisher="HAS", title="Transidentité : prise en charge de l’adulte — R40", url="https://www.has-sante.fr/upload/docs/application/pdf/2025-07/transidentite_prise_en_charge_de_ladulte_-_recommandations.pdf",
      document_date="2025-07", section="Page 21, R40",
      quote=q("HAS", "R40. En l’absence d’étude clinique spécifique, le bilan clinique de surveillance d’une personne trans sous hormones féminisantes sera fait à 3 mois puis à une fréquence adaptée au cas par cas jusqu’à ce que le dosage soit dans les valeurs de référence puis une fois par an.")),
]
reporting = [
 dict(region="FR", publisher="ANSM", url="https://ansm.sante.fr/documents/reference/declarer-un-effet-indesirable/comment-declarer-si-vous-etes-patient-ou-usager",
      quotes=[q("FR_ADR", "Utilisez le portail de signalement des effets indésirables : signalement.social-sante.gouv.fr")]),
 dict(region="TW", publisher="衛生福利部食品藥物管理署", url="https://www.fda.gov.tw/TC/sitecontent.aspx?sid=4240",
      quotes=[q("TW_ADR", "民眾亦可主動通報相關不良反應。"), q("TW_ADR", "1.諮詢電話: 02-23960100")]),
 dict(region="CN", publisher="《药品不良反应报告和监测管理办法》（卫生部令第81号）", url="https://www.gov.cn/gongbao/content/2011/content_2004739.htm",
      quotes=[q("CN_ADR", "个人发现新的或者严重的药品不良反应，可以向经治医师报告，也可以向药品生产、经营企业或者当地的药品不良反应监测机构报告，必要时提供相关的病历资料。")]),
]
rules = [
 dict(molecule="E2", ester="E2", routes=["ORAL"], sources=["FR_PROVAMES"]),
 dict(molecule="E2", ester="E2", routes=["SUBLINGUAL"], sources=["FR_PROVAMES"], note="SUBLINGUAL_NOT_COVERED"),
 dict(molecule="E2", ester="EV", routes=["ORAL"], sources=["CN_BUJIALE", "FR_PROGYNOVA"]),
 dict(molecule="E2", ester="EV", routes=["SUBLINGUAL"], sources=["CN_BUJIALE", "FR_PROGYNOVA"], note="SUBLINGUAL_NOT_COVERED"),
 dict(molecule="E2", ester="E2", routes=["GEL"], sources=["FR_OESTRODOSE", "FR_ESTREVA", "CN_AISITUO"]),
 dict(molecule="E2", ester="E2", routes=["PATCH"], sources=["FR_DERMESTRIL"]),
 dict(molecule="CPA", sources=["FR_ANDROCUR", "FR_ANSM_CPA", "TW_MOHW_2022_CPA"]),
 dict(molecule="SPI", none_listed=True),
]
out = dict(version="2026-10-07", note="Generated from the originals listed in docs/wellbeing-research.md; every quote was checked verbatim against the downloaded source.",
           sources=sources, actions=actions_out, groups={k: dict(en=v[0], zh=v[1], zh_Hant=v[2], fr=v[3]) for k, v in groups.items()},
           entries=E, rules=rules, monitoring=monitoring, reporting=reporting)
json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("entries", len(E), "groups", len(used), "sources", len(sources))
