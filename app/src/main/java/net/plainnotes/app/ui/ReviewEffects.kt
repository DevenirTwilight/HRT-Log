package net.plainnotes.app.ui

import net.plainnotes.app.R

/** Source table values transcribed in docs/wellbeing-research.md §2.2/2.3. No personal predictions. */
data class ReviewEffect(val id:String,val label:Int,val esName:String,val socName:String,val esOnset:String,val esMax:String,val socOnset:String,val socMax:String)
val REVIEW_EFFECTS=listOf(
    ReviewEffect("FAT",R.string.wb_effect_fat,"Body fat redistribution","Redistribution of body fat","3–6 mo","2–3 y","3–6 months","2–5 years"),
    ReviewEffect("MUSCLE",R.string.wb_effect_muscle,"Decreased muscle mass and strength","Decrease in muscle mass and strength","3–6 mo","1–2 y","3–6 months","1–2 years"),
    ReviewEffect("SKIN",R.string.wb_effect_skin,"Softening of skin/decreased oiliness","Softening of skin/decreased oiliness","3–6 mo","Unknown","3–6 months","Unknown"),
    ReviewEffect("LIBIDO",R.string.wb_effect_libido,"Decreased sexual desire","Decreased sexual desire","1–3 mo","3–6 mo","1–3 months","Unknown"),
    ReviewEffect("ERECTIONS",R.string.wb_effect_erections,"Decreased spontaneous erections","Decreased spontaneous erections","1–3 mo","3–6 mo","1–3 months","3–6 months"),
    ReviewEffect("BREASTS",R.string.wb_effect_breasts,"Breast growth","Breast growth","3–6 mo","2–3 y","3–6 months","2–5 years"),
    ReviewEffect("TESTES",R.string.wb_effect_testes,"Decreased testicular volume","Decreased testicular volume","3–6 mo","2–3 y","3–6 months","Variable"),
    ReviewEffect("BODY_HAIR",R.string.wb_effect_body_hair,"Decreased terminal hair growth","Decreased terminal hair growth","6–12 mo",">3 y","6–12 months","> 3 years"),
    ReviewEffect("SCALP_HAIR",R.string.wb_effect_scalp_hair,"Male pattern hair loss","Increased scalp hair","Variable","—","Variable","Variable"),
)
