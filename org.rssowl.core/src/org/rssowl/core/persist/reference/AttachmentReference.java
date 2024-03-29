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

package org.rssowl.core.persist.reference;

import org.rssowl.core.persist.IAttachment;
import org.rssowl.core.persist.service.PersistenceException;

/**
 * Implementation of the <code>ModelReference</code> for the Type
 * <code>IAttachment</code>.
 * 
 * @author bpasero
 */
public final class AttachmentReference extends ModelReference {

  /**
   * Instantiates a new leightweight reference. Any resolve()-call will be
   * passed to the <code>IModelDAO</code> to load the heavyweight type from
   * the persistance layer.
   * 
   * @param id The ID of the type to use for loading the type from the
   * persistance layer.
   */
  public AttachmentReference(long id) {
    super(id, IAttachment.class);
  }
  
  @Override
  public IAttachment resolve() throws PersistenceException {
    return (IAttachment) super.resolve();
  }
}