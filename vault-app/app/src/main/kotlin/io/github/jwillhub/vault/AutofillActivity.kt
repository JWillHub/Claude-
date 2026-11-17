package io.github.jwillhub.vault

import android.app.Activity
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import android.view.autofill.AutofillManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import io.github.jwillhub.vault.core.Entry
import io.github.jwillhub.vault.core.Matcher

/**
 * Shown on top of another app during autofill: asks for the master password when the vault is
 * locked, lets the user pick a login when none is linked to that app or site yet, and stores a
 * login captured while the vault was locked.
 */
class AutofillActivity : BaseActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_UNLOCK = 1
        const val MODE_PICK = 2
        const val MODE_SAVE = 3
    }

    override val needsUnlockedVault = false
    private var mode = 0
    private var form: Form? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        mode = intent.getIntExtra(EXTRA_MODE, 0)
        if (mode != MODE_SAVE) {
            @Suppress("DEPRECATION")
            val structure = intent.getParcelableExtra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)
            form = structure?.let { FormParser.parse(it) }
            if (form == null) {
                finish()
                return
            }
        }
        if (!Session.exists(this)) {
            toast("Open Vault and create your vault first")
            finish()
            return
        }
        if (Session.isUnlocked(this)) proceed() else setContentView(UnlockPanel(this) { proceed() }.view)
    }

    private fun proceed() {
        when (mode) {
            MODE_SAVE -> {
                val login = PendingSave.take()
                if (login != null) {
                    Session.saveCredential(this, login.pkg, login.webDomain, login.title, login.username, login.password)
                    toast("Saved to Vault")
                }
                finish()
            }
            MODE_UNLOCK -> {
                val form = form!!
                val matches = Session.matches(form.pkg, form.webDomain)
                if (matches.isNotEmpty()) finishWith(Responses.unlocked(this, form, matches)) else showPicker()
            }
            MODE_PICK -> showPicker()
            else -> finish()
        }
    }

    private fun showPicker() {
        val form = form!!
        setTitle("Choose a login")
        val adapter = EntryListAdapter(this)
        val all = Session.all()
        lateinit var search: EditText
        search = searchBox { adapter.show(all, it) }
        adapter.show(all, "")
        val list = ListView(this).apply {
            this.adapter = adapter
            setOnItemClickListener { _, _, position, _ -> picked(adapter.getItem(position)) }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(column(16).apply {
            addView(body(
                if (all.isEmpty()) "Your vault has no logins yet."
                else "No login is linked to ${form.target} yet. The one you choose will be offered there from now on."
            ))
            addView(search)
        })
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun picked(entry: Entry) {
        val form = form!!
        if (!Session.isUnlocked(this)) {
            // Locked itself while the list was open: ask for the master password again.
            setContentView(UnlockPanel(this) { showPicker() }.view)
            return
        }
        // Remember the choice. A login that already belongs to a different website is filled this once
        // but left as it is.
        val domain = form.webDomain
        if (domain == null) {
            if (form.pkg !in entry.apps) Session.put(this, entry.copy(apps = entry.apps + form.pkg))
        } else if (Matcher.hostOf(entry.url) == null) {
            Session.put(this, entry.copy(url = "https://$domain"))
        }
        if (mode == MODE_PICK) {
            finishWith(Responses.dataset(this, entry, form))
        } else {
            finishWith(Responses.chosen(this, form, entry))
        }
    }

    private fun finishWith(result: Parcelable) {
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result))
        finish()
    }
}
