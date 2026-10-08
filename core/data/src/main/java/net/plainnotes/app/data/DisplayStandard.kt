package net.plainnotes.app.data

import net.plainnotes.app.domain.TherapyStandard

/** UI-only adapter; storage signatures, context v1 and Visit Pack template 1 keep their own builders. */
fun RegimenDefinition.therapyStandard(normalizeCadence:Boolean=true):TherapyStandard {
    val m=snapshot(0)
    val product=when(m?.route){"GEL"->m.profile?.gel_product_id?.let{"gel:$it"};"PATCH"->m.profile?.patch_release_ug_day?.let{"patch:$it"};else->null}
    val standard=TherapyStandard(m?.molecule,m?.profile?.ester,m?.route,m?.unit,product,kind,interval,
        if(kind=="WEEKLY")Integer.bitCount(weekdays) else 0,
        if(kind=="EVERY_N_HOURS")listOf(dose) else times.map{it.second ?: dose})
    if(!normalizeCadence)return standard
    return when {
        kind=="EVERY_N_HOURS" && interval<=24 && 24%interval==0->standard.copy(kind="EVERY_N_DAYS",interval=1,doses=List(24/interval){dose})
        kind=="EVERY_N_HOURS" && interval%24==0->standard.copy(kind="EVERY_N_DAYS",interval=interval/24)
        kind=="WEEKLY" && interval==1 && weekdays==127->standard.copy(kind="EVERY_N_DAYS",interval=1,weeklyCount=0)
        else->standard
    }
}
