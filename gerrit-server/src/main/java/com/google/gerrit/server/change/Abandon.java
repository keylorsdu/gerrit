// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class Abandon implements RestModifyView<ChangeResource, AbandonInput>,
    UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Abandon.class);

  private final ChangeHooks hooks;
  private final AbandonedSender.Factory abandonedSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final ChangeIndexer indexer;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;

  @Inject
  Abandon(ChangeHooks hooks,
      AbandonedSender.Factory abandonedSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      ChangeIndexer indexer,
      ChangeUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil) {
    this.hooks = hooks;
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.indexer = indexer;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, AbandonInput input)
      throws AuthException, ResourceConflictException, OrmException,
      IOException {
    ChangeControl control = req.getControl();
    IdentifiedUser caller = (IdentifiedUser) control.getCurrentUser();
    Change change = req.getChange();
    if (!control.canAbandon()) {
      throw new AuthException("abandon not permitted");
    } else if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + status(change));
    } else if (change.getStatus() == Change.Status.DRAFT) {
      throw new ResourceConflictException("draft changes cannot be abandoned");
    }
    change = abandon(control, input.message, caller.getAccount());
    return json.create(ChangeJson.NO_OPTIONS).format(change);
  }

  public Change abandon(ChangeControl control,
      String msgTxt, Account acc) throws ResourceConflictException,
      OrmException, IOException {
    Change change;
    ChangeMessage message;
    ChangeUpdate update;
    Change.Id changeId = control.getChange().getId();
    ReviewDb db = dbProvider.get();
    db.changes().beginTransaction(changeId);
    try {
      change = db.changes().atomicUpdate(
        changeId,
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isOpen()) {
              change.setStatus(Change.Status.ABANDONED);
              ChangeUtil.updated(change);
              return change;
            }
            return null;
          }
        });
      if (change == null) {
        throw new ResourceConflictException("change is "
            + status(db.changes().get(changeId)));
      }

      //TODO(yyonas): atomic update was not propagated
      update = updateFactory.create(control, change.getLastUpdatedOn());
      message = newMessage(msgTxt, acc != null ? acc.getId() : null, change);
      cmUtil.addChangeMessage(db, update, message);
      db.commit();
    } finally {
      db.rollback();
    }
    update.commit();

    indexer.index(db, change);
    try {
      ReplyToChangeSender cm = abandonedSenderFactory.create(change.getId());
      if (acc != null) {
        cm.setFrom(acc.getId());
      }
      cm.setChangeMessage(message);
      cm.send();
    } catch (Exception e) {
      log.error("Cannot email update for change " + change.getChangeId(), e);
    }
    hooks.doChangeAbandonedHook(change,
        acc,
        db.patchSets().get(change.currentPatchSetId()),
        Strings.emptyToNull(msgTxt),
        db);
    return change;
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Abandon")
      .setTitle("Abandon the change")
      .setVisible(resource.getChange().getStatus().isOpen()
          && resource.getChange().getStatus() != Change.Status.DRAFT
          && resource.getControl().canAbandon());
  }

  private ChangeMessage newMessage(String msgTxt, Account.Id accId,
      Change change) throws OrmException {
    StringBuilder msg = new StringBuilder();
    msg.append("Abandoned");
    if (!Strings.nullToEmpty(msgTxt).trim().isEmpty()) {
      msg.append("\n\n");
      msg.append(msgTxt.trim());
    }

    ChangeMessage message = new ChangeMessage(
        new ChangeMessage.Key(
            change.getId(),
            ChangeUtil.messageUUID(dbProvider.get())),
        accId,
        change.getLastUpdatedOn(),
        change.currentPatchSetId());
    message.setMessage(msg.toString());
    return message;
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
