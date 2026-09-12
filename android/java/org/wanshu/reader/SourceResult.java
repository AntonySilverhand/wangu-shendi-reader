package org.wanshu.reader;

/** 顶层响应对象：不得改为嵌套类。 */
final class SourceResult {
  final int status;
  final byte[] body;
  final String finalUrl;
  final String errorMessage;

  SourceResult(int status, byte[] body, String finalUrl, String errorMessage) {
    this.status = status;
    this.body = body;
    this.finalUrl = finalUrl;
    this.errorMessage = errorMessage;
  }
}
