package dev.prokop.ibkr.twsapi;

import java.util.concurrent.CompletableFuture;

public class TwsApiDemo {

    public static void main(String[] args) throws InterruptedException {
        TwsApi twsApi = new TwsApi();
        twsApi.on(TwsEvent.Error.class, System.out::println);
        twsApi.on(TwsEvent.Pnl.class, System.out::println);
        twsApi.on(TwsEvent.Position.class, System.out::println);

        long x = System.currentTimeMillis();
        CompletableFuture<Void> connected = twsApi.connect("127.0.0.1");
        System.out.println(System.currentTimeMillis()-x);
        twsApi.reqPnL(twsApi.getAccountsList().getFirst(), "");
        twsApi.reqPositions();

        //connected.join(); - no longer needed

        System.out.println(System.currentTimeMillis()-x);

        Thread.sleep(10000);
        twsApi.disconnect();
    }

}
