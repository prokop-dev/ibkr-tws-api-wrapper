package dev.prokop.ibkr.twsapi.demo;

import dev.prokop.ibkr.twsapi.TwsApi;

import java.util.concurrent.CompletableFuture;

public class ConnectionDemo {

    public static void main(String[] args) {
        long startConnecting = System.currentTimeMillis();
        final TwsApi twsApi = new TwsApi();
        System.out.println("Time since start (new TwsApi()): " + (System.currentTimeMillis() - startConnecting) + " ms.");

        CompletableFuture<Void> connected = twsApi.connect("127.0.0.1");

        System.out.println("Time since start (twsApi.connect): " + (System.currentTimeMillis() - startConnecting) + " ms.");
        System.out.println("connected state: " + connected.state());
        connected.join();
        System.out.println("connected state: " + connected.state());
        System.out.println("Time to operational (connected.join()): " + (System.currentTimeMillis() - startConnecting) + " ms.");

        twsApi.disconnect();
        System.out.println("Time to disconnected (twsApi.disconnect()): " + (System.currentTimeMillis() - startConnecting) + " ms.");
    }

}
