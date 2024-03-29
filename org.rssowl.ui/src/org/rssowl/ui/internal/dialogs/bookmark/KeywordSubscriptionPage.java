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

package org.rssowl.ui.internal.dialogs.bookmark;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.persist.pref.DefaultPreferences;
import org.rssowl.core.util.StringUtils;
import org.rssowl.core.util.URIUtils;
import org.rssowl.ui.internal.OwlUI;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bpasero
 */
public class KeywordSubscriptionPage extends WizardPage {
  private static final String URL_INPUT_TOKEN = "[:]";
  private static final String KEYWORD_FEED_EXTENSION_POINT = "org.rssowl.ui.KeywordFeed";

  /* Base class for Search Engines */
  static class SearchEngine {
    private final String fId;
    private final String fName;
    private final String fIconPath;
    private final String fUrl;

    SearchEngine(String id, String name, String iconPath, String url) {
      fId = id;
      fName = name;
      fIconPath = iconPath;
      fUrl = url;
    }

    String getId() {
      return fId;
    }

    String getName() {
      return fName;
    }

    String getLabel(String keywords) {
      return fName + " on '" + keywords + "'";
    }

    String getIconPath() {
      return fIconPath;
    }

    String toUrl(String keywords) {
      keywords = URIUtils.urlEncode(keywords);
      return StringUtils.replaceAll(fUrl, URL_INPUT_TOKEN, keywords);
    }
  }

  private static final List<SearchEngine> fgSearchEngines = loadSearchEngines();
  private SearchEngine fSelectedEngine;

  /**
   * @param pageName
   */
  protected KeywordSubscriptionPage(String pageName) {
    super(pageName, pageName, OwlUI.getImageDescriptor("icons/wizban/bkmrk_wiz.gif"));
    setMessage("Create a new Bookmark to read News from a Feed.");
  }

  private static List<SearchEngine> loadSearchEngines() {
    List<SearchEngine> engines = new ArrayList<SearchEngine>(5);

    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement elements[] = reg.getConfigurationElementsFor(KEYWORD_FEED_EXTENSION_POINT);

    /* For each contributed property keyword feed */
    for (IConfigurationElement element : elements) {
      String id = element.getAttribute("id");
      String name = element.getAttribute("name");
      String iconPath = element.getAttribute("icon"); //TODO Support loading icon from different plugin
      String url = element.getAttribute("url");

      engines.add(new SearchEngine(id, name, iconPath, url));
    }

    return engines;
  }

  /**
   * @return Returns the currently selected SearchEngine.
   */
  public SearchEngine getSelectedEngine() {
    return fSelectedEngine;
  }

  /*
   * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
   */
  /*
   * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
   */
  public void createControl(Composite parent) {
    Composite container = new Composite(parent, SWT.NONE);
    container.setLayout(new GridLayout(1, false));

    Label infoLabel = new Label(container, SWT.NONE);
    infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    infoLabel.setText("Please select from one of the following search engines: ");

    Composite contentMargin = new Composite(container, SWT.NONE);
    contentMargin.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    contentMargin.setLayout(new GridLayout(3, false));
    ((GridLayout) contentMargin.getLayout()).marginTop = 10;
    ((GridLayout) contentMargin.getLayout()).verticalSpacing = 20;

    String lastKeywordFeedId = Owl.getPreferenceService().getGlobalScope().getString(DefaultPreferences.LAST_KEYWORD_FEED);
    if (!StringUtils.isSet(lastKeywordFeedId))
      lastKeywordFeedId = fgSearchEngines.get(0).getId();

    for (final SearchEngine engine : fgSearchEngines) {
      Button button = new Button(contentMargin, SWT.RADIO);
      button.setText(engine.getName());
      button.setImage(OwlUI.getImage(button, engine.getIconPath()));
      button.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          fSelectedEngine = engine;
        }
      });

      /* Pre-select last used engine */
      if (lastKeywordFeedId.equals(engine.getId())) {
        button.setSelection(true);
        button.setFocus();
        fSelectedEngine = engine;
      }
    }

    setControl(container);
  }
}