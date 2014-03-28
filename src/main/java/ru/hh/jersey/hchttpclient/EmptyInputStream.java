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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class EmptyInputStream extends InputStream {
  @Override
  public int read(byte[] bytes) throws IOException {
    return -1;
  }

  @Override
  public int read(byte[] bytes, int i, int i1) throws IOException {
    return -1;
  }

  @Override
  public long skip(long l) throws IOException {
    throw new EOFException();
  }

  @Override
  public int available() throws IOException {
    return 0;
  }

  @Override
  public void close() throws IOException { }

  @Override
  public void mark(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset() throws IOException { }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public int read() throws IOException {
    return -1;
  }
}
