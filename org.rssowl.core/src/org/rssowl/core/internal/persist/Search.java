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

import org.eclipse.core.runtime.Assert;
import org.rssowl.core.persist.ISearch;
import org.rssowl.core.persist.ISearchCondition;
import org.rssowl.core.persist.reference.SearchReference;

import java.util.ArrayList;
import java.util.List;

public class Search extends AbstractEntity implements ISearch  {

  private String fName;
  private List<ISearchCondition> fSearchConditions;
  private boolean fMatchAllConditions;

  public Search(Long id, String name) {
    super(id);
    Assert.isNotNull(name, "name");
    fName = name;
    fSearchConditions = new ArrayList<ISearchCondition>(5);
  }

  /**
   * Provided for deserialization
   */
  protected Search() {
    super();
  }

  /*
   * @see org.rssowl.core.model.types.ISearchMark#addSearchCondition(org.rssowl.core.model.reference.SearchConditionReference)
   */
  public synchronized void addSearchCondition(ISearchCondition searchCondition) {
    Assert.isNotNull(searchCondition, "Exception adding NULL as Search Condition into SearchMark"); //$NON-NLS-1$
    fSearchConditions.add(searchCondition);
  }

  /*
   * @see org.rssowl.core.model.types.ISearchMark#removeSearchCondition(org.rssowl.core.model.search.ISearchCondition)
   */
  public synchronized boolean removeSearchCondition(ISearchCondition searchCondition) {
    return fSearchConditions.remove(searchCondition);
  }

  public synchronized boolean containsSearchCondition(ISearchCondition searchCondition) {
    return fSearchConditions.contains(searchCondition);
  }

  /*
   * @see org.rssowl.core.model.types.ISearchMark#getSearchConditions()
   */
  public synchronized List<ISearchCondition> getSearchConditions() {
    return new ArrayList<ISearchCondition>(fSearchConditions);
  }

  /*
   * @see org.rssowl.core.model.types.ISearchMark#requiresAllConditions()
   */
  public synchronized boolean matchAllConditions() {
    return fMatchAllConditions;
  }

  /*
   * @see org.rssowl.core.model.types.ISearchMark#setRequireAllConditions(boolean)
   */
  public synchronized void setMatchAllConditions(boolean requiresAllConditions) {
    fMatchAllConditions = requiresAllConditions;
  }

  public synchronized String getName() {
    return fName;
  }

  public SearchReference toReference() {
    return new SearchReference(getIdAsPrimitive());
  }

  public synchronized void setName(String name) {
    Assert.isNotNull(name, "name");
    fName = name;
  }
}
