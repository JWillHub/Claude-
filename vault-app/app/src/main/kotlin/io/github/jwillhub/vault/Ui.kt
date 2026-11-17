package io.github.jwillhub.vault

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

const val PASSWORD_INPUT = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
const val MIN_MASTER_LENGTH = 10

/** Every screen: hidden from screenshots and the recent-apps preview, and never offered to autofill. */
abstract class BaseActivity : Activity() {
    protected open val needsUnlockedVault = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    override fun onResume() {
        super.onResume()
        if (needsUnlockedVault && !Session.isUnlocked(this)) {
            startActivity(
                Intent(this, UnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }

    override fun onUserInteraction() {
        Session.touch()
    }
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Context.column(padding: Int = 20): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
}

fun Context.scrolling(content: View): ScrollView = ScrollView(this).apply {
    isFillViewport = true
    addView(content)
}

fun Context.caption(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 13f
    alpha = 0.7f
    setPadding(0, dp(14), 0, 0)
}

fun Context.body(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 15f
    setLineSpacing(0f, 1.15f)
}

fun Context.input(hint: String, type: Int): EditText = EditText(this).apply {
    this.hint = hint
    inputType = type
    if (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) setSingleLine()
    // Typing counts as use, so the vault does not lock in the middle of an edit.
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = Session.touch()
    })
}

fun Context.button(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    isAllCaps = false
    setOnClickListener { onClick() }
}

fun Context.flatButton(text: String, onClick: () -> Unit): Button =
    Button(this, null, android.R.attr.borderlessButtonStyle).apply {
        this.text = text
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setOnClickListener { onClick() }
    }

/** The typed text as characters that can be wiped afterwards, unlike a String. */
fun EditText.chars(): CharArray {
    val length = text.length
    return CharArray(length).also { text.getChars(0, length, it, 0) }
}

/** Runs slow work (key derivation) off the main thread behind a message that cannot be dismissed. */
fun <T> Activity.busy(message: String, work: () -> T, done: (T) -> Unit) {
    val dialog = AlertDialog.Builder(this).setMessage(message).setCancelable(false).show()
    Thread {
        val result = runCatching(work)
        runOnUiThread {
            if (!isFinishing && !isDestroyed) dialog.dismiss()
            result.onSuccess(done).onFailure { toast("That did not work: ${it.message}") }
        }
    }.start()
}

private val clipboardHandler = Handler(Looper.getMainLooper())

/** Copies a secret, marked so Android does not show it in the clipboard preview, and clears it after 30 s. */
fun Context.copySecret(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Vault", text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
    clipboardHandler.removeCallbacksAndMessages(null)
    clipboardHandler.postDelayed({
        try {
            clipboard.clearPrimaryClip()
        } catch (e: Exception) {
            // nothing to clear
        }
    }, 30_000)
    toast("Copied. The clipboard is cleared in 30 seconds.")
}

/** The master-password form: creates the vault on first use, unlocks it afterwards. */
class UnlockPanel(private val activity: Activity, private val onUnlocked: () -> Unit) {
    val view: View

    init {
        val creating = !Session.exists(activity)
        val root = activity.column(28)
        root.gravity = Gravity.CENTER_VERTICAL

        root.addView(TextView(activity).apply {
            text = if (creating) "Create your vault" else "Vault is locked"
            textSize = 24f
        })
        root.addView(activity.body(
            if (creating)
                "Choose a master password of at least $MIN_MASTER_LENGTH characters. It encrypts everything " +
                    "you store here and is never saved anywhere. If you forget it, the vault cannot be " +
                    "recovered by anyone."
            else "Enter your master password."
        ).apply { setPadding(0, activity.dp(8), 0, activity.dp(12)) })

        val password = activity.input("Master password", PASSWORD_INPUT)
        val confirm = activity.input("Repeat master password", PASSWORD_INPUT)
        val error = TextView(activity).apply { setTextColor(0xFFC62828.toInt()) }
        root.addView(password)
        if (creating) root.addView(confirm)
        root.addView(error)

        fun submit() {
            val typed = password.chars()
            if (creating) {
                val repeated = confirm.chars()
                val same = typed.contentEquals(repeated)
                repeated.fill('\u0000')
                if (typed.size < MIN_MASTER_LENGTH) {
                    error.text = "Use at least $MIN_MASTER_LENGTH characters."
                    typed.fill('\u0000')
                    return
                }
                if (!same) {
                    error.text = "The two passwords do not match."
                    typed.fill('\u0000')
                    return
                }
            } else if (typed.isEmpty()) {
                return
            }
            error.text = ""
            activity.busy(
                if (creating) "Creating vault…" else "Unlocking…",
                work = {
                    try {
                        if (creating) {
                            Session.create(activity, typed)
                            true
                        } else {
                            Session.unlock(activity, typed)
                        }
                    } finally {
                        typed.fill('\u0000')
                    }
                },
                done = { ok ->
                    password.text.clear()
                    confirm.text.clear()
                    if (ok) onUnlocked() else error.text = "Wrong master password."
                },
            )
        }

        val last = if (creating) confirm else password
        last.imeOptions = EditorInfo.IME_ACTION_DONE
        last.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else false
        }
        root.addView(activity.button(if (creating) "Create vault" else "Unlock") { submit() })
        view = activity.scrolling(root)
        password.requestFocus()
    }
}
