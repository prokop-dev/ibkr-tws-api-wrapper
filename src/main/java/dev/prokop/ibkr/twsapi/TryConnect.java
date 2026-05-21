package dev.prokop.ibkr.twsapi;

import com.ib.client.*;
import com.ib.controller.ApiController;

public class TryConnect {

    public static void main(String[] args) {
        EWrapper eWrapper = new DefaultEWrapper() {
            @Override
            public void connectAck() {
                System.out.println("connectAck");
            }
        };

        EReaderSignal eReaderSignal = new EJavaSignal();

        EClientSocket eClientSocket = new EClientSocket(eWrapper, eReaderSignal);
        eClientSocket.eConnect("127.0.0.1", 4001, 1);

        EReader eReader = new EReader(eClientSocket, eReaderSignal);
        eReader.start();

        new Thread(() -> {
            while (eClientSocket.isConnected()) {
                eReaderSignal.waitForSignal();
                try {
                    eReader.processMsgs();
                } catch (Exception e) {
                    // ...
                }
            }
        }).start();
    }

}
