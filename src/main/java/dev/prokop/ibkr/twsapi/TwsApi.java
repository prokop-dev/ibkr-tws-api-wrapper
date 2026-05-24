package dev.prokop.ibkr.twsapi;

import com.ib.client.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TwsApi {

    /**
     * A "Readiness Gate" that handles three distinct states: Disconnected, Connecting, and Ready.
     */
    private final AtomicReference<CompletableFuture<Void>> readyGate =
            // Initialize as a failed future so calls before connect() fail immediately
            new AtomicReference<>(CompletableFuture.failedFuture(new IllegalStateException("Not connected")));

    private final EClientSocket eClientSocket;

    public TwsApi() {
        // Just initializes parent of EClientSocket, no socket/connectivity here.
        eClientSocket = new EClientSocket(eWrapper, eReaderSignal);
    }

    private void ensureReady() {
        CompletableFuture<Void> gate = readyGate.get();
        try {
            // Wait for readiness with a timeout to avoid hanging the MCP server forever
            gate.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("TWS API not ready: " + e.getMessage(), e);
        }
    }

    public CompletableFuture<Void> connect(String host) {
        return connect(host, 4001, 1);
    }

    public CompletableFuture<Void> connect(String host, int port, int clientId) {
        readyGate.set(new CompletableFuture<>()); // Replace the gate with a new, incomplete future
        System.out.println("Connecting to " + host);
        eClientSocket.eConnect(host, port, clientId);
        if (eClientSocket.isConnected()) {
            start();
        } else {
            readyGate.get().completeExceptionally(
                    new RuntimeException("Failed to connect to " + host + ":" + port + ". Is IB Gateway running and API enabled?")
            );
        }
        return readyGate.get();
    }

    private void start() {
        System.out.println("Connected successfully! Starting reader thread...");

        // Create the background reader thread to process incoming socket data
        final EReader eReader = new EReader(eClientSocket, eReaderSignal);
        eReader.start();

        // Thread to process the signal queue and feed data to EWrapper
        new Thread(() -> {
            while (eClientSocket.isConnected()) {
                eReaderSignal.waitForSignal();
                try {
                    eReader.processMsgs();
                } catch (Exception e) {
                    System.err.println("Exception handling message: " + e.getMessage());
                }
            }
            System.out.println("Exit of the loop");
        }).start();
    }


    public void disconnect() {
        System.out.println("Dis1");
        if (eClientSocket.isConnected()) {
            eClientSocket.eDisconnect();
        }
        System.out.println("Dis2");
    }

    private final EReaderSignal eReaderSignal = new EJavaSignal();
    private final EWrapper eWrapper = new DefaultEWrapper() {

        @Override
        public void accountSummary(int reqId, String account, String tag, String value, String currency) {
            dispatch(new TwsEvent.AccountSummary(reqId, account, tag, value, currency));
        }

        @Override
        public void accountSummaryEnd(int reqId) {
            dispatch(new TwsEvent.AccountSummaryEnd(reqId));
        }

        @Override
        public void connectAck() {
            System.out.println("Connection acknowledged by IB Gateway!");
        }

        @Override
        public void connectionClosed() {
            System.out.println("Connection to IB Gateway closed.");
            // Reset the gate to a new incomplete future.
            // Any thread that calls a req* method from this moment on will
            // BLOCK at ensureReady() until the reconnection logic finishes.
            readyGate.set(new CompletableFuture<>());
            dispatch(new TwsEvent.ConnectionClosed());
        }

        @Override
        public void error(Exception e) {
            System.out.println("API Exception: " + e.getMessage());
        }

        @Override
        public void error(String str) {
            System.out.println("API Error Message: " + str);
        }

        @Override
        public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
            dispatch(new TwsEvent.Error(id, errorTime, errorCode, errorMsg, advancedOrderRejectJson));
        }

        @Override
        public void managedAccounts(String accountsList) {
            System.out.println("managedAccounts:" + accountsList);
            managedAccounts.addAll(List.of(accountsList.split(",")));
        }

        @Override
        public void nextValidId(int orderId) {
            System.out.println("nextValidId:" + orderId);
            nextValidId.set(orderId);

            // Open the gate! All blocked req* calls now proceed simultaneously.
            readyGate.get().complete(null);
        }

        @Override
        public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
            dispatch(new TwsEvent.Pnl(reqId, dailyPnL, unrealizedPnL, realizedPnL));
        }

        @Override
        public void position(String account, Contract contract, Decimal pos, double avgCost) {
            dispatch(new TwsEvent.Position(account, contract, pos, avgCost));
        }

        @Override
        public void positionEnd() {
            dispatch(new TwsEvent.PositionEnd());
        }
    };

    private final Map<Class<? extends TwsEvent>, List<Consumer<? extends TwsEvent>>> listeners = new ConcurrentHashMap<>();

    public <T extends TwsEvent & TwsEvent.Concrete> void on(Class<T> type, Consumer<T> consumer) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    private <T extends TwsEvent> void dispatch(T event) {
        List<Consumer<? extends TwsEvent>> consumers = listeners.get(event.getClass());
        if (consumers != null) {
            consumers.forEach(c -> ((Consumer<T>) c).accept(event));
        }
    }

    private final List<String> managedAccounts = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextValidId = new AtomicInteger(Integer.MIN_VALUE);

    private int nextValidId() {
        return nextValidId.getAndIncrement();
    }

    public List<String> getAccountsList() {
        ensureReady();
        return Collections.unmodifiableList(managedAccounts);
    }

    public int reqAccountSummary(String group, String tags) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqAccountSummary(reqId, group, tags);
        return reqId;
    }

    public int reqPnL(String account, String modelCode) {
        ensureReady(); // This blocks until nextValidId is received
        final var reqId = nextValidId();
        eClientSocket.reqPnL(reqId, account, modelCode);
        return reqId;
    }

    public void reqPositions() {
        ensureReady();
        eClientSocket.reqPositions();
    }

}
