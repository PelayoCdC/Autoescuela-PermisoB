package com.pelayo.autoescuela.lwjgl3;

import com.pelayo.autoescuela.app.Speech;

/**
 * Voz del examinador en Windows con System.Speech (la síntesis de voz que trae
 * el sistema), lanzando PowerShell por cada frase. Elige una voz en español si
 * la hay. Una frase nueva corta la anterior.
 */
final class WindowsSpeech implements Speech {

    private Process current;

    static boolean available() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    @Override
    public synchronized void speak(String text) {
        stop();
        String escaped = text.replace("'", "''").replace("\r", " ").replace("\n", " ");
        String script = "Add-Type -AssemblyName System.Speech;"
            + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;"
            + "$v = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'es*' } | Select-Object -First 1;"
            + "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) };"
            + "$s.Rate = 1; $s.Speak('" + escaped + "')";
        try {
            current = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (Exception e) {
            current = null; // sin PowerShell: sin voz
        }
    }

    @Override
    public synchronized void stop() {
        if (current != null) {
            current.destroy();
            current = null;
        }
    }
}
