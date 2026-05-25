package dev.prokop.ibkr.twsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class TwsApiDemo {

    public static void main(String[] args) throws InterruptedException {
        TwsApi twsApi = new TwsApi();

        // Callback registration
        twsApi.on(TwsEvent.AccountSummary.class, event -> System.out.println("AccountSummary: " + event));
        twsApi.on(TwsEvent.AccountSummaryEnd.class, event -> System.out.println("AccountSummaryEnd: " + event));
        twsApi.on(TwsEvent.ConnectionClosed.class, event -> System.out.println("ConnectionClosed: " + event));
        twsApi.on(TwsEvent.Error.class, event -> System.out.println("Error: " + event));
        twsApi.on(TwsEvent.Pnl.class, event -> System.out.println("Pnl: " + event));
        //twsApi.on(TwsEvent.Position.class, event -> System.out.println("Position: {}", event));
        twsApi.on(TwsEvent.PositionEnd.class, event -> System.out.println("PositionEnd: " + event));

        long startConnecting = System.currentTimeMillis();

        // you really do not need that completable future, as req* methods have built-in protection.
        CompletableFuture<Void> connected = twsApi.connect("127.0.0.1");
        System.out.println("connected state: " + connected.state());
        System.out.println("Time since epoch: " + (System.currentTimeMillis() - startConnecting));

        //connected.join(); - not needed, as req* methods have built-in protection.

        twsApi.reqPositions();
        System.out.println("connected state: " + connected.state());
        twsApi.reqPnL(twsApi.getAccountsList().getFirst(), "");
        // note that thread silently paused till API was ready to serve requests.
        System.out.println("Time since epoch: " + (System.currentTimeMillis() - startConnecting));

        twsApi.reqAccountSummary("All", "NetLiquidation, TotalCashValue, ExcessLiquidity");

        Thread.sleep(10000);
        twsApi.disconnect();
        Thread.sleep(2000);
    }

}
