## Objective

The objective of this proposal is to provide a workflow that supports a private
self-review phase before notifying reviewers. A change owner that opts into this
workflow is able to fully stage their change -- including adding reviewers --
without inviting attention until they explicitly request for code review to
*start*.

To support this workflow without confusing the user, PolyGerrit should make the
following clear:

*   Whether reviewers have been notified about the current patch set
*   Whether an action the user is taking is going to notify reviewers
*   How to publish a patch set and thereby notify reviewers

## Background

The existing Gerrit workflow eagerly notifies reviewers whenever they are added
to a change or whenever a new revision is uploaded to a change they are
reviewing. Users and tools have some control over this, as most API calls that
emit notifications (as well as pushes to the magic branch) support a *notify*
option. The value given to this option may limit or completely suppress the
scope of notifications for the given operation.

Users coming from other code review systems may already be accustomed to a
workflow with deferred notifications. A case in point is Rietveld, a system from
which thousands of developers are migrating to Gerrit. In Rietveld, a change
author uploads a new change or revision, but no one sees it or is notified about
it until the author clicks the "Start Review" button in the Rietveld UI.

Like Rietveld, this proposal recommends offering this workflow on a patch set
by patch set basis. That is, a revision to an existing change would also be
hidden and unannounced until the change owner publishes it.

We have existing users who prefer having this proposed self-review phase. Some
have made use of the *notify* option to emulate it. For example, Chromium
projects have their own upload tool that wraps the underlying git push command
to the corresponding magic branch. By default it uses *notify=NONE* to
completely suppress notifications, and it also eagerly adds reviewers to the
change if the author has selected them at this point. Since no one is notified
by default, it is up to the change author to trigger initial notifications by
opening the change in PolyGerrit and making a trivial reply.

The UI does not support these users in this task. PolyGerrit has no way of
determining whether notifications have been sent out to existing reviewers on a
given change. The user therefore may not know that no one has been notified.

Many of these changes are also abandoned during their initial self-review phase.
Subscribers to existing watch topics have no way of filtering out changes that
never reach the stage of peer review.

Many of the concerns outlined in this background section come from our issue
tracker:

*   [Issue 4390](https://bugs.chromium.org/p/gerrit/issues/detail?id=4390) calls
    for a new notification type for changes that actually receive review
*   [Issue 3798](https://bugs.chromium.org/p/gerrit/issues/detail?id=3798) would
    also be satisfied by this new notification type
*   [Issue 4489](https://bugs.chromium.org/p/gerrit/issues/detail?id=4489) shows
    how users currently work around the lack of this status, and the confusion
    that ensues
*   [Issue 4673](https://bugs.chromium.org/p/gerrit/issues/detail?id=4673)
    discusses the excess of emails that go out from Gerrit, as compared to
    Rietveld

This proposal sets out to resolve all of the above.

## Requirements

We don't want the notification semantics for existing users who aren't using
this workflow to change at all. This new workflow involving deferred
notifications must be opted into on a per site/project/user/change basis.

The reviewable status of a patch set needs to be made clear in the PolyGerrit
UI. A user should always clearly understand whether reviewers have been notified
about their change or revision. The UI should always position itself to lead the
change author toward taking the next step toward getting their change merged.

A patch set or change that is not yet reviewable should still be accessible over
the REST API. This is in order to support existing continuous integration
systems, which must remain available to change authors even during their private
self-review phase.

## Design Ideas

We will add a nullable *reviewable* timestamp to patch sets in ReviewDb and
NoteDb. This is a timestamp, rather than a boolean, so that PolyGerrit can
incorporate this event in the timeline displayed to users accordingly. Reviewers
should appear to have been added at the earliest time that a patch set was made
reviewable after they were added as reviewers. If that patch set is not yet
reviewable, then those reviewers should only be visible to the change owner.

The schema upgrade for ReviewDb will mark all existing patch sets as reviewable
using their created on timestamp. For NoteDb, we will rely on an explicit footer
to mark patch sets as not yet reviewable. Existing patch sets from prior to this
change will therefore be considered reviewable at the time they were created.
Patch sets uploaded after this change with deferred reviewability will have a
*Reviewable: false* footer. When such patch sets are later made reviewable, an
entry will be added to the NoteDb log that sets this timestamp.

We will add a new *reviewable* push option to manage whether a patch set is
immediately stamped as reviewable when it's pushed. It will take a boolean value
which, when true, marks the revision ready for review as it is created. If
false, then it will force the new revision to be in the not yet reviewable
state.

The default value of the *reviewable* push option is, by default, true. We will
add a *reviewable_default* project setting to override the default value[^1] of
the *reviewable* push option. If a value for this option is explicitly provided
at push time, that value overrides all defaults. The default value for
*reviewable_default* will be true, effectively preserving the existing workflow
where revisions are always immediately reviewable. In this case a user must push
with *reviewable=false* to override.

A project preferring a private self-review phase for all changes by default
would set *reviewable_default* to false to opt into this new workflow. In this
case a user must push with *reviewable* or *reviewable=true* to override and
immediately mark their revision as reviewable as it is pushed.

Once a change has at least one reviewable patch set, it should itself be
considered to be reviewable. When a reviewer is added to a change that is *not*
yet reviewable, then they should not be notified, regardless of the value of any
given *notify* option.[^2]

We will create a new REST API endpoint to mark not yet reviewable patch sets as
reviewable. It will notify all watchers and reviewers according to the given
*notify* option (which defaults to *ALL*).

There will be a new project watch/notify type, *REVIEWABLE_CHANGES*. It filters
changes by whether they have any reviewable patch set.[^3]

The PolyGerrit UI will be modified to support this new workflow by making it
clear to the user whether the current patch set has been made reviewable, and
offering a button to make it reviewable. PolyGerrit should omit events from the
timeline if they correspond to a patch set that is not yet reviewable. Patch
sets that have not been marked reviewable should only be visible to the owner.
For example, if PS1 is reviewable, PS2 through PS4 are not yet reviewable, and
PS5 is reviewable, then reviewers and other users will only see PS1 and PS5.

## Alternatives Considered

One idea we considered was adding a new status for changes called *Started*.
Adding another status compounds the complexity of the system, and it's not clear
that this benefits users. Upon further consideration, it didn't adequately cover
the need to defer notifications on revisions, either.

A project can subscribe a mailing list to *all_comments* and
*submitted_changes*, instead of *new_changes*, to reduce noise from changes that
never make it to review. This approach misses out when reviewers are added by
push option, however.

Technically, a savvy user can manage their desired workflow today. This isn't a
viable approach for all users, and it may lead to a situation where a user needs
to post a trivial review message to start the review. For this to be tractable,
the UI needs to make it clear to the user when and how to "publish" their
change in this manner, so we need something like this published bit anyway.

We could rely more heavily on drafts, but there is a lot of work to do to make
them less confusing. They also don't completely fit the desired workflow, e.g.
when a reviewer is added, the draft change shows up on the reviewer's dashboard.
It might be reasonable to repurpose drafts, but the only clear plan that has
emerged so far is to get rid of them. It doesn't make sense to build new
features on top of a feature that's gathering momentum for removal.

Change edits are another way that a user can stage a private revision.
PolyGerrit isn't slated to support change edits until late mid-2017 at the
earliest, and it's not clear how a user could invoke CI on a change edit.

## Notes

[^1]:
     A boolean push option with default value dependent on project config may
     not be straightforward. Implementation may require it to be more like a
     three-state enum (unspecified, true, or false) to accommodate this project
     setting.

[^2]:
     Alternatively, we could support notification if the *notify* option is
     explicitly given and is either *ALL* or *OWNER_REVIEWERS*.

[^3]:
     It might be worthwhile to support a search term for this as well
     (is:reviewable). I'm not sure how hard it is to backfill people's indexes.
     Maybe it would be crafted as a negative? This would enable filtering of not
     yet reviewable changes from dashboards, although it doesn't solve the
     problem of "pending" reviewers seeing the change.
