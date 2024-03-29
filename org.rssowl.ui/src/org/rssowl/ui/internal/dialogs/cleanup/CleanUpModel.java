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

package org.rssowl.ui.internal.dialogs.cleanup;

import org.rssowl.core.Owl;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.ILabel;
import org.rssowl.core.persist.IModelFactory;
import org.rssowl.core.persist.INews;
import org.rssowl.core.persist.ISearchCondition;
import org.rssowl.core.persist.ISearchField;
import org.rssowl.core.persist.SearchSpecifier;
import org.rssowl.core.persist.INews.State;
import org.rssowl.core.persist.dao.DynamicDAO;
import org.rssowl.core.persist.dao.ILabelDAO;
import org.rssowl.core.persist.dao.INewsDAO;
import org.rssowl.core.persist.reference.NewsReference;
import org.rssowl.core.persist.service.IModelSearch;
import org.rssowl.core.util.DateUtils;
import org.rssowl.core.util.SearchHit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Creates the collection of <code>CleanUpTask</code> that the user may choose
 * to perform as clean up.
 *
 * @author bpasero
 */
public class CleanUpModel {

  /* One Day in millis */
  private static final long DAY = 24 * 60 * 60 * 1000;

  private List<CleanUpGroup> fTasks;
  private final CleanUpOperations fOps;
  private final Collection<IBookMark> fBookmarks;
  private IModelFactory fFactory;
  private IModelSearch fModelSearch;
  private ISearchField fLocationField;
  private ISearchField fAgeInDaysField;
  private String fNewsName;
  private INewsDAO fNewsDao;

  /**
   * @param operations
   * @param bookmarks
   */
  public CleanUpModel(CleanUpOperations operations, Collection<IBookMark> bookmarks) {
    fOps = operations;
    fBookmarks = bookmarks;
    fTasks = new ArrayList<CleanUpGroup>();
    fFactory = Owl.getModelFactory();
    fModelSearch = Owl.getPersistenceService().getModelSearch();
    fNewsDao = DynamicDAO.getDAO(INewsDAO.class);

    String newsName = INews.class.getName();
    fLocationField = fFactory.createSearchField(INews.LOCATION, newsName);
    fAgeInDaysField = fFactory.createSearchField(INews.AGE_IN_DAYS, newsName);
    fNewsName = INews.class.getName();
  }

  /**
   * @return Returns the Task Groups
   */
  public List<CleanUpGroup> getTasks() {
    return fTasks;
  }

  private ISearchCondition getLocationCondition(IBookMark mark) {
    Long[][] value = new Long[3][1];
    value[1][0] = mark.getId();
    return fFactory.createSearchCondition(fLocationField, SearchSpecifier.IS, value);
  }

  /** Calculate Tasks */
  public void generate() {
    Set<IBookMark> bookmarksToDelete = new HashSet<IBookMark>();
    Map<IBookMark, Set<NewsReference>> newsToDelete = new HashMap<IBookMark, Set<NewsReference>>();

    /* 0.) Create Recommended Tasks */
    CleanUpGroup recommendedTasks = new CleanUpGroup("Recommended Operations");
    recommendedTasks.addTask(new DefragDatabaseTask(recommendedTasks));
    recommendedTasks.addTask(new OptimizeSearchTask(recommendedTasks));
    fTasks.add(recommendedTasks);

    /* 1.) Delete BookMarks that have Last Visit > X Days ago */
    if (fOps.deleteFeedByLastVisit()) {
      CleanUpGroup group = new CleanUpGroup("Bookmarks that have not been displayed for " + fOps.getLastVisitDays() + " days");

      int days = fOps.getLastVisitDays();
      long maxLastVisitDate = DateUtils.getToday().getTimeInMillis() - (days * DAY);

      for (IBookMark mark : fBookmarks) {
        Date date = mark.getLastVisitDate();

        /* Use Creation Date if mark was never visited */
        if (date == null)
          date = mark.getCreationDate();

        if (date == null || date.getTime() <= maxLastVisitDate) {
          bookmarksToDelete.add(mark);
          group.addTask(new BookMarkTask(group, mark));
        }
      }

      if (!group.isEmpty())
        fTasks.add(group);
    }

    /* 2.) Delete BookMarks that have not updated in X Days */
    if (fOps.deleteFeedByLastUpdate()) {
      CleanUpGroup group = new CleanUpGroup("Bookmarks that have not been updated for " + fOps.getLastUpdateDays() + " days");

      // TODO Remove this block in M9
      ISearchField stateField = fFactory.createSearchField(INews.STATE, fNewsName);
      EnumSet<State> visibleStates = EnumSet.of(INews.State.NEW, INews.State.UNREAD, INews.State.UPDATED, INews.State.READ);
      ISearchCondition stateCondition = fFactory.createSearchCondition(stateField, SearchSpecifier.IS, visibleStates);
      ISearchCondition ageCond = fFactory.createSearchCondition(fAgeInDaysField, SearchSpecifier.IS_LESS_THAN, fOps.getLastUpdateDays());

      int days = fOps.getLastUpdateDays();
      long maxLastUpdateDate = DateUtils.getToday().getTimeInMillis() - (days * DAY);

      /* For each selected Bookmark */
      for (IBookMark mark : fBookmarks) {

        /* Ignore if Bookmark gets already deleted */
        if (bookmarksToDelete.contains(mark))
          continue;

        Date date = mark.getMostRecentNewsDate();

        /* Use old strategy (M7 and before) to check for last update */
        if (date == null) {
          List<ISearchCondition> conditions = new ArrayList<ISearchCondition>(3);
          conditions.add(getLocationCondition(mark));
          conditions.add(ageCond);
          conditions.add(stateCondition);

          List<SearchHit<NewsReference>> results = filterInvalidResults(fModelSearch.searchNews(conditions, true));
          if (results.isEmpty()) {
            bookmarksToDelete.add(mark);
            group.addTask(new BookMarkTask(group, mark));
          }
        }

        /* Use new strategy (M8) to check for the last update */
        else if (date.getTime() <= maxLastUpdateDate) {
          bookmarksToDelete.add(mark);
          group.addTask(new BookMarkTask(group, mark));
        }
      }

      if (!group.isEmpty())
        fTasks.add(group);
    }

    /* 3.) Delete BookMarks that have Connection Error */
    if (fOps.deleteFeedsByConError()) {
      CleanUpGroup group = new CleanUpGroup("Bookmarks with a connection error");

      for (IBookMark mark : fBookmarks) {
        if (!bookmarksToDelete.contains(mark) && mark.isErrorLoading()) {
          bookmarksToDelete.add(mark);
          group.addTask(new BookMarkTask(group, mark));
        }
      }

      if (!group.isEmpty())
        fTasks.add(group);
    }

    /* 4.) Delete Duplicate BookMarks */
    if (fOps.deleteFeedsByDuplicates()) {
      CleanUpGroup group = new CleanUpGroup("Most recently created duplicate bookmarks");

      for (IBookMark currentBookMark : fBookmarks) {
        if (!bookmarksToDelete.contains(currentBookMark)) {

          /* Group of Bookmarks referencing the same Feed sorted by Creation Date */
          Set<IBookMark> sortedBookmarkGroup = new TreeSet<IBookMark>(new Comparator<IBookMark>() {
            public int compare(IBookMark o1, IBookMark o2) {
              return o1.getCreationDate().compareTo(o2.getCreationDate());
            }
          });

          /* Add Current Bookmark and Duplicates */
          for (IBookMark bookMark : fBookmarks) {
            if (!bookmarksToDelete.contains(bookMark) && bookMark.getFeedLinkReference().equals(currentBookMark.getFeedLinkReference())) {
              sortedBookmarkGroup.add(bookMark);
            }
          }

          /* Delete most recent duplicates if any */
          if (sortedBookmarkGroup.size() > 1) {
            Iterator<IBookMark> iterator = sortedBookmarkGroup.iterator();
            iterator.next(); // Ignore first, oldest one

            while (iterator.hasNext()) {
              IBookMark bookmark = iterator.next();
              bookmarksToDelete.add(bookmark);
              group.addTask(new BookMarkTask(group, bookmark));
            }
          }
        }
      }

      if (!group.isEmpty())
        fTasks.add(group);
    }

    /* Reusable State Condition */
    EnumSet<State> states = fOps.keepUnreadNews() ? EnumSet.of(INews.State.READ) : EnumSet.of(INews.State.NEW, INews.State.UNREAD, INews.State.UPDATED, INews.State.READ);
    ISearchField stateField = fFactory.createSearchField(INews.STATE, fNewsName);
    ISearchCondition stateCondition = fFactory.createSearchCondition(stateField, SearchSpecifier.IS, states);

    /* Reusable Sticky Condition */
    ISearchField stickyField = fFactory.createSearchField(INews.IS_FLAGGED, fNewsName);
    ISearchCondition stickyCondition = fFactory.createSearchCondition(stickyField, SearchSpecifier.IS_NOT, true);

    /* Reusable Label Condition */
    Collection<ILabel> labels = DynamicDAO.getDAO(ILabelDAO.class).loadAll();
    ISearchField labelField = fFactory.createSearchField(INews.LABEL, fNewsName);
    List<ISearchCondition> labelConditions = new ArrayList<ISearchCondition>(labels.size());
    for (ILabel label : labels) {
      labelConditions.add(fFactory.createSearchCondition(labelField, SearchSpecifier.IS_NOT, label.getName()));
    }

    /* 4.) Delete News that exceed a certain limit in a Feed */
    if (fOps.deleteNewsByCount()) {
      CleanUpGroup group = new CleanUpGroup("News exceeding a limit of " + fOps.getMaxNewsCountPerFeed() + " per feed");

      /* For each selected Bookmark */
      for (IBookMark mark : fBookmarks) {

        /* Ignore if Bookmark gets already deleted */
        if (bookmarksToDelete.contains(mark))
          continue;

        List<ISearchCondition> conditions = new ArrayList<ISearchCondition>(3);
        conditions.add(getLocationCondition(mark));
        conditions.add(stickyCondition);
        conditions.add(stateCondition);
        conditions.addAll(labelConditions);

        /* Check if result count exceeds limit */
        List<SearchHit<NewsReference>> results = filterInvalidResults(fModelSearch.searchNews(conditions, true));
        if (results.size() > fOps.getMaxNewsCountPerFeed()) {
          int toDeleteValue = results.size() - fOps.getMaxNewsCountPerFeed();

          /* Resolve News */
          List<INews> resolvedNews = new ArrayList<INews>(results.size());
          for (SearchHit<NewsReference> result : results)
            resolvedNews.add(result.getResult().resolve());

          /* Sort by Date */
          Collections.sort(resolvedNews, new Comparator<INews>() {
            public int compare(INews news1, INews news2) {
              return DateUtils.getRecentDate(news1).compareTo(DateUtils.getRecentDate(news2));
            }
          });

          Set<NewsReference> newsOfMarkToDelete = new HashSet<NewsReference>();
          for (int i = 0; i < resolvedNews.size() && i < toDeleteValue; i++)
            newsOfMarkToDelete.add(new NewsReference(resolvedNews.get(i).getId()));

          if (!newsOfMarkToDelete.isEmpty()) {
            newsToDelete.put(mark, newsOfMarkToDelete);
            group.addTask(new NewsTask(group, mark, newsOfMarkToDelete));
          }
        }
      }

      if (!group.isEmpty())
        fTasks.add(group);
    }

    /* 5.) Delete News with an age > X Days */
    if (fOps.deleteNewsByAge()) {
      CleanUpGroup group = new CleanUpGroup("News older than " + fOps.getMaxNewsAge() + " days");

      ISearchField ageInDaysField = fFactory.createSearchField(INews.AGE_IN_DAYS, fNewsName);
      ISearchCondition ageCond = fFactory.createSearchCondition(ageInDaysField, SearchSpecifier.IS_GREATER_THAN, fOps.getMaxNewsAge());

      /* For each selected Bookmark */
      for (IBookMark mark : fBookmarks) {

        /* Ignore if Bookmark gets already deleted */
        if (bookmarksToDelete.contains(mark))
          continue;

        List<ISearchCondition> conditions = new ArrayList<ISearchCondition>(4);
        conditions.add(getLocationCondition(mark));
        conditions.add(ageCond);
        conditions.add(stateCondition);
        conditions.add(stickyCondition);
        conditions.addAll(labelConditions);

        List<SearchHit<NewsReference>> results = filterInvalidResults(fModelSearch.searchNews(conditions, true));
        Set<NewsReference> newsOfMarkToDelete = new HashSet<NewsReference>();
        for (SearchHit<NewsReference> result : results)
          newsOfMarkToDelete.add(result.getResult());

        if (!newsOfMarkToDelete.isEmpty()) {
          Collection<NewsReference> existingNewsOfMarkToDelete = newsToDelete.get(mark);

          /* First time the Mark is treated */
          if (existingNewsOfMarkToDelete == null) {
            newsToDelete.put(mark, newsOfMarkToDelete);
            group.addTask(new NewsTask(group, mark, newsOfMarkToDelete));
          }

          /* Existing Mark */
          else {
            newsOfMarkToDelete.removeAll(existingNewsOfMarkToDelete);

            if (!newsOfMarkToDelete.isEmpty()) {
              existingNewsOfMarkToDelete.addAll(newsOfMarkToDelete);
              group.addTask(new NewsTask(group, mark, newsOfMarkToDelete));
            }
          }
        }
      }

      if (!group.isEmpty())
        fTasks.add(group);
    }

    /* 6.) Delete Read News */
    if (fOps.deleteReadNews()) {
      CleanUpGroup group = new CleanUpGroup("Read News");

      EnumSet<State> readState = EnumSet.of(INews.State.READ);
      ISearchCondition stateCond = fFactory.createSearchCondition(stateField, SearchSpecifier.IS, readState);

      /* For each selected Bookmark */
      for (IBookMark mark : fBookmarks) {

        /* Ignore if Bookmark gets already deleted */
        if (bookmarksToDelete.contains(mark))
          continue;

        List<ISearchCondition> conditions = new ArrayList<ISearchCondition>(3);
        conditions.add(getLocationCondition(mark));
        conditions.add(stateCond);
        conditions.add(stickyCondition);
        conditions.addAll(labelConditions);

        List<SearchHit<NewsReference>> results = filterInvalidResults(fModelSearch.searchNews(conditions, true));
        Set<NewsReference> newsOfMarkToDelete = new HashSet<NewsReference>();
        for (SearchHit<NewsReference> result : results)
          newsOfMarkToDelete.add(result.getResult());

        if (!newsOfMarkToDelete.isEmpty()) {
          Collection<NewsReference> existingNewsOfMarkToDelete = newsToDelete.get(mark);

          /* First time the Mark is treated */
          if (existingNewsOfMarkToDelete == null) {
            newsToDelete.put(mark, newsOfMarkToDelete);
            group.addTask(new NewsTask(group, mark, newsOfMarkToDelete));
          }

          /* Existing Mark */
          else {
            newsOfMarkToDelete.removeAll(existingNewsOfMarkToDelete);

            if (!newsOfMarkToDelete.isEmpty()) {
              existingNewsOfMarkToDelete.addAll(newsOfMarkToDelete);
              group.addTask(new NewsTask(group, mark, newsOfMarkToDelete));
            }
          }
        }
      }

      if (!group.isEmpty())
        fTasks.add(group);
    }
  }

  /* TODO Have to test if Entity really exists (bug 337) */
  private List<SearchHit<NewsReference>> filterInvalidResults(List<SearchHit<NewsReference>> results) {
    List<SearchHit<NewsReference>> validResults = new ArrayList<SearchHit<NewsReference>>(results.size());

    for (SearchHit<NewsReference> searchHit : results) {
      if (fNewsDao.exists(searchHit.getResult().getId()))
        validResults.add(searchHit);
    }

    return validResults;
  }
}