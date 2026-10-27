package com.winstone.connect.ui

/**
 * Mirror of the canonical web list in src/lib/contact-close-reasons.ts.
 * The server re-validates every code; this copy only drives the picker.
 */
object ContactCloseReasons {
    val all: List<Pair<String, String>> = listOf(
        "not_interested" to "Customer Not Interested",
        "info_completed" to "Information Completed",
        "converted" to "Converted / Closed",
        "wrong_number" to "Wrong Number",
        "duplicate" to "Duplicate",
        "no_contact" to "Customer Requested No Further Contact",
        "other" to "Other",
    )

    fun isValid(code: String, note: String): Boolean =
        all.any { it.first == code } && (code != "other" || note.trim().length > 1)
}
