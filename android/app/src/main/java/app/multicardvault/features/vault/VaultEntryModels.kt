package app.multicardvault.features.vault

data class VaultEntry(
    val idHex: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class VaultEntryDraft(
    val editingEntryIdHex: String? = null,
    val title: String = "",
    val content: String = "",
) {
    val isEditing: Boolean
        get() = editingEntryIdHex != null
}
