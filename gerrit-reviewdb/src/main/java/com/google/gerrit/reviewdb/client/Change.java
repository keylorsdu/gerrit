// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.RowVersion;
import com.google.gwtorm.client.StringKey;

import java.sql.Timestamp;

/**
 * A change proposed to be merged into a {@link Branch}.
 * <p>
 * The data graph rooted below a Change can be quite complex:
 *
 * <pre>
 *   {@link Change}
 *     |
 *     +- {@link ChangeMessage}: &quot;cover letter&quot; or general comment.
 *     |
 *     +- {@link PatchSet}: a single variant of this change.
 *          |
 *          +- {@link PatchSetApproval}: a +/- vote on the change's current state.
 *          |
 *          +- {@link PatchSetAncestor}: parents of this change's commit.
 *          |
 *          +- {@link PatchLineComment}: comment about a specific line
 * </pre>
 * <p>
 * <h5>PatchSets</h5>
 * <p>
 * Every change has at least one PatchSet. A change starts out with one
 * PatchSet, the initial proposal put forth by the change owner. This
 * {@link Account} is usually also listed as the author and committer in the
 * PatchSetInfo.
 * <p>
 * The {@link PatchSetAncestor} entities are a mirror of the Git commit
 * metadata, providing access to the information without needing direct
 * accessing Git. These entities are actually legacy artifacts from Gerrit 1.x
 * and could be removed, replaced by direct RevCommit access.
 * <p>
 * Each PatchSet contains zero or more Patch records, detailing the file paths
 * impacted by the change (otherwise known as, the file paths the author
 * added/deleted/modified). Sometimes a merge commit can contain zero patches,
 * if the merge has no conflicts, or has no impact other than to cut off a line
 * of development.
 * <p>
 * Each PatchLineComment is a draft or a published comment about a single line
 * of the associated file. These are the inline comment entities created by
 * users as they perform a review.
 * <p>
 * When additional PatchSets appear under a change, these PatchSets reference
 * <i>replacement</i> commits; alternative commits that could be made to the
 * project instead of the original commit referenced by the first PatchSet.
 * <p>
 * A change has at most one current PatchSet. The current PatchSet is updated
 * when a new replacement PatchSet is uploaded. When a change is submitted, the
 * current patch set is what is merged into the destination branch.
 * <p>
 * <h5>ChangeMessage</h5>
 * <p>
 * The ChangeMessage entity is a general free-form comment about the whole
 * change, rather than PatchLineComment's file and line specific context. The
 * ChangeMessage appears at the start of any email generated by Gerrit, and is
 * shown on the change overview page, rather than in a file-specific context.
 * Users often use this entity to describe general remarks about the overall
 * concept proposed by the change.
 * <p>
 * <h5>PatchSetApproval</h5>
 * <p>
 * PatchSetApproval entities exist to fill in the <i>cells</i> of the approvals
 * table in the web UI. That is, a single PatchSetApproval record's key is the
 * tuple {@code (PatchSet,Account,ApprovalCategory)}. Each PatchSetApproval
 * carries with it a small score value, typically within the range -2..+2.
 * <p>
 * If an Account has created only PatchSetApprovals with a score value of 0, the
 * Change shows in their dashboard, and they are said to be CC'd (carbon copied)
 * on the Change, but are not a direct reviewer. This often happens when an
 * account was specified at upload time with the {@code --cc} command line flag,
 * or have published comments, but left the approval scores at 0 ("No Score").
 * <p>
 * If an Account has one or more PatchSetApprovals with a score != 0, the Change
 * shows in their dashboard, and they are said to be an active reviewer. Such
 * individuals are highlighted when notice of a replacement patch set is sent,
 * or when notice of the change submission occurs.
 */
public final class Change {
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }

    /** Parse a Change.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }

    public static Id fromRef(final String ref) {
      return PatchSet.Id.fromRef(ref).getParentKey();
    }
  }

  /** Globally unique identification of this change. */
  public static class Key extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = 60)
    protected String id;

    protected Key() {
    }

    public Key(final String id) {
      this.id = id;
    }

    @Override
    public String get() {
      return id;
    }

    @Override
    protected void set(String newValue) {
      id = newValue;
    }

    /** Construct a key that is after all keys prefixed by this key. */
    public Key max() {
      final StringBuilder revEnd = new StringBuilder(get().length() + 1);
      revEnd.append(get());
      revEnd.append('\u9fa5');
      return new Key(revEnd.toString());
    }

    /** Obtain a shorter version of this key string, using a leading prefix. */
    public String abbreviate() {
      final String s = get();
      return s.substring(0, Math.min(s.length(), 9));
    }

    /** Parse a Change.Key out of a string representation. */
    public static Key parse(final String str) {
      final Key r = new Key();
      r.fromString(str);
      return r;
    }
  }

  /** Minimum database status constant for an open change. */
  private static final char MIN_OPEN = 'a';
  /** Database constant for {@link Status#NEW}. */
  public static final char STATUS_NEW = 'n';
  /** Database constant for {@link Status#SUBMITTED}. */
  public static final char STATUS_SUBMITTED = 's';
  /** Database constant for {@link Status#DRAFT}. */
  public static final char STATUS_DRAFT = 'd';
  /** Database constant for {@link Status#WORKINPROGRESS} */
  private static final char STATUS_WIP = 'w';
  /** Maximum database status constant for an open change. */
  private static final char MAX_OPEN = 'z';

  /** Database constant for {@link Status#MERGED}. */
  public static final char STATUS_MERGED = 'M';

  /**
   * Current state within the basic workflow of the change.
   *
   * <p>
   * Within the database, lower case codes ('a'..'z') indicate a change that is
   * still open, and that can be modified/refined further, while upper case
   * codes ('A'..'Z') indicate a change that is closed and cannot be further
   * modified.
   * */
  public static enum Status {
    /**
     * Change is open and pending review, or review is in progress.
     *
     * <p>
     * This is the default state assigned to a change when it is first created
     * in the database. A change stays in the NEW state throughout its review
     * cycle, until the change is submitted or abandoned.
     *
     * <p>
     * Changes in the NEW state can be moved to:
     * <ul>
     * <li>{@link #SUBMITTED} - when the Submit Patch Set action is used;
     * <li>{@link #ABANDONED} - when the Abandon action is used.
     * </ul>
     */
    NEW(STATUS_NEW),

    /**
     * Change is open, but has been submitted to the merge queue.
     *
     * <p>
     * A change enters the SUBMITTED state when an authorized user presses the
     * "submit" action through the web UI, requesting that Gerrit merge the
     * change's current patch set into the destination branch.
     *
     * <p>
     * Typically a change resides in the SUBMITTED for only a brief sub-second
     * period while the merge queue fires and the destination branch is updated.
     * However, if a dependency commit (a {@link PatchSetAncestor}, directly or
     * transitively) is not yet merged into the branch, the change will hang in
     * the SUBMITTED state indefinately.
     *
     * <p>
     * Changes in the SUBMITTED state can be moved to:
     * <ul>
     * <li>{@link #NEW} - when a replacement patch set is supplied, OR when a
     * merge conflict is detected;
     * <li>{@link #MERGED} - when the change has been successfully merged into
     * the destination branch;
     * <li>{@link #ABANDONED} - when the Abandon action is used.
     * </ul>
     */
    SUBMITTED(STATUS_SUBMITTED),

    /**
     * Change is a draft change that only consists of draft patchsets.
     *
     * <p>
     * This is a change that is not meant to be submitted or reviewed yet. If
     * the uploader publishes the change, it becomes a NEW change.
     * Publishing is a one-way action, a change cannot return to DRAFT status.
     * Draft changes are only visible to the uploader and those explicitly
     * added as reviewers.
     *
     * <p>
     * Changes in the DRAFT state can be moved to:
     * <ul>
     * <li>{@link #NEW} - when the change is published, it becomes a new change;
     * </ul>
     */
    DRAFT(STATUS_DRAFT),

    /**
     * Change is closed, and submitted to its destination branch.
     *
     * <p>
     * Once a change has been merged, it cannot be further modified by adding a
     * replacement patch set. Draft comments however may be published,
     * supporting a post-submit review.
     */
    MERGED(STATUS_MERGED),

    /**
     * Change is still open, but a work in progress.
     *
     * <p>
     * The change owner, or someone with approval authority, has set a change from
     * {@link #NEW} to this state. It implies that there is more work to be done,
     * but the change will not show up in any review lists until a new patch set
     * is pushed.
     */
    WORKINPROGRESS(STATUS_WIP),

    /**
     * Change is closed, but was not submitted to its destination branch.
     *
     * <p>
     * Once a change has been abandoned, it cannot be further modified by adding
     * a replacement patch set, and it cannot be merged. Draft comments however
     * may be published, permitting reviewers to send constructive feedback.
     */
    ABANDONED('A');

    private final char code;
    private final boolean closed;

    private Status(final char c) {
      code = c;
      closed = !(MIN_OPEN <= c && c <= MAX_OPEN);
    }

    public char getCode() {
      return code;
    }

    public boolean isOpen() {
      return !closed;
    }

    public boolean isClosed() {
      return closed;
    }

    public static Status forCode(final char c) {
      for (final Status s : Status.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  /** Locally assigned unique identifier of the change */
  @Column(id = 1)
  protected Id changeId;

  /** Globally assigned unique identifier of the change */
  @Column(id = 2)
  protected Key changeKey;

  /** optimistic locking */
  @Column(id = 3)
  @RowVersion
  protected int rowVersion;

  /** When this change was first introduced into the database. */
  @Column(id = 4)
  protected Timestamp createdOn;

  /**
   * When was a meaningful modification last made to this record's data
   * <p>
   * Note, this update timestamp includes its children.
   */
  @Column(id = 5)
  protected Timestamp lastUpdatedOn;

  /** A {@link #lastUpdatedOn} ASC,{@link #changeId} ASC for sorting. */
  @Column(id = 6, length = 16)
  protected String sortKey;

  @Column(id = 7, name = "owner_account_id")
  protected Account.Id owner;

  /** The branch (and project) this change merges into. */
  @Column(id = 8)
  protected Branch.NameKey dest;

  /** Is the change currently open? Set to {@link #status}.isOpen(). */
  @Column(id = 9)
  protected boolean open;

  /** Current state code; see {@link Status}. */
  @Column(id = 10)
  protected char status;

  /** The total number of {@link PatchSet} children in this Change. */
  @Column(id = 11)
  protected int nbrPatchSets;

  /** The current patch set. */
  @Column(id = 12)
  protected int currentPatchSetId;

  /** Subject from the current patch set. */
  @Column(id = 13)
  protected String subject;

  /** Topic name assigned by the user, if any. */
  @Column(id = 14, notNull = false)
  protected String topic;

  /**
   * Null if the change has never been tested.
   * Empty if it has been tested but against a branch that does
   * not exist.
   */
  @Column(id = 15, notNull = false)
  protected RevId lastSha1MergeTested;

  @Column(id = 16)
  protected boolean mergeable;

  protected Change() {
  }

  public Change(final Change.Key newKey, final Change.Id newId,
      final Account.Id ownedBy, final Branch.NameKey forBranch) {
    changeKey = newKey;
    changeId = newId;
    createdOn = new Timestamp(System.currentTimeMillis());
    lastUpdatedOn = createdOn;
    owner = ownedBy;
    dest = forBranch;
    setStatus(Status.NEW);
    setLastSha1MergeTested(null);
  }

  /** Legacy 32 bit integer identity for a change. */
  public Change.Id getId() {
    return changeId;
  }

  /** Legacy 32 bit integer identity for a change. */
  public int getChangeId() {
    return changeId.get();
  }

  /** The Change-Id tag out of the initial commit, or a natural key. */
  public Change.Key getKey() {
    return changeKey;
  }

  public void setKey(final Change.Key k) {
    changeKey = k;
  }

  public Timestamp getCreatedOn() {
    return createdOn;
  }

  public Timestamp getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  public void resetLastUpdatedOn() {
    lastUpdatedOn = new Timestamp(System.currentTimeMillis());
  }

  public int getNumberOfPatchSets() {
    return nbrPatchSets;
  }

  public String getSortKey() {
    return sortKey;
  }

  public void setSortKey(final String newSortKey) {
    sortKey = newSortKey;
  }

  public Account.Id getOwner() {
    return owner;
  }

  public Branch.NameKey getDest() {
    return dest;
  }

  public Project.NameKey getProject() {
    return dest.getParentKey();
  }

  public String getSubject() {
    return subject;
  }

  /** Get the id of the most current {@link PatchSet} in this change. */
  public PatchSet.Id currentPatchSetId() {
    if (currentPatchSetId > 0) {
      return new PatchSet.Id(changeId, currentPatchSetId);
    }
    return null;
  }

  public void setCurrentPatchSet(final PatchSetInfo ps) {
    currentPatchSetId = ps.getKey().get();
    subject = ps.getSubject();
  }

  /**
   * Allocate a new PatchSet id within this change.
   * <p>
   * <b>Note: This makes the change dirty. Call update() after.</b>
   */
  public void nextPatchSetId() {
    ++nbrPatchSets;
  }

  /**
   * Reverts to an older PatchSet id within this change.
   * <p>
   * <b>Note: This makes the change dirty. Call update() after.</b>
   */
  public void removeLastPatchSetId() {
    --nbrPatchSets;
  }

  public PatchSet.Id currPatchSetId() {
    return new PatchSet.Id(changeId, nbrPatchSets);
  }

  public Status getStatus() {
    return Status.forCode(status);
  }

  public void setStatus(final Status newStatus) {
    open = newStatus.isOpen();
    status = newStatus.getCode();
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public RevId getLastSha1MergeTested() {
    return lastSha1MergeTested;
  }

  public void setLastSha1MergeTested(RevId lastSha1MergeTested) {
    this.lastSha1MergeTested = lastSha1MergeTested;
  }

  public boolean isMergeable() {
    return mergeable;
  }

  public void setMergeable(boolean mergeable) {
    this.mergeable = mergeable;
  }
}
