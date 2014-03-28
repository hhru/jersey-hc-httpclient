/*
 * Copyright 2009-2011 ООО "Хэдхантер"
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.hh.jersey.hchttpclient;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.TerminatingClientHandler;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.core.header.InBoundHeaders;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientParamBean;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;

public final class ApacheHttpClientHandler extends TerminatingClientHandler {
  private final HttpClient client;

  public ApacheHttpClientHandler(HttpClient client) {
    this.client = client;
  }

  public HttpClient getHttpClient() {
    return client;
  }

  @Override
  public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
    Map<String, Object> props = cr.getProperties();

    HttpRequestBase method = getHttpMethod(cr);

    HttpParams methodParams = method.getParams();
    ClientParamBean clientParams = new ClientParamBean(methodParams);
    HttpConnectionParamBean connParams = new HttpConnectionParamBean(methodParams);

    clientParams.setHandleRedirects(cr.getPropertyAsFeature(ClientConfig.PROPERTY_FOLLOW_REDIRECTS));

    Integer readTimeout = (Integer) props.get(ClientConfig.PROPERTY_READ_TIMEOUT);
    if (readTimeout != null) {
      connParams.setSoTimeout(readTimeout);
    }

    writeOutBoundHeaders(cr.getHeaders(), method);

    if (method instanceof HttpEntityEnclosingRequestBase) {
      HttpEntityEnclosingRequestBase entMethod = (HttpEntityEnclosingRequestBase) method;

      if (cr.getEntity() != null) {
        Integer chunkedEncodingSize = (Integer) props.get(ClientConfig.PROPERTY_CHUNKED_ENCODING_SIZE);
        RequestEntityWriter requestEntityWriter = getRequestEntityWriter(cr);
        if (chunkedEncodingSize != null) {
          // There doesn't seems to be a way to set the chunk size.
          EntityTemplate entity = new EntityTemplate(new RequestEntityProducer(requestEntityWriter));
          entity.setContentType(requestEntityWriter.getMediaType().toString());
          entity.setChunked(true);
          entMethod.setEntity(entity);
        } else {
          entMethod.setEntity(new LazyHttpEntity(requestEntityWriter));
        }
      }
    }

    try {
      return new HttpClientResponse(client.execute(method));
    } catch (IOException | RuntimeException e) {
      throw new ClientHandlerException(e);
    }
  }

  private HttpRequestBase getHttpMethod(ClientRequest cr) {
    final String strMethod = cr.getMethod();

    switch (strMethod) {
      case "GET":
        return new HttpGet(cr.getURI());
      case "POST":
        return new HttpPost(cr.getURI());
      case "PUT":
        return new HttpPut(cr.getURI());
      case "DELETE":
        return new HttpDelete(cr.getURI());
      case "HEAD":
        return new HttpHead(cr.getURI());
      case "OPTIONS":
        return new HttpOptions(cr.getURI());
      default:
        throw new ClientHandlerException("Method " + strMethod + " is not supported.");
    }
  }

  private void writeOutBoundHeaders(MultivaluedMap<String, Object> metadata, HttpRequestBase method) {
    for (Map.Entry<String, List<Object>> e : metadata.entrySet()) {
      List<Object> vs = e.getValue();
      if (vs.size() == 1) {
        method.setHeader(e.getKey(), ClientRequest.getHeaderValue(vs.get(0)));
      } else {
        StringBuilder b = new StringBuilder();
        for (Object v : e.getValue()) {
          if (b.length() > 0) {
            b.append(',');
          }
          b.append(ClientRequest.getHeaderValue(v));
        }
        method.setHeader(e.getKey(), b.toString());
      }
    }
  }

  private InBoundHeaders getInBoundHeaders(HttpResponse method) {
    InBoundHeaders headers = new InBoundHeaders();
    Header[] respHeaders = method.getAllHeaders();
    for (Header header : respHeaders) {
      List<String> list = headers.get(header.getName());
      if (list == null) {
        list = new ArrayList<>();
      }
      list.add(header.getValue());
      headers.put(header.getName(), list);
    }
    return headers;
  }

  private static InputStream entity(HttpResponse resp) throws IOException {
    if (resp.getEntity() == null) {
      return new EmptyInputStream();
    }
    return resp.getEntity().getContent();
  }

  private final class HttpClientResponse extends ClientResponse {
    HttpClientResponse(HttpResponse resp) throws IOException {
      super(resp.getStatusLine().getStatusCode(), getInBoundHeaders(resp), entity(resp), getMessageBodyWorkers());
    }

    @Override
    public boolean hasEntity() {
      return !(getEntityInputStream() instanceof EmptyInputStream);
    }

    @Override
    public String toString() {
      return "response status of " + this.getStatus();
    }
  }

  private static class RequestEntityProducer implements ContentProducer {
    private final RequestEntityWriter re;

    private RequestEntityProducer(RequestEntityWriter re) {
      this.re = re;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
      re.writeRequestEntity(out);
      out.close();
    }
  }

  private static class LazyHttpEntity implements HttpEntity {
    private byte[] bytes;
    private final RequestEntityWriter re;

    private LazyHttpEntity(RequestEntityWriter re) {
      this.re = re;
    }

    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public boolean isChunked() {
      return false;
    }

    @Override
    public long getContentLength() {
      try {
        getIt();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return bytes.length;
    }

    @Override
    public Header getContentType() {
      return new BasicHeader("Content-Type", re.getMediaType().toString());
    }

    @Override
    public Header getContentEncoding() {
      return null;
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
      getIt();
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
      getIt();
      outstream.write(bytes);
    }

    private void getIt() throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      re.writeRequestEntity(bytes);
      this.bytes = bytes.toByteArray();
    }

    @Override
    public boolean isStreaming() {
      return false;
    }

    @Override
    public void consumeContent() throws IOException { }
  }
}
