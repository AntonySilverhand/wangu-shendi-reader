package org.wanshu.reader;

/** 顶层响应对象：不得改为嵌套类。 */
final class SourceResult {
  final int status;
  final byte[] body;
  final String finalUrl;
  final String errorMessage;
  /** 上游 Content-Type（封面图用） */
  final String contentType;

  SourceResult(int status, byte[] body, String finalUrl, String errorMessage) {
    this(status, body, finalUrl, errorMessage, null);
  }

  SourceResult(int status, byte[] body, String finalUrl, String errorMessage, String contentType) {
    this.status = status;
    this.body = body;
    this.finalUrl = finalUrl;
    this.errorMessage = errorMessage;
    this.contentType = contentType;
  }
}
