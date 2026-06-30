# Project Rules — Modern Java TWS API Wrapper

## Project Overview

This is a lightweight, high-performance Java 21+ wrapper around the Interactive Brokers (IBKR) Trader Workstation (TWS) API.
It transforms the legacy callback-based `EWrapper` / `EClientSocket` API into a modern, event-driven, type-safe interface using sealed interfaces and records.
The wrapper is designed to serve as the core engine for a Model Context Protocol (MCP) Server.

The wrapper code lives under `dev.prokop.ibkr.twsapi` (4 files today, growing).
The vendored IBKR TWS API source lives under `com.ib.*` (~296 files).

---

## Read-Only Boundary — CRITICAL

> **NEVER modify any file under `src/main/java/com/ib/`.**

These are vendored upstream IBKR TWS API sources. They are read-only reference material.
You may (and should) **read** them to understand callback signatures, request methods, and domain types.
Key files to consult:
- `com/ib/client/EWrapper.java` — all ~91 classic callback method signatures (ignore `*ProtoBuf` variants)
- `com/ib/client/EClientSocket.java` — all outbound request methods (`req*`, `place*`, `cancel*`, etc.)
- `com/ib/client/Contract.java`, `Order.java`, `Execution.java`, etc. — domain types used in signatures

---

## Architecture — The Three Layers

### 1. `TwsEvent` — The Domain Events (sealed interface + records)
- Location: `TwsEvent.java`
- Every IBKR callback becomes a **Java record** inside this sealed interface.
- Each record **must** implement both `TwsEvent` and `TwsEvent.Concrete`.
- The `Concrete` marker interface is a compile-time gate: it ensures only concrete event records can be used in `TwsApi.on()` listener registration (preventing misuse with the sealed parent).
- Record field names **must match** the corresponding `EWrapper` method parameter names exactly.
- Record field types **must match** the `EWrapper` parameter types exactly (use `com.ib.client.*` types like `Contract`, `Decimal`, `Order`, etc.).
- Naming: the record name matches the `EWrapper` method name in PascalCase (e.g., `tickPrice(...)` → `TickPrice`).

**Pattern:**
```java
record TickPrice(int tickerId, int field, double price, TickAttrib attrib) implements TwsEvent, Concrete { }
```

### 2. `TwsApi` — The Engine (connection, dispatch, request methods)
- Location: `TwsApi.java`
- Contains the `EClientSocket`, `EReader` lifecycle, and the `EWrapper` anonymous implementation.
- The `EWrapper` implementation lives as a **private field** (`eWrapper`) inside `TwsApi`.

#### Adding a new callback (inbound data):
1. Add the record to `TwsEvent.java` (see above).
2. Override the corresponding method in the `eWrapper` field inside `TwsApi.java`.
3. Call `dispatch(new TwsEvent.XxxYyy(...))` with all parameters forwarded.

**Pattern (inside the `eWrapper` field):**
```java
@Override
public void tickPrice(int tickerId, int field, double price, TickAttrib attrib) {
    dispatch(new TwsEvent.TickPrice(tickerId, field, price, attrib));
}
```

#### Adding a new request method (outbound request):
1. Add a public method to `TwsApi`.
2. **Always** call `ensureReady()` first — this blocks until the API handshake is complete.
3. If the request requires a request ID, obtain one via `nextValidId()`.
4. Delegate to the corresponding `eClientSocket.req*()` / `eClientSocket.place*()` method.
5. Return the `reqId` (as `int`) if one was allocated, or `void` for global subscriptions.

**Pattern:**
```java
public int reqHistoricalData(...) {
    ensureReady();
    final var reqId = nextValidId();
    eClientSocket.reqHistoricalData(reqId, ...);
    return reqId;
}
```

### 3. `TwsSyncBridge` — The Stateful View (CompletableFuture bridge)
- Location: `TwsSyncBridge.java`
- Provides a higher-level, stateful API on top of `TwsApi`.
- Listens to `TwsEvent` records via `twsApi.on(...)` and aggregates state into `ConcurrentHashMap` caches.
- Exposes `CompletableFuture`-based getters that wait for initial sync completion.
- Uses the "initial sync" pattern: a `CompletableFuture<Void>` that completes when the first `*End` event arrives.

---

## Java Style & Conventions

- **Java 21+**: Use records, sealed interfaces, pattern matching. No Lombok.
- **Logging**: Use SLF4J (`LoggerFactory.getLogger()`). No `System.out.println` except in demo code.
- **Concurrency**: Use `ConcurrentHashMap`, `CopyOnWriteArrayList`, `AtomicInteger`, `AtomicReference`, `CompletableFuture`. No `synchronized` blocks unless truly necessary.
- **Naming**: Follow standard Java conventions. Event records use PascalCase matching the `EWrapper` method name.
- **Imports**: Prefer explicit imports. `com.ib.client.*` wildcard is acceptable given the large number of types.
- **No new dependencies** without explicit approval. The project intentionally has a minimal footprint (SLF4J, Logback, Protobuf).

---

## Workflow for Expanding API Coverage

When asked to add coverage for a new TWS API feature (e.g., historical data, contract details, orders):

1. **Read the `EWrapper` method signatures** in `com/ib/client/EWrapper.java` for the callbacks involved. Ignore `*ProtoBuf` variants — only wrap the classic (non-protobuf) methods.
2. **Read the `EClientSocket` methods** for the corresponding outbound requests.
3. **Add event records** to `TwsEvent.java`.
4. **Add `EWrapper` overrides** to the `eWrapper` field in `TwsApi.java`, dispatching the new event records.
5. **Add `req*` methods** to `TwsApi.java` for the outbound requests.
6. **Optionally extend `TwsSyncBridge`** if the feature benefits from stateful caching (e.g., positions, account values).
7. **Update `TwsApiDemo.java`** with example usage if helpful.

---

## What NOT to Do

- Do NOT create new classes for individual API features. Everything goes into the existing 4 files.
- Do NOT wrap the `*ProtoBuf` callback variants — only classic `EWrapper` callbacks.
- Do NOT introduce frameworks, DI containers, or annotation processors.
- Do NOT change the `pom.xml` without explicit approval.
- Do NOT refactor the internal structure (e.g., extracting the `eWrapper` into a separate class) without discussion.

---

## IBKR API Reference

When you need to look up TWS API details:
- The **source of truth** is the vendored code under `src/main/java/com/ib/`.
- The official IBKR API documentation is at: https://ibkrcampus.com/ibkr-api-page/twsapi-doc/
- The EWrapper interface has **~91 classic callback methods** (as of TWS API 10.x). The current wrapper covers 7. The goal is full coverage.
