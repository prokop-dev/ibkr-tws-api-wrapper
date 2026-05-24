# Modern Java TWS API Wrapper

A lightweight, high-performance Java 21+ wrapper for the Interactive Brokers (IBKR) Trader Workstation (TWS) API.

## Purpose

This library is designed to bridge the gap between the legacy IBKR Java API and modern software engineering patterns. Its primary mission is to serve as the core engine for a **Model Context Protocol (MCP) Server**, enabling Large Language Models (LLMs) to interact seamlessly and safely with trading activities.

By providing a clean, event-driven, and asynchronous interface, this wrapper makes it trivial to expose brokerage functions (positions, PnL, order management) as tools to AI assistants.

## Key Features

- **Java 21 Native:** Leverages `records`, `sealed interfaces`, and pattern matching for a robust and type-safe domain model.
- **Asynchronous by Design:** Uses `CompletableFuture` to bridge the gap between IBKR's asynchronous callback model and modern request/response patterns.
- **Event-Driven:** Implements a clean event dispatching system using strongly-typed event records.
- **MCP Optimized:** Specifically architected to support the high-signal, low-noise requirements of MCP server implementations.
- **Minimal Dependencies:** Keeps the footprint small, focusing on performance and reliability.

## Architecture

The project is structured to separate concerns:

1.  **`TwsApi` (The Engine):** Manages the low-level socket connection, `EReader` lifecycle, and raw `EWrapper` callbacks.
2.  **`TwsEvent` (The Domain):** A rich set of Java records representing all incoming data from TWS.
3.  **`TwsSyncBridge` (Planned):** A high-level bridge that manages Request IDs and provides `CompletableFuture` methods for synchronous-like interaction.

## Distribution

This library will be released to **Maven Central**, providing a stable and versioned dependency for trading applications and MCP server implementations.

## Native Image Build (GraalVM)

This library and its demo can be compiled into a standalone native executable using GraalVM. This is highly recommended for **MCP Server** deployments to ensure near-instant startup times and zero JVM dependency on the host machine.

### Prerequisites

1.  **GraalVM JDK 21+**: We recommend [GraalVM for JDK 21](https://www.graalvm.org/downloads/).
    *   On macOS: `brew install --cask graalvm-jdk@21`
2.  **Native Image Tool**: Ensure `native-image` is in your `PATH`.
    *   Verify with: `native-image --version`

### Building the Executable

Run the following command to compile the project into a native binary:

```bash
mvn clean package -Pnative
```

The resulting binary will be located at:
`target/tws-api-demo`

### Why Native?
- **Fast Startup:** < 50ms (vs ~2s for JVM), critical for responsive MCP tools.
- **Lower Footprint:** Reduced memory usage, ideal for side-car processes.
- **Portability:** A single binary file that doesn't require a Java installation on the host.

## Usage

```java
TwsApi api = new TwsApi();
api.connect("127.0.0.1").thenAccept(client -> {
    System.out.println("Ready to trade!");
    client.reqPositions();
});
```

---
*Note: This is a work in progress. Ensure you have IB Gateway or TWS running with API access enabled.*
