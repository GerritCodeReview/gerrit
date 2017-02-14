# Proposal for Work In Progress (WIP) workflow

## Objective

Add a new property for unmerged changes called *wip*, for "work-in-progress" changes. A WIP change
is a change that is not currently ready for review. The UI will use this property to expose a
variation of the standard code review workflow to the owner of a change, where the owner can stage a
patch set for review without distracting their future or ongoing reviewers.

Add a "Start Review" button with a clear call to action to PolyGerrit for WIP changes.
The change owner uses this button to mark a change as no longer WIP and explicitly (re-)invite
reviewers to start reviewing by sending them notifications and adding the change to their
respective dashboards.

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

### Existing Workflow

Notification and dashboard behavior for existing users who do not opt into this new workflow is
completely unaffected.

### New Change Properties

Changes have two new properties:

* *wip* is a boolean. When true we call the corresponding change a WIP change.
* *review_started* is a nullable timestamp. It is set to the current time whenever false is assigned
  to the *wip* property.

These two properties are supported in both ReviewDb and NoteDb. The schema upgrade for ReviewDb
should set *wip* to false for all existing changes, and *review_started* to the created time of each
existing change. In NoteDb, these properties are derived according to a *Wip* footer that assigns
either true or false.

### Push Option

A new push option, *wip*, sets the *wip* property on the corresponding change to true. This provides
a way of creating a change as WIP from its inception, as well as a way of transitioning a change
under review back into WIP as the author prepares the next patch set for review. Examples:

* git push -o wip origin HEAD:refs/for/master
* git push origin HEAD:refs/for/master%wip

### Settings

A new inheritable project setting, *wip_default*, makes all pushes set *wip* to true by default. In
this case, a user must provide *wip=false* as a push option to opt out of this workflow.

(Optional) A new user setting, *wip_default*, has the same effect as the project setting and
overrides it.

### REST API

A new REST API endpoint supports setting the *wip* property on a given change to true:

* POST /changes/{change-id}/wip

There is also a corresponding REST API endpoint for setting the *wip* property to false:

* POST /changes/{change-id}/ready

Both endpoints take as input an optional message. This message will appear in the change's
log and in email notifications. Either action triggers a notification, so both endpoints also take
as input an optional *notify* setting, which defaults to *ALL*.

Only the owner of a change or an admin can modify a change's *wip* property. Modifying the *wip*
property is prohibited on merged changes, where *wip* is frozen as false.

### Notifications

Certain notifications on WIP changes are targeted to a tighter audience, or suppressed entirely:

* Actions that create a new patch set in a WIP change default to notifying *OWNER* instead of
  *ALL*
* Actions that add a reviewer or CC to a WIP change default to notifying *NONE*
* Abandoning a WIP change defaults to notifying *OWNER_REVIEWERS*.
* Reviewing a WIP change defaults to notifying *OWNER*. However, reviews posted through PolyGerrit set
  *notify* to *ALL*. This supports feedback to the author from CI or static analysis tools, but
  ensures that human responses reach the expected audience.

### Events

Two new stream events support plugin notification for changes to the *wip* property:

* WorkInProgressEvent
* ReadyEvent

### Watches

A new watch type called *REVIEW_STARTED_CHANGES* filters changes by whether they've ever gone into
review (that is, ever had *wip* set to false). This may include changes that are currently WIP.
For example, if a change is created as WIP and abandoned, it doesn't notify this watch type.
However, if it later is restored and review is started, then it does notify *REVIEW_STARTED_CHANGES*.
These notifications continue to *REVIEW_STARTED_CHANGES* even if the change later returns to WIP.

### PolyGerrit

WIP changes have first class treatment in the PolyGerrit UI, with some sort of styling to emphasize
that they are not currently under review.

The primary call-to-action for a WIP change viewed by its owner is the "Start Review" button.
This opens a variation of the reply dialog that highlights which new reviewers/CCs will be notified,
and gives the owner the opportunity to include an optional message and vote on labels.

The existing reply flow is also supported on WIP changes. It can be used to manage reviewers/CCs,
but the message is optional under WIP changes.

The UI also provides a button to move a change under review into a WIP change, as an alternative
to using a push option. This provides a way for the owner to mark the change as WIP before they've
prepared a revision.

### Search and Dashboards

The WIP property is incorporated into the search index.

There is a new section for outgoing WIP changes (*Pending reviews*?). The underlying query is
for WIP changes owned by the user.

The query driving the existing *Incoming reviews* section subtracts WIP changes from the results.
This has the effect of hiding WIP changes from their reviewers' dashboards.

(Optional) Instead of hiding WIP changes, deemphasize them.

## Previous work

* First attempt to implement the WIP workflow in gerrit core was based on extending Change.Status
enum with new value WIP
* Followed up by attempt to extend the Change.Status enum with new value WIP in Gerrit core, but
extract the WIP feature as a plugin
* Followed up by adding configuration option to disable draft workflow with
`changes.allowdrafts=false` in gerrit core, to abuse the free change status value for WIP workflow,
implemented in wip plugin

## Alternatives considered

* Change edits can be used to hide notifications, but there are several shortcomings. Certain code
  review features aren't available on change edits, like review comments from CI or static analysis.
  Integrations are much more familiar with the API surface of patch sets than change edits. Robot
  comments can only be posted on patch sets. There is no way to create a new change without a patch
  set. A reviewer can still view a WIP change, for example upon request by the owner, but change
  edits are private. For these reasons we recommend that change edits be reserved for quick edits in
  the web UI.
* Dedicated label can be used, with vote permission granted to change owner only, combined with
  customized dashboard to filter out negative votes on this label. We would still have to defer
  notifications somehow, and we don't have precedent for basing that on a label.
* Abuse existing label, say CRVW and interpret the blocking-Vote of change owner as virtual WIP
  state. This has the same drawbacks as the dedicated label alternative.
