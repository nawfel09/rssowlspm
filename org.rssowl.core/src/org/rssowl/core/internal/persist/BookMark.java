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
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.INews;
import org.rssowl.core.persist.NewsCounter;
import org.rssowl.core.persist.INews.State;
import org.rssowl.core.persist.reference.BookMarkReference;
import org.rssowl.core.persist.reference.FeedLinkReference;
import org.rssowl.core.persist.reference.NewsReference;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A usual bookmark as seen in Firefox or other Browsers. The Bookmark is used
 * to define a position for a <code>Feed</code> inside the hierarchy of Folders.
 * The user may define some properties, e.g. how often to reload the related
 * Feed.
 * 
 * @author bpasero
 */
public class BookMark extends Mark implements IBookMark {
	private String fFeedLink;
	private transient FeedLinkReference fFeedLinkReference;
	private boolean fIsErrorLoading;
	private transient NewsCounter fNewsCounter;
	private long fMostRecentNewsDate = -1;

	/**
	 * Creates a new Element of the type BookMark. A BookMark is only visually
	 * represented in case it was added to a Folder. Make sure to add it to a
	 * Folder using <code>Folder#addMark(Mark)</code>
	 * 
	 * @param id
	 *            The unique ID of this type.
	 * @param folder
	 *            The Folder this BookMark belongs to.
	 * @param feedRef
	 *            The reference to the feed this BookMark is related to.
	 * @param name
	 *            The Name of this BookMark.
	 */
	public BookMark(Long id, IFolder folder, FeedLinkReference feedRef,
			String name) {
		super(id, folder, name);
		Assert.isNotNull(feedRef, "feedRef cannot be null"); //$NON-NLS-1$
		fFeedLinkReference = feedRef;
		fFeedLink = feedRef.getLinkAsText();
	}

	/**
	 * Default constructor for deserialization
	 */
	protected BookMark() {
		// As per javadoc
	}

	public synchronized Date getMostRecentNewsDate() {
		if (fMostRecentNewsDate < 0)
			return null;

		return new Date(fMostRecentNewsDate);
	}

	public synchronized void setMostRecentNewsDate(Date date) {
		Assert.isNotNull(date, "date");
		fMostRecentNewsDate = date.getTime();
	}

	public synchronized void setNewsCounter(NewsCounter newsCounter) {
		fNewsCounter = newsCounter;
	}

	/*
	 * @see org.rssowl.core.model.types.IFeed#isErrorLoading()
	 */
	public synchronized boolean isErrorLoading() {
		return fIsErrorLoading;
	}

	/*
	 * @see org.rssowl.core.model.types.IFeed#setErrorLoading(boolean)
	 */
	public synchronized void setErrorLoading(boolean isErrorLoading) {
		fIsErrorLoading = isErrorLoading;
	}

	/*
	 * @see org.rssowl.core.model.types.IBookMark#getFeedLinkReference()
	 */
	public synchronized FeedLinkReference getFeedLinkReference() {
		if (fFeedLinkReference == null)
			fFeedLinkReference = new FeedLinkReference(createURI(fFeedLink));

		return fFeedLinkReference;
	}

	/*
	 * @see
	 * org.rssowl.core.model.types.IBookMark#setFeedLinkReference(org.rssowl
	 * .core.model.reference.FeedLinkReference)
	 */
	public synchronized void setFeedLinkReference(FeedLinkReference feedLinkRef) {
		Assert.isNotNull(feedLinkRef, "link cannot be null"); //$NON-NLS-1$
		fFeedLinkReference = feedLinkRef;
		fFeedLink = feedLinkRef.getLinkAsText();
	}

	/**
	 * Compare the given type with this type for identity.
	 * 
	 * @param bookMark
	 *            to be compared.
	 * @return whether this object and <code>bookMark</code> are identical. It
	 *         compares all the fields.
	 */
	public synchronized boolean isIdentical(IBookMark bookMark) {
		if (this == bookMark)
			return true;

		if (!(bookMark instanceof BookMark))
			return false;

		synchronized (bookMark) {
			BookMark b = (BookMark) bookMark;

			return getId() == b.getId()
					&& (getParent() == null ? b.getParent() == null
							: getParent().equals(b.getParent()))
					&& (getCreationDate() == null ? b.getCreationDate() == null
							: getCreationDate().equals(b.getCreationDate()))
					&& (getName() == null ? b.getName() == null : getName()
							.equals(b.getName()))
					&& (getLastVisitDate() == null ? b.getLastVisitDate() == null
							: getLastVisitDate().equals(b.getLastVisitDate()))
					&& getPopularity() == b.getPopularity()
					&& fIsErrorLoading == b.fIsErrorLoading
					&& (getProperties() == null ? b.getProperties() == null
							: getProperties().equals(b.getProperties()));
		}
	}

	@SuppressWarnings("nls")
	@Override
	public synchronized String toLongString() {
		return super.toString() + ", Is Error Loading: " + fIsErrorLoading
				+ ", Belongs " + "to Feed = " + fFeedLink + ")";
	}

	@Override
	@SuppressWarnings("nls")
	public synchronized String toString() {
		return super.toString() + "Belongs to Feed = " + fFeedLink + ")";
	}

	/* getIdAsPrimitive is synchronized so this method doesn't need to be */
	public BookMarkReference toReference() {
		return new BookMarkReference(getIdAsPrimitive());
	}

	/* getFeedLinkReference is synchronized, so no need to synchronize this */
	public synchronized List<INews> getNews() {
		return getFeedLinkReference().resolve().getNews();
	}

	/* getFeedLinkReference is synchronized, so no need to synchronize this */
	public List<INews> getNews(Set<State> states) {
		return getFeedLinkReference().resolve().getNewsByStates(states);
	}

	public synchronized int getNewsCount(Set<State> states) {
		if (fNewsCounter != null) {
			if (states.equals(EnumSet.of(INews.State.NEW)))
				return fNewsCounter.getNewCount(getFeedLinkReference()
						.getLink());

			if (states.equals(EnumSet.of(INews.State.NEW, INews.State.UNREAD,
					INews.State.UPDATED)))
				return fNewsCounter.getUnreadCount(getFeedLinkReference()
						.getLink());
		}

		return getNews(states).size();
	}

	/* getNewsCount(states) is synchronized so this method does not need to be */
	public double getReadPercentage() {
		int total = this.getNews().size();
		
		if(total != 0) {
			int read = this.getNewsCount(EnumSet.of(State.READ));
			
			double percentage = (read * 100) / ((double) total);
			return Math.round(percentage * 100.0) / 100.0;
		}else
			return 0;
	}

	/* getNews(states) is synchronized so this method does not need to be */
	public List<NewsReference> getNewsRefs() {
		return getNewsRefs(EnumSet.allOf(INews.State.class));
	}

	/* getNews(states) is synchronized so this method does not need to be */
	public List<NewsReference> getNewsRefs(Set<State> states) {
		List<INews> news = getNews(states);
		List<NewsReference> newsRefs = new ArrayList<NewsReference>();
		for (INews newsItem : news) {
			newsRefs.add(new NewsReference(newsItem.getId()));
		}
		return newsRefs;
	}

	public boolean isGetNewsRefsEfficient() {
		return false;
	}

	/* getFeedLinkReference is synchronized, so no need to synchronize this */
	public boolean containsNews(INews news) {
		return news.getParentId() == 0
				&& news.getFeedLinkAsText().equals(fFeedLink);
	}

	public synchronized int getStickyNewsCount() {
		if (fNewsCounter != null)
			return fNewsCounter
					.getStickyCount(getFeedLinkReference().getLink());

		int stickyCount = 0;
		for (INews news : getNews()) {
			if (news.isFlagged())
				++stickyCount;
		}
		return stickyCount;
	}
}