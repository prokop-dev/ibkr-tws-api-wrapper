package dev.prokop.ibkr.twsapi;

import com.ib.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TwsApi {

    private static final Logger log = LoggerFactory.getLogger(TwsApi.class);

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
        log.info("Connecting to " + host);
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
        log.info("Connected successfully! Starting reader thread...");

        // Create the background reader thread to process incoming socket data
        final EReader eReader = new EReader(eClientSocket, eReaderSignal);
        eReader.setName("TWS-EReader");
        eReader.start();

        // Thread to process the signal queue and feed data to EWrapper
        Thread processMsgThread = new Thread(() -> {
            while (eClientSocket.isConnected()) {
                eReaderSignal.waitForSignal();
                try {
                    eReader.processMsgs();
                } catch (Exception e) {
                    log.error("Exception handling message: " + e.getMessage());
                }
            }
            log.info("Thread exit.");
        });
        processMsgThread.setName("TwsEvent-Processor");
        processMsgThread.start();
    }


    public void disconnect() {
        if (eClientSocket.isConnected()) {
            eClientSocket.eDisconnect();
        }
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
        public void bondContractDetails(int reqId, ContractDetails contractDetails) {
            dispatch(new TwsEvent.BondContractDetails(reqId, contractDetails));
        }

        @Override
        public void commissionAndFeesReport(CommissionAndFeesReport commissionAndFeesReport) {
            dispatch(new TwsEvent.CommissionAndFeesReport(commissionAndFeesReport));
        }

        @Override
        public void completedOrder(Contract contract, Order order, OrderState orderState) {
            dispatch(new TwsEvent.CompletedOrder(contract, order, orderState));
        }

        @Override
        public void completedOrdersEnd() {
            dispatch(new TwsEvent.CompletedOrdersEnd());
        }

        @Override
        public void connectAck() {
            log.info("Connection acknowledged by IB Gateway!");
        }

        @Override
        public void connectionClosed() {
            log.info("Connection to IB Gateway closed.");
            // Reset the gate to a new incomplete future.
            // Any thread that calls a req* method from this moment on will
            // BLOCK at ensureReady() until the reconnection logic finishes.
            readyGate.set(new CompletableFuture<>());
            dispatch(new TwsEvent.ConnectionClosed());
        }

        @Override
        public void error(Exception e) {
            log.info("API Exception: " + e.getMessage());
        }

        @Override
        public void error(String str) {
            log.info("API Error Message: " + str);
        }

        @Override
        public void contractDetails(int reqId, ContractDetails contractDetails) {
            dispatch(new TwsEvent.ContractDetails(reqId, contractDetails));
        }

        @Override
        public void contractDetailsEnd(int reqId) {
            dispatch(new TwsEvent.ContractDetailsEnd(reqId));
        }

        @Override
        public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
            dispatch(new TwsEvent.Error(id, errorTime, errorCode, errorMsg, advancedOrderRejectJson));
        }

        @Override
        public void execDetails(int reqId, Contract contract, Execution execution) {
            dispatch(new TwsEvent.ExecDetails(reqId, contract, execution));
        }

        @Override
        public void execDetailsEnd(int reqId) {
            dispatch(new TwsEvent.ExecDetailsEnd(reqId));
        }

        @Override
        public void managedAccounts(String accountsList) {
            log.info("managedAccounts:" + accountsList);
            managedAccounts.addAll(List.of(accountsList.split(",")));
        }

        @Override
        public void marketDataType(int reqId, int marketDataType) {
            dispatch(new TwsEvent.MarketDataType(reqId, marketDataType));
        }

        @Override
        public void nextValidId(int orderId) {
            log.info("nextValidId:" + orderId);
            nextValidId.set(orderId);

            // Open the gate! All blocked req* calls now proceed simultaneously.
            readyGate.get().complete(null);
        }

        @Override
        public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
            dispatch(new TwsEvent.OpenOrder(orderId, contract, order, orderState));
        }

        @Override
        public void openOrderEnd() {
            dispatch(new TwsEvent.OpenOrderEnd());
        }

        @Override
        public void orderBound(long permId, int clientId, int orderId) {
            dispatch(new TwsEvent.OrderBound(permId, clientId, orderId));
        }

        @Override
        public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
            dispatch(new TwsEvent.OrderStatus(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld, mktCapPrice));
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

        @Override
        public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
            dispatch(new TwsEvent.SymbolSamples(reqId, contractDescriptions));
        }

        @Override
        public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureLastTradeDate, double dividendImpact, double dividendsToLastTradeDate) {
            dispatch(new TwsEvent.TickEFP(tickerId, tickType, basisPoints, formattedBasisPoints, impliedFuture, holdDays, futureLastTradeDate, dividendImpact, dividendsToLastTradeDate));
        }

        @Override
        public void tickGeneric(int tickerId, int tickType, double value) {
            dispatch(new TwsEvent.TickGeneric(tickerId, tickType, value));
        }

        @Override
        public void tickOptionComputation(int tickerId, int field, int tickAttrib, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
            dispatch(new TwsEvent.TickOptionComputation(tickerId, field, tickAttrib, impliedVol, delta, optPrice, pvDividend, gamma, vega, theta, undPrice));
        }

        @Override
        public void tickPrice(int tickerId, int field, double price, TickAttrib attrib) {
            dispatch(new TwsEvent.TickPrice(tickerId, field, price, attrib));
        }

        @Override
        public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
            dispatch(new TwsEvent.TickReqParams(tickerId, minTick, bboExchange, snapshotPermissions));
        }

        @Override
        public void tickSize(int tickerId, int field, Decimal size) {
            dispatch(new TwsEvent.TickSize(tickerId, field, size));
        }

        @Override
        public void tickSnapshotEnd(int reqId) {
            dispatch(new TwsEvent.TickSnapshotEnd(reqId));
        }

        @Override
        public void tickString(int tickerId, int tickType, String value) {
            dispatch(new TwsEvent.TickString(tickerId, tickType, value));
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

    // --- Orders & Executions (Wave 1) ---

    public int placeOrder(Contract contract, Order order) {
        ensureReady();
        final var orderId = nextValidId();
        eClientSocket.placeOrder(orderId, contract, order);
        return orderId;
    }

    public void cancelOrder(int orderId, OrderCancel orderCancel) {
        ensureReady();
        eClientSocket.cancelOrder(orderId, orderCancel);
    }

    public void cancelOrder(int orderId) {
        cancelOrder(orderId, new OrderCancel());
    }

    public void reqOpenOrders() {
        ensureReady();
        eClientSocket.reqOpenOrders();
    }

    public void reqAllOpenOrders() {
        ensureReady();
        eClientSocket.reqAllOpenOrders();
    }

    public void reqAutoOpenOrders(boolean autoBind) {
        ensureReady();
        eClientSocket.reqAutoOpenOrders(autoBind);
    }

    public int reqExecutions(ExecutionFilter filter) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqExecutions(reqId, filter);
        return reqId;
    }

    public void reqCompletedOrders(boolean apiOnly) {
        ensureReady();
        eClientSocket.reqCompletedOrders(apiOnly);
    }

    // --- Contract Details (Wave 2) ---

    public int reqContractDetails(Contract contract) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqContractDetails(reqId, contract);
        return reqId;
    }

    public int reqMatchingSymbols(String pattern) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqMatchingSymbols(reqId, pattern);
        return reqId;
    }

    // --- Market Data (Wave 3) ---

    public int reqMktData(Contract contract, String genericTickList, boolean snapshot, boolean regulatorySnapshot, List<TagValue> mktDataOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqMktData(reqId, contract, genericTickList, snapshot, regulatorySnapshot, mktDataOptions);
        return reqId;
    }

    public int reqMktData(Contract contract, String genericTickList, boolean snapshot) {
        return reqMktData(contract, genericTickList, snapshot, false, null);
    }

    public void cancelMktData(int tickerId) {
        ensureReady();
        eClientSocket.cancelMktData(tickerId);
    }

    public void reqMarketDataType(int marketDataType) {
        ensureReady();
        eClientSocket.reqMarketDataType(marketDataType);
    }

}
