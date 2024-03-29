/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2008 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.core.internal.interpreter;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.JDOMParseException;
import org.jdom.input.SAXBuilder;
import org.rssowl.core.internal.Activator;
import org.rssowl.core.interpreter.IXMLParser;
import org.rssowl.core.interpreter.ParserException;
import org.xml.sax.InputSource;
import org.xml.sax.ext.EntityResolver2;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Default Implementation of the ISAXParser Interface using the JDKs default XML
 * Parser. The implementation is smart enough to try the platforms default
 * encoding in case a first attempt of parsing the XML fails.
 *
 * @author bpasero
 */
public class DefaultSaxParserImpl implements IXMLParser {

  /* Feature in Xerces to allow Java Encoding Names */
  private static final String ALLOW_JAVA_ENCODINGS = "http://apache.org/xml/features/allow-java-encodings"; //$NON-NLS-1$

  /* DTD to use for all XMLs */
  private static final String DEFAULT_DTD = "entities.dtd";

  /* A Stream that overrides close() to do nothing */
  private static class KeepAliveInputStream extends BufferedInputStream {
    KeepAliveInputStream(InputStream in) {
      super(in);
    }

    @Override
    public void close() {
    // Disable, because Xerces is automatically closing the Stream
    }

    void reallyClose() throws IOException {
      super.close();
    }
  }

  /*
   * @see org.rssowl.core.interpreter.ISAXParser#init()
   */
  public void init() {
  /* Nothing to do here */
  }

  /*
   * @see org.rssowl.core.interpreter.ISAXParser#parse(java.io.InputStream)
   */
  public Document parse(InputStream inS) throws ParserException {
    Document document = null;
    Exception ex = null;
    SAXBuilder builder = getBuilder();

    /* Set a Mark to support a 2d Run */
    KeepAliveInputStream keepAliveIns = new KeepAliveInputStream(inS);
    keepAliveIns.mark(0);

    /* First Run - Try with Documents own Encoding */
    try {
      document = builder.build(keepAliveIns);
    } catch (JDOMException e) {
      ex = e;
    } catch (IOException e) {
      ex = e;
    }

    /* Second Run - Try with Platform Default Encoding */
    if (ex instanceof JDOMParseException) {

      /* Try to reset the Stream to 0 */
      boolean reset = false;
      try {
        keepAliveIns.reset();
        reset = true;
      } catch (IOException e) {
        /* Reset Failed, do not override previous exception */
      }

      /* In case reset-operation was successfull */
      if (reset) {
        try {
          document = builder.build(new InputStreamReader(keepAliveIns));
        } catch (JDOMException e) {
          ex = e;
        } catch (IOException e) {
          ex = e;
        }
      }
    }

    /* Close Stream */
    try {
      keepAliveIns.reallyClose();
    } catch (IOException e) {
      ex = e;
    }

    /* In case of an exception */
    if (ex != null && document == null && Activator.getDefault() != null)
      throw new ParserException(Activator.getDefault().createErrorStatus(ex.getMessage(), ex));

    /* Return Document */
    return document;
  }

  private SAXBuilder getBuilder() {
    SAXBuilder builder = new SAXBuilder();

    /* Support Java Encoding Names */
    builder.setFeature(ALLOW_JAVA_ENCODINGS, true);

    /* Custom Entitiy Resolution */
    builder.setEntityResolver(new EntityResolver2() {

      /*
       * @see org.xml.sax.ext.EntityResolver2#getExternalSubset(java.lang.String,
       * java.lang.String)
       */
      public InputSource getExternalSubset(String name, String baseURI) {
        return new InputSource(getClass().getResourceAsStream(DEFAULT_DTD));
      }

      /*
       * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String,
       * java.lang.String)
       */
      public InputSource resolveEntity(String publicId, String systemId) {
        return resolveEntity(null, publicId, null, systemId);
      }

      /*
       * @see org.xml.sax.ext.EntityResolver2#resolveEntity(java.lang.String,
       * java.lang.String, java.lang.String, java.lang.String)
       */
      public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) {
        return new InputSource(getClass().getResourceAsStream(DEFAULT_DTD));
      }
    });

    return builder;
  }
}