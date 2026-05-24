package dev.prokop.ibkr.twsapi;

import java.util.concurrent.CompletableFuture;

public class TwsApiDemo {

    public static void main(String[] args) throws InterruptedException {
        TwsApi twsApi = new TwsApi();

        // Callback registration
        twsApi.on(TwsEvent.AccountSummary.class, System.out::println);
        twsApi.on(TwsEvent.AccountSummaryEnd.class, System.out::println);
        twsApi.on(TwsEvent.ConnectionClosed.class, System.out::println);
        twsApi.on(TwsEvent.Error.class, System.out::println);
        twsApi.on(TwsEvent.Pnl.class, System.out::println);
        //twsApi.on(TwsEvent.Position.class, System.out::println);
        twsApi.on(TwsEvent.PositionEnd.class, System.out::println);

        long startConnecting = System.currentTimeMillis();

        // you really do not need that completable future, as req* methods have built-in protection.
        CompletableFuture<Void> connected = twsApi.connect("127.0.0.1");
        System.out.println("connected state:" + connected.state());
        System.out.println("Time since epoch: " + (System.currentTimeMillis()-startConnecting));

        //connected.join(); - not needed, as req* methods have built-in protection.

        twsApi.reqPositions();
        System.out.println("connected state:" + connected.state());
        twsApi.reqPnL(twsApi.getAccountsList().getFirst(), "");
        // note that thread silently paused till API was ready to serve requests.
        System.out.println("Time since epoch: " + (System.currentTimeMillis()-startConnecting));

        twsApi.reqAccountSummary("All", "NetLiquidation, TotalCashValue, ExcessLiquidity");

        Thread.sleep(10000);
        twsApi.disconnect();
        Thread.sleep(2000);
    }

}
