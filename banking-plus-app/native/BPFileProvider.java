package io.github.jwillhub.bankingplus;

// Banking+'s own file sharer (calendar reminders, app updates). It needs its own class name because
// Capacitor's template already declares androidx.core.content.FileProvider for something else.
public class BPFileProvider extends androidx.core.content.FileProvider {}
