package io.github.jwillhub.vault

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.autofill.AutofillManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseActivity() {
    private companion object {
        const val REQUEST_AUTOFILL = 1
        const val REQUEST_EXPORT = 2
        const val REQUEST_IMPORT = 3
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    }

    private lateinit var autofillStatus: TextView
    private lateinit var lockButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle("Settings")
        actionBar?.setDisplayHomeAsUpEnabled(true)
        val root = column()

        root.addView(caption("Autofill"))
        autofillStatus = body("")
        root.addView(autofillStatus)
        root.addView(button("Choose Vault as the autofill service") { openAutofillSettings() })
        root.addView(body(
            "In Chrome, also open Settings, Autofill services and choose \"Autofill using another service\"."
        ).apply { alpha = 0.7f; textSize = 13f })

        root.addView(caption("Locking"))
        lockButton = button("") { chooseLockTime() }
        root.addView(lockButton)
        root.addView(button("Change master password") { changePassword() })

        root.addView(caption("Backup"))
        root.addView(body(
            "A backup is a copy of the encrypted vault file. It can only be opened with the master " +
                "password it was made with. Keep one somewhere other than this phone: if the phone is " +
                "lost or the app is removed, the vault goes with it."
        ))
        root.addView(button("Save an encrypted backup") { exportBackup() })
        root.addView(button("Bring in a backup") { pickBackup() })

        root.addView(caption("About"))
        root.addView(body(
            "Vault works entirely on this phone. It has no permission to use the network, so nothing " +
                "you store here can be sent anywhere by the app."
        ))
        setContentView(scrolling(root))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val on = getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
        autofillStatus.text = if (on) "Vault is your autofill service." else "Vault is not your autofill service yet."
        lockButton.text = "Lock after ${minutesLabel(Session.lockMinutes(this))} without use"
    }

    private fun minutesLabel(minutes: Int) = if (minutes == 1) "1 minute" else "$minutes minutes"

    private fun openAutofillSettings() {
        try {
            startActivityForResult(
                Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:$packageName")),
                REQUEST_AUTOFILL,
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun chooseLockTime() {
        val choices = Session.LOCK_CHOICES
        AlertDialog.Builder(this)
            .setTitle("Lock after")
            .setSingleChoiceItems(
                choices.map { minutesLabel(it) }.toTypedArray(),
                choices.indexOf(Session.lockMinutes(this)),
            ) { dialog, which ->
                Session.setLockMinutes(this, choices[which])
                dialog.dismiss()
                refresh()
            }
            .show()
    }

    private fun changePassword() {
        val current = input("Current master password", PASSWORD_INPUT)
        val fresh = input("New master password", PASSWORD_INPUT)
        val repeat = input("Repeat new master password", PASSWORD_INPUT)
        val content = column().apply {
            addView(current)
            addView(fresh)
            addView(repeat)
        }
        AlertDialog.Builder(this)
            .setTitle("Change master password")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Change") { _, _ ->
                val old = current.chars()
                val new = fresh.chars()
                val again = repeat.chars()
                val problem = when {
                    new.size < MIN_MASTER_LENGTH -> "Use at least $MIN_MASTER_LENGTH characters."
                    !new.contentEquals(again) -> "The two new passwords do not match."
                    else -> null
                }
                again.fill('\u0000')
                if (problem != null) {
                    old.fill('\u0000')
                    new.fill('\u0000')
                    toast(problem)
                    return@setPositiveButton
                }
                busy(
                    "Re-encrypting the vault…",
                    work = {
                        try {
                            if (Session.verify(this, old)) {
                                Session.changePassword(this, new)
                                true
                            } else false
                        } finally {
                            old.fill('\u0000')
                            new.fill('\u0000')
                        }
                    },
                    done = { ok -> toast(if (ok) "Master password changed" else "The current password is wrong") },
                )
            }
            .show()
    }

    private fun exportBackup() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, "vault-backup-$date.vlt"),
            REQUEST_EXPORT,
        )
    }

    private fun pickBackup() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
            REQUEST_IMPORT,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return
        try {
            when (requestCode) {
                REQUEST_EXPORT -> {
                    contentResolver.openOutputStream(uri)!!.use { it.write(Session.exportBytes(this)) }
                    toast("Backup saved")
                }
                REQUEST_IMPORT -> {
                    val bytes = contentResolver.openInputStream(uri)!!.use { stream ->
                        val read = stream.readNBytes(MAX_BACKUP_BYTES + 1)
                        require(read.size <= MAX_BACKUP_BYTES) { "the file is too large to be a backup" }
                        read
                    }
                    askBackupPassword(bytes)
                }
            }
        } catch (e: Exception) {
            toast("That did not work: ${e.message}")
        }
    }

    private fun askBackupPassword(backup: ByteArray) {
        val password = input("Master password of the backup", PASSWORD_INPUT)
        AlertDialog.Builder(this)
            .setTitle("Bring in a backup")
            .setMessage("Logins from the backup are added to this vault. Where both have the same login, the one edited last is kept.")
            .setView(column().apply { addView(password) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Bring in") { _, _ ->
                val typed = password.chars()
                busy(
                    "Opening the backup…",
                    work = {
                        try {
                            Session.importBackup(this, backup, typed)
                        } finally {
                            typed.fill('\u0000')
                        }
                    },
                    done = { count ->
                        toast(if (count == null) "Wrong password, or this is not a Vault backup" else "Brought in $count logins")
                    },
                )
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
