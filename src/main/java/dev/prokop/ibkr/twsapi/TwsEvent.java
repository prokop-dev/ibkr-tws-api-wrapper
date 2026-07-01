package dev.prokop.ibkr.twsapi;

import com.ib.client.*;

public sealed interface TwsEvent {

    sealed interface Concrete {
        // enforces TwsEvent implementations only as method arguments at the compile time
    }

    record AccountSummary(int reqId, String account, String tag, String value, String currency) implements TwsEvent, Concrete { }
    record AccountSummaryEnd(int reqId) implements TwsEvent, Concrete { }
    record BondContractDetails(int reqId, com.ib.client.ContractDetails contractDetails) implements TwsEvent, Concrete { }
    record CommissionAndFeesReport(com.ib.client.CommissionAndFeesReport commissionAndFeesReport) implements TwsEvent, Concrete { }
    record CompletedOrder(Contract contract, Order order, OrderState orderState) implements TwsEvent, Concrete { }
    record CompletedOrdersEnd() implements TwsEvent, Concrete { }
    record ConnectionClosed() implements TwsEvent, Concrete { }
    record ContractDetails(int reqId, com.ib.client.ContractDetails contractDetails) implements TwsEvent, Concrete { }
    record ContractDetailsEnd(int reqId) implements TwsEvent, Concrete { }
    record Error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) implements TwsEvent, Concrete { }
    record ExecDetails(int reqId, Contract contract, Execution execution) implements TwsEvent, Concrete { }
    record ExecDetailsEnd(int reqId) implements TwsEvent, Concrete { }
    record OpenOrder(int orderId, Contract contract, Order order, OrderState orderState) implements TwsEvent, Concrete { }
    record OpenOrderEnd() implements TwsEvent, Concrete { }
    record OrderBound(long permId, int clientId, int orderId) implements TwsEvent, Concrete { }
    record OrderStatus(int orderId, String status, Decimal filled, Decimal remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) implements TwsEvent, Concrete { }
    record Pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) implements TwsEvent, Concrete { }
    record Position(String account, Contract contract, Decimal pos, double avgCost) implements TwsEvent, Concrete { }
    record PositionEnd() implements TwsEvent, Concrete {}
    record SymbolSamples(int reqId, ContractDescription[] contractDescriptions) implements TwsEvent, Concrete { }

}

