
package org.rssowl.core.internal.persist.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.Activator;
import org.rssowl.core.internal.InternalOwl;
import org.rssowl.core.internal.persist.dao.EntitiesToBeIndexedDAOImpl;
import org.rssowl.core.internal.persist.service.DBHelper;
import org.rssowl.core.internal.persist.service.EntityIdsByEventType;
import org.rssowl.core.persist.IEntity;
import org.rssowl.core.persist.ILabel;
import org.rssowl.core.persist.INews;
import org.rssowl.core.persist.ISearchCondition;
import org.rssowl.core.persist.ISearchField;
import org.rssowl.core.persist.SearchSpecifier;
import org.rssowl.core.persist.event.LabelAdapter;
import org.rssowl.core.persist.event.LabelEvent;
import org.rssowl.core.persist.event.NewsEvent;
import org.rssowl.core.persist.event.NewsListener;
import org.rssowl.core.persist.event.runnable.EventType;
import org.rssowl.core.persist.reference.ModelReference;
import org.rssowl.core.persist.reference.NewsReference;
import org.rssowl.core.persist.service.PersistenceException;
import org.rssowl.core.util.JobQueue;
import org.rssowl.core.util.SearchHit;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * @author ijuma
 * @author bpasero
 */
public class Indexer {

  /* Delay in millis before showing Progress of Indexing */
  private static final int INDEX_JOB_PROGRESS_DELAY = 800;

  /* Lucene only allows 1 Indexer to run at the same time */
  private static final int MAX_INDEX_JOBS_COUNT = 1;

  /* DWord to disable stop words when Indexing */
  private static final String DISABLE_STOP_WORDS_PROPERTY = "disableStopWords";

  /* The directory to the lucene index */
  private final Directory fIndexDirectory;

  /* The IndexWriter to add/update/delete Documents */
  private IndexWriter fIndexWriter;

  private final JobQueue fJobQueue;
  private NewsListener fNewsListener;
  private LabelAdapter fLabelListener;
  private volatile boolean fFlushRequired;
  private final ModelSearchImpl fSearch;
  static final boolean DISABLE_STOP_WORDS = System.getProperty(DISABLE_STOP_WORDS_PROPERTY) != null;
  private final EntityIdsByEventType fUncommittedNews;

  /* The Default Analyzer */
  private static class DefaultAnalyzer extends KeywordAnalyzer {

    /*
     * @see org.apache.lucene.analysis.KeywordAnalyzer#tokenStream(java.lang.String,
     * java.io.Reader)
     */
    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
      TokenStream result = super.tokenStream(fieldName, reader);
      result = new LowerCaseFilter(result);

      return result;
    }

    @Override
    public TokenStream reusableTokenStream(String fieldName, Reader reader) {
      return tokenStream(fieldName, reader);
    }
  }

  /**
   * @param search
   * @param directory
   * @throws PersistenceException
   */
  Indexer(ModelSearchImpl search, Directory directory) throws PersistenceException {
    fSearch = search;
    fIndexDirectory = directory;
    fJobQueue = new JobQueue("Updating Saved Searches", MAX_INDEX_JOBS_COUNT, Integer.MAX_VALUE, true, INDEX_JOB_PROGRESS_DELAY);
    fUncommittedNews = new EntityIdsByEventType(false);
  }

  /**
   * TODO Provide generic method that can deal with any entity!
   *
   * @param entities
   * @param isUpdate
   */
  synchronized void index(List<INews> entities, boolean isUpdate) {
    int docCount = 0;

    /* For each Event */
    for (ListIterator<INews> it = entities.listIterator(entities.size()); it.hasPrevious();) {
      INews news = it.previous();
      it.remove();
      NewsDocument newsDoc = new NewsDocument(news);
      try {
        if (newsDoc.addFields()) {
          docCount++;

          /* Update Event */
          if (isUpdate) {
            Term term = createTerm(news);
            fUncommittedNews.addUpdatedEntity(news);
            fIndexWriter.updateDocument(term, newsDoc.getDocument());
          }

          /* Added Event */
          else {
            fUncommittedNews.addPersistedEntity(news);
            fIndexWriter.addDocument(newsDoc.getDocument());
          }

        }
      } catch (IOException e) {
        Activator.getDefault().getLog().log(Activator.getDefault().createErrorStatus(e.getMessage(), e));
      }
    }

    /*
     * Change the fFlushRequired field at the end. This increases concurrency
     * slightly by allowing some minor staleness. More concretely if a reader
     * performs a search while index is taking place for the first time since
     * the last flush, it will just use the current searcher instead of blocking.
     * This is similar to what is done in removeFromIndex.
     */
    if (docCount > 0) {
      fFlushRequired = true;

      /* Notify Listeners */
      fSearch.notifyIndexUpdated(docCount);
    }
  }

  /**
   * TODO Provide generic method that can deal with any Event!
   *
   * @param entities
   * @throws IOException
   */
  synchronized void removeFromIndex(Collection<NewsReference> entities) throws IOException {
    int docCount = 0;

    /* For each entity */
    for (NewsReference newsRef : entities) {
      Term term = createTerm(newsRef);
      fUncommittedNews.addRemovedEntityId(newsRef.getId());
      fIndexWriter.deleteDocuments(term);
      docCount++;
    }

    if (docCount > 0) {
      /* Mark as in need for a flush */
      fFlushRequired = true;

      /* Notify Listeners */
      fSearch.notifyIndexUpdated(docCount);
    }
  }

  //TODO Consider renaming to commitIfNecessary
  //TODO Remove fFlushRequired and rely on fUncommittedNews
  //TODO Perhaps commit after fUncommittedNews has a certain size instead
  //of relying always in this method. In most situations this method will
  //be called often though
  boolean flushIfNecessary() throws PersistenceException {
    if (!fFlushRequired)
      return false;

    synchronized (this) {
      /*
       * Another thread got the lock before us and flushed. We must still return
       * {@code true} to let the caller know that the index has changed.
       */
      if (!fFlushRequired)
        return true;

      dispose();
      createIndexWriter();
      saveCommittedNews(false, new EntityIdsByEventType(fUncommittedNews));
      fUncommittedNews.clear();
    }
    return true;
  }

  synchronized void shutdown(boolean emergency) {
    unregisterListeners();
    dispose();
    if (!emergency) {
      saveCommittedNews(true, fUncommittedNews);
      fUncommittedNews.clear();
    }
  }

  /**
   * Deletes all the information that is stored in the search index. This must
   * be called if the information stored in the persistence layer has been
   * cleared with a method that does not issue events for the elements that are
   * removed.
   *
   * @throws IOException
   */
  synchronized void clearIndex() throws IOException {
    dispose();
    /* Database got cleared, so we don't need to worry about syncing these values */
    fUncommittedNews.clear();
    if (IndexReader.indexExists(fIndexDirectory))
      fIndexWriter = createIndexWriter(fIndexDirectory, true);
  }

  /**
   * Optimizes the Lucene Index.
   *
   * @throws CorruptIndexException
   * @throws IOException
   */
  synchronized void optimize() throws CorruptIndexException, IOException {
    fIndexWriter.optimize();
  }

  /**
   * Creates the <code>Analyzer</code> that is used for all analyzation of
   * Fields and Queries.
   *
   * @return Returns the <code>Analyzer</code> that is used for all
   * analyzation of Fields and Queries.
   */
  static Analyzer createAnalyzer() {
    PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new DefaultAnalyzer());

    /* Standard (Lowercase, Letter, Stop,...) */
    StandardAnalyzer stdAnalyzer;
    if (DISABLE_STOP_WORDS)
      stdAnalyzer = new StandardAnalyzer(Collections.EMPTY_SET);
    else
      stdAnalyzer = new StandardAnalyzer();

    analyzer.addAnalyzer(String.valueOf(INews.TITLE), stdAnalyzer);
    analyzer.addAnalyzer(String.valueOf(INews.DESCRIPTION), stdAnalyzer);
    analyzer.addAnalyzer(String.valueOf(INews.ATTACHMENTS_CONTENT), stdAnalyzer);

    /* Simple (Lowercase, Whitespace Tokzenizer) */
    LowercaseWhitespaceAnalyzer simpleAnalyzer = new LowercaseWhitespaceAnalyzer();
    analyzer.addAnalyzer(String.valueOf(INews.AUTHOR), simpleAnalyzer);
    analyzer.addAnalyzer(String.valueOf(INews.LABEL), simpleAnalyzer);

    /* Simple (Lowercase, Delim Tokenizer) */
    analyzer.addAnalyzer(String.valueOf(INews.CATEGORIES), new LowercaseDelimiterAnalyzer('\n'));

    return analyzer;
  }

  private void init() throws PersistenceException {

    /* Create Index Writer */
    createIndexWriter();

    /* Listen to Model Events */
    registerListeners();

    /* Index outstanding news */
    if (!InternalOwl.TESTING) {
      Job delayedIndexJob = new Job("Indexing from dirty shutdown") {
        @Override
        protected IStatus run(IProgressMonitor monitor) {
          for (IndexingTask task : getIndexOutstandingEntitiesTasks())
            fJobQueue.schedule(task);

          return Status.OK_STATUS;
        }
      };

      delayedIndexJob.setSystem(true);
      delayedIndexJob.setUser(false);
      delayedIndexJob.schedule(1000);
    } else {
      for (IndexingTask task : getIndexOutstandingEntitiesTasks())
        task.run(new NullProgressMonitor());
    }
  }

  private List<IndexingTask> getIndexOutstandingEntitiesTasks() {
    final EntitiesToBeIndexedDAOImpl dao = DBHelper.getEntitiesToBeIndexedDAO();
    List<IndexingTask> indexingTasks = new ArrayList<IndexingTask>(3);
    if (dao != null) {
      EntityIdsByEventType outstandingNewsIds = dao.load();
      indexingTasks.add(new IndexingTask(Indexer.this, EventType.PERSIST, outstandingNewsIds.getPersistedEntityRefs()));
      indexingTasks.add(new IndexingTask(Indexer.this, EventType.UPDATE, outstandingNewsIds.getUpdatedEntityRefs()));
      indexingTasks.add(new IndexingTask(Indexer.this, EventType.REMOVE, outstandingNewsIds.getRemovedEntityRefs()));
    }
    return indexingTasks;
  }

  synchronized void initIfNecessary() {
    if (fIndexWriter == null)
      init();
  }

  private void createIndexWriter() {

    /* Create the Index if required */
    try {
      fIndexWriter = createIndexWriter(fIndexDirectory, !IndexReader.indexExists(fIndexDirectory));
    } catch (IOException e) {
      throw new PersistenceException(e.getMessage(), e);
    }
  }

  private void registerListeners() {

    /* Listener already registered */
    if (fNewsListener != null)
      return;

    /* Listen to News-Events */
    fNewsListener = new NewsListener() {
      public void entitiesAdded(Set<NewsEvent> events) {
        handleEntitiesAdded(events);
      }

      public void entitiesUpdated(Set<NewsEvent> events) {

        /* An Updated News may involve Restore, Removal or actual Update */
        Set<NewsEvent> newsToUpdate = null;
        Set<NewsEvent> newsToRestore = null;
        Set<NewsEvent> newsToDelete = null;
        for (NewsEvent event : events) {
          boolean wasVisible = event.getOldNews().isVisible();
          boolean isVisible = event.getEntity().isVisible();

          /* News got Deleted/Hidden */
          if (wasVisible && !isVisible) {
            if (newsToDelete == null)
              newsToDelete = new HashSet<NewsEvent>(5);

            newsToDelete.add(event);
          }

          /* News got Restored */
          else if (!wasVisible && isVisible) {
            if (newsToRestore == null)
              newsToRestore = new HashSet<NewsEvent>(5);

            newsToRestore.add(event);
          }

          /* Normal Update */
          else if (wasVisible && isVisible) {
            if (newsToUpdate == null)
              newsToUpdate = new HashSet<NewsEvent>(5);

            newsToUpdate.add(event);
          }
        }

        if (newsToRestore != null)
          handleEntitiesAdded(newsToRestore);

        if (newsToUpdate != null)
          handleEntitiesUpdated(newsToUpdate);

        if (newsToDelete != null)
          handleEntitiesDeleted(newsToDelete);
      }

      public void entitiesDeleted(Set<NewsEvent> events) {
        handleEntitiesDeleted(events);
      }
    };

    /* Listen to Label-Events */
    fLabelListener = new LabelAdapter() {
      @Override
      public void entitiesUpdated(Set<LabelEvent> events) {

        /* Re-Index all News when a containing Label updates */
        ISearchField searchField = Owl.getModelFactory().createSearchField(INews.LABEL, INews.class.getName());

        Set<Long> newsIndexed = new HashSet<Long>();
        for (LabelEvent labelEvent : events) {
          ILabel oldLabel = labelEvent.getOldLabel();
          ILabel updatedLabel = labelEvent.getEntity();
          if (!oldLabel.getName().equals(updatedLabel.getName())) {
            ISearchCondition searchCondition = Owl.getModelFactory().createSearchCondition(searchField, SearchSpecifier.IS, oldLabel.getName());
            List<SearchHit<NewsReference>> hits = Owl.getPersistenceService().getModelSearch().searchNews(Collections.singletonList(searchCondition), true);

            List<INews> newsList = new ArrayList<INews>(hits.size());
            for (SearchHit<NewsReference> hit : hits) {
              INews news = hit.getResult().resolve();
              if (news != null && !newsIndexed.contains(news.getId())) {
                newsList.add(news);
                newsIndexed.add(news.getId());
              }
            }
            if (!newsList.isEmpty()) {
              if (!InternalOwl.TESTING)
                fJobQueue.schedule(new IndexingTask(Indexer.this, newsList, EventType.UPDATE));
              else
                new IndexingTask(Indexer.this, newsList, EventType.UPDATE).run(new NullProgressMonitor());
            }
          }
        }
      }
    };

    /* We register listeners as part of initialisation, we must use InternalOwl */
    InternalOwl.getDefault().getPersistenceService().getDAOService().getNewsDAO().addEntityListener(fNewsListener);
    InternalOwl.getDefault().getPersistenceService().getDAOService().getLabelDAO().addEntityListener(fLabelListener);
  }

  private void handleEntityEvents(Set<NewsEvent> events, EventType eventType) {
    if (!InternalOwl.TESTING)
      fJobQueue.schedule(new IndexingTask(Indexer.this, events, eventType));
    else
      new IndexingTask(Indexer.this, events, eventType).run(new NullProgressMonitor());
  }

  private void handleEntitiesAdded(Set<NewsEvent> events) {
    handleEntityEvents(events, EventType.PERSIST);
  }

  private void handleEntitiesUpdated(Set<NewsEvent> events) {
    handleEntityEvents(events, EventType.UPDATE);
  }

  private void handleEntitiesDeleted(Set<NewsEvent> events) {
    handleEntityEvents(events, EventType.REMOVE);
  }

  private void unregisterListeners() {
    if (fNewsListener != null)
      Owl.getPersistenceService().getDAOService().getNewsDAO().removeEntityListener(fNewsListener);

    if (fLabelListener != null)
      Owl.getPersistenceService().getDAOService().getLabelDAO().removeEntityListener(fLabelListener);

    fNewsListener = null;
  }

  private IndexWriter createIndexWriter(Directory directory, boolean create) throws IOException {
    IndexWriter indexWriter = new IndexWriter(directory, false, createAnalyzer(), create);
    indexWriter.setMergeFactor(6);
    fFlushRequired = false;
    return indexWriter;
  }

  private Term createTerm(ModelReference reference) {
    String value = String.valueOf(reference.getId());
    return new Term(SearchDocument.ENTITY_ID_TEXT, value);
  }

  private Term createTerm(IEntity entity) {
    String value = String.valueOf(entity.getId());
    return new Term(SearchDocument.ENTITY_ID_TEXT, value);
  }

  private void dispose() throws PersistenceException {
    if (fIndexWriter == null)
      return;

    try {
      fIndexWriter.close();
    } catch (IOException e) {
      throw new PersistenceException(e);
    }
    fIndexWriter = null;
    fFlushRequired = false;
  }

  private static void saveCommittedNews(boolean sync, final EntityIdsByEventType uncommittedNews) {
    if (uncommittedNews.size() == 0)
      return;

    if (sync || InternalOwl.TESTING)
      doSaveCommittedNews(uncommittedNews);
    else {
      Job job = new Job("Save indexer news that have been committed") {
        @Override
        protected IStatus run(IProgressMonitor monitor) {
          doSaveCommittedNews(uncommittedNews);
          return Status.OK_STATUS;
        }
      };
      job.setSystem(true);
      job.schedule();
    }
  }

  private static void doSaveCommittedNews(EntityIdsByEventType uncommittedNews) {
    EntitiesToBeIndexedDAOImpl dao = DBHelper.getEntitiesToBeIndexedDAO();
    if (dao != null) {
      EntityIdsByEventType newsToBeIndexed = dao.load();

      /*
       * null here means that there was a fast shutdown and the database is
       * already closed. We'll just re-index on start-up.
       */
      if (newsToBeIndexed != null) {
        newsToBeIndexed.removeAll(uncommittedNews.getPersistedEntityIds(), uncommittedNews.getUpdatedEntityIds(), uncommittedNews.getRemovedEntityIds());
        dao.save(newsToBeIndexed);
      }
    }
  }
}