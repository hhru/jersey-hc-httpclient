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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProviderFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;

public class ApacheHttpClient extends Client {
  public ApacheHttpClient() {
    this(createDefaultClientHander(), new DefaultClientConfig(), null);
  }

  public ApacheHttpClient(ApacheHttpClientHandler root) {
    this(root, new DefaultClientConfig(), null);
  }

  public ApacheHttpClient(ApacheHttpClientHandler root, ClientConfig config) {
    this(root, config, null);
  }

  public ApacheHttpClient(ApacheHttpClientHandler root, ClientConfig config,
                          IoCComponentProviderFactory provider) {
    super(root, config, provider);

    HttpClient client = root.getHttpClient();

    HttpConnectionParamBean connParams = new HttpConnectionParamBean(client.getParams());

    Integer connectTimeout = (Integer) config.getProperty(ClientConfig.PROPERTY_CONNECT_TIMEOUT);
    if (connectTimeout != null) {
      connParams.setConnectionTimeout(connectTimeout);
    }

    Integer readTimeout = (Integer) config.getProperty(ClientConfig.PROPERTY_READ_TIMEOUT);
    if (readTimeout != null) {
      connParams.setSoTimeout(readTimeout);
    }
  }

  public static ApacheHttpClient create() {
    return new ApacheHttpClient(createDefaultClientHander());
  }

  public static ApacheHttpClient create(ClientConfig cc) {
    return new ApacheHttpClient(createDefaultClientHander(), cc);
  }

  public static ApacheHttpClient create(ClientConfig cc, IoCComponentProviderFactory provider) {
    return new ApacheHttpClient(createDefaultClientHander(), cc, provider);
  }

  private static ApacheHttpClientHandler createDefaultClientHander() {
    HttpParams params = new BasicHttpParams();

    final HttpClient client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, new SchemeRegistry()),
            params);

    return new ApacheHttpClientHandler(client);
  }
}