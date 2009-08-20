// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetApproval;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.Change.Status;
import com.google.gerrit.pgm.CmdLineParser;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.FieldSetter;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.OptionHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class ApproveCommand extends BaseCommand {
  public class ApproveCmdLineParser extends CmdLineParser {

    public ApproveCmdLineParser(final Object bean) {
      super(bean);
    }
  }

  protected final CmdLineParser newCmdLineParserInstance(final Object bean) {
    Field f = null;
    ApproveCmdLineParser parser = new ApproveCmdLineParser(bean);

    try {
      f = CmdOption.class.getField("value");

      for (CmdOption c : optionList) {
        parser.addOption(new FieldSetter(c, f), c);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return parser;
  }


  public class CmdOption implements Option {
    private String metaVar;
    private boolean multiValued;
    private String name;
    private boolean required;
    private String usage;

    private String approvalKey;
    private Short approvalMax;
    private Short approvalMin;
    private String descrName;

    public Short value;

    public CmdOption(final String name, final String usage, final String key,
        final Short min, final Short max, final String descrName) {
      this.name = name;
      this.usage = usage;

      this.metaVar = "";
      this.multiValued = false;
      this.required = false;
      this.value = null;

      this.approvalKey = key;
      this.approvalMax = max;
      this.approvalMin = min;
      this.descrName = descrName;
    }

    @Override
    public final String[] aliases() {
      return new String[0];
    }

    @Override
    public final Class<? extends OptionHandler> handler() {
      return OptionHandler.class;
    }

    @Override
    public final String metaVar() {
      return metaVar;
    }

    @Override
    public final boolean multiValued() {
      return multiValued;
    }

    @Override
    public final String name() {
      return name;
    }

    @Override
    public final boolean required() {
      return required;
    }

    @Override
    public final String usage() {
      return usage;
    }

    public final Short value() {
      return value;
    }

    public final String approvalKey() {
      return approvalKey;
    }

    public final Short approvalMax() {
      return approvalMax;
    }

    public final Short approvalMin() {
      return approvalMin;
    }

    public final String descrName() {
      return descrName;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return null;
    }
  }

  @Argument(index = 0, metaVar = "CHANGE-ID", required = true, usage = "Id of changeset that is being scored")
  private int changeId;

  @Option(name = "--comment", usage = "Comment for the change", metaVar = "COMMENT")
  private String changeComment;

  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private CommentSender.Factory commentSenderFactory;

  @Inject
  private PatchSetInfoFactory patchSetInfoFactory;


  private static final int CMD_ERR = 3;
  private List<CmdOption> optionList;


  @Override
  public final void start() throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        getApprovalNames();
        parseCommandLine();
        PrintWriter p = toPrintWriter(out);
        validateParameters();

        try {
          final Transaction txn = db.beginTransaction();
          final Change.Id cid = new Change.Id(changeId);
          final Change c = db.changes().get(cid);

          StringBuffer sb = new StringBuffer();
          sb.append("Patch Set: ");
          sb.append(c.currentPatchSetId().get());
          sb.append(" ");

          for (CmdOption co : optionList) {
            String message = "";
            Short score = co.value();

            ApprovalCategory.Id category =
                new ApprovalCategory.Id(co.approvalKey());
            if (co.value() != null) {
              addApproval(c, category, co.value(), txn);
            } else {
              PatchSetApproval.Key psaKey =
                new PatchSetApproval.Key(c.currentPatchSetId(), currentUser
                    .getAccountId(), category);

              PatchSetApproval psa = db.patchSetApprovals().get(psaKey);
              if (psa == null) {
                score = null;
              } else {
                score = psa.getValue();
              }
            }

            if(score != null) {
              message = db.approvalCategoryValues().get(
                  new ApprovalCategoryValue.Id(category, score)).getName();
            }

            sb.append(" " + message + ";");
          }

          sb.deleteCharAt(sb.length() - 1);
          sb.append("\n\n");

          if (changeComment != null) {
            sb.append(changeComment);
          }

          String uuid = ChangeUtil.messageUUID(db);
          ChangeMessage cm =
              new ChangeMessage(new ChangeMessage.Key(cid, uuid), currentUser
                  .getAccountId());
          cm.setMessage(sb.toString());
          db.changeMessages().insert(Collections.singleton(cm), txn);
          ChangeUtil.updated(c);
          db.changes().update(Collections.singleton(c), txn);
          txn.commit();
          sendMail(c, c.currentPatchSetId(), cm);

          p.print(sb.toString() + "\n");
          p.flush();
        } catch (OrmException e) {
          throw new Failure(CMD_ERR, "Error accessing the database\n"
              + "Detailed message:\n" + e.getMessage());
        } catch (EmailException e) {
          throw new Failure(CMD_ERR, "Error when trying to send email\n"
              + "Detailed message:\n" + e.getMessage());
        } catch (Exception e) {
          throw new Failure(CMD_ERR, "Received an error\n"
              + "Detailed message:\n" + e.getMessage());
        }
      }
    });
  }

  private void sendMail(final Change c, final PatchSet.Id psid,
      final ChangeMessage message) throws PatchSetInfoNotAvailableException,
      EmailException, OrmException {
    PatchSet ps = db.patchSets().get(psid);
    final CommentSender cm;
    cm = commentSenderFactory.create(c);
    cm.setFrom(currentUser.getAccountId());
    cm.setPatchSet(ps, patchSetInfoFactory.get(psid));
    cm.setChangeMessage(message);
    cm.setReviewDb(db);
    cm.send();
  }

  private void addApproval(final Change c, final ApprovalCategory.Id cat,
      final short score, final Transaction txn) throws OrmException {
    PatchSetApproval.Key psaKey =
        new PatchSetApproval.Key(c.currentPatchSetId(), currentUser
            .getAccountId(), cat);

    PatchSetApproval psa = db.patchSetApprovals().get(psaKey);

    if (psa == null) {
      psa = new PatchSetApproval(psaKey, score);
      db.patchSetApprovals().insert(Collections.singleton(psa), txn);
    } else {
      psa.setGranted();
      psa.setValue(score);
      db.patchSetApprovals().update(Collections.singleton(psa), txn);
    }
  }


  private void validateParameters() throws OrmException, Failure {
    Change.Id cid = new Change.Id(changeId);
    Change c = db.changes().get(cid);
    if (c == null) {
      throw new Failure(CMD_ERR, "Invalid change id");
    }

    if (c.getStatus() != Status.NEW) {
      throw new Failure(CMD_ERR, "Change is in the wrong state.");
    }

    boolean noOptions = true;

    for (CmdOption co : optionList) {
      if (co.value() == null && co.required()) {
        throw new Failure(CMD_ERR, co.name() + " is required");
      }

      if (co.value != null) {
        noOptions = false;
        if (co.value() > co.approvalMax() || co.value() < co.approvalMin()) {
          throw new Failure(CMD_ERR, co.descrName() + " score is not valid ("
              + co.approvalMin().toString() + ".." + co.approvalMax() + ")");
        }
      }
    }

    if(noOptions && changeComment == null) {
      throw new Failure(CMD_ERR, "Must provide at 1 option");
    }
  }

  private void getApprovalNames() throws OrmException {
    SortedMap<Short, String> acvMap = new TreeMap<Short, String>();
    optionList = new ArrayList<CmdOption>();
    ResultSet<ApprovalCategory> rs = db.approvalCategories().all();

    for (ApprovalCategory c : rs) {
      if (c.getFunctionName().equals("MaxWithBlock")) {
        ResultSet<ApprovalCategoryValue> acvrs =
            db.approvalCategoryValues().byCategory(c.getId());
        short min = Short.MAX_VALUE;
        short max = Short.MIN_VALUE;
        String usage = "";

        for (ApprovalCategoryValue acv : acvrs) {
          if (min > acv.getValue()) {
            min = acv.getValue();
          }
          if (max < acv.getValue()) {
            max = acv.getValue();
          }

          acvMap.put(acv.getValue(), acv.getName());
        }

        usage += "Score for " + c.getName() + "\n";

        // This is to make sure that the values are in sorted order.
        Iterator<Short> i = acvMap.keySet().iterator();
        while (i.hasNext()) {
          Short key = i.next();
          usage += String.format("%4d", key) + "  -  " + acvMap.get(key) + "\n";
        }

        optionList.add(new CmdOption("--" + c.getName().toLowerCase()
            .replace(' ', '-'), usage, c.getId().get(), min, max, c.getName()));

        usage = "";
        acvMap.clear();
      }
    }
  }
}
