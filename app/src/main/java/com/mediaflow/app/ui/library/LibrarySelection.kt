package com.mediaflow.app.ui.library

/**
 * Multi-select state for library items (long-press to enter).
 */
data class LibrarySelection(
    val selectedIds: Set<String> = emptySet(),
    val inSelectionMode: Boolean = false,
) {
    val count: Int get() = selectedIds.size

    fun enter(id: String): LibrarySelection =
        copy(inSelectionMode = true, selectedIds = setOf(id))

    fun toggle(id: String): LibrarySelection {
        if (!inSelectionMode) return enter(id)
        val next = if (id in selectedIds) selectedIds - id else selectedIds + id
        return if (next.isEmpty()) LibrarySelection() else copy(selectedIds = next)
    }

    fun selectAll(ids: Collection<String>): LibrarySelection =
        copy(inSelectionMode = true, selectedIds = ids.toSet())

    fun clear(): LibrarySelection = LibrarySelection()
}
