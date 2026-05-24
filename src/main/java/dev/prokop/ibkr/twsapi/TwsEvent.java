package dev.prokop.ibkr.twsapi;

import com.ib.client.Contract;
import com.ib.client.Decimal;

public sealed interface TwsEvent {

    sealed interface Concrete {
        // enforces TwsEvent implementations only as method arguments at the compile time
    }

    record ConnectionClosed() implements TwsEvent, Concrete { }
    record Error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) implements TwsEvent, Concrete { }
    record Pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) implements TwsEvent, Concrete { }
    record Position(String account, Contract contract, Decimal pos, double avgCost) implements TwsEvent, Concrete { }
    record PositionEnd() implements TwsEvent, Concrete {}

}
