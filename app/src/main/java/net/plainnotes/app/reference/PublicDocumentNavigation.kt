package net.plainnotes.app.reference

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import net.plainnotes.app.MainActivity

internal enum class DocumentOpenResult { OPENED, INVALID_SOURCE, NOT_AUTHORIZED, BROWSER_UNAVAILABLE }
internal data class DocumentNavigationState(val initialized:Boolean,val foreground:Boolean,val contentVisible:Boolean,val locked:Boolean,
    val ownerGeneration:Long,val currentGeneration:Long,val privateTarget:Boolean,val disguised:Boolean,val primaryAuthorized:Boolean) {
    fun allows(expectedOwner:Long)=initialized && foreground && contentVisible && !locked && !privateTarget &&
        ownerGeneration==expectedOwner && currentGeneration==expectedOwner && (!disguised || primaryAuthorized)
}
internal fun Context.documentActivity():MainActivity? {
    var value=this
    val seen=HashSet<Context>()
    while(seen.add(value)) {
        if(value is MainActivity)return value
        value=(value as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
internal object PublicDocumentNavigation {
    fun open(context:Context,url:String,cis:String?,owner:Long?,userGesture:Boolean):DocumentOpenResult {
        val activity=context.documentActivity()
        return openChecked(url,cis,owner,userGesture,{activity!=null && activity.documentNavigationAllowed(it)}) {intent->activity!!.startActivity(intent)}
    }
    internal fun openChecked(url:String,cis:String?,owner:Long?,userGesture:Boolean,authorized:(Long)->Boolean,launch:(Intent)->Unit):DocumentOpenResult {
        if(!PublicProductPage.valid(url,cis))return DocumentOpenResult.INVALID_SOURCE
        if(!userGesture || owner==null || !authorized(owner))return DocumentOpenResult.NOT_AUTHORIZED
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        return try {launch(intent);DocumentOpenResult.OPENED} catch(_:RuntimeException){DocumentOpenResult.BROWSER_UNAVAILABLE}
    }
}
