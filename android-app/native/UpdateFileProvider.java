package io.github.jwillhub.rollingcredits;

import androidx.core.content.FileProvider;

// Shares the downloaded update with Android's installer (its own class so it doesn't clash with Capacitor's provider)
public class UpdateFileProvider extends FileProvider {}
