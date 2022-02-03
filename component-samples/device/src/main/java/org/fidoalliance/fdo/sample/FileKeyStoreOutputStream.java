package org.fidoalliance.fdo.sample;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.fidoalliance.fdo.protocol.dispatch.KeyStoreOutputStreamFunction;

public class FileKeyStoreOutputStream implements KeyStoreOutputStreamFunction {

  @Override
  public OutputStream apply(String s) throws IOException {
    return new FileOutputStream(s);
  }
}