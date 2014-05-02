package viggo.omniscient;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class Logger {

    private final Plugin plugin;
    private final java.util.logging.Logger uLogger;
    private AtomicInteger currentSevereCnt = new AtomicInteger();

    public Logger(Plugin plugin, java.util.logging.Logger uLogger) {
        this.plugin = plugin;
        this.uLogger = uLogger;
    }

    public void logInfo(String message) {
        uLogger.info(String.format("[%d] %s", Thread.currentThread().getId(), message));
    }

    public void logWarn(String message) {
        uLogger.warning(String.format("[%d] %s", Thread.currentThread().getId(), message));
    }

    public void logSevere(String message) {

        uLogger.severe(String.format("[%d] %s", Thread.currentThread().getId(), message));

        final int maxSevereLogsToDump = 10; // could consider settings, but nah!

        if (currentSevereCnt.incrementAndGet() < maxSevereLogsToDump) {

            final String dumpLogFilename = String.format("%d.dump.log", new Date().getTime());
            final String dumpLogFilePath = plugin.getDataFolder().toString() + File.separator + dumpLogFilename;

            try {
                PrintWriter out = new PrintWriter(dumpLogFilePath);
                out.println(String.format("[%d] %s", Thread.currentThread().getId(), message));
                out.flush();
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (plugin.settings.devUser == null) {
            return;
        }

        final Player player = plugin.getServer().getPlayer(plugin.settings.devUser);

        if (player != null && player.isOnline()) {
            player.sendMessage("Omniscient error: " + message.substring(0, Math.min(message.length() - 1, 30)));
        }
    }

    public void logSevere(Throwable t) {
        // http://stackoverflow.com/questions/1149703/stacktrace-to-string-in-java
        String str = ExceptionUtils.getStackTrace(t);
        logSevere(str);
    }

    public void logSevere(Throwable t, CommandSender sender) {

        logSevere(t);

        if (sender == null) {
            return;
        }

        try {
            final StackTraceElement stackTraceElement = t.getStackTrace()[0];
            String message = String.format(
                    "Error:%s @ %s[%d]",
                    t.getMessage(),
                    stackTraceElement.getMethodName(),
                    stackTraceElement.getLineNumber()
            );
            sender.sendMessage(message);

        } catch (Throwable tSwallow) {
        }

    }
}
