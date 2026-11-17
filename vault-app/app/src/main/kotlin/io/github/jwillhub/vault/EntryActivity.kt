package io.github.jwillhub.vault

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.graphics.Typeface
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.jwillhub.vault.core.Entry
import io.github.jwillhub.vault.core.Matcher
import io.github.jwillhub.vault.core.PasswordGenerator

/** Add, view and edit one login. */
class EntryActivity : BaseActivity() {
    companion object {
        const val EXTRA_ID = "id"
    }

    private var original = Entry()
    private var isNew = true
    private var apps: List<String> = emptyList()
    private lateinit var title: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var url: EditText
    private lateinit var notes: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existing = intent.getStringExtra(EXTRA_ID)?.let { Session.get(it) }
        isNew = existing == null
        original = existing ?: Entry()
        apps = original.apps
        setTitle(if (isNew) "New login" else "Login")
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val plain = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        title = input("Name, for example the site or app", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        username = input("Username or email", plain or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        password = input("Password", PASSWORD_INPUT).apply { typeface = Typeface.MONOSPACE }
        url = input("example.com", plain or InputType.TYPE_TEXT_VARIATION_URI)
        notes = input("Notes", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            .apply { minLines = 2 }
        title.setText(original.title)
        username.setText(original.username)
        password.setText(original.password)
        url.setText(original.url)
        notes.setText(original.notes)

        val root = column()
        root.addView(caption("Name"))
        root.addView(title)

        root.addView(caption("Username"))
        root.addView(row(username, flatButton("Copy") { copySecret(username.text.toString()) }))

        root.addView(caption("Password"))
        var visible = false
        lateinit var toggle: TextView
        toggle = flatButton("Show") {
            visible = !visible
            password.inputType = if (visible) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else PASSWORD_INPUT
            password.typeface = Typeface.MONOSPACE
            password.setSelection(password.text.length)
            toggle.text = if (visible) "Hide" else "Show"
        }
        root.addView(row(password, toggle, flatButton("Copy") { copySecret(password.text.toString()) }))
        root.addView(flatButton("Generate a password…") { showGenerator() })

        root.addView(caption("Website (used to fill this login in browsers)"))
        root.addView(url)

        val appsCaption = caption("Apps this login fills into")
        val appsText = body(apps.joinToString("\n"))
        lateinit var unlink: View
        unlink = flatButton("Unlink these apps") {
            apps = emptyList()
            appsCaption.visibility = View.GONE
            appsText.visibility = View.GONE
            unlink.visibility = View.GONE
        }
        if (apps.isNotEmpty()) {
            root.addView(appsCaption)
            root.addView(appsText)
            root.addView(unlink)
        }

        root.addView(caption("Notes"))
        root.addView(notes)
        setContentView(scrolling(root))
    }

    private fun row(field: View, vararg buttons: View): LinearLayout = LinearLayout(this).apply {
        addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        for (b in buttons) addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun current(): Entry = original.copy(
        title = title.text.toString().trim(),
        username = username.text.toString(),
        password = password.text.toString(),
        url = url.text.toString().trim(),
        apps = apps,
        notes = notes.text.toString(),
    )

    private fun save() {
        var entry = current()
        if (entry.title.isEmpty()) entry = entry.copy(title = Matcher.hostOf(entry.url) ?: "Untitled")
        if (!Session.isUnlocked(this)) {
            toast("The vault locked itself before this was saved")
            finish()
            return
        }
        Session.put(this, entry)
        toast("Saved")
        finish()
    }

    private fun showGenerator() {
        val preview = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 17f
            setTextIsSelectable(false)
            setPadding(0, 0, 0, dp(12))
        }
        val lengthLabel = caption("")
        val length = SeekBar(this).apply {
            min = 8
            max = 64
            progress = 20
        }
        val upper = CheckBox(this).apply { text = "Capital letters"; isChecked = true }
        val digits = CheckBox(this).apply { text = "Digits"; isChecked = true }
        val symbols = CheckBox(this).apply { text = "Symbols"; isChecked = true }

        fun regenerate() {
            lengthLabel.text = "Length: ${length.progress}"
            preview.text = PasswordGenerator.generate(length.progress, upper.isChecked, digits.isChecked, symbols.isChecked)
        }
        length.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) = regenerate()
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        for (box in listOf(upper, digits, symbols)) box.setOnCheckedChangeListener { _, _ -> regenerate() }
        regenerate()

        val content = column().apply {
            addView(preview)
            addView(lengthLabel)
            addView(length)
            addView(upper)
            addView(digits)
            addView(symbols)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Generate a password")
            .setView(content)
            .setPositiveButton("Use it") { _, _ -> password.setText(preview.text.toString()) }
            .setNeutralButton("Another", null)
            .setNegativeButton("Cancel", null)
            .show()
        dialog.window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
        // Set after show() so that "Another" makes a new password without closing the dialog.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { regenerate() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Save").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        if (!isNew) menu.add(0, 2, 1, "Delete")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> leave()
            1 -> save()
            2 -> AlertDialog.Builder(this)
                .setMessage("Delete this login? This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    Session.delete(this, original.id)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun leave() {
        if (current() == original) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setMessage("Discard your changes?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = leave()
}
