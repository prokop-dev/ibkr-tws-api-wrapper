package dev.prokop.ibkr.twsapi;

import com.ib.client.Contract;
import com.ib.client.Decimal;

public sealed interface TwsEvent {

    sealed interface Concrete {
        // enforces TwsEvent implementations only at compile time
    }

    record Pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) implements TwsEvent, Concrete {
    }

    record Position(String account, Contract contract, Decimal pos, double avgCost) implements TwsEvent, Concrete {
    }

}
