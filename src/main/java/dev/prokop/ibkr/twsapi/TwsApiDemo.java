package dev.prokop.ibkr.twsapi;

import java.util.concurrent.CompletableFuture;

public class TwsApiDemo {

    public static void main(String[] args) throws InterruptedException {
        TwsApi twsApi = new TwsApi();
        CompletableFuture<Void> connected = twsApi.connect("127.0.0.1");
        connected.join();

        twsApi.on(TwsEvent.Error.class, System.out::println);
        twsApi.on(TwsEvent.Pnl.class, System.out::println);
        twsApi.on(TwsEvent.Position.class, System.out::println);

        twsApi.reqPnL(twsApi.getAccountsList().get(0), "");
        twsApi.reqPositions();
        Thread.sleep(10000);
        twsApi.disconnect();
    }

}
