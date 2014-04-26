package viggo.omniscient;

public class DelayedWhiteListEnabler implements Runnable {
    private final Plugin plugin;

    public DelayedWhiteListEnabler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.doDisableWhiteList();
    }
}
