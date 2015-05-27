package edu.ucla.cs.ndnwhiteboard.interfaces;

import net.named_data.jndn.sync.ChronoSync2013;

import java.util.Map;

/**
 * Interface for Activity that uses ChronoSync
 */
public abstract class NDNChronoSyncActivity extends NDNActivity {
    public ChronoSync2013 sync = null;
    public String username;

    // Keeping track of what seq nos are requested from each user
    public Map<String, Long> highestRequested;

    public String applicationBroadcastPrefix;
}
