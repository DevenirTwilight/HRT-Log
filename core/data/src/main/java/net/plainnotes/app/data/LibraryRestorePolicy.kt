package net.plainnotes.app.data

/** A caller may impose stricter joint limits, never enlarge the reviewed local budgets. */
internal data class LibraryRestorePolicy(val maximumAliases:Int=512,
    val maximumCiphertextBytes:Long=256L*1024*1024,val minimumFreeBytes:Long=32L*1024*1024) {
    init {require(maximumAliases in 0..512 && maximumCiphertextBytes in 0..256L*1024*1024 && minimumFreeBytes>=32L*1024*1024)}
    fun restrict(a:LibraryRestoreAdmission.Available):LibraryRestoreAdmission.Available {
        val usedAliases=512-a.aliases;val usedCipher=256L*1024*1024-a.ciphertext
        require(usedAliases<=maximumAliases && usedCipher<=maximumCiphertextBytes) {"Retained resources exceed the stricter policy"}
        return a.copy(aliases=maximumAliases-usedAliases,ciphertext=maximumCiphertextBytes-usedCipher)
    }
}
