package dev.prokop.ibkr.twsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TwsSyncBridge provides a stateful, synchronous-like view over the asynchronous TWS API.
 * It maintains a local mirror of the portfolio state (positions, PnL) and bridges
 * callbacks to CompletableFutures.
 */
public class TwsSyncBridge {

    private static final Logger log = LoggerFactory.getLogger(TwsSyncBridge.class);

    public static void main(String[] args) throws Exception {
        final var twsApi = new TwsApi();
        twsApi.connect("127.0.0.1");

        final var twsSyncBridge = new TwsSyncBridge(twsApi);
        log.info("Positions: {}", twsSyncBridge.getPositions().get());
        log.info("Managed Accounts: {}", twsSyncBridge.getManagedAccounts().get());
    }

    private final TwsApi twsApi;
    private final Map<String, TwsEvent.Position> positions = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> positionsInitialSync = new CompletableFuture<>();

    public TwsSyncBridge(TwsApi twsApi) {
        this.twsApi = twsApi;
        setupListeners();

        // Automatically start the global positions subscription
        this.twsApi.reqPositions();
    }

    private void setupListeners() {
        // Track real-time position updates
        twsApi.on(TwsEvent.Position.class, position -> {
            // Uniquely identify a position by Account ID and Contract ID
            String key = position.account() + ":" + position.contract().conid();
            
            if (position.pos().isZero()) {
                positions.remove(key);
            } else {
                positions.put(key, position);
            }
        });

        // Signal when the initial snapshot is complete
        twsApi.on(TwsEvent.PositionEnd.class, end -> {
            if (!positionsInitialSync.isDone()) {
                positionsInitialSync.complete(null);
            }
        });
    }

    /**
     * Returns the current snapshot of all positions.
     * The future completes once the initial TWS snapshot is finished.
     */
    public CompletableFuture<Collection<TwsEvent.Position>> getPositions() {
        return positionsInitialSync.thenApply(v -> Collections.unmodifiableCollection(positions.values()));
    }

    public CompletableFuture<Collection<String>> getManagedAccounts() {
        return CompletableFuture.completedFuture(twsApi.getAccountsList());
    }

    /**
     * Returns a future that completes when the bridge has finished its initial data sync.
     */
    public CompletableFuture<Void> ready() {
        return positionsInitialSync;
    }
}
