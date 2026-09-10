package org.wanshu.reader;

import java.io.IOException;
import java.net.Socket;

/** 顶层类：避免 JDK 21 编译的内部类属性触发旧版 d8 崩溃。 */
final class AcceptLoop implements Runnable {
  private final LocalServer server;

  AcceptLoop(LocalServer server) {
    this.server = server;
  }

  @Override
  public void run() {
    while (server.isRunning()) {
      try {
        Socket client = server.accept();
        new Thread(new Connection(server, client), "reader-conn").start();
      } catch (IOException e) {
        if (server.isRunning()) e.printStackTrace();
      }
    }
  }
}
