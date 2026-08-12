package io.github.darkpro1337.bot.keyboards

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import io.github.darkpro1337.bot.wizard.WatchDraft
import io.github.darkpro1337.greenhouse.MetadataFieldOption

object Callbacks {
    const val OFFICES_DONE = "nav:offices_done"
    const val OFFICES_CLEAR = "nav:offices_clear"
    const val META_DONE = "nav:meta_done"
    const val META_SKIP = "nav:meta_skip"
    const val CANCEL = "nav:cancel"
    const val PREFIX_OFFICE = "o:"
    const val PREFIX_META = "m:"
    const val PREFIX_REMOVE = "rm:"

    fun officeToggle(officeId: Long) = "$PREFIX_OFFICE$officeId"
    fun metaToggle(valueIndex: Int) = "$PREFIX_META$valueIndex"
    fun remove(watchId: Long) = "$PREFIX_REMOVE$watchId"
}

object WatchKeyboards {
    fun offices(draft: WatchDraft): InlineKeyboardMarkup {
        val offices = draft.catalog?.offices.orEmpty()
        val rows = offices.chunked(2).map { chunk ->
            chunk.map { office ->
                val mark = if (office.id in draft.selectedOfficeIds) "✓ " else ""
                InlineKeyboardButton.CallbackData(
                    text = "$mark${office.name}".take(64),
                    callbackData = Callbacks.officeToggle(office.id),
                )
            }
        }

        val controls = listOf(
            listOf(
                InlineKeyboardButton.CallbackData("Clear", Callbacks.OFFICES_CLEAR),
                InlineKeyboardButton.CallbackData("Next →", Callbacks.OFFICES_DONE),
            ),
            listOf(InlineKeyboardButton.CallbackData("Cancel", Callbacks.CANCEL)),
        )
        return InlineKeyboardMarkup.create(rows + controls)
    }

    fun metadata(draft: WatchDraft, field: MetadataFieldOption): InlineKeyboardMarkup {
        val selected = draft.selectedMetadata[field.fieldId].orEmpty()
        val rows = field.values.mapIndexed { index, value ->
            val mark = if (value in selected) "✓ " else ""
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "$mark$value".take(64),
                    callbackData = Callbacks.metaToggle(index),
                ),
            )
        }

        val controls = listOf(
            listOf(
                InlineKeyboardButton.CallbackData("Skip field", Callbacks.META_SKIP),
                InlineKeyboardButton.CallbackData("Next →", Callbacks.META_DONE),
            ),
            listOf(InlineKeyboardButton.CallbackData("Cancel", Callbacks.CANCEL)),
        )

        return InlineKeyboardMarkup.create(rows + controls)
    }

    fun removeList(watchIds: List<Long>): InlineKeyboardMarkup {
        val rows = watchIds.map { id ->
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "Remove #$id",
                    callbackData = Callbacks.remove(id),
                ),
            )
        }

        return InlineKeyboardMarkup.create(rows)
    }
}
