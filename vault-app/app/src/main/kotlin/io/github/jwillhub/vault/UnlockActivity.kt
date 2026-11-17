package io.github.jwillhub.vault

import android.content.Intent
import android.os.Bundle

class UnlockActivity : BaseActivity() {
    override val needsUnlockedVault = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(UnlockPanel(this) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }.view)
    }
}
