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

package org.rssowl.core.internal.persist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.rssowl.core.Owl;
import org.rssowl.core.persist.ILabel;
import org.rssowl.core.persist.INews;
import org.rssowl.core.persist.ISearchCondition;
import org.rssowl.core.persist.ISearchField;
import org.rssowl.core.persist.ISearchMark;
import org.rssowl.core.persist.SearchSpecifier;
import org.rssowl.core.persist.reference.LabelReference;
import org.rssowl.core.persist.reference.NewsReference;
import org.rssowl.core.persist.service.IModelSearch;
import org.rssowl.core.util.SearchHit;
import org.rssowl.core.util.StringUtils;

/**
 * A Label for a News. Some predefined Labels could be "Important", "Work",
 * "Personal", "Todo". Labels should be added by the user and be shown in a
 * custom Color. Labels could also be used to represent AmphetaRate ratings.
 *
 * @author bpasero
 */
public class Label extends AbstractEntity implements ILabel {

  private String fName;
  private String fColor;
  
  //TODO Userstory 4: searchMark added to Label
  private ISearchMark fSearchMark;

  /**
   * Creates a new Element of type Label.
   *
   * @param id The unique ID of this Label.
   * @param name The Name of this Label.
   */
  public Label(Long id, String name) {
    super(id);
    Assert.isNotNull(name, "The type Label requires a Name that is not NULL"); //$NON-NLS-1$
    fName = name;
  }

  /**
   * Default constructor for deserialization
   */
  protected Label() {
    // As per javadoc
  }

  /*
   * @see org.rssowl.core.model.types.impl.ILabel#getColor()
   */
  public synchronized String getColor() {
    return fColor;
  }

  /*
   * @see org.rssowl.core.model.types.ILabel#setColor(java.lang.String)
   */
  public synchronized void setColor(String color) {
    Assert.isLegal(StringUtils.isValidRGB(color), "Color must be using the format \"R,G,B\", for example \"255,255,127\""); //$NON-NLS-1$
    fColor = color;
  }

  /*
   * @see org.rssowl.core.model.types.impl.ILabel#getName()
   */
  public synchronized String getName() {
    return fName;
  }

  /*
   * @see org.rssowl.core.model.types.ILabel#setName(java.lang.String)
   */
  public synchronized void setName(String name) {
    Assert.isNotNull(name, "The type Label requires a Name that is not NULL"); //$NON-NLS-1$
    fName = name;
  }

  /**
   * Compare the given type with this type for identity.
   *
   * @param label to be compared.
   * @return whether this object and <code>label</code> are identical. It
   * compares all the fields.
   */
  public synchronized boolean isIdentical(ILabel label) {
    if (this == label)
      return true;

    if (!(label instanceof Label))
      return false;

    synchronized (label) {
      Label l = (Label) label;

      return getId() == l.getId() && fName.equals(l.fName) &&
          (fColor == null ? l.fColor == null : fColor.equals(l.fColor)) &&
          (getProperties() == null ? l.getProperties() == null : getProperties().equals(l.getProperties()));
    }
  }

  @Override
  @SuppressWarnings("nls")
  public synchronized String toString() {
    return super.toString() + "Name = " + fName + ", Color = " + fColor + ")";
  }

  public LabelReference toReference() {
    return new LabelReference(getIdAsPrimitive());
  }

  //TODO Userstory 4: searchMark added to Label (setter)
  public void setSearchMark(ISearchMark searchMark) {
	  fSearchMark = searchMark;
  }
	
  //TODO Userstory 4: searchMark added to Label (getter)
  public ISearchMark getSearchMark() {
	  return fSearchMark;
  }

  public int getStickyNewsCount() {
	  int count = 0;
	  Collection<INews> news = findNewsWithLabel();
	  for (INews newsItem : news) {
		if (newsItem.isFlagged()) {
			count++;
		}
	  }
	  return count;
  }
  
  public Collection<INews> findNewsWithLabel() {
	    List<INews> news = new ArrayList<INews>();

	    ISearchField fLabelField = Owl.getModelFactory().createSearchField(INews.LABEL, INews.class.getName());
	    ISearchCondition condition = Owl.getModelFactory().createSearchCondition(fLabelField, SearchSpecifier.IS, this.getName());
	    IModelSearch fModelSearch = Owl.getPersistenceService().getModelSearch();
	    List<SearchHit<NewsReference>> result = fModelSearch.searchNews(Collections.singleton(condition), false);

	    for (SearchHit<NewsReference> hit : result) {
	      INews newsitem = hit.getResult().resolve();
	      if (newsitem != null) {
	        Set<ILabel> newsLabels = newsitem.getLabels();
	        if (newsLabels != null && newsLabels.contains(this))
	          news.add(newsitem);
	      }
	    }

	    return news;
	  }
}