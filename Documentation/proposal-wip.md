# Proposal for Work In Progress (WIP) workflow

## Objective

Add a new property for unmerged changes called *wip*, for "work-in-progress" changes. A WIP change
is a change that is not currently ready for review. The UI will use this property to expose a
variation of the standard code review workflow to the owner of a change, where the owner can stage a
patch set for review without distracting their future or ongoing reviewers. We will add a
"Start Review" button with a clear call to action for the change owner to mark a change as no longer
WIP. This action explicitly (re-)invites reviewers to start reviewing, and moves the change into
their respective dashboards.

## Background

Software development is all about communication, with many teams distributed across different
countries, continents, and time zones. Gerrit should streamline the communication process and make
the next action to take obvious and easy to understand for all participants. Part of this challenge
is to make it clear to a reviewer when their attention on a change is needed, without requiring the
reviewer to closely follow someone else's development process or scan the content of a change in
order to make a judgment call.

The major channels for obtaining reviewer attention are notifications and the reviewer's dashboard.
A change author can suppress notifications, but this requires an esoteric push option for uploaded
changes, and an option on API actions that is currently unsupported by PolyGerrit. The use of
notification suppression is not recorded anywhere, so there's no way to make it clear to the user
that they haven't notified their reviewers yet. As for suppressing appearance in a future reviewer's
dashboard, the only option available to the change author is simply not adding them as a reviewer
until the change is ready for review. This doesn't really make sense if the user wants to take an
ongoing review offline to work on a significant revision.

Because of these factors, we often overcommunicate to reviewers before a change is ready for
review, and undercommunicate once it is ready for review. Watchers who subscribe to a notify type to
observe ongoing code reviews across a project also receive an excess of communication, because they
have no way to filter out changes that never reach the state of active review.

This proposal addresses these problems, some of which are captured in the following bug reports from
users:

*   [Issue 4489](https://bugs.chromium.org/p/gerrit/issues/detail?id=4489) shows
    how users currently work around the lack of this status, and the confusion
    that ensues
*   [Issue 4390](https://bugs.chromium.org/p/gerrit/issues/detail?id=4390) calls
    for a new notification type for changes that actually receive review
*   [Issue 3798](https://bugs.chromium.org/p/gerrit/issues/detail?id=3798) would
    also be satisfied by this new notification type
*   [Issue 4673](https://bugs.chromium.org/p/gerrit/issues/detail?id=4673)
    discusses the excess of emails that go out from Gerrit, as compared to
    Rietveld
*   [Issue 5599](https://bugs.chromium.org/p/gerrit/issues/detail?id=5599)
    is another consequence of the confusion created by suppressing notifications,
    where editing a commit message in the UI inadvertently "starts" the
    review and prematurely notifies upcoming reviewers with an email that doesn't
    include the change's diff

## Requirements

Notification and dashboard behavior for existing users who do not opt into this workflow should not
change at all.

Changes have two new properties:

* *wip* is a boolean. When true we call the corresponding change a WIP change.
* *review_started* is a nullable timestamp. It is set to the current time whenever false is assigned
  to the *wip* property.

These two properties are supported in both ReviewDb and NoteDb. The schema upgrade for ReviewDb
should set *wip* to false for all existing changes, and *review_started* to the created time of each
existing change. In NoteDb, these properties are derived based on a *Wip* footer that assigns either
true or false.

A new push option, *wip*, sets the *wip* property on the corresponding change to true. This provides
a way of creating a change as WIP from its inception, as well as a way of transitioning a change
under review back into WIP as the author prepares the next patch set for review. Examples:

* git push -o wip origin HEAD:refs/for/master
* git push origin HEAD:refs/for/master%wip

A new project setting, *wip_default*, makes all pushes set *wip* to true by default. In this case,
a user must provide *wip=false* as a push option to opt out of this workflow.

Optional: A new user setting, *wip_default*, has the same effect as the project setting and
overrides it.

A new REST API endpoint supports setting the *wip* property on a given change to true:

* POST /changes/{change-id}/wip

There is also a corresponding REST API endpoint for setting the *wip* property to false:

* POST /changes/{change-id}/ready

Both endpoints take as input an optional message. This message will appear in the change's
log and in email notifications. Either action triggers a notification, so both endpoints also take
as input an optional *notify* setting, which defaults to *ALL*.

Only the owner of a change or an admin can modify a change's *wip* property. Modifying the *wip*
property is prohibited on merged changes, where *wip* is frozen as false.

Certain notifications on WIP changes are targeted to a tighter audience, or suppressed entirely:

* Actions that create a new patch set in a WIP change default to notifying *OWNER* instead of
  *ALL*
* Actions that add a reviewer or CC to a WIP change default to notifying *NONE*
* Abandoning a WIP change defaults to notifying *OWNER_REVIEWERS*.
* Reviewing a WIP change defaults to notifying *OWNER*. However, reviews posted through PolyGerrit set
  *notify* to *ALL*. This supports feedback to the author from CI or static analysis tools, but
  ensures that human responses reach the expected audience.
* (Optional) Reviewers added during a change's WIP phase should not be subscribed to the change
  at all, until the change next moves out of WIP.[^1]

Two new stream events support plugin notification for changes to the *wip* property:

* WorkInProgressEvent
* ReadyEvent

A new watch type called *REVIEW_STARTED_CHANGES* filters changes by whether they've ever gone into
review (that is, ever had *wip* set to false). This may include changes that are currently WIP.
For example, if a change is created as WIP and abandoned, it doesn't notify this watch type.
However, if it later is restored and review is started, then it does notify *REVIEW_STARTED_CHANGES*.
These notifications continue to *REVIEW_STARTED_CHANGES* even if the change later returns to WIP.

WIP changes have first class treatment in the PolyGerrit UI. A WIP change has a "Start
Review" button with a clear call to action. Reviewers can be added without using the reply
dialog. Reviewers can be added to a WIP change, but they will not be notified until/unless the
review is started. The UI also provides a way to move a change under review into a WIP change,
without needing to upload a new patch set.

The WIP property is incorporated into the search index. The queries that drive the PolyGerrit
dashboard take WIP into account. There is a new section for outgoing WIP changes (*Pending reviews*?).
WIP changes are deemphasized in *Incoming reviews* (or perhaps do not appear at all in that section).

## Previous work

* First attempt to implement the WIP workflow in gerrit core was based on extending Change.Status
enum with new value WIP
* Followed up by attempt to extend the Change.Status enum with new value WIP in Gerrit core, but
extract the WIP feature as a plugin
* Followed up by adding configuration option to disable draft workflow with
`changes.allowdrafts=false` in gerrit core, to abuse the free change status value for WIP workflow,
implemented in wip plugin

## Alternatives considered

* Change edits can be used to hide notifications. However, support for change edits in PolyGerrit
  isn't coming soon enough. Certain code review features aren't available on code edits, either,
  like review comments from CI or static analysis. Integration is easier on patch sets. Change edits
  should be reserved for quick edits in the web UI. There is no way to create a change without an
  initial patch set.
* Dedicated label can be used, with vote permission granted to change owner only, combined with
customized dashboard to filter out negative votes on this label
* Abuse existing label, say CRVW and interpret the blocking-Vote of change owner as virtual WIP
state. With secondary index label-predicate extension, i added recently, this query can be
expressed as:

  label:Code-Review-2,owner

Dashboards could be customized to filter out those changes.

Note that in both altenative approaches, only Dashboard filtering issues is addressed. Turning off
the Notification Firehouse problem is unsolved, unfortunately. Also plugins, see below, cannot guess
and react on WIP changes.

## Notes

[^1]:
    We may want to add a new ReviewerState, FUTURE. When *wip* transitions to false, all FUTURE
    reviewers transition to REVIEWER and receive a NewChange email. This may be too complicated to
    implement in the first pass of this project. Alternatively, when the POST is made to ready, a
    new reviewers on the change can be determined and two distinct emails can be sent out, one just
    for the new reviewers and one for everyone else.
