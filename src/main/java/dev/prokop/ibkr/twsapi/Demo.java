package dev.prokop.ibkr.twsapi;

public class Demo {

    public static void main(String[] args) throws InterruptedException {
        TwsApi twsApi = new TwsApi();
        twsApi.connect("127.0.0.1");
        Thread.sleep(2000);


        twsApi.on(TwsEvent.Pnl.class, System.out::println);
        twsApi.on(TwsEvent.Position.class, System.out::println);

        twsApi.reqPnL(twsApi.getAccountsList().get(0), "");
        twsApi.reqPositions();
        Thread.sleep(10000);
        twsApi.disconnect();
    }

}
