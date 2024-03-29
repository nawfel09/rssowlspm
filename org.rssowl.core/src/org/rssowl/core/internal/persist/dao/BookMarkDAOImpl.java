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

package org.rssowl.core.internal.persist.dao;

import org.rssowl.core.internal.persist.BookMark;
import org.rssowl.core.internal.persist.service.DBHelper;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.dao.IBookMarkDAO;
import org.rssowl.core.persist.event.BookMarkEvent;
import org.rssowl.core.persist.event.BookMarkListener;
import org.rssowl.core.persist.reference.FeedLinkReference;
import org.rssowl.core.persist.service.PersistenceException;

import com.db4o.ext.Db4oException;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A data-access-object for <code>IBookMark</code>s.
 *
 * @author Ismael Juma (ismael@juma.me.uk)
 */
public final class BookMarkDAOImpl extends AbstractEntityDAO<IBookMark, BookMarkListener, BookMarkEvent> implements IBookMarkDAO {

  /** Default constructor using the specific IPersistable for this DAO */
  public BookMarkDAOImpl() {
    super(BookMark.class, false);
  }

  @Override
  protected final BookMarkEvent createDeleteEventTemplate(IBookMark entity) {
    return createSaveEventTemplate(entity);
  }

  @Override
  protected final BookMarkEvent createSaveEventTemplate(IBookMark entity) {
    return new BookMarkEvent(entity, null, true);
  }

  public final Collection<IBookMark> loadAll(FeedLinkReference feedRef) {
    try {
      Collection<IBookMark> marks = DBHelper.loadAllBookMarks(fDb, feedRef);
      activateAll(marks);
      return new ArrayList<IBookMark>(marks);
    } catch (Db4oException e) {
      throw new PersistenceException(e);
    }
  }
}