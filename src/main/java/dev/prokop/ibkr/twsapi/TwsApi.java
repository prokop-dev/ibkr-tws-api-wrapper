package dev.prokop.ibkr.twsapi;

import com.ib.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        public void accountDownloadEnd(String accountName) {
            dispatch(new TwsEvent.AccountDownloadEnd(accountName));
        }

        @Override
        public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value, String currency) {
            dispatch(new TwsEvent.AccountUpdateMulti(reqId, account, modelCode, key, value, currency));
        }

        @Override
        public void accountUpdateMultiEnd(int reqId) {
            dispatch(new TwsEvent.AccountUpdateMultiEnd(reqId));
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
        public void currentTime(long time) {
            dispatch(new TwsEvent.CurrentTime(time));
        }

        @Override
        public void currentTimeInMillis(long timeInMillis) {
            dispatch(new TwsEvent.CurrentTimeInMillis(timeInMillis));
        }

        @Override
        public void deltaNeutralValidation(int reqId, DeltaNeutralContract deltaNeutralContract) {
            dispatch(new TwsEvent.DeltaNeutralValidation(reqId, deltaNeutralContract));
        }

        @Override
        public void displayGroupList(int reqId, String groups) {
            dispatch(new TwsEvent.DisplayGroupList(reqId, groups));
        }

        @Override
        public void displayGroupUpdated(int reqId, String contractInfo) {
            dispatch(new TwsEvent.DisplayGroupUpdated(reqId, contractInfo));
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
        public void familyCodes(FamilyCode[] familyCodes) {
            dispatch(new TwsEvent.FamilyCodes(familyCodes));
        }

        @Override
        public void fundamentalData(int reqId, String data) {
            dispatch(new TwsEvent.FundamentalData(reqId, data));
        }

        @Override
        public void headTimestamp(int reqId, String headTimestamp) {
            dispatch(new TwsEvent.HeadTimestamp(reqId, headTimestamp));
        }

        @Override
        public void histogramData(int reqId, List<HistogramEntry> items) {
            dispatch(new TwsEvent.HistogramData(reqId, items));
        }

        @Override
        public void historicalData(int reqId, Bar bar) {
            dispatch(new TwsEvent.HistoricalData(reqId, bar));
        }

        @Override
        public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
            dispatch(new TwsEvent.HistoricalDataEnd(reqId, startDateStr, endDateStr));
        }

        @Override
        public void historicalDataUpdate(int reqId, Bar bar) {
            dispatch(new TwsEvent.HistoricalDataUpdate(reqId, bar));
        }

        @Override
        public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {
            dispatch(new TwsEvent.HistoricalNews(requestId, time, providerCode, articleId, headline));
        }

        @Override
        public void historicalNewsEnd(int requestId, boolean hasMore) {
            dispatch(new TwsEvent.HistoricalNewsEnd(requestId, hasMore));
        }

        @Override
        public void historicalSchedule(int reqId, String startDateTime, String endDateTime, String timeZone, List<HistoricalSession> sessions) {
            dispatch(new TwsEvent.HistoricalSchedule(reqId, startDateTime, endDateTime, timeZone, sessions));
        }

        @Override
        public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {
            dispatch(new TwsEvent.HistoricalTicks(reqId, ticks, done));
        }

        @Override
        public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {
            dispatch(new TwsEvent.HistoricalTicksBidAsk(reqId, ticks, done));
        }

        @Override
        public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {
            dispatch(new TwsEvent.HistoricalTicksLast(reqId, ticks, done));
        }

        @Override
        public void managedAccounts(String accountsList) {
            log.info("managedAccounts:" + accountsList);
            managedAccounts.addAll(List.of(accountsList.split(",")));
        }

        @Override
        public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
            dispatch(new TwsEvent.MktDepthExchanges(depthMktDataDescriptions));
        }

        @Override
        public void marketDataType(int reqId, int marketDataType) {
            dispatch(new TwsEvent.MarketDataType(reqId, marketDataType));
        }

        @Override
        public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
            dispatch(new TwsEvent.MarketRule(marketRuleId, priceIncrements));
        }

        @Override
        public void newsArticle(int requestId, int articleType, String articleText) {
            dispatch(new TwsEvent.NewsArticle(requestId, articleType, articleText));
        }

        @Override
        public void newsProviders(NewsProvider[] newsProviders) {
            dispatch(new TwsEvent.NewsProviders(newsProviders));
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
        public void pnlSingle(int reqId, Decimal pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {
            dispatch(new TwsEvent.PnlSingle(reqId, pos, dailyPnL, unrealizedPnL, realizedPnL, value));
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
        public void positionMulti(int reqId, String account, String modelCode, Contract contract, Decimal pos, double avgCost) {
            dispatch(new TwsEvent.PositionMulti(reqId, account, modelCode, contract, pos, avgCost));
        }

        @Override
        public void positionMultiEnd(int reqId) {
            dispatch(new TwsEvent.PositionMultiEnd(reqId));
        }

        @Override
        public void realtimeBar(int reqId, long time, double open, double high, double low, double close, Decimal volume, Decimal wap, int count) {
            dispatch(new TwsEvent.RealtimeBar(reqId, time, open, high, low, close, volume, wap, count));
        }

        @Override
        public void receiveFA(int faDataType, String xml) {
            dispatch(new TwsEvent.ReceiveFA(faDataType, xml));
        }

        @Override
        public void replaceFAEnd(int reqId, String text) {
            dispatch(new TwsEvent.ReplaceFAEnd(reqId, text));
        }

        @Override
        public void rerouteMktDataReq(int reqId, int conId, String exchange) {
            dispatch(new TwsEvent.RerouteMktDataReq(reqId, conId, exchange));
        }

        @Override
        public void rerouteMktDepthReq(int reqId, int conId, String exchange) {
            dispatch(new TwsEvent.RerouteMktDepthReq(reqId, conId, exchange));
        }

        @Override
        public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
            dispatch(new TwsEvent.ScannerData(reqId, rank, contractDetails, distance, benchmark, projection, legsStr));
        }

        @Override
        public void scannerDataEnd(int reqId) {
            dispatch(new TwsEvent.ScannerDataEnd(reqId));
        }

        @Override
        public void scannerParameters(String xml) {
            dispatch(new TwsEvent.ScannerParameters(xml));
        }

        @Override
        public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId, String tradingClass, String multiplier, Set<String> expirations, Set<Double> strikes) {
            dispatch(new TwsEvent.SecurityDefinitionOptionalParameter(reqId, exchange, underlyingConId, tradingClass, multiplier, expirations, strikes));
        }

        @Override
        public void securityDefinitionOptionalParameterEnd(int reqId) {
            dispatch(new TwsEvent.SecurityDefinitionOptionalParameterEnd(reqId));
        }

        @Override
        public void smartComponents(int reqId, Map<Integer, Map.Entry<String, Character>> theMap) {
            dispatch(new TwsEvent.SmartComponents(reqId, theMap));
        }

        @Override
        public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {
            dispatch(new TwsEvent.SoftDollarTiers(reqId, tiers));
        }

        @Override
        public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
            dispatch(new TwsEvent.SymbolSamples(reqId, contractDescriptions));
        }

        @Override
        public void tickByTickAllLast(int reqId, int tickType, long time, double price, Decimal size, TickAttribLast tickAttribLast, String exchange, String specialConditions) {
            dispatch(new TwsEvent.TickByTickAllLast(reqId, tickType, time, price, size, tickAttribLast, exchange, specialConditions));
        }

        @Override
        public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, Decimal bidSize, Decimal askSize, TickAttribBidAsk tickAttribBidAsk) {
            dispatch(new TwsEvent.TickByTickBidAsk(reqId, time, bidPrice, askPrice, bidSize, askSize, tickAttribBidAsk));
        }

        @Override
        public void tickByTickMidPoint(int reqId, long time, double midPoint) {
            dispatch(new TwsEvent.TickByTickMidPoint(reqId, time, midPoint));
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
        public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline, String extraData) {
            dispatch(new TwsEvent.TickNews(tickerId, timeStamp, providerCode, articleId, headline, extraData));
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

        @Override
        public void updateAccountTime(String timeStamp) {
            dispatch(new TwsEvent.UpdateAccountTime(timeStamp));
        }

        @Override
        public void updateAccountValue(String key, String value, String currency, String accountName) {
            dispatch(new TwsEvent.UpdateAccountValue(key, value, currency, accountName));
        }

        @Override
        public void updateMktDepth(int tickerId, int position, int operation, int side, double price, Decimal size) {
            dispatch(new TwsEvent.UpdateMktDepth(tickerId, position, operation, side, price, size));
        }

        @Override
        public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, Decimal size, boolean isSmartDepth) {
            dispatch(new TwsEvent.UpdateMktDepthL2(tickerId, position, marketMaker, operation, side, price, size, isSmartDepth));
        }

        @Override
        public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
            dispatch(new TwsEvent.UpdateNewsBulletin(msgId, msgType, message, origExchange));
        }

        @Override
        public void updatePortfolio(Contract contract, Decimal position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
            dispatch(new TwsEvent.UpdatePortfolio(contract, position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName));
        }

        @Override
        public void userInfo(int reqId, String whiteBrandingId) {
            dispatch(new TwsEvent.UserInfo(reqId, whiteBrandingId));
        }

        @Override
        public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {
            dispatch(new TwsEvent.VerifyAndAuthCompleted(isSuccessful, errorText));
        }

        @Override
        public void verifyAndAuthMessageAPI(String apiData, String xyzChallenge) {
            dispatch(new TwsEvent.VerifyAndAuthMessageAPI(apiData, xyzChallenge));
        }

        @Override
        public void verifyCompleted(boolean isSuccessful, String errorText) {
            dispatch(new TwsEvent.VerifyCompleted(isSuccessful, errorText));
        }

        @Override
        public void verifyMessageAPI(String apiData) {
            dispatch(new TwsEvent.VerifyMessageAPI(apiData));
        }

        @Override
        public void wshEventData(int reqId, String dataJson) {
            dispatch(new TwsEvent.WshEventData(reqId, dataJson));
        }

        @Override
        public void wshMetaData(int reqId, String dataJson) {
            dispatch(new TwsEvent.WshMetaData(reqId, dataJson));
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

    // --- Historical Data (Wave 4) ---

    public int reqHistoricalData(Contract contract, String endDateTime, String durationStr, String barSizeSetting, String whatToShow, int useRTH, int formatDate, boolean keepUpToDate, List<TagValue> chartOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqHistoricalData(reqId, contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate, keepUpToDate, chartOptions);
        return reqId;
    }

    public int reqHistoricalData(Contract contract, String endDateTime, String durationStr, String barSizeSetting, String whatToShow, int useRTH, boolean keepUpToDate) {
        return reqHistoricalData(contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, 1, keepUpToDate, null);
    }

    public void cancelHistoricalData(int tickerId) {
        ensureReady();
        eClientSocket.cancelHistoricalData(tickerId);
    }

    public int reqHeadTimestamp(Contract contract, String whatToShow, int useRTH, int formatDate) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqHeadTimestamp(reqId, contract, whatToShow, useRTH, formatDate);
        return reqId;
    }

    public int reqHistogramData(Contract contract, boolean useRTH, String timePeriod) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqHistogramData(reqId, contract, useRTH, timePeriod);
        return reqId;
    }

    public void cancelHistogramData(int tickerId) {
        ensureReady();
        eClientSocket.cancelHistogramData(tickerId);
    }

    // --- PnL Single & Account Updates (Wave 5) ---

    public void reqAccountUpdates(boolean subscribe, String acctCode) {
        ensureReady();
        eClientSocket.reqAccountUpdates(subscribe, acctCode);
    }

    public int reqPnLSingle(String account, String modelCode, int conId) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqPnLSingle(reqId, account, modelCode, conId);
        return reqId;
    }

    public void cancelPnLSingle(int reqId) {
        ensureReady();
        eClientSocket.cancelPnLSingle(reqId);
    }

    public int reqPositionsMulti(String account, String modelCode) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqPositionsMulti(reqId, account, modelCode);
        return reqId;
    }

    public void cancelPositionsMulti(int reqId) {
        ensureReady();
        eClientSocket.cancelPositionsMulti(reqId);
    }

    public int reqAccountUpdatesMulti(String account, String modelCode, boolean ledgerAndNLV) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqAccountUpdatesMulti(reqId, account, modelCode, ledgerAndNLV);
        return reqId;
    }

    public void cancelAccountUpdatesMulti(int reqId) {
        ensureReady();
        eClientSocket.cancelAccountUpdatesMulti(reqId);
    }

    // --- Tick-by-Tick & Real-Time Bars (Wave 6) ---

    public int reqRealTimeBars(Contract contract, int barSize, String whatToShow, boolean useRTH, List<TagValue> realTimeBarsOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqRealTimeBars(reqId, contract, barSize, whatToShow, useRTH, realTimeBarsOptions);
        return reqId;
    }

    public int reqRealTimeBars(Contract contract, int barSize, String whatToShow, boolean useRTH) {
        return reqRealTimeBars(contract, barSize, whatToShow, useRTH, null);
    }

    public void cancelRealTimeBars(int tickerId) {
        ensureReady();
        eClientSocket.cancelRealTimeBars(tickerId);
    }

    public int reqHistoricalTicks(Contract contract, String startDateTime, String endDateTime, int numberOfTicks, String whatToShow, int useRth, boolean ignoreSize, List<TagValue> miscOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqHistoricalTicks(reqId, contract, startDateTime, endDateTime, numberOfTicks, whatToShow, useRth, ignoreSize, miscOptions);
        return reqId;
    }

    public int reqTickByTickData(Contract contract, String tickType, int numberOfTicks, boolean ignoreSize) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqTickByTickData(reqId, contract, tickType, numberOfTicks, ignoreSize);
        return reqId;
    }

    public void cancelTickByTickData(int reqId) {
        ensureReady();
        eClientSocket.cancelTickByTickData(reqId);
    }

    // --- Market Depth (Wave 7) ---

    public int reqMktDepth(Contract contract, int numRows, boolean isSmartDepth, List<TagValue> mktDepthOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqMktDepth(reqId, contract, numRows, isSmartDepth, mktDepthOptions);
        return reqId;
    }

    public int reqMktDepth(Contract contract, int numRows, boolean isSmartDepth) {
        return reqMktDepth(contract, numRows, isSmartDepth, null);
    }

    public void cancelMktDepth(int tickerId, boolean isSmartDepth) {
        ensureReady();
        eClientSocket.cancelMktDepth(tickerId, isSmartDepth);
    }

    public void reqMktDepthExchanges() {
        ensureReady();
        eClientSocket.reqMktDepthExchanges();
    }

    // --- Scanner, News, Options, Misc (Wave 8) ---

    public void reqCurrentTime() {
        ensureReady();
        eClientSocket.reqCurrentTime();
    }

    public int reqFundamentalData(Contract contract, String reportType, List<TagValue> fundamentalDataOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqFundamentalData(reqId, contract, reportType, fundamentalDataOptions);
        return reqId;
    }

    public void cancelFundamentalData(int reqId) {
        ensureReady();
        eClientSocket.cancelFundamentalData(reqId);
    }

    public int reqSecDefOptParams(String underlyingSymbol, String futFopExchange, String underlyingSecType, int underlyingConId) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqSecDefOptParams(reqId, underlyingSymbol, futFopExchange, underlyingSecType, underlyingConId);
        return reqId;
    }

    public int reqSoftDollarTiers() {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqSoftDollarTiers(reqId);
        return reqId;
    }

    public void reqFamilyCodes() {
        ensureReady();
        eClientSocket.reqFamilyCodes();
    }

    public void reqNewsProviders() {
        ensureReady();
        eClientSocket.reqNewsProviders();
    }

    public int reqNewsArticle(String providerCode, String articleId, List<TagValue> newsArticleOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqNewsArticle(reqId, providerCode, articleId, newsArticleOptions);
        return reqId;
    }

    public int reqHistoricalNews(int conId, String providerCodes, String startDateTime, String endDateTime, int totalResults, List<TagValue> historicalNewsOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqHistoricalNews(reqId, conId, providerCodes, startDateTime, endDateTime, totalResults, historicalNewsOptions);
        return reqId;
    }

    public int reqSmartComponents(String bboExchange) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqSmartComponents(reqId, bboExchange);
        return reqId;
    }

    public void reqMarketRule(int marketRuleId) {
        ensureReady();
        eClientSocket.reqMarketRule(marketRuleId);
    }

    public int reqScannerSubscription(ScannerSubscription subscription, List<TagValue> scannerSubscriptionOptions, List<TagValue> scannerSubscriptionFilterOptions) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqScannerSubscription(reqId, subscription, scannerSubscriptionOptions, scannerSubscriptionFilterOptions);
        return reqId;
    }

    public void cancelScannerSubscription(int tickerId) {
        ensureReady();
        eClientSocket.cancelScannerSubscription(tickerId);
    }

    public void reqScannerParameters() {
        ensureReady();
        eClientSocket.reqScannerParameters();
    }

    public int reqWshMetaData() {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqWshMetaData(reqId);
        return reqId;
    }

    public int reqWshEventData(WshEventData wshEventData) {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqWshEventData(reqId, wshEventData);
        return reqId;
    }

    public void cancelWshMetaData(int reqId) {
        ensureReady();
        eClientSocket.cancelWshMetaData(reqId);
    }

    public void cancelWshEventData(int reqId) {
        ensureReady();
        eClientSocket.cancelWshEventData(reqId);
    }

    public int reqUserInfo() {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.reqUserInfo(reqId);
        return reqId;
    }

    public void reqCurrentTimeInMillis() {
        ensureReady();
        eClientSocket.reqCurrentTimeInMillis();
    }

    public void requestFA(int faDataType) {
        ensureReady();
        eClientSocket.requestFA(faDataType);
    }

    public void replaceFA(int reqId, int faDataType, String xml) {
        ensureReady();
        eClientSocket.replaceFA(reqId, faDataType, xml);
    }

    public void verifyRequest(String apiName, String apiVersion) {
        ensureReady();
        eClientSocket.verifyRequest(apiName, apiVersion);
    }

    public void verifyMessage(String apiData) {
        ensureReady();
        eClientSocket.verifyMessage(apiData);
    }

    public void verifyAndAuthRequest(String apiName, String apiVersion, String opaqueIsvKey) {
        ensureReady();
        eClientSocket.verifyAndAuthRequest(apiName, apiVersion, opaqueIsvKey);
    }

    public void verifyAndAuthMessage(String apiData, String xyzResponse) {
        ensureReady();
        eClientSocket.verifyAndAuthMessage(apiData, xyzResponse);
    }

    public int queryDisplayGroups() {
        ensureReady();
        final var reqId = nextValidId();
        eClientSocket.queryDisplayGroups(reqId);
        return reqId;
    }

    public void subscribeToGroupEvents(int reqId, int groupId) {
        ensureReady();
        eClientSocket.subscribeToGroupEvents(reqId, groupId);
    }

    public void updateDisplayGroup(int reqId, String contractInfo) {
        ensureReady();
        eClientSocket.updateDisplayGroup(reqId, contractInfo);
    }

    public void unsubscribeFromGroupEvents(int reqId) {
        ensureReady();
        eClientSocket.unsubscribeFromGroupEvents(reqId);
    }

}
