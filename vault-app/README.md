# Vault for Android

An offline password manager written in Kotlin. Logins are kept in one encrypted file on the phone,
opened with a master password, and offered to other apps and browsers through Android's autofill
service.

- **Offline by construction.** The app requests no `INTERNET` permission, so Android does not let it
  open a network connection. The build checks the finished APK for this. Android's cloud backup and
  device-to-device transfer are switched off for the app as well.
- **Encryption.** The master password is stretched with PBKDF2-HMAC-SHA256 (600,000 rounds, random
  256-bit salt) into a key for AES-256-GCM. The password and key are never stored; a wrong password or
  an altered file is rejected. `core/VaultCrypto.kt` is the whole of it, and `CoreTest.kt` checks it.
- **Autofill.** In browsers a login is offered only on the exact website it was saved for. In other
  apps it is offered only after you have picked it for that app once. While the vault is locked the
  suggestion is just "Unlock Vault".
- **No libraries.** Only the Kotlin standard library and what Android provides.
- Screens are hidden from screenshots and the recent-apps view; the vault locks itself after a period
  without use (5 minutes unless changed); copied passwords are cleared from the clipboard after 30 s.
- **Backups.** Settings can save a copy of the encrypted vault file and merge one back in. There is no
  password recovery: without the master password the file cannot be opened.

`.github/workflows/vault-app.yml` runs the tests, builds a signed release APK and publishes it as the
`vault-latest` release:
https://github.com/JWillHub/Claude-/releases/download/vault-latest/vault.apk

Signing: with the repository's `SIGNING_PASSPHRASE` secret set, builds are signed with the private key
in `lumen-app/signing.keystore.enc`. Without it they are signed with the public key in
`lumen-app/debug.keystore`, which anyone can use to sign an APK that Android would accept as an update
to this app. Android only installs an update signed with the same key as the installed app, so moving
from one key to the other means: save a backup, uninstall, install the new build, bring the backup in.
