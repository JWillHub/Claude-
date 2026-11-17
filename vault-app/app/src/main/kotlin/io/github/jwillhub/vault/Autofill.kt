package io.github.jwillhub.vault

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import io.github.jwillhub.vault.core.Entry
import io.github.jwillhub.vault.core.Matcher

/** The login form found on the screen another app asked to have filled. */
class Form(
    val pkg: String,
    /** Set only when a trusted browser reported which site it is showing. */
    val webDomain: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val username: String,
    val password: String,
) {
    val ids: Array<AutofillId> get() = listOfNotNull(usernameId, passwordId).toTypedArray()
    val target: String get() = webDomain ?: pkg
}

/** Finds the username and password fields in the view tree Android hands to an autofill service. */
object FormParser {
    private const val OTHER = 0
    private const val USERNAME = 1
    private const val PASSWORD = 2

    private class Field(val id: AutofillId, val kind: Int, val text: String)

    fun parse(structure: AssistStructure): Form? {
        val pkg = structure.activityComponent?.packageName ?: return null
        val fields = ArrayList<Field>()
        var domain: String? = null

        fun walk(node: AssistStructure.ViewNode) {
            if (domain == null) domain = node.webDomain?.takeIf { it.isNotEmpty() }
            val id = node.autofillId
            if (id != null && isTextInput(node)) {
                val value = node.autofillValue
                val text = if (value != null && value.isText) value.textValue.toString() else node.text?.toString().orEmpty()
                fields.add(Field(id, classify(node), text))
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)

        val passwordIndex = fields.indexOfFirst { it.kind == PASSWORD }
        val password = fields.getOrNull(passwordIndex)
        val username = if (password != null) {
            // The username is the labelled field before the password, or failing that the nearest text field above it.
            val before = fields.subList(0, passwordIndex)
            before.lastOrNull { it.kind == USERNAME } ?: before.lastOrNull { it.kind == OTHER }
        } else {
            // Sign-in pages that ask for the username first and the password on the next page.
            fields.firstOrNull { it.kind == USERNAME }
        }
        if (password == null && username == null) return null

        return Form(
            pkg = pkg,
            webDomain = domain?.takeIf { pkg in Matcher.BROWSERS },
            usernameId = username?.id,
            passwordId = password?.id,
            username = username?.text.orEmpty(),
            password = password?.text.orEmpty(),
        )
    }

    private fun htmlAttribute(node: AssistStructure.ViewNode, name: String): String =
        node.htmlInfo?.attributes?.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second?.lowercase().orEmpty()

    private fun isTextInput(node: AssistStructure.ViewNode): Boolean {
        if (node.visibility != View.VISIBLE) return false
        val html = node.htmlInfo
        if (html != null) {
            if (!html.tag.equals("input", ignoreCase = true)) return false
            return htmlAttribute(node, "type") in setOf("", "text", "email", "password", "tel", "number", "url")
        }
        if (node.autofillType != View.AUTOFILL_TYPE_TEXT) return false
        return node.inputType != InputType.TYPE_NULL || node.className?.contains("EditText") == true
    }

    private fun classify(node: AssistStructure.ViewNode): Int {
        // 1. What the app or page declares outright.
        val hints = node.autofillHints?.map { it.lowercase() }.orEmpty()
        if (hints.any { it.contains("password") }) return PASSWORD
        if (hints.any { it.contains("username") || it.contains("email") }) return USERNAME

        val type = htmlAttribute(node, "type")
        val autocomplete = htmlAttribute(node, "autocomplete")
        if (type == "password" || autocomplete.contains("password")) return PASSWORD
        if (type == "email" || autocomplete == "username" || autocomplete == "email") return USERNAME

        // 2. How the field is configured.
        val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) return PASSWORD
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return USERNAME
        }
        if (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return PASSWORD

        // 3. A guess from the field's name and placeholder.
        val words = listOf(node.idEntry, node.hint, htmlAttribute(node, "name"), htmlAttribute(node, "id"))
            .joinToString(" ") { it.orEmpty() }.lowercase()
        if (words.contains("pass") || words.contains("pwd")) return PASSWORD
        if (listOf("user", "email", "e-mail", "login", "account", "identifier").any { words.contains(it) }) return USERNAME
        return OTHER
    }
}

/** A login captured by "Save to Vault?" while the vault was locked, held in memory until it is unlocked. */
object PendingSave {
    class Login(val pkg: String, val webDomain: String?, val title: String, val username: String, val password: String)

    private var login: Login? = null

    @Synchronized
    fun set(value: Login) {
        login = value
    }

    @Synchronized
    fun take(): Login? = login.also { login = null }
}

/** Builds what the service answers the autofill framework with. */
object Responses {
    private const val MAX_SUGGESTIONS = 8

    private fun row(context: Context, text: String): RemoteViews =
        RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, text)
        }

    fun sender(context: Context, mode: Int): IntentSender {
        val intent = Intent(context, AutofillActivity::class.java).putExtra(AutofillActivity.EXTRA_MODE, mode)
        // Mutable because the framework adds the screen structure to the intent when it launches it.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(context, mode, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable).intentSender
    }

    @Suppress("DEPRECATION")
    fun dataset(context: Context, entry: Entry, form: Form): Dataset {
        val label = listOf(entry.title, entry.username).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Login" }
        val builder = Dataset.Builder(row(context, label))
        form.usernameId?.let { builder.setValue(it, AutofillValue.forText(entry.username)) }
        form.passwordId?.let { builder.setValue(it, AutofillValue.forText(entry.password)) }
        return builder.build()
    }

    /** A suggestion that opens the vault so the user can pick any login. */
    @Suppress("DEPRECATION")
    private fun searchDataset(context: Context, form: Form): Dataset {
        val builder = Dataset.Builder(row(context, "Search Vault…"))
        for (id in form.ids) builder.setValue(id, null)
        builder.setAuthentication(sender(context, AutofillActivity.MODE_PICK))
        return builder.build()
    }

    private fun addSaveInfo(builder: FillResponse.Builder, form: Form) {
        val passwordId = form.passwordId ?: return
        val types = SaveInfo.SAVE_DATA_TYPE_PASSWORD or
            (if (form.usernameId != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
        val info = SaveInfo.Builder(types, arrayOf(passwordId))
        form.usernameId?.let { info.setOptionalIds(arrayOf(it)) }
        info.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        builder.setSaveInfo(info.build())
    }

    /** The vault is unlocked: suggest the matching logins, and offer a search for anything else. */
    fun unlocked(context: Context, form: Form, matches: List<Entry>): FillResponse {
        val builder = FillResponse.Builder()
        for (entry in matches.take(MAX_SUGGESTIONS)) builder.addDataset(dataset(context, entry, form))
        builder.addDataset(searchDataset(context, form))
        addSaveInfo(builder, form)
        return builder.build()
    }

    /** One chosen login, as the answer after the user unlocked the vault and picked it. */
    fun chosen(context: Context, form: Form, entry: Entry): FillResponse {
        val builder = FillResponse.Builder().addDataset(dataset(context, entry, form))
        addSaveInfo(builder, form)
        return builder.build()
    }

    /** The vault is locked: reveal nothing, only offer to unlock. */
    @Suppress("DEPRECATION")
    fun locked(context: Context, form: Form): FillResponse {
        val builder = FillResponse.Builder()
            .setAuthentication(form.ids, sender(context, AutofillActivity.MODE_UNLOCK), row(context, "Unlock Vault"))
        addSaveInfo(builder, form)
        return builder.build()
    }
}
