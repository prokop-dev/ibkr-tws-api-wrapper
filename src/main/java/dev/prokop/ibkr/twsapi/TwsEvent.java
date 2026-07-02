package dev.prokop.ibkr.twsapi;

import com.ib.client.*;

import java.util.List;

public sealed interface TwsEvent {

    sealed interface Concrete {
        // enforces TwsEvent implementations only as method arguments at the compile time
    }

    record AccountSummary(int reqId, String account, String tag, String value, String currency) implements TwsEvent, Concrete { }
    record AccountSummaryEnd(int reqId) implements TwsEvent, Concrete { }
    record AccountDownloadEnd(String accountName) implements TwsEvent, Concrete { }
    record AccountUpdateMulti(int reqId, String account, String modelCode, String key, String value, String currency) implements TwsEvent, Concrete { }
    record AccountUpdateMultiEnd(int reqId) implements TwsEvent, Concrete { }
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
    record HeadTimestamp(int reqId, String headTimestamp) implements TwsEvent, Concrete { }
    record HistogramData(int reqId, List<HistogramEntry> items) implements TwsEvent, Concrete { }
    record HistoricalData(int reqId, Bar bar) implements TwsEvent, Concrete { }
    record HistoricalDataEnd(int reqId, String startDateStr, String endDateStr) implements TwsEvent, Concrete { }
    record HistoricalDataUpdate(int reqId, Bar bar) implements TwsEvent, Concrete { }
    record HistoricalSchedule(int reqId, String startDateTime, String endDateTime, String timeZone, List<HistoricalSession> sessions) implements TwsEvent, Concrete { }
    record HistoricalTicks(int reqId, List<HistoricalTick> ticks, boolean done) implements TwsEvent, Concrete { }
    record HistoricalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) implements TwsEvent, Concrete { }
    record HistoricalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) implements TwsEvent, Concrete { }
    record MarketDataType(int reqId, int marketDataType) implements TwsEvent, Concrete { }
    record OpenOrder(int orderId, Contract contract, Order order, OrderState orderState) implements TwsEvent, Concrete { }
    record OpenOrderEnd() implements TwsEvent, Concrete { }
    record OrderBound(long permId, int clientId, int orderId) implements TwsEvent, Concrete { }
    record OrderStatus(int orderId, String status, Decimal filled, Decimal remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) implements TwsEvent, Concrete { }
    record Pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) implements TwsEvent, Concrete { }
    record PnlSingle(int reqId, Decimal pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) implements TwsEvent, Concrete { }
    record Position(String account, Contract contract, Decimal pos, double avgCost) implements TwsEvent, Concrete { }
    record PositionEnd() implements TwsEvent, Concrete {}
    record PositionMulti(int reqId, String account, String modelCode, Contract contract, Decimal pos, double avgCost) implements TwsEvent, Concrete { }
    record PositionMultiEnd(int reqId) implements TwsEvent, Concrete { }
    record RealtimeBar(int reqId, long time, double open, double high, double low, double close, Decimal volume, Decimal wap, int count) implements TwsEvent, Concrete { }
    record SymbolSamples(int reqId, ContractDescription[] contractDescriptions) implements TwsEvent, Concrete { }
    record TickByTickAllLast(int reqId, int tickType, long time, double price, Decimal size, TickAttribLast tickAttribLast, String exchange, String specialConditions) implements TwsEvent, Concrete { }
    record TickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, Decimal bidSize, Decimal askSize, TickAttribBidAsk tickAttribBidAsk) implements TwsEvent, Concrete { }
    record TickByTickMidPoint(int reqId, long time, double midPoint) implements TwsEvent, Concrete { }
    record TickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureLastTradeDate, double dividendImpact, double dividendsToLastTradeDate) implements TwsEvent, Concrete { }
    record TickGeneric(int tickerId, int tickType, double value) implements TwsEvent, Concrete { }
    record TickOptionComputation(int tickerId, int field, int tickAttrib, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) implements TwsEvent, Concrete { }
    record TickPrice(int tickerId, int field, double price, TickAttrib attrib) implements TwsEvent, Concrete { }
    record TickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) implements TwsEvent, Concrete { }
    record TickSize(int tickerId, int field, Decimal size) implements TwsEvent, Concrete { }
    record TickSnapshotEnd(int reqId) implements TwsEvent, Concrete { }
    record TickString(int tickerId, int tickType, String value) implements TwsEvent, Concrete { }
    record UpdateAccountTime(String timeStamp) implements TwsEvent, Concrete { }
    record UpdateAccountValue(String key, String value, String currency, String accountName) implements TwsEvent, Concrete { }
    record UpdatePortfolio(Contract contract, Decimal position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) implements TwsEvent, Concrete { }

}

