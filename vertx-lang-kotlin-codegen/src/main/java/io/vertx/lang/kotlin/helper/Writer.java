package io.vertx.lang.kotlin.helper;


import java.io.PrintWriter;
import java.io.StringWriter;

public class Writer {

  private static final int INDENT_SIZE = 2;

  private StringWriter stringWriter = new StringWriter();
  private PrintWriter writer = new PrintWriter(stringWriter);
  private int indent;
  private boolean first;

  public PrintWriter printWriter() {
    return writer;
  }

  public Writer println() {
    return print('\n');
  }

  public Writer println(CharSequence csq) {
    print(csq);
    println();
    return this;
  }

  public Writer print(CharSequence csq) {
    print(csq, 0, csq.length());
    return this;
  }

  public Writer print(CharSequence csq, int start, int end) {
    while (start < end) {
      print(csq.charAt(start++));
    }
    return this;
  }

  public Writer print(char c) {
    if (c == '\n') {
      first = true;
    } else if (first) {
      first = false;
      for (int i = 0; i < indent; i++) {
        writer.append(' ');
      }
    }
    writer.print(c);
    return this;
  }

  public Writer indent() {
    indent += INDENT_SIZE;
    return this;
  }

  public Writer unindent() {
    if (indent < INDENT_SIZE) {
      throw new IllegalStateException();
    }
    indent -= INDENT_SIZE;
    return this;
  }

  @Override
  public String toString() {
    return stringWriter.toString();
  }
}
