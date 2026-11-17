package io.github.jwillhub.vault

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest

/** Android's autofill framework calls this whenever a text field in another app gains focus. */
class VaultAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            val form = structure?.let { FormParser.parse(it) }
            if (form == null || form.pkg == packageName || !Session.exists(this)) {
                callback.onSuccess(null)
                return
            }
            val response = if (Session.isUnlocked(this)) {
                Responses.unlocked(this, form, Session.matches(form.pkg, form.webDomain))
            } else {
                Responses.locked(this, form)
            }
            callback.onSuccess(response)
        } catch (e: Exception) {
            callback.onFailure(e.message)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            val form = structure?.let { FormParser.parse(it) }
            if (form == null || form.password.isEmpty() || !Session.exists(this)) {
                callback.onFailure("Nothing to save")
                return
            }
            val title = form.webDomain ?: appLabel(form.pkg)
            if (Session.isUnlocked(this)) {
                Session.saveCredential(this, form.pkg, form.webDomain, title, form.username, form.password)
                callback.onSuccess()
            } else {
                // Saving needs the key, so keep the login in memory and ask for the master password.
                PendingSave.set(PendingSave.Login(form.pkg, form.webDomain, title, form.username, form.password))
                callback.onSuccess(Responses.sender(this, AutofillActivity.MODE_SAVE))
            }
        } catch (e: Exception) {
            callback.onFailure(e.message)
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
