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

package org.rssowl.ui.internal;

import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.rssowl.core.persist.IMark;
import org.rssowl.core.persist.INewsMark;
import org.rssowl.ui.internal.editors.feed.FeedView;
import org.rssowl.ui.internal.editors.feed.FeedViewInput;
import org.rssowl.ui.internal.util.EditorUtils;

import java.util.List;

/**
 * The <code>EditorDNDImpl</code> validates and handles drop-operations
 * resulting from a DND.
 *
 * @author bpasero
 */
public class EditorDNDImpl extends DropTargetAdapter {

  /*
   * @see org.eclipse.swt.dnd.DropTargetAdapter#dragOver(org.eclipse.swt.dnd.DropTargetEvent)
   */
  @Override
  public void dragOver(DropTargetEvent event) {

    /* Local Selection Transfer */
    if (LocalSelectionTransfer.getTransfer().isSupportedType(event.currentDataType)) {
      ISelection selection = LocalSelectionTransfer.getTransfer().getSelection();

      /* Check that Selection only contains IMarks */
      if (selection instanceof IStructuredSelection) {
        List< ? > selectedObjects = ((IStructuredSelection) selection).toList();
        for (Object selectedObject : selectedObjects) {
          if (!(selectedObject instanceof IMark))
            return;
        }
      }

      /* Support Operation */
      event.detail = DND.DROP_COPY;
    }
  }

  /*
   * @see org.eclipse.swt.dnd.DropTargetAdapter#drop(org.eclipse.swt.dnd.DropTargetEvent)
   */
  @Override
  public void drop(DropTargetEvent event) {

    /* Local Selection Transfer */
    if (LocalSelectionTransfer.getTransfer().isSupportedType(event.currentDataType)) {
      ISelection selection = LocalSelectionTransfer.getTransfer().getSelection();
      if (selection instanceof IStructuredSelection) {
        List< ? > selectedObjects = ((IStructuredSelection) selection).toList();
        drop(selectedObjects);
      }
    }
  }

  private void drop(List< ? > objects) {
    int openedEditors = 0;
    int maxOpenEditors = EditorUtils.getOpenEditorLimit();
    boolean activateEditor = OpenStrategy.activateOnOpen();

    /* For each dropped Object */
    for (int i = 0; i < objects.size() && openedEditors < maxOpenEditors; i++) {
      Object obj = objects.get(i);

      /* This is an INewsMark being dropped - display it */
      if (obj instanceof INewsMark) {
        INewsMark mark = ((INewsMark) obj);
        try {
          IWorkbenchPage page = OwlUI.getPageAtCursor();
          if (page != null) {
            page.openEditor(new FeedViewInput(mark), FeedView.ID, activateEditor);
            openedEditors++;
          }
        } catch (PartInitException e) {
          Activator.getDefault().getLog().log(e.getStatus());
        }
      }
    }
  }
}