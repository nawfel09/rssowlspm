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

package org.rssowl.ui.internal.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.IMark;
import org.rssowl.core.persist.dao.DynamicDAO;
import org.rssowl.core.persist.dao.IPreferenceDAO;
import org.rssowl.core.persist.reference.FolderReference;
import org.rssowl.core.persist.service.PersistenceException;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.dialogs.bookmark.CreateBookmarkWizard;
import org.rssowl.ui.internal.views.explorer.BookMarkExplorer;

/**
 * @author bpasero
 */
public class NewBookMarkAction implements IWorkbenchWindowActionDelegate, IObjectActionDelegate {
  private Shell fShell;
  private IFolder fParent;
  private IMark fPosition;
  private String fPreSetLink;

  /** Keep for Reflection */
  public NewBookMarkAction() {
    this(null, null, null);
  }

  /**
   * @param shell
   * @param parent
   * @param position
   */
  public NewBookMarkAction(Shell shell, IFolder parent, IMark position) {
    this(shell, parent, position, null);
  }

  /**
   * @param shell
   * @param parent
   * @param position
   * @param preSetLink
   */
  public NewBookMarkAction(Shell shell, IFolder parent, IMark position, String preSetLink) {
    fShell = shell;
    fParent = parent;
    fPreSetLink = preSetLink;
    fPosition = position;
  }

  /*
   * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#dispose()
   */
  public void dispose() {}

  /*
   * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#init(org.eclipse.ui.IWorkbenchWindow)
   */
  public void init(IWorkbenchWindow window) {
    fShell = window.getShell();
  }

  /*
   * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
   */
  public void run(IAction action) {

    /* Get the parent Folder */
    IFolder parent = getParent();

    /* Show Dialog */
    CreateBookmarkWizard wizard = new CreateBookmarkWizard(parent, fPosition, fPreSetLink);
    WizardDialog dialog = new WizardDialog(fShell, wizard);
    dialog.create();
    dialog.open();
  }

  /*
   * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction,
   * org.eclipse.jface.viewers.ISelection)
   */
  public void selectionChanged(IAction action, ISelection selection) {

    /* Delete the old Selection */
    fParent = null;
    fPosition = null;

    /* Check Selection */
    if (selection instanceof IStructuredSelection) {
      IStructuredSelection structSel = (IStructuredSelection) selection;
      if (!structSel.isEmpty()) {
        Object firstElement = structSel.getFirstElement();
        if (firstElement instanceof IFolder)
          fParent = (IFolder) firstElement;
        else if (firstElement instanceof IMark) {
          fParent = ((IMark) firstElement).getParent();
          fPosition = ((IMark) firstElement);
        }
      }
    }
  }

  /*
   * @see org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.action.IAction,
   * org.eclipse.ui.IWorkbenchPart)
   */
  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    fShell = targetPart.getSite().getShell();
  }

  private IFolder getParent() throws PersistenceException {
    String selectedBookMarkSetPref = BookMarkExplorer.getSelectedBookMarkSetPref(OwlUI.getWindow());
    Long selectedRootFolderID = DynamicDAO.getDAO(IPreferenceDAO.class).load(selectedBookMarkSetPref).getLong();

    /* Check if available Parent is still valid */
    if (fParent != null) {
      if (hasParent(fParent, new FolderReference(selectedRootFolderID)))
        return fParent;
    }

    /* Otherwise return visible root-folder */
    return new FolderReference(selectedRootFolderID).resolve();
  }

  private boolean hasParent(IFolder folder, FolderReference folderRef) {
    if (folder == null)
      return false;

    if (folderRef.references(folder))
      return true;

    return hasParent(folder.getParent(), folderRef);
  }
}