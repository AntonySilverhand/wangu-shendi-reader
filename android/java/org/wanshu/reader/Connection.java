package org.wanshu.reader;

import java.net.Socket;

final class Connection implements Runnable {
  private final LocalServer server;
  private final Socket client;

  Connection(LocalServer server, Socket client) {
    this.server = server;
    this.client = client;
  }

  @Override
  public void run() {
    server.handle(client);
  }
}
