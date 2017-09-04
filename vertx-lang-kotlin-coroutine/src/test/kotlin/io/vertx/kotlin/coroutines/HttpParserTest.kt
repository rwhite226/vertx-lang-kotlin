package io.vertx.kotlin.coroutines

import io.vertx.core.MultiMap
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.parsetools.RecordParser
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner::class)
class HttpParserTest {

  private lateinit var vertx: Vertx

  @Before
  fun before() {
    vertx = Vertx.vertx()
  }

  @After
  fun after(testContext: TestContext) {
    vertx.close(testContext.asyncAssertSuccess())
  }

  class Request(val line : String, val headers : Map<String, String>,  val body : Buffer? = null) {
    val s = line.split(" ")
    val method = s[0];
    val uri = s[1]
    val version = s[2]
  }

  fun startServer(testContext: TestContext, handler: (Request) -> Unit) {
    val async = testContext.async()
    val server = vertx.createNetServer().connectHandler { so ->
      val recordParser = RecordParser.newDelimited("\r\n", so)
      val channel = toChannel(vertx, recordParser)
      vertx.runCoroutine {
        val line = channel.receive().toString()
        val headers = HashMap<String, String>()
        while (true) {
          val header = channel.receive().toString()
          if (header.isEmpty()) {
            break
          }
          val pos = header.indexOf(':')
          headers[header.substring(0, pos).toLowerCase()] = header.substring(pos + 1).trim()
        }
        val transferEncoding = headers.get("transfer-encoding")
        val contentLength = headers.get("content-length")
        val request : Request
        if (transferEncoding == "chunked") {
          val body = Buffer.buffer()
          while (true) {
            val len = channel.receive().toString().toInt(16)
            if (len == 0) {
              break
            }
            recordParser.fixedSizeMode(len + 2)
            val chunk = channel.receive()
            body.appendBuffer(chunk, 0, chunk.length() - 2)
            recordParser.delimitedMode("\r\n")
          }
          request = Request(line, headers, body)
        } else if (contentLength != null) {
          recordParser.fixedSizeMode(contentLength.toInt())
          val body = channel.receive()
          request = Request(line, headers, body)
        } else {
          request = Request(line, headers)
        }
        handler(request)
        so.write("HTTP/1.1 200 OK\r\n\r\n")
      }
    }
    server.listen(8080, testContext.asyncAssertSuccess { async.complete() })
    async.awaitSuccess(20000)
  }

  @Test
  fun testGet(testContext: TestContext) {
    val async = testContext.async()
    startServer(testContext) { req ->
      testContext.assertEquals("GET", req.method)
      testContext.assertEquals("/foo", req.uri)
      testContext.assertEquals("HTTP/1.1", req.version)
      testContext.assertNull(req.body)
      async.complete()
    }
    val client = vertx.createHttpClient()
    client.get(8080, "localhost", "/foo") { resp ->

    }.exceptionHandler { err ->
      testContext.fail(err)
    }.end()
  }

  @Test
  fun testPut(testContext: TestContext) {
    val async = testContext.async()
    startServer(testContext) { req ->
      testContext.assertEquals("PUT", req.method)
      testContext.assertEquals("/foo", req.uri)
      testContext.assertEquals("HTTP/1.1", req.version)
      testContext.assertEquals("abc123", req.body.toString())
      async.complete()
    }
    val client = vertx.createHttpClient()
    client.put(8080, "localhost", "/foo") { resp ->

    }.exceptionHandler { err ->
      testContext.fail(err)
    }.end("abc123")
  }

  @Test
  fun testPutChunked(testContext: TestContext) {
    val async = testContext.async()
    startServer(testContext) { req ->
      testContext.assertEquals("PUT", req.method)
      testContext.assertEquals("/foo", req.uri)
      testContext.assertEquals("HTTP/1.1", req.version)
      testContext.assertEquals("abc123", req.body.toString())
      async.complete()
    }
    val client = vertx.createHttpClient()
    val req = client.put(8080, "localhost", "/foo") { resp ->

    }.exceptionHandler { err ->
      testContext.fail(err)
    }.setChunked(true)
    req.write("abc")
    vertx.setTimer(1) {
      req.write("123")
      req.end()
    }
  }
}
