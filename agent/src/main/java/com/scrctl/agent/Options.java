package com.scrctl.agent;

import com.scrctl.agent.util.Ln;

import java.util.Locale;

public class Options {

    private Ln.Level logLevel = Ln.Level.DEBUG;
    private int aid = -1; // 31-bit non-negative value, or -1

    private boolean cleanup = true;

    public Ln.Level getLogLevel() {
        return logLevel;
    }

    public int getAid() {
        return aid;
    }

    public boolean getCleanup() {
        return cleanup;
    }

    @SuppressWarnings("MethodLength")
    public static Options parse(String... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing client version");
        }

        String clientVersion = args[0];
        if (!clientVersion.equals(BuildConfig.VERSION_NAME)) {
            throw new IllegalArgumentException(
                    "The server version (" + BuildConfig.VERSION_NAME + ") does not match the client " + "(" + clientVersion + ")");
        }

        Options options = new Options();

        for (int i = 1; i < args.length; ++i) {
            String arg = args[i];
            int equalIndex = arg.indexOf('=');
            if (equalIndex == -1) {
                throw new IllegalArgumentException("Invalid key=value pair: \"" + arg + "\"");
            }
            String key = arg.substring(0, equalIndex);
            String value = arg.substring(equalIndex + 1);
            switch (key) {
                case "aid":
                    int aid = Integer.parseInt(value, 0x10);
                    if (aid < -1) {
                        throw new IllegalArgumentException("aid may not be negative (except -1 for 'none'): " + aid);
                    }
                    options.aid = aid;
                    break;
                case "log_level":
                    options.logLevel = Ln.Level.valueOf(value.toUpperCase(Locale.ENGLISH));
                    break;
                default:
                    Ln.w("Unknown server option: " + key);
                    break;
            }
        }

        return options;
    }

}
