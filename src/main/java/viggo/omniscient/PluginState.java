package viggo.omniscient;

public enum PluginState {
    Unknown,
    Disabled,
    Reloading,
    ReloadError,
    SafetyModeEmptyBlockInfo,
    SafetyModeTooManyUnknownBlocksFound,
    Synchronizing, DatabaseConnectionPending, Running
}
