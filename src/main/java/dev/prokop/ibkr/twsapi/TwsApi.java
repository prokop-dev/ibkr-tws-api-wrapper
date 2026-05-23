package dev.prokop.ibkr.twsapi;

import com.ib.client.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class TwsApi {

    private CompletableFuture<Void> readyFuture;
    private final EClientSocket eClientSocket;

    public TwsApi() {
        System.out.println("Creating EClient / EClientSocket...");
        eClientSocket = new EClientSocket(eWrapper, eReaderSignal);
    }

    public CompletableFuture<Void> connect(String host) {
        readyFuture = new CompletableFuture<>();
        System.out.println("Connecting to " + host);
        eClientSocket.eConnect(host, 4001, 1);
        if (eClientSocket.isConnected()) {
            start();
        } else {
            readyFuture.completeExceptionally(
                    new RuntimeException("Failed to connect to " + host + ":4001. Is IB Gateway running and API enabled?")
            );
        }
        return readyFuture;
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
    private final Map<Class<? extends TwsEvent>, List<Consumer<? extends TwsEvent>>> listeners = new ConcurrentHashMap<>();

    private final EWrapper eWrapper = new DefaultEWrapper() {

        @Override
        public void connectAck() {
            System.out.println("Connection acknowledged by IB Gateway!");
        }

        @Override
        public void connectionClosed() {
            System.out.println("Connection to IB Gateway closed.");
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
            System.out.println("managedAccounts:"+accountsList);
            managedAccounts.addAll(List.of(accountsList.split(",")));
        }

        @Override
        public void nextValidId(int orderId) {
            System.out.println("nextValidId:"+orderId);
            nextValidId.set(orderId);
            readyFuture.complete(null);
        }

        @Override
        public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
            dispatch(new TwsEvent.Pnl(reqId, dailyPnL, unrealizedPnL, realizedPnL));
        }

        @Override
        public void position(String account, Contract contract, Decimal pos, double avgCost) {
            dispatch(new TwsEvent.Position(account, contract, pos, avgCost));
        }

    };

    public <T extends TwsEvent & TwsEvent.Concrete> void on(Class<T> type, Consumer<T> consumer) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(consumer);
        System.out.println("registered: " + consumer);
    }

    private <T extends TwsEvent> void dispatch(T event) {
        List<Consumer<? extends TwsEvent>> consumers = listeners.get(event.getClass());
        if (consumers != null) {
            consumers.forEach(c -> ((Consumer<T>) c).accept(event));
        }
    }

    private final List<String> managedAccounts = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextValidId = new AtomicInteger(Integer.MIN_VALUE);

    public int nextValidId() {
        return nextValidId.getAndIncrement();
    }

    public List<String> getAccountsList() {
        return Collections.unmodifiableList(managedAccounts);
    }

    public void reqPnL(String account, String modelCode) {
        eClientSocket.reqPnL(nextValidId(), account, modelCode);
    }

    public void reqPositions() {
        eClientSocket.reqPositions();
    }

}
