---
name: gerrit-frontend-engineering
description: Provides guidance and best practices on Polygerrit UI development, frontend architecture, and TypeScript/JS coding standards in Gerrit.
---

# Frontend Engineering & UI Development Engineering Guide

## Executive Summary

Welcome to the Frontend Engineering & UI Development guide. This repository
serves as the authoritative source of tribal knowledge for our UI architecture,
born from historical refactoring efforts, critical performance optimizations,
and the ongoing necessity to prevent recurrent regression of known failure
modes. By codifying these engineering standards, we ensure that incoming
engineers can confidently navigate the complexities of our frontend ecosystem
without falling into legacy traps, introducing unverified UI states, or
triggering silent runtime failures.

This guide enforces strict architectural boundaries across the entire UI
development lifecycle. It mandates rigorous state encapsulation within the Lit
framework, uncompromising TypeScript type safety, and highly resilient
client-server integrations. It further standardizes hermetic UI testing
methodologies, unifies our CSS design systems, and outlines strict strategies
for client-side performance profiling.

Adherence to these principles guarantees structural consistency and system
reliability. Whether you are building dynamic web components, integrating
complex REST API endpoints, or optimizing data payloads for AI contexts and
telemetry, this documentation establishes the foundational constraints required
to ship a performant, predictable, and scalable user interface.

## Summary

| Chapter Theme / Title                | Scope & Objective                     |
| :----------------------------------- | :------------------------------------ |
| **Lit Framework Idioms & State       | Enforce strict Lit framework idioms   |
: Encapsulation**                      : by leveraging declarative rendering,  :
:                                      : reactive property encapsulation, and  :
:                                      : native lifecycle hooks. Avoid         :
:                                      : imperative DOM manipulation and       :
:                                      : properly isolate transient UI status  :
:                                      : from the core data models.            :
| **TypeScript Strictness & Type       | This domain governs the strict        |
: Safety**                             : enforcement of TypeScript type safety :
:                                      : by explicitly forbidding unsafe       :
:                                      : casting and blanket compiler          :
:                                      : suppressions. It mandates precise     :
:                                      : component modeling, strict interface  :
:                                      : adherence, and proper access          :
:                                      : modifiers to eliminate silent runtime :
:                                      : failures and unverified states.       :
| **Hermetic Testing & Visual          | This chapter mandates the strict      |
: Regression**                         : isolation of unit tests,              :
:                                      : comprehensive visual validation using :
:                                      : full shadow DOM snapshots, and the    :
:                                      : centralization of test data           :
:                                      : generation to guarantee hermetic      :
:                                      : execution and prevent false-positive  :
:                                      : assertions.                           :
| **Client-Side Performance &          | This theme governs the optimization   |
: Telemetry**                          : of client-side performance by         :
:                                      : enforcing strict telemetry on         :
:                                      : synchronous CPU-bound operations and  :
:                                      : minimizing network latency. It        :
:                                      : mandates the caching of asynchronous  :
:                                      : API promises, the derivation of state :
:                                      : from existing payloads, and the rigid :
:                                      : bounding of background data requests. :
| **CSS Architecture & Design System   | This domain governs the consistent    |
: Consistency**                        : application of styles across the UI,  :
:                                      : mandating the use of content-driven   :
:                                      : layout techniques, externalized       :
:                                      : custom property configurations for    :
:                                      : visual assets, and declarative Lit    :
:                                      : directives over imperative inline     :
:                                      : styling.                              :
| **API Integration & Error Handling** | This chapter governs the resilient    |
:                                      : integration of frontend logic with    :
:                                      : REST APIs. It establishes strict      :
:                                      : constraints for maintaining backend   :
:                                      : payload parity, explicitly modeling   :
:                                      : structural variances, and enforcing   :
:                                      : centralized error handling over       :
:                                      : localized `try...catch` blocks.       :
| **AI Context & Telemetry Payload     | This domain governs the client-side   |
: Optimization**                       : lifecycle and optimization of data    :
:                                      : structures sent to AI agents and      :
:                                      : telemetry pipelines. It strictly      :
:                                      : dictates payload deduplication        :
:                                      : strategies, transparent AI token      :
:                                      : constraint surfacing, and rigorous    :
:                                      : parsing validation to prevent silent  :
:                                      : context loss.                         :

--------------------------------------------------------------------------------
--------------------------------------------------------------------------------

## Chapter: Lit Framework Idioms & State Encapsulation

**Context:** Enforce strict Lit framework idioms by leveraging declarative
rendering, reactive property encapsulation, and native lifecycle hooks. Avoid
imperative DOM manipulation and properly isolate transient UI status from the
core data models.

### Summary

| Rule ID   | Principle /        | Priority | Primary Symptom / Trap           |
:           : Constraint         :          :                                  :
| :-------- | :----------------- | :------- | :------------------------------- |
| **T1-01** | Separation of Data | High     | Reusing a data property to hold  |
:           : and UI State       :          : UI loading or error text, which  :
:           : Variables          :          : corrupts the data being passed   :
:           :                    :          : to child components or the       :
:           :                    :          : clipboard.                       :
| **T1-02** | Explicit Boolean   | High     | Inspecting the DOM via           |
:           : Properties over    :          : `this.children` in Lit element   :
:           : Dynamic Slot       :          : methods to determine if a named  :
:           : Detection          :          : slot element was provided by the :
:           :                    :          : parent.                          :
| **T1-03** | Declarative Event  | Medium   | Attaching DOM event listeners    |
:           : Listeners in Lit   :          : imperatively within the          :
:           : Templates          :          : component constructor.           :
| **T1-04** | Declarative        | Medium   | Chaining ternary operators       |
:           : Conditional CSS    :          : inside a template string to      :
:           : via classMap       :          : build a class list.              :
| **T1-05** | Centralized        | Medium   | Defining a custom manager object |
:           : Periodic           :          : or singleton strictly tied to    :
:           : LitElement Updates :          : one specific UI component type   :
:           : via Generic        :          : for periodic updates.            :
:           : Utility            :          :                                  :
| **T1-06** | Declarative        | Medium   | Manually invoking setAttribute   |
:           : Attribute          :          : or removeAttribute within a Lit  :
:           : Reflection in Lit  :          : lifecycle method to sync DOM     :
:           : Components         :          : properties.                      :
| **T1-07** | Idempotent         | Medium   | Firing a tracking event inside   |
:           : Telemetry          :          : `updated` purely based on        :
:           : Reporting in Lit   :          : conditional presence, without a  :
:           : Component          :          : state flag acknowledging it      :
:           : Lifecycle Hooks    :          : fired.                           :
| **T1-08** | Strict Truthiness  | High     | Relying on optional chaining     |
:           : Checks for         :          : length checks to represent empty :
:           : Asynchronous Array :          : or missing data.                 :
:           : Data               :          :                                  :
| **T1-09** | Pre-computing      | Medium   | A helper method called in        |
:           : Derived State in   :          : `render()` that parses raw       :
:           : Lit Lifecycle      :          : strings into arrays on every     :
:           : Methods            :          : invocation.                      :
| **T1-10** | Returning          | Medium   | Using an empty template literal  |
:           : `nothing` for      :          : or omitting a return when a      :
:           : Empty Templates in :          : condition fails in the template. :
:           : Lit                :          :                                  :
| **T1-11** | Single-Pass        | Medium   | Assigning values to `@state()`   |
:           : Initialization of  :          : or `@property()` fields inside   :
:           : Reactive           :          : the `firstUpdated` hook.         :
:           : Properties         :          :                                  :
| **T1-12** | Guarding           | High     | Executing timeout handlers or    |
:           : Asynchronous State :          : asynchronous DOM updates without :
:           : Updates            :          : confirming the component remains :
:           : Post-Disconnection :          : in the DOM structure.            :
| **T1-13** | Use Lit classMap   | Medium   | Concatenating ternary operators  |
:           : Directive for      :          : inside the `class` attribute     :
:           : Conditional CSS    :          : string of a Lit HTML template.   :
:           : Classes            :          :                                  :
| **T1-14** | Strict Lit State   | High     | Using the `@property` decorator  |
:           : Encapsulation      :          : for variables that represent     :
:           :                    :          : internal component state (like   :
:           :                    :          : data lists or loading spinners). :
| **T1-15** | Declarative        | Medium   | Controlling element visibility   |
:           : Component          :          : using CSS class conditionals.    :
:           : Visibility         :          :                                  :
:           : Toggling           :          :                                  :
| **T1-16** | Immutable Lit Form | High     | Mutating form state fields       |
:           : State Resets       :          : directly without triggering a    :
:           :                    :          : reference change for Lit to      :
:           :                    :          : detect.                          :

--------------------------------------------------------------------------------

### Rules

#### T1-01: Separation of Data and UI State Variables

> **Rule:** Never overload core data variables with transient UI status strings.
> You must use dedicated status properties to manage UI state separately from
> the underlying data model.
>
> **What:** Do not overload core data variables with transient UI status strings
> (e.g., 'Loading...', 'Error'). Use dedicated status properties to manage UI
> state separately from the underlying data model.
>
> **Applies To:** Lit components managing asynchronous operations and displaying
> resulting data alongside loading or error states.
>
> **Why:** A `generatedPassword` property was overloaded to temporarily hold
> 'Generating...' or 'Failed to generate'. This caused the associated 'Copy to
> clipboard' component to copy the error or status text instead of a valid
> password. Failing to adhere to this typically results in **Invalid Data
> Copied**.

**Trap 1: Reusing a data property to hold UI loading or error text, which
corrupts the data being passed to child components or the clipboard.**

**Don't:**

```typescript
this._generatedPassword = 'Generating...';
this.restApiService.generatePassword().then(newPassword => {
  this._generatedPassword = newPassword ?? 'Failed to generate';
});
```

**Do:**

```typescript
this.status = 'Generating...';
this.restApiService.generatePassword().then(newPassword => {
  if (newPassword) {
    this.generatedPassword = newPassword;
    this.status = undefined;
  } else {
    this.status = 'Failed to generate';
  }
});
```

#### T1-02: Explicit Boolean Properties over Dynamic Slot Detection

> **Rule:** Always control conditional layout wrappers using explicit boolean
> properties mapped to the component API. Never dynamically query the DOM for
> the presence of assigned slots.
>
> **What:** Control the rendering of conditional layout wrappers using explicit
> boolean properties mapped to the component's API, rather than dynamically
> querying the DOM for the presence of assigned slots.
>
> **Applies To:** Lit components featuring conditional layout containers that
> wrap `<slot>` elements (e.g., search bars with optional leading icons).
>
> **Why:** Dynamically checking `hasNamedSlot` by iterating over `this.children`
> was computationally brittle and caused rendering bugs where structural
> elements (like search icons) were accidentally removed or created unintended
> 'ghost spacing'. Failing to adhere to this typically results in **Layout
> Regression / Missing Elements**.

**Trap 1: Inspecting the DOM via `this.children` in Lit element methods to
determine if a named slot element was provided by the parent.**

**Don't:**

```typescript
private hasNamedSlot(name: string): boolean {
  return Array.from(this.children).some(
    el => el.getAttribute('slot') === name
  );
}

render() {
  return this.hasNamedSlot('leading-icon') ? html`<div><slot name="leading-icon"></slot></div>` : nothing;
}
```

**Do:**

```typescript
@property({type: Boolean})
showLeadingIcon = false;

render() {
  return this.showLeadingIcon ? html`<div><slot name="leading-icon"></slot></div>` : nothing;
}
```

#### T1-03: Declarative Event Listeners in Lit Templates

> **Rule:** Always bind event listeners declaratively directly within the
> component's `render()` template. Never imperatively attach listeners using
> `this.addEventListener` in the constructor.
>
> **What:** Bind event listeners declaratively directly within the component's
> `render()` template using `@event` syntax, rather than imperatively attaching
> them using `this.addEventListener` in the constructor.
>
> **Applies To:** LitElement initialization and user interaction event handling.
>
> **Why:** Imperatively adding listeners via the constructor disconnects the
> logic from the declarative template, risks memory leaks if not cleaned up,
> leaves inline documentation orphaned, and triggers automated code health
> warnings. Failing to adhere to this typically results in **Structural
> Anti-Pattern / Orphaned Context**.

**Trap 1: Attaching DOM event listeners imperatively within the component
constructor.**

**Don't:**

```typescript
constructor() {
  super();
  // BAD: Imperative binding
  this.addEventListener('mousedown', e => this.handleMouseDown(e));
}
```

**Do:**

```html
override render() {
  // GOOD: Declarative binding directly in the Lit template
  return html`
    <div class="menu" @mousedown=${this.handleMenuMouseDown}>
      <div class="menu-item">${this.hoverCardText}</div>
    </div>
  `;
}
```

#### T1-04: Declarative Conditional CSS via classMap

> **Rule:** Must use the Lit `classMap` directive for applying conditional CSS
> classes in templates. Avoid manual string interpolation with ternary
> operators.
>
> **What:** Use the Lit `classMap` directive for applying conditional CSS
> classes in templates instead of manual string interpolation with ternary
> operators.
>
> **Applies To:** Lit Framework templates (`render()` methods).
>
> **Why:** Manual string interpolation for classes is difficult to read and
> prone to whitespace concatenation errors, which leads to incorrectly applied
> or missed CSS selectors during dynamic state changes. Failing to adhere to
> this typically results in **Malformed Class Strings / UI Bugs**.

**Trap 1: Chaining ternary operators inside a template string to build a class
list.**

**Don't:**

```typescript
<div class="diffContainer ${this.shownSidebar ? 'sidebarOpen' : ''} ${this.file?.diffs_too_expensive_to_compute ? 'hidden' : ''}">
```

**Do:**

```typescript
<div class=${classMap({
  diffContainer: true,
  sidebarOpen: this.shownSidebar,
  hidden: !!this.file?.diffs_too_expensive_to_compute
})}>
```

#### T1-05: Centralized Periodic LitElement Updates via Generic Utility

> **Rule:** Always register components requiring timer-based periodic
> re-rendering with a centralized update manager. Never implement individual
> `setInterval` loops inside isolated components.
>
> **What:** Components requiring timer-based periodic re-rendering must register
> with a centralized `PeriodicUpdateManager` rather than implementing individual
> `setInterval` and lifecycle cleanup logic inside the component.
>
> **Applies To:** LitElements displaying time-sensitive data (e.g., relative
> dates, "time ago" formatters).
>
> **Why:** Individual date formatter components initially implemented their own
> object literal manager or interval timers. This violated the separation of
> concerns and led to memory leaks if components failed to clean up their
> specific timers upon disconnection. Failing to adhere to this typically
> results in **Memory Leaks / Duplicated Timer Logic**.

**Trap 1: Defining a custom manager object or singleton strictly tied to one
specific UI component type for periodic updates.**

**Don't:**

```typescript
export const dateFormatterManager = {
  formatters: new Set<GrDateFormatter>(),
  register(formatter) { /* set interval to call requestUpdate */ }
}
```

**Do:**

```typescript
// In periodic-update-util.ts
export class PeriodicUpdateManager<T extends LitElement> {
  constructor(private readonly refreshIntervalMs: number) {}
  register(component: T) { /* generic interval logic */ }
}

// In component
override connectedCallback() {
  super.connectedCallback();
  dateFormatterManager.register(this);
}
```

#### T1-06: Declarative Attribute Reflection in Lit Components

> **Rule:** Must use Lit's `@property({reflect: true})` decorator to synchronize
> a component's property state with DOM attributes. Never manually manipulate
> attributes via `setAttribute` within lifecycle methods.
>
> **What:** Use Lit's @property({reflect: true}) decorator to automatically
> synchronize a component's property state with its corresponding DOM
> attributes, replacing manual DOM manipulation calls.
>
> **Applies To:** Lit web components, styling hooks, and state management.
>
> **Why:** A custom icon wrapper was manually setting and removing an attribute
> inside the `willUpdate` lifecycle method to apply CSS selectors, violating
> declarative state management principles. Failing to adhere to this typically
> results in **Imperative DOM Manipulation**.

**Trap 1: Manually invoking setAttribute or removeAttribute within a Lit
lifecycle method to sync DOM properties.**

**Don't:**

```typescript
override willUpdate() {
  if (this.icon) {
    this.setAttribute('custom', '');
  } else {
    this.removeAttribute('custom');
  }
}
```

**Do:**

```typescript
@property({type: Boolean, reflect: true})
custom = false;

override willUpdate() {
  this.custom = this.icon ? true : false;
}
```

#### T1-07: Idempotent Telemetry Reporting in Lit Component Lifecycle Hooks

> **Rule:** Must guard telemetry interactions fired during the `updated()`
> lifecycle hook with a dedicated state flag. Never allow subsequent, unrelated
> property changes to trigger duplicate reporting.
>
> **What:** Telemetry interactions fired during the `updated()` lifecycle hook
> must be guarded by a dedicated state flag to prevent duplicate reporting
> triggered by subsequent, unrelated property changes.
>
> **Applies To:** Lit components executing side effects (like analytics or
> impression tracking) within the `updated()` loop.
>
> **Why:** When streaming AI responses, the component's state rapidly updated.
> Without an idempotent guard flag, the system generated multiple telemetry
> events for the exact same interaction every time the state re-rendered.
> Failing to adhere to this typically results in **Duplicate Impression
> Reporting**.

**Trap 1: Firing a tracking event inside `updated` purely based on conditional
presence, without a state flag acknowledging it fired.**

**Don't:**

```typescript
override updated(changedProperties: PropertyValues) {
  if (this.message()?.responseComplete) {
    this.reportSuggestionsShown();
  }
}
```

**Do:**

```typescript
private reportedSuggestionsShown = false;

override updated(changedProperties: PropertyValues) {
  if (!this.reportedSuggestionsShown && this.message()?.responseComplete) {
    this.reportSuggestionsShown();
    this.reportedSuggestionsShown = true;
  }
}
```

#### T1-08: Strict Truthiness Checks for Asynchronous Array Data

> **Rule:** Always explicitly check for null or undefined before evaluating the
> `.length` property of asynchronous array data. Never rely solely on optional
> chaining length checks for truthiness.
>
> **What:** When determining the fallback UI state based on asynchronous array
> data (like API responses), explicitly check for null/undefined before checking
> the `.length` property.
>
> **Applies To:** Lit components conditionally rendering UI elements based on
> the loaded state of arrays.
>
> **Why:** Checking `array?.length === 0` fails when the array is still
> `undefined`, because `undefined === 0` resolves to `false`, causing the
> application to skip rendering the fallback UI during the loading or
> uninitialized state. Failing to adhere to this typically results in **Missing
> Fallback UI**.

**Trap 1: Relying on optional chaining length checks to represent empty or
missing data.**

**Don't:**

```typescript
if (this.repoLabels?.length === 0) {
  return this.renderDefaultParameterInputField();
}
```

**Do:**

```typescript
if (!this.repoLabels || this.repoLabels.length === 0) {
  return this.renderDefaultParameterInputField();
}
```

#### T1-09: Pre-computing Derived State in Lit Lifecycle Methods

> **Rule:** Always pre-compute derived state within the `willUpdate` lifecycle
> method or a dedicated observer. Never process strings or execute heavy array
> computations directly inside the `render()` loop.
>
> **What:** Avoid processing strings or doing heavy array computations directly
> inside the `render()` method or helper template methods. Instead, reactively
> compute derived state (e.g., parsing a string into an array) within the
> `willUpdate` lifecycle method or a dedicated observer.
>
> **Applies To:** Lit components dealing with data transformation before
> rendering.
>
> **Why:** Dynamic computation inside render cycles degrades performance and
> muddles template readability. Helper methods that executed `.split()` and
> regex operations on strings were running repeatedly on every re-render.
> Failing to adhere to this typically results in **Redundant Computations**.

**Trap 1: A helper method called in `render()` that parses raw strings into
arrays on every invocation.**

**Don't:**

```typescript
private getParameters(): string[] {
  if (this.parameters) return this.parameters;
  if (this.parameterStr?.trim()) {
    return this.parameterStr.trim().split(/\s+/);
  }
  return [];
}

render() {
  const params = this.getParameters();
  // ...
}
```

**Do:**

```typescript
// Handle the calculation inside `willUpdate` reacting to changes in `this.parameterStr`
willUpdate(changedProperties: PropertyValues) {
  if (changedProperties.has('parameterStr')) {
    this.parameters = this.parameterStr?.trim() ? this.parameterStr.trim().split(/\s+/) : [];
  }
}
```

#### T1-10: Returning `nothing` for Empty Templates in Lit

> **Rule:** Must explicitly return the `nothing` sentinel value instead of an
> empty template literal when rendering conditionally empty elements.
>
> **What:** In Lit templates, explicitly return the `nothing` sentinel value
> instead of an empty template literal when rendering conditionally empty
> elements.
>
> **Applies To:** Lit components, specifically conditional rendering blocks.
>
> **Why:** Historically, empty template instances (html``) were returned for
> false conditions, which created unnecessary markers in the DOM, slightly
> degrading rendering efficiency and DOM cleanliness. Failing to adhere to this
> typically results in **DOM Clutter**.

**Trap 1: Using an empty template literal or omitting a return when a condition
fails in the template.**

**Don't:**

```typescript
// BAD: Returning empty template
render() {
  if (!this.show) return html``;
  return html`<div>Content</div>`;
}
```

**Do:**

```typescript
// GOOD: Returning nothing
import {nothing} from 'lit';

render() {
  if (!this.show) return nothing;
  return html`<div>Content</div>`;
}
```

#### T1-11: Single-Pass Initialization of Reactive Properties

> **Rule:** Always initialize base reactive properties during construction or
> via bound property updates. Never use the `firstUpdated` lifecycle hook for
> initial assignment.
>
> **What:** Base reactive properties must be initialized during construction or
> via bound property updates rather than using the `firstUpdated` lifecycle
> hook, which triggers a redundant second render cycle.
>
> **Applies To:** Lit components, specifically lifecycle hooks (`constructor`,
> `willUpdate`, `firstUpdated`).
>
> **Why:** Assigning derived URL states inside `firstUpdated` triggered
> immediate, unnecessary re-renders, hurting initial paint performance. Failing
> to adhere to this typically results in **Redundant Re-rendering**.

**Trap 1: Assigning values to `@state()` or `@property()` fields inside the
`firstUpdated` hook.**

**Don't:**

```typescript
// BAD: Triggers a second render cycle immediately after the first
override firstUpdated() {
  this.hostUrl = window.location.origin;
}
```

**Do:**

```typescript
// GOOD: Reactively bound to updates before render
override willUpdate(changedProperties: PropertyValues) {
  if (!this.hostUrl) {
    this.hostUrl = window.location.origin;
  }
}
```

**Exceptions:** Properties that strictly depend on measuring DOM dimensions or
child element readiness after layout.

#### T1-12: Guarding Asynchronous State Updates Post-Disconnection

> **Rule:** Always explicitly verify `this.isConnected` before executing
> asynchronous tasks or debounced updates. Never execute callbacks that mutate
> state during DOM teardown.
>
> **What:** Components that schedule asynchronous tasks or debounced updates
> must explicitly verify `this.isConnected` before executing work to prevent
> mutations during DOM teardown.
>
> **Applies To:** Event handlers, async callbacks, and debounced routines in Lit
> web components.
>
> **Why:** Property updates triggered `willUpdate` hooks even after
> `disconnectedCallback` had run, scheduling new background tasks for components
> that were no longer attached to the document. Failing to adhere to this
> typically results in **Memory Leaks**.

**Trap 1: Executing timeout handlers or asynchronous DOM updates without
confirming the component remains in the DOM structure.**

**Don't:**

```typescript
// BAD: Running debounced task without verifying connection
updateSuggestions() {
  this.scheduleDebounceTask();
}
```

**Do:**

```typescript
// GOOD: Short-circuiting if disconnected
updateSuggestions() {
  if (!this.isConnected) return;
  this.scheduleDebounceTask();
}
```

#### T1-13: Use Lit classMap Directive for Conditional CSS Classes

> **Rule:** Must use Lit's `classMap` directive for managing multiple
> conditional CSS classes. Never use manual string concatenation or ternary
> chaining within class attributes.
>
> **What:** Use Lit's `classMap` directive for managing multiple conditional CSS
> classes rather than manual string interpolation.
>
> **Applies To:** Lit component templates rendering conditional classes based on
> component state.
>
> **Why:** Historically, manual string concatenation for multiple conditional
> CSS classes led to messy, error-prone template structures that were difficult
> to read and maintain. Failing to adhere to this typically results in
> **Unreadable/Error-Prone Templates**.

**Trap 1: Concatenating ternary operators inside the `class` attribute string of
a Lit HTML template.**

**Don't:**

```html
class="context-chip ${this.isSuggestion ? 'suggested-chip' : ''} ${this.isCustomAction ? 'custom-action-chip' : ''}"
```

**Do:**

```html
class=${classMap({'context-chip': true, 'suggested-chip': this.isSuggestion, 'custom-action-chip': this.isCustomAction})}
```

#### T1-14: Strict Lit State Encapsulation

> **Rule:** Always isolate internal component variables that drive UI re-renders
> using the `@state` decorator. Never use `@property` for internal state that is
> not intended to be configured via HTML attributes.
>
> **What:** Internal component variables that drive UI re-renders but are not
> intended to be configured via HTML attributes must use the `@state` decorator
> instead of `@property`.
>
> **Applies To:** All Lit-based Web Components.
>
> **Why:** Component logic was exposing internal data retrieval statuses (like
> loading state or fetched lists) as public `@property` attributes. This
> polluted the component's public API surface and allowed external DOM
> manipulation to improperly overwrite internal component state. Failing to
> adhere to this typically results in **State Leakage**.

**Trap 1: Using the `@property` decorator for variables that represent internal
component state (like data lists or loading spinners).**

**Don't:**

```typescript
// BAD: Internal state exposed as an attribute
@property({type: Boolean})
_loading = true;

@property({type: Array})
submitRequirements?: SubmitRequirementInfo[];
```

**Do:**

```typescript
// GOOD: Internal state isolated using @state
@state()
loading = true;

@state()
submitRequirements?: SubmitRequirementInfo[];
```

#### T1-15: Declarative Component Visibility Toggling

> **Rule:** Always handle conditional rendering of DOM elements using Lit's
> declarative `when` or `nothing` directives. Never dynamically apply CSS
> classes that set `display: none` to toggle visibility.
>
> **What:** Conditional rendering of DOM elements must be handled using Lit's
> declarative `when` or `nothing` directives within the HTML template, rather
> than dynamically applying CSS classes that set `display: none`.
>
> **Applies To:** All Lit templates, particularly for loading states and
> conditional sections.
>
> **Why:** Loading states were previously managed by dynamically assigning a
> `.loading` CSS class to an element, which relied on external stylesheet rules
> to hide/show the node. This made the UI state harder to reason about and
> bypassed Lit's native DOM reconciliation. Failing to adhere to this typically
> results in **Layout Shifts / DOM Bloat**.

**Trap 1: Controlling element visibility using CSS class conditionals.**

**Don't:**

```typescript
// BAD: CSS-driven visibility
render() {
  return html`
    <table class="${this.loading ? 'loading' : ''}">
      <!-- rows -->
    </table>
  `;
}
// Relying on: .loading #target { display: none; }
```

**Do:**

```typescript
// GOOD: Declarative Lit rendering directives
render() {
  return html`
    <tbody>
      ${when(
        this.loading,
        () => html`<tr><td>Loading...</td></tr>`,
        () => html`<!-- Render data rows -->`
      )}
    </tbody>
  `;
}
```

#### T1-16: Immutable Lit Form State Resets

> **Rule:** Always assign a newly constructed object reference to the state
> property when resetting form state or clearing dialog inputs. Never mutate the
> existing object's properties in-place.
>
> **What:** When resetting form state or clearing dialog inputs in Lit, assign a
> newly constructed object reference to the state property rather than mutating
> the existing object's properties in-place.
>
> **Applies To:** Lit form components and dialogs with complex object state
> (`@state`).
>
> **Why:** Form dialogs failed to clear properly after creating a new item
> because the state object was mutated in place or partially reset, resulting in
> stale UI data where fields appeared populated but submitted empty values.
> Failing to adhere to this typically results in **Stale UI Data**.

**Trap 1: Mutating form state fields directly without triggering a reference
change for Lit to detect.**

**Don't:**

```typescript
// BAD: In-place mutation does not consistently trigger a full re-render
handleCreateCancel() {
  this.newRequirement.name = '';
  this.newRequirement.description = '';
  this.dialog.close();
}
```

**Do:**

```typescript
// GOOD: Provide a new object reference to trigger full reactive updates
private getEmptyRequirement() {
  return { name: '', description: '' };
}

handleCreateCancel() {
  this.newRequirement = this.getEmptyRequirement();
  this.dialog.close();
}
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T6 | API Integration & Error Handling - *Fetching asynchronous
    data structures via REST clients dictates Lit component reactive loading
    states and rendering fallbacks.*
*   **Downstream:** T5 | CSS Architecture & Design System Consistency - *Lit
    template directives like `classMap` dynamically consume centralized
    structural CSS classes and shared UI styles.*
*   **Downstream:** T7 | AI Context & Telemetry Payload Optimization - *Lit
    lifecycle methods like `updated()` natively trigger telemetry interaction
    payloads that require idempotent guards to prevent data bloat.*

## Chapter: TypeScript Strictness & Type Safety

**Context:** This domain governs the strict enforcement of TypeScript type
safety by explicitly forbidding unsafe casting and blanket compiler
suppressions. It mandates precise component modeling, strict interface
adherence, and proper access modifiers to eliminate silent runtime failures and
unverified states.

### Summary

| Rule ID   | Principle / Constraint           | Priority | Primary Symptom /  |
:           :                                  :          : Trap               :
| :-------- | :------------------------------- | :------- | :----------------- |
| **T2-01** | Targeted Type Casting over Broad | High     | Suppressing all    |
:           : Error Suppression                :          : TypeScript         :
:           :                                  :          : compiler errors on :
:           :                                  :          : a line just to     :
:           :                                  :          : bypass a private   :
:           :                                  :          : visibility check   :
:           :                                  :          : in a unit test.    :
| **T2-02** | Removal of Underscore Prefixes   | Medium   | Prefixing reactive |
:           : for Reactive Properties          :          : Lit properties     :
:           :                                  :          : with an underscore :
:           :                                  :          : to denote private  :
:           :                                  :          : state.             :
| **T2-03** | Elimination of TypeScript        | High     | Suppressing the    |
:           : Compiler Suppressions in Unit    :          : compiler error to  :
:           : Tests                            :          : mutate a private   :
:           :                                  :          : property in the    :
:           :                                  :          : test setup.        :
| **T2-04** | Utilizing TypeScript Utility     | High     | Constructing an    |
:           : Types over Unsafe Casting        :          : object with        :
:           :                                  :          : missing properties :
:           :                                  :          : and masking the    :
:           :                                  :          : error by casting   :
:           :                                  :          : it as the full     :
:           :                                  :          : interface type.    :
| **T2-05** | Prototype Methods over Arrow     | Medium   | Using an arrow     |
:           : Function Properties              :          : function assigned  :
:           :                                  :          : to a variable      :
:           :                                  :          : inside the class   :
:           :                                  :          : body.              :
| **T2-06** | Elimination of Unsafe `any` Type | High     | Suppressing        |
:           : Casts                            :          : TypeScript errors  :
:           :                                  :          : or forcibly        :
:           :                                  :          : casting objects to :
:           :                                  :          : `any` when dynamic :
:           :                                  :          : types don't        :
:           :                                  :          : strictly align.    :
| **T2-07** | Testing Private State            | Medium   | Forcibly accessing |
:           : Encapsulation Rules              :          : class internals    :
:           :                                  :          : using string-keyed :
:           :                                  :          : bracket notation   :
:           :                                  :          : in test files.     :
| **T2-08** | Eliminate Unsafe 'any' Type      | Medium   | Casting function   |
:           : Casting in Test Stubs            :          : arguments to `any` :
:           :                                  :          : within a mock's    :
:           :                                  :          : custom callback    :
:           :                                  :          : function.          :
| **T2-09** | Suppression of Private Member    | Medium   | Casting the class  |
:           : Access in Tests via              :          : reference or       :
:           : @ts-expect-error                 :          : global object to   :
:           :                                  :          : `any` to bypass    :
:           :                                  :          : TypeScript's       :
:           :                                  :          : visibility and     :
:           :                                  :          : presence checks.   :
| **T2-10** | Enforce Type Contracts Over      | Medium   | Adding             |
:           : Redundant Runtime Checks         :          : `.filter(Boolean)` :
:           :                                  :          : or explicit        :
:           :                                  :          : `undefined` checks :
:           :                                  :          : on an array that   :
:           :                                  :          : is strictly typed  :
:           :                                  :          : as containing only :
:           :                                  :          : defined objects.   :
| **T2-11** | Explicit TypeScript Access       | Medium   | Prefixing internal |
:           : Modifiers                        :          : class methods or   :
:           :                                  :          : properties with an :
:           :                                  :          : underscore while   :
:           :                                  :          : leaving them       :
:           :                                  :          : functionally       :
:           :                                  :          : public.            :
| **T2-12** | Idiomatic Boolean Casting        | Medium   | Casting a          |
:           :                                  :          : potentially        :
:           :                                  :          : undefined boolean  :
:           :                                  :          : to a strict        :
:           :                                  :          : boolean using the  :
:           :                                  :          : nullish coalescing :
:           :                                  :          : operator.          :

--------------------------------------------------------------------------------

### Rules

#### T2-01: Targeted Type Casting over Broad Error Suppression

> **Rule:** Always use targeted type casts (e.g., `as any`) to bypass specific
> visibility constraints in unit tests rather than silencing the entire line
> with `@ts-expect-error`.
>
> **What:** Replace blanket `@ts-expect-error` directives with targeted type
> casts (e.g., `(element as any)`) when attempting to access private component
> methods or properties in unit tests.
>
> **Applies To:** TypeScript unit tests interacting with encapsulated component
> logic.
>
> **Why:** Using `// @ts-expect-error` suppresses all TypeScript errors on the
> subsequent line. Historically, this masked actual bugs like typos in test
> assertions (e.g., a typo in `assert.isFalse`), rendering the tests unreliable.
> Failing to adhere to this typically results in **Masked Bugs / Silent
> Failures**.

**Trap 1: Suppressing all TypeScript compiler errors on a line just to bypass a
private visibility check in a unit test.**

**Don't:**

```typescript
// BAD: Masks all TS errors, including typos in the assert statement.
// @ts-expect-error
assert.isFalse(element.hasAiComments());
```

**Do:**

```typescript
// GOOD: Bypasses visibility selectively while maintaining strict type checking on the assertion.
assert.isFalse((element as any).hasAiComments());
```

--------------------------------------------------------------------------------

#### T2-02: Removal of Underscore Prefixes for Reactive Properties

> **Rule:** Never use leading underscores for Lit element properties; strictly
> enforce private state using TypeScript access modifiers.
>
> **What:** Do not use leading underscores for Lit element properties. Rely on
> TypeScript `private` access modifiers to encapsulate internal state instead of
> naming conventions.
>
> **Applies To:** All LitElement `@property` and `@state` declarations.
>
> **Why:** Leading underscores were heavily used in the legacy Polymer
> implementation to denote privacy, but they violate current TypeScript
> strictness standards and style guidelines for the modernized PolyGerrit UI.
> Failing to adhere to this typically results in **Style Guide Violation**.

**Trap 1: Prefixing reactive Lit properties with an underscore to denote private
state.**

**Don't:**

```typescript
@property({type: String})
_passwordUrl: string | null = null;
```

**Do:**

```typescript
@property({type: String})
passwordUrl: string | null = null;
```

--------------------------------------------------------------------------------

#### T2-03: Elimination of TypeScript Compiler Suppressions in Unit Tests

> **Rule:** Must never bypass compiler checks to modify test internals;
> explicitly elevate visibility of the required property and document the
> exception.
>
> **What:** Unit tests must not bypass compiler checks using @ts-expect-error to
> test internal logic. Instead, widen the visibility of the target property or
> method from private to public/internal, and document it with a comment.
>
> **Applies To:** TypeScript unit test suites and component class files (e.g.,
> Lit components).
>
> **Why:** Developers were using @ts-expect-error directives to suppress
> compiler errors when assigning or calling private component members in tests.
> This masked actual type regressions from the TS compiler. Failing to adhere to
> this typically results in **Type Safety Bypass**.

**Trap 1: Suppressing the compiler error to mutate a private property in the
test setup.**

**Don't:**

```typescript
// In test file:
// @ts-expect-error
element.docsBaseUrl = 'https://docs.com/';
// @ts-expect-error
assert.equal(element.computeHelpUrl(), '...');
```

**Do:**

```typescript
// In component file:
// private but used in test
public docsBaseUrl = '';

// In test file (no suppressions):
element.docsBaseUrl = 'https://docs.com/';
assert.equal(element.computeHelpUrl(), '...');
```

--------------------------------------------------------------------------------

#### T2-04: Utilizing TypeScript Utility Types over Unsafe Casting

> **Rule:** Always construct precise subsets of interfaces using utility types
> like `Pick<T, K>` rather than forcibly blinding the compiler via `as Type`.
>
> **What:** When creating objects that fulfill only a specific subset of an
> interface's requirements, construct the correct type signature using
> TypeScript utility types (e.g., Pick<T, K>) rather than using 'as Type' type
> assertions to blind the compiler.
>
> **Applies To:** TypeScript data transformations, API response mapping, and
> component state variables.
>
> **Why:** A subset of a LabelDefinitionInfo object was generated and
> aggressively typed using `as LabelDefinitionInfo`. This misled consumers of
> the data about which properties were actually populated, creating potential
> runtime hazards. Failing to adhere to this typically results in **Unsafe Type
> Assertion**.

**Trap 1: Constructing an object with missing properties and masking the error
by casting it as the full interface type.**

**Don't:**

```typescript
const partial = {
  name: 'LabelName',
  values: { '+1': '' }
} as LabelDefinitionInfo;
```

**Do:**

```typescript
const partial: Pick<LabelDefinitionInfo, 'name'> & Pick<LabelDefinitionInfo, 'values'> = {
  name: 'LabelName',
  values: { '+1': '' }
};
```

--------------------------------------------------------------------------------

#### T2-05: Prototype Methods over Arrow Function Properties

> **Rule:** Always define class behaviors as standard prototype methods to avoid
> the excess instantiation overhead caused by arrow function properties.
>
> **What:** Define class behaviors using standard class methods rather than
> assigning arrow functions as class properties to optimize memory usage.
>
> **Applies To:** TypeScript class definitions across the application
> (Providers, Services, Models).
>
> **Why:** Assigning arrow functions directly as class properties caused a new
> instance of the function to be created in memory for every instantiation of
> the class, unnecessarily increasing memory overhead. Failing to adhere to this
> typically results in **Excessive Memory Overhead**.

**Trap 1: Using an arrow function assigned to a variable inside the class
body.**

**Don't:**

```typescript
export class LabelSuggestionsProvider {
  getSuggestions = (
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> => {
    // logic
  };
}
```

**Do:**

```typescript
export class LabelSuggestionsProvider {
  getSuggestions(
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> {
    // logic
  }
}
```

**Exceptions:** Arrow functions are acceptable if the method is passed around as
a callback and strictly requires preserving the `this` lexical binding without
manual `.bind(this)`.

--------------------------------------------------------------------------------

#### T2-06: Elimination of Unsafe `any` Type Casts

> **Rule:** Never use the `any` type; enforce strict bounds using concrete
> interfaces or rely on `unknown` for safe downcasting.
>
> **What:** The `any` type must be strictly avoided. Use explicit interfaces,
> strict types, or `unknown` for downcasting, eliminating `@ts-expect-error` and
> `@typescript-eslint/no-explicit-any`.
>
> **Applies To:** Global TypeScript codebase, particularly component state
> assignments, plugin configurations, and test setups.
>
> **Why:** Developer convenience led to pervasive use of `any` type casting,
> bypassing the compiler and masking structural mismatches that caused uncaught
> runtime exceptions when interfaces evolved. Failing to adhere to this
> typically results in **Type Erasure**.

**Trap 1: Suppressing TypeScript errors or forcibly casting objects to `any`
when dynamic types don't strictly align.**

**Don't:**

```typescript
// BAD: Bypassing type safety
// eslint-disable-next-line @typescript-eslint/no-explicit-any
.then((element: any) => {
  assert.strictEqual(element, module);
})
```

**Do:**

```typescript
// GOOD: Using unknown or explicit interfaces
.then((element: unknown) => {
  assert.strictEqual(element, module);
})
```

--------------------------------------------------------------------------------

#### T2-07: Testing Private State Encapsulation Rules

> **Rule:** Must never pierce class boundaries using bracket notation
> (`['property']`) in tests; natively expose and document properties required
> for testing.
>
> **What:** Do not bypass private property access via bracket notation
> (`element['privateProp']`) in tests. If a property must be exposed for
> testing, remove the `private` modifier and document it with a standard
> comment.
>
> **Applies To:** Component state definitions and their respective test suites.
>
> **Why:** Engineers were circumventing class lexical scope constraints by using
> bracket notation to set private component states in tests. This violated the
> TypeScript style guide and evaded static analysis tools. Failing to adhere to
> this typically results in **Encapsulation Violation**.

**Trap 1: Forcibly accessing class internals using string-keyed bracket notation
in test files.**

**Don't:**

```typescript
// BAD: Bypassing private scope
element['stages'] = [{ condition: 'status:open' }];
```

**Do:**

```typescript
// GOOD: Remove private, add explicit documentation, and use dot notation
// In component:
@state() // private but used in tests
stages: Stage[] = [];

// In test:
element.stages = [{ condition: 'status:open' }];
```

--------------------------------------------------------------------------------

#### T2-08: Eliminate Unsafe 'any' Type Casting in Test Stubs

> **Rule:** Never substitute actual types with `any` in mock definitions;
> enforce accurate mock function signatures or use `unknown`.
>
> **What:** Avoid using the `any` type when defining function arguments in stub
> callbacks (e.g., Sinon `callsFake`). Use the actual mocked type or `unknown`.
>
> **Applies To:** Sinon stubs and mock definitions within unit tests.
>
> **Why:** Using the 'any' type bypassed the TypeScript compiler's type
> checking, allowing signature mismatches between the mock and the actual
> implementation to silently pass. Failing to adhere to this typically results
> in **Silent Type Mismatches**.

**Trap 1: Casting function arguments to `any` within a mock's custom callback
function.**

**Don't:**

```typescript
sinon.stub(Obj, 'method').callsFake(function (this: Obj, account: any) { ... })
```

**Do:**

```typescript
sinon.stub(Obj, 'method').callsFake(function (this: Obj, account: unknown) { ... })
```

--------------------------------------------------------------------------------

#### T2-09: Suppression of Private Member Access in Tests via @ts-expect-error

> **Rule:** Always apply `@ts-expect-error` to suppress targeted visibility
> issues in mocks rather than annihilating all type validation by casting to
> `any`.
>
> **What:** When accessing private or static members in unit tests for mocking
> purposes, use the `@ts-expect-error` directive instead of casting the entire
> class or object to `any`.
>
> **Applies To:** Unit test setup scripts needing to stub private, protected, or
> unexported properties on classes or global objects.
>
> **Why:** Casting objects to 'any' completely stripped all type checking for
> subsequent operations. Utilizing `@ts-expect-error` maintains the type
> context, ensuring the test fails at compile-time if the underlying property's
> visibility or type changes in the future. Failing to adhere to this typically
> results in **Complete Type Loss**.

**Trap 1: Casting the class reference or global object to `any` to bypass
TypeScript's visibility and presence checks.**

**Don't:**

```typescript
// BAD: Overrides all type checking
const libLoader = (GrImageViewer as any).libLoader;
(window as any).resemble = sinon.stub();
```

**Do:**

```typescript
// GOOD: Suppresses visibility error but retains underlying type
// @ts-expect-error
const libLoader = GrImageViewer.libLoader;
// @ts-expect-error
window.resemble = sinon.stub();
```

--------------------------------------------------------------------------------

#### T2-10: Enforce Type Contracts Over Redundant Runtime Checks

> **Rule:** Must never litter code with redundant falsy checks for
> arrays/objects explicitly typed as defined; fix non-compliant test data
> upstream.
>
> **What:** Do not add redundant runtime filtering or null-checks for conditions
> that the TypeScript definitions explicitly forbid. Fix the upstream source or
> test data instead.
>
> **Applies To:** Business logic and data processing on strictly typed models.
>
> **Why:** Adding defensive runtime checks for strictly-typed data degraded
> readability and masked underlying flaws in test setups that were passing
> invalid, poorly-mocked data into functions. Failing to adhere to this
> typically results in **Masked Upstream Bugs / Cluttered Logic**.

**Trap 1: Adding `.filter(Boolean)` or explicit `undefined` checks on an array
that is strictly typed as containing only defined objects.**

**Don't:**

```typescript
// Method strictly accepts: changes: ChangeInfo[]
async sync(changes: ChangeInfo[]) {
  // BAD: Redundant runtime check
  const validChanges = changes.filter(c => c && isChangeInfo(c));
  const basicChanges = new Map(validChanges.map(c => [c.id, c]));
}
```

**Do:**

```typescript
// GOOD: Trust the contract and fix failing tests upstream
async sync(changes: ChangeInfo[]) {
  const basicChanges = new Map(changes.map(c => [c.id, c]));
}
```

**Exceptions:** External payloads lacking robust typing across integration
boundaries.

--------------------------------------------------------------------------------

#### T2-11: Explicit TypeScript Access Modifiers

> **Rule:** Always codify class visibility through strict `private`,
> `protected`, or `public` modifiers instead of falling back on leading
> underscore conventions.
>
> **What:** Class members and methods intended for internal use must be enforced
> using the TypeScript `private` access modifier rather than relying on the
> legacy `_` (underscore) naming convention.
>
> **Applies To:** TypeScript classes and Web Component definitions.
>
> **Why:** The codebase contained legacy properties like `_loading` and methods
> without explicit access modifiers, which failed to utilize the TypeScript
> compiler to prevent external dependencies from coupling to internal
> implementation details. Failing to adhere to this typically results in
> **Broken Encapsulation**.

**Trap 1: Prefixing internal class methods or properties with an underscore
while leaving them functionally public.**

**Don't:**

```typescript
// BAD: Relying on naming conventions for privacy
@state()
_loading = true;

renderBoolean() { ... }
```

**Do:**

```typescript
// GOOD: Enforcing privacy with TypeScript modifiers
@state()
private loading = true;

private renderCheckmark() { ... }
```

--------------------------------------------------------------------------------

#### T2-12: Idiomatic Boolean Casting

> **Rule:** Always cast optional or truthy/falsy values using the idiomatic
> double-negation operator (`!!`) rather than the nullish coalescing operator
> (`?? false`).
>
> **What:** To cast an optionally undefined or truthy/falsy value to a strict
> boolean, use the double-negation operator (`!!`) instead of the nullish
> coalescing operator (`?? false`).
>
> **Applies To:** TypeScript logic handling optional object properties or API
> responses.
>
> **Why:** During permission evaluation, `access?.is_owner ?? false` was flagged
> during review as unidiomatic, and the codebase was aligned to use standard
> double-negation for boolean casts. Failing to adhere to this typically results
> in **Unidiomatic Code**.

**Trap 1: Casting a potentially undefined boolean to a strict boolean using the
nullish coalescing operator.**

**Don't:**

```typescript
// BAD: Verbose and unidiomatic casting
this.isProjectOwner = access?.is_owner ?? false;
```

**Do:**

```typescript
// GOOD: Concise, standard boolean cast
this.isProjectOwner = !!access?.is_owner;
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T1 | Lit Framework Idioms & State Encapsulation - *TypeScript
    strictness principles govern the visibility and structural integrity of Lit
    component state variables and properties.*
*   **Downstream:** T3 | Hermetic Testing & Visual Regression - *Strict typing
    directly dictates the syntax and permitted mocking strategies for test stubs
    and fixtures.*

## Chapter: Hermetic Testing & Visual Regression

**Context:** This chapter mandates the strict isolation of unit tests,
comprehensive visual validation using full shadow DOM snapshots, and the
centralization of test data generation to guarantee hermetic execution and
prevent false-positive assertions.

### Summary

| Rule ID   | Principle /        | Priority | Primary Symptom / Trap           |
:           : Constraint         :          :                                  :
| :-------- | :----------------- | :------- | :------------------------------- |
| **T3-01** | Strict DOM Query   | High     | Querying for an element and      |
:           : Assertions in      :          : manually asserting its existence :
:           : Testing            :          : using an assertion library.      :
| **T3-02** | Shadow DOM Event   | Medium   | Relying exclusively on the       |
:           : Property Fallbacks :          : element property which may       :
:           : for Tests          :          : remain empty in test fixtures.   :
| **T3-03** | Comprehensive      | High     | Checking the `length` of         |
:           : Shadow DOM         :          : elements matching a query        :
:           : Snapshot           :          : selector to verify UI rendering. :
:           : Assertions         :          :                                  :
| **T3-04** | Centralized Test   | Medium   | Manually creating partial data   |
:           : Data Generation    :          : structures in tests and using    :
:           : via Factory        :          : type casting to satisfy the      :
:           : Helpers            :          : compiler.                        :
| **T3-05** | Isolation of       | Medium   | Adding a visualDiff snapshot     |
:           : Visual Regression  :          : assertion inside standard        :
:           : Tests              :          : logical unit tests.              :
| **T3-06** | Awaiting           | High     | Triggering a UI update and       |
:           : Asynchronous       :          : immediately asserting on the     :
:           : Render Cycles      :          : resulting DOM without waiting    :
:           : Before Assertions  :          : for the Lit rendering engine     :
:           :                    :          : lifecycle.                       :
| **T3-07** | DRY Centralization | Medium   | Duplicating 5+ lines of UI       |
:           : of Shared Test     :          : interaction setup across         :
:           : Setup Logic        :          : multiple test cases.             :
| **T3-08** | Avoid              | Medium   | Assigning a random, dynamic      |
:           : Monkey-Patching    :          : property to the component being  :
:           : Test-Only          :          : tested to track mock responses   :
:           : Properties on      :          : or internal states.              :
:           : Component          :          :                                  :
:           : Instances          :          :                                  :
| **T3-09** | Explicit Mocking   | High     | Allowing the component to        |
:           : of External        :          : natively load its third-party    :
:           : Network Assets in  :          : script dependencies during a     :
:           : Tests              :          : basic unit test fixture setup.   :
| **T3-10** | Strict Limits on   | High     | Increasing the Mocha test suite  |
:           : Test Timeouts for  :          : timeout inside a failing         :
:           : Visual Regressions :          : screenshot test to force it to   :
:           :                    :          : pass.                            :
| **T3-11** | Realistic          | High     | Rendering a generic template     |
:           : Component          :          : without required inputs and      :
:           : Initialization in  :          : asserting the shadow DOM is      :
:           : Tests              :          : empty.                           :
| **T3-12** | Descriptive and    | Medium   | Naming a test based on an        |
:           : Contextual Test    :          : abstract concept rather than the :
:           : Case Naming        :          : behavioral condition.            :
| **T3-13** | Production-Aligned | Medium   | Defining static mock data for    |
:           : Visual Regression  :          : visual tests that includes       :
:           : Mock Data          :          : properties normally filtered out :
:           :                    :          : by the production environment.   :

--------------------------------------------------------------------------------

### Rules

#### T3-01: Strict DOM Query Assertions in Testing

> **Rule:** Always use the `queryAndAssert` utility to locate and verify DOM
> elements simultaneously in unit tests.
>
> **What:** Use the `queryAndAssert` utility to locate DOM elements in unit
> tests instead of using `query` coupled with manual truthiness assertions or
> optional chaining.
>
> **Applies To:** Frontend Web Component testing; specifically LitElement test
> fixtures interacting with the DOM.
>
> **Why:** Using a standard `query` that returns `null` when an element is
> missing causes cryptic `TypeError`s later in the test execution. Asserting
> immediately provides clear, fail-fast test reporting. Failing to adhere to
> this typically results in **Test TypeErrors / Cryptic Failures**.

**Trap 1: Querying for an element and manually asserting its existence using an
assertion library.**

**Don't:**

```typescript
const warning = query(element, '.expensiveDiff');
assert.isOk(warning);
assert.include(warning.textContent, 'Diff too expensive');
```

**Do:**

```typescript
const warning = queryAndAssert(element, '.expensiveDiff');
assert.include(warning.textContent, 'Diff too expensive');
```

**Trap 2: Using optional chaining to silently swallow null elements when
checking class lists.**

**Don't:**

```typescript
const diffContainer = query(element, '.diffContainer');
assert.isTrue(diffContainer?.classList.contains('hidden'));
```

**Do:**

```typescript
const diffContainer = queryAndAssert(element, '.diffContainer');
assert.isTrue(diffContainer.classList.contains('hidden'));
```

#### T3-02: Shadow DOM Event Property Fallbacks for Tests

> **Rule:** Must implement explicit attribute fallbacks when reading custom
> event properties that fail to synchronize in hermetic test environments.
>
> **What:** When handling custom event properties (like `value` from custom
> `md-outlined-select` components) that fail to reliably synchronize to the
> target's property in the testing environment, safely fallback to reading the
> `value` attribute.
>
> **Applies To:** Lit element event handlers dealing with custom web components
> and executed in hermetic testing environments.
>
> **Why:** Automated tests simulating user selections in `md-outlined-select`
> were failing because the testing framework failed to read the value from the
> event target property, requiring an explicit fallback to the attribute.
> Failing to adhere to this typically results in **False Negative Test
> Failures**.

**Trap 1: Relying exclusively on the element property which may remain empty in
test fixtures.**

**Don't:**

```typescript
@change=${(e: Event) => {
  this.selectedLabelForVote = (e.target as HTMLSelectElement).value;
}}
```

**Do:**

```typescript
@change=${(e: Event) => {
  // TODO: Remove reading from attribute once test env issue is fixed
  this.selectedLabelForVote =
    ((e.target as HTMLSelectElement).value ||
    (e.target as HTMLSelectElement).getAttribute('value')) ?? '';
}}
```

#### T3-03: Comprehensive Shadow DOM Snapshot Assertions

> **Rule:** Never use shallow DOM assertions; always validate UI structures
> using `assert.shadowDom.equal`.
>
> **What:** UI component tests must use `assert.shadowDom.equal` to validate the
> entire rendered structure against an expected HTML snapshot, rather than
> asserting shallow DOM properties.
>
> **Applies To:** Frontend UI unit testing of Lit web components.
>
> **Why:** During refactoring, comprehensive DOM checks were accidentally
> replaced with shallow element counts, creating a blind spot where regressions
> in structural layout, attributes, and text content could slip past CI. Failing
> to adhere to this typically results in **Missed UI Regressions**.

**Trap 1: Checking the `length` of elements matching a query selector to verify
UI rendering.**

**Don't:**

```typescript
// BAD: Shallow DOM assertion
const flowElements = element.shadowRoot!.querySelectorAll('.flow');
assert.equal(flowElements.length, 2);
```

**Do:**

```typescript
// GOOD: Full Shadow DOM snapshot assertion
assert.shadowDom.equal(
  element,
  /* HTML */ `
    <div class="container">
      <div class="flow">...</div>
      <div class="flow">...</div>
    </div>
  `
);
```

#### T3-04: Centralized Test Data Generation via Factory Helpers

> **Rule:** Always construct mock data structures using centralized factory
> helpers configured with `Partial<T>` overrides.
>
> **What:** Avoid manually instantiating large mock data payloads. Utilize
> centralized factory functions that accept `Partial<T>` and provide sensible
> defaults, eliminating manual type assertions.
>
> **Applies To:** Frontend unit tests and mock data setup blocks.
>
> **Why:** Tests repeatedly hardcoded large data objects, making the test suite
> brittle to schema changes and requiring explicit type casting (`as FlowInfo`)
> to bypass compiler complaints about missing fields. Failing to adhere to this
> typically results in **Brittle Tests**.

**Trap 1: Manually creating partial data structures in tests and using type
casting to satisfy the compiler.**

**Don't:**

```typescript
// BAD: Manual object creation with casting
const flow = {
  uuid: 'flow1',
  owner: {name: 'owner1'},
  created: '2025-01-01T10:00:00.000Z' as Timestamp,
} as FlowInfo;
```

**Do:**

```typescript
// GOOD: Using a test data generator with Partial overrides
const flow = createFlow({
  uuid: 'flow1',
  owner: {name: 'owner1', _account_id: 1 as AccountId},
});
```

#### T3-05: Isolation of Visual Regression Tests

> **Rule:** Must isolate DOM snapshot and screenshot operations into parallel
> `_screenshot_test.ts` files.
>
> **What:** Visual regression (screenshot) tests utilizing DOM snapshots must
> reside in a separate `_screenshot_test.ts` file rather than being appended to
> standard unit test files.
>
> **Applies To:** Test directory structure and files utilizing `visualDiff`.
>
> **Why:** Combining fast unit tests with slow visual diffing tests bloated unit
> execution times and complicated testing step separation in the CI pipeline.
> Failing to adhere to this typically results in **CI Bottleneck**.

**Trap 1: Adding a visualDiff snapshot assertion inside standard logical unit
tests.**

**Don't:**

*   Putting visual `visualDiff` test blocks into the standard
    `gr-[component]_test.ts` file alongside business logic tests.

**Do:**

*   Creating a parallel `gr-[component]_screenshot_test.ts` dedicated
    exclusively to visual assertions.

#### T3-06: Awaiting Asynchronous Render Cycles Before Assertions

> **Rule:** Always `await element.updateComplete` prior to evaluating DOM nodes
> following a UI state change.
>
> **What:** Tests asserting UI state changes must strictly `await
> element.updateComplete` before querying DOM elements or their properties,
> avoiding false positive evaluations.
>
> **Applies To:** Lit web component unit tests.
>
> **Why:** Tests were erroneously passing because asynchronous state updates
> lacked `await`, causing assertions to execute and pass before the updated
> component render cycle actually fired. Failing to adhere to this typically
> results in **False Positive Tests**.

**Trap 1: Triggering a UI update and immediately asserting on the resulting DOM
without waiting for the Lit rendering engine lifecycle.**

**Don't:**

```typescript
// BAD: Synchronous assertion after state change
element.renderInOrder([{path: 'p2'}]);
assert.equal(reviewStub.callCount, 1);
```

**Do:**

```typescript
// GOOD: Awaiting update completion
await element.renderInOrder([{path: 'p2'}]);
await element.updateComplete;
assert.equal(reviewStub.callCount, 1);
```

#### T3-07: DRY Centralization of Shared Test Setup Logic

> **Rule:** Must extract repeated setup mechanisms and multi-step UI sequences
> into centralized, shared asynchronous helpers.
>
> **What:** Repeated interaction sequences required to establish a test's
> initial state (e.g., clicking menus, awaiting cycles, querying elements) must
> be extracted into asynchronous helper functions.
>
> **Applies To:** Component test suites with repetitive, multi-step UI
> interaction setups.
>
> **Why:** Tests were repeatedly copying and pasting the multi-step DOM
> interaction needed to open a modal and fetch an input reference, inflating the
> codebase and making structural changes painful. Failing to adhere to this
> typically results in **High Maintenance Burden**.

**Trap 1: Duplicating 5+ lines of UI interaction setup across multiple test
cases.**

**Don't:**

*   Copying and pasting the same set of element lookups, simulated clicks, and
    `await element.updateComplete;` lines into every test block.

**Do:**

*   Extracting the setup steps into a shared, typed async helper (e.g., `async
    function openLinkDialogAndGetInput(): Promise<HTMLInputElement>`) and
    calling it in each test.

#### T3-08: Avoid Monkey-Patching Test-Only Properties on Component Instances

> **Rule:** Never mutate component instances with custom, untyped tracking
> properties; track test states using locally scoped variables.
>
> **What:** Avoid mutating component instances with test-only custom properties
> to track state during tests. Utilize locally scoped variables inside the test
> setup or perform proper stub verification instead.
>
> **Applies To:** Unit testing, specifically when stubbing instance methods or
> external calls.
>
> **Why:** Attaching custom test-only properties (like tracking URLs directly on
> the element) polluted the component object model, bypassed encapsulation, and
> risked state leakage between unit tests. Failing to adhere to this typically
> results in **Test State Leakage**.

**Trap 1: Assigning a random, dynamic property to the component being tested to
track mock responses or internal states.**

**Don't:**

```typescript
.callsFake(function (this: MyComponent, arg: any) {
  // BAD: Modifying the instance for testing purposes
  (this as any).__test_url = arg;
  return 'data:image...';
});
// ... later in the test ...
assert.equal((element as any).__test_url, expectedUrl);
```

**Do:**

```typescript
let generatedUrl: string;
.callsFake(function (this: MyComponent, arg: unknown) {
  // GOOD: Using a scoped variable for tracking
  generatedUrl = arg;
  return 'data:image...';
});
// ... later in the test ...
assert.equal(generatedUrl, expectedUrl);
```

#### T3-09: Explicit Mocking of External Network Assets in Tests

> **Rule:** Must completely stub remote library calls or image-fetching
> mechanisms to execute entirely in isolation.
>
> **What:** External libraries that trigger network requests (e.g., fetching
> scripts or image diffing workers) must be fully mocked in local test setups to
> prevent 404 network errors and ensure hermetic execution.
>
> **Applies To:** Unit testing for components integrating with third-party,
> dynamically loaded libraries.
>
> **Why:** Failure to mock external dependencies caused the test runner to
> attempt fetching remote or non-existent local assets, resulting in 404 console
> errors, flaky tests, and significantly slower execution times. Failing to
> adhere to this typically results in **404 Network Errors**.

**Trap 1: Allowing the component to natively load its third-party script
dependencies during a basic unit test fixture setup.**

**Don't:**

```typescript
setup(async () => {
  // BAD: Component attempts to download external libraries over the network
  element = await fixture(`<my-viewer></my-viewer>`);
});
```

**Do:**

```typescript
setup(async () => {
  // GOOD: Mock external library loaders to resolve instantly
  // @ts-expect-error
  const libLoader = MyViewer.libLoader;
  sinon.stub(libLoader, 'getLibrary').resolves();

  // @ts-expect-error
  window.externalLib = sinon.stub().returns({ ... });

  element = await fixture(`<my-viewer></my-viewer>`);
});
```

#### T3-10: Strict Limits on Test Timeouts for Visual Regressions

> **Rule:** Never augment native test timeouts (`this.timeout()`) as a
> workaround for slow rendering components or unreliable baselines.
>
> **What:** Do not artificially inflate test timeouts (e.g., Mocha
> `this.timeout()`) to mask slow component rendering or flakiness in visual
> regression tests. Maintain default timeouts.
>
> **Applies To:** Screenshot testing and visual regression test suites.
>
> **Why:** Artificially extending timeouts allowed significant performance
> regressions in UI rendering to go unnoticed, as tests would wait excessively
> long for slow components to stabilize rather than failing fast. Failing to
> adhere to this typically results in **Masked Performance Regressions**.

**Trap 1: Increasing the Mocha test suite timeout inside a failing screenshot
test to force it to pass.**

**Don't:**

```typescript
suite('component screenshot tests', function () {
  this.timeout(4000);
  // ... test body ...
});
```

**Do:**

```typescript
suite('component screenshot tests', () => {
  // Default timeout ensures fast failure if rendering degrades
  // Update screenshots with CLI commands if baseline legitimately changed
  // ... test body ...
});
```

#### T3-11: Realistic Component Initialization in Tests

> **Rule:** Must initialize required component properties to functional values
> prior to asserting against the shadow DOM.
>
> **What:** Unit tests that assert against a component's shadow DOM must
> initialize the component with realistic property states, ensuring the DOM is
> not trivially empty or unrendered.
>
> **Applies To:** Lit component tests doing Shadow DOM assertions.
>
> **Why:** Asserting against an empty or completely uninitialized DOM resulted
> in false-positive test passes that failed to verify any actual template or
> structural logic. Failing to adhere to this typically results in **False
> Positive Tests**.

**Trap 1: Rendering a generic template without required inputs and asserting the
shadow DOM is empty.**

**Don't:**

```typescript
test('render', async () => {
  await element.updateComplete;
  assert.shadowDom.equal(element, '');
});
```

**Do:**

```typescript
test('render', async () => {
  element.name = 'Test';
  await element.updateComplete;
  assert.shadowDom.equal(
    element,
    `<div><h3 class="title">Test</h3></div>`
  );
});
```

#### T3-12: Descriptive and Contextual Test Case Naming

> **Rule:** Always name test cases with strict, functional "renders [state] if
> [condition]" descriptions.
>
> **What:** Test cases must use descriptive 'renders X if Y' naming structures
> rather than relying on abstract, ambiguous, or overly technical jargon that
> lacks functional context.
>
> **Applies To:** Unit test descriptions (the string argument in `test()` or
> `it()`).
>
> **Why:** Ambiguous test names using mathematical or overly specific internal
> jargon made it difficult for developers to deduce the functional intent of the
> test upon failure without reading the implementation. Failing to adhere to
> this typically results in **Unclear Test Intent**.

**Trap 1: Naming a test based on an abstract concept rather than the behavioral
condition.**

**Don't:**

*   test('render attempt ordinal', async () => { ... });

**Do:**

*   test('renders attempt number if not single attempt', async () => { ... });

#### T3-13: Production-Aligned Visual Regression Mock Data

> **Rule:** Must guarantee that mock datasets used in visual regressions bypass
> data points filtered out by production pipeline logic.
>
> **What:** Mock data structures used to drive screenshot/visual regression
> tests must precisely mirror the data shapes and filtering logic deployed in
> the production frontend.
>
> **Applies To:** Frontend visual regression tests (`*_screenshot_test.ts`) and
> mock data fixtures.
>
> **Why:** Visual tests previously hardcoded reference types that were
> intentionally stripped out by frontend plugins in production. This resulted in
> visual tests asserting on UI components that users would never actually see.
> Failing to adhere to this typically results in **False Positive Tests**.

**Trap 1: Defining static mock data for visual tests that includes properties
normally filtered out by the production environment.**

**Don't:**

*   Defining test data that bypasses standard application filtering logic to
    artificially inflate component coverage.

**Do:**

*   Removing filtered mock references from visual test configurations so the
    test exactly matches the filtered production state.

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T1 | Lit Framework Idioms & State Encapsulation - *Hermetic UI
    tests strictly rely on accurate Lit lifecycle declarations and asynchronous
    `updateComplete` chains mapped here.*
*   **Upstream:** T2 | TypeScript Strictness & Type Safety - *Type-safe factory
    generators utilizing `Partial<T>` and eliminating `any` casting rely
    completely on the strictness mandates of this domain.*

## Chapter: Client-Side Performance & Telemetry

**Context:** This theme governs the optimization of client-side performance by
enforcing strict telemetry on synchronous CPU-bound operations and minimizing
network latency. It mandates the caching of asynchronous API promises, the
derivation of state from existing payloads, and the rigid bounding of background
data requests.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T4-01** | Synchronous Execution     | High     | Assuming a save operation |
:           : Telemetry Profiling       :          : is only slow due to       :
:           :                           :          : network requests and      :
:           :                           :          : leaving synchronous data  :
:           :                           :          : preparation unmeasured.   :
| **T4-02** | Elimination of Redundant  | High     | Executing an asynchronous |
:           : API Data Fetches          :          : API call to pull data     :
:           :                           :          : that already exists as a  :
:           :                           :          : property of a currently   :
:           :                           :          : loaded state object.      :
| **T4-03** | Promise-Based API Request | High     | Calling the API directly  |
:           : Caching                   :          : on every query invocation :
:           :                           :          : without storing the       :
:           :                           :          : ongoing or resolved       :
:           :                           :          : request.                  :
| **T4-04** | Capped Payloads for       | High     | Passing `undefined` or    |
:           : Autocomplete Suggestion   :          : leaving limit parameters  :
:           : Requests                  :          : empty for data            :
:           :                           :          : aggregation endpoints     :
:           :                           :          : used merely for           :
:           :                           :          : suggestion dropdowns.     :

--------------------------------------------------------------------------------

### Rules

#### T4-01: Synchronous Execution Telemetry Profiling

> **Rule:** Always instrument synchronous, CPU-bound logic with telemetry timers
> to surface main-thread blocking operations. Never assume UI latency is solely
> caused by asynchronous network requests.
>
> **What:** Instrument synchronous, CPU-bound logic (e.g., object comparison,
> distance calculations) with telemetry timers to identify main-thread blocking
> operations, rather than solely profiling asynchronous network calls.
>
> **Applies To:** Frontend Performance Profiling; particularly in high-frequency
> or data-heavy UI actions like draft comment saving and text matching.
>
> **Why:** Historically, performance metrics focused heavily on network request
> times. This hid UI freezes caused by expensive O(n^2) synchronous calculations
> on the main thread, such as running deep equality checks against large
> AI-generated patch suggestions. Failing to adhere to this typically results in
> **Main Thread UI Freezes**.

**Trap 1: Assuming a save operation is only slow due to network requests and
leaving synchronous data preparation unmeasured.**

**Don't:**

```typescript
// BAD: Only timing the network request
const result = await this.restApiService.saveDraft(draft);
```

**Do:**

```typescript
// GOOD: Timing CPU-bound data preparation independently
const fixTimer = this.reporting.getTimer('UpdateDraftComment - isFixSuggestionChanged');
if (this.isFixSuggestionChanged()) {
  draft.fix_suggestions = this.getFixSuggestions();
}
fixTimer.end();

const networkTimer = this.reporting.getTimer('UpdateDraftComment - network');
const result = await this.restApiService.saveDraft(draft);
networkTimer.end();
```

--------------------------------------------------------------------------------

#### T4-02: Elimination of Redundant API Data Fetches

> **Rule:** Always derive state directly from globally hydrated context objects
> instead of executing redundant REST API requests.
>
> **What:** Derive required state directly from existing context payloads (such
> as the globally hydrated Change object) rather than making redundant network
> requests to fetch subsets of identical data.
>
> **Applies To:** Frontend components querying repository metadata, permissions,
> or labels.
>
> **Why:** The UI was performing a dedicated REST API call to fetch repository
> labels, adding UI latency, even though the allowed labels were already
> hydrated within the `change.permitted_labels` property on the global change
> object. Failing to adhere to this typically results in **Redundant Network
> Latency**.

**Trap 1: Executing an asynchronous API call to pull data that already exists as
a property of a currently loaded state object.**

**Don't:**

```typescript
// Anti-pattern: Fetching when the data is already there
this.repoLabels = await this.restApiService.getRepoLabels(change.project);
```

**Do:**

```typescript
// Preferred: Mapping from existing context
const permittedLabels = change.permitted_labels ?? {};
this.repoLabels = Object.entries(permittedLabels).map(...);
```

**Exceptions:** If the existing payload is known to be stale or intentionally
truncated for performance reasons in that specific context.

--------------------------------------------------------------------------------

#### T4-03: Promise-Based API Request Caching

> **Rule:** Must cache the Promise of an API request to prevent redundant
> network calls during rapid UI events, ensuring errors resolve safely to avoid
> cache poisoning.
>
> **What:** Cache the Promise of an API request to prevent redundant network
> calls during rapid UI events (e.g., autocomplete typing), and ensure failed
> requests resolve to a safe fallback (like undefined) so the cache is not
> poisoned permanently.
>
> **Applies To:** Frontend services making API calls based on user input,
> specifically Autocomplete and Suggestion Providers.
>
> **Why:** Fetching data on every keystroke without caching led to spamming the
> backend API, especially when the data subset being searched was static (e.g.,
> repository labels) and could be filtered purely on the client side. Failing to
> adhere to this typically results in **Backend API Spam / High Latency**.

**Trap 1: Calling the API directly on every query invocation without storing the
ongoing or resolved request.**

**Don't:**

```typescript
getSuggestions(expression: string) {
  if (!this.repoName) return Promise.resolve([]);
  return this.restApiService.getRepoLabels(this.repoName).then(labels => {
    // filter logic
  });
}
```

**Do:**

```typescript
getSuggestions(expression: string) {
  if (!this.repoName) return Promise.resolve([]);
  if (!this.cachedLabelsPromise) {
    this.cachedLabelsPromise = this.restApiService
      .getRepoLabels(this.repoName)
      .catch(err => {
        reportingService.error('Provider', err);
        return undefined; // Ensure caught errors resolve safely
      });
  }
  return this.cachedLabelsPromise.then(labels => {
    // filter logic
  });
}
```

**Exceptions:** Queries that rely on server-side filtering (e.g., passing the
user's keystroke to the backend endpoint directly) cannot be cached in this
manner.

--------------------------------------------------------------------------------

#### T4-04: Capped Payloads for Autocomplete Suggestion Requests

> **Rule:** Never execute unbounded background API queries; always enforce hard
> payload limits for components like autocomplete and dialogs to prevent UI
> freezes.
>
> **What:** Backend API queries triggered by background UI components (like
> autocomplete dropdowns or dialog filling) must be strictly bounded to a hard
> limit to prevent downloading excessive payloads.
>
> **Applies To:** API query parameters, specifically REST API requests fetching
> background data for UI presentation.
>
> **Why:** An unbounded query for open changes downloaded over 600kB of data
> from the network, causing a severe UX lag and freezing the user interface on
> slower internet connections. Failing to adhere to this typically results in
> **Network Bottleneck**.

**Trap 1: Passing `undefined` or leaving limit parameters empty for data
aggregation endpoints used merely for suggestion dropdowns.**

**Don't:**

```typescript
// BAD: Unbounded fetching for suggestions
await this.restApiService.getChanges(
  undefined, // no limit
  'is:open -age:90d'
);
```

**Do:**

```typescript
// GOOD: Hardcoded maximum to prevent bandwidth exhaustion
await this.restApiService.getChanges(
  /* changeNumber=*/ 450,
  'is:open -age:90d'
);
```

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T6 | API Integration & Error Handling - *Defines the core
    structures of REST API operations and error fallback mechanisms necessary
    for effective client-side caching.*
*   **Downstream:** T7 | AI Context & Telemetry Payload Optimization - *Consumes
    the telemetry pipelines defined here to bound and optimize specialized AI
    interaction reporting.*

## Chapter: CSS Architecture & Design System Consistency

**Context:** This domain governs the consistent application of styles across the
UI, mandating the use of content-driven layout techniques, externalized custom
property configurations for visual assets, and declarative Lit directives over
imperative inline styling.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T5-01** | Dynamic Sizing for Custom | Medium   | Embedding static pixel    |
:           : SVG Icons via CSS         :          : values for width and      :
:           :                           :          : height inside the raw SVG :
:           :                           :          : literal.                  :
| **T5-02** | Dynamic Width via         | Medium   | Hardcoding width in       |
:           : Content-Driven CSS Sizing :          : pixels or percentages to  :
:           :                           :          : achieve visual uniformity :
:           :                           :          : despite unpredictable     :
:           :                           :          : content strings.          :
| **T5-03** | Declarative Conditional   | Medium   | Controlling visibility by |
:           : Visibility using Class    :          : binding ternary operators :
:           : Maps                      :          : to the inline `style`     :
:           :                           :          : attribute.                :

--------------------------------------------------------------------------------

### Rules

#### T5-01: Dynamic Sizing for Custom SVG Icons via CSS

> **Rule:** Always omit hardcoded dimensional attributes from custom SVG markup.
> Must control icon sizing dynamically using CSS custom properties to ensure
> cross-context scalability.
>
> **What:** Custom SVG icon definitions must not contain hardcoded `width` or
> `height` attributes within the markup. Dimensions should be omitted from the
> SVG template and controlled dynamically via CSS custom properties.
>
> **Applies To:** Lit icon wrapper components (`gr-icon`) and centralized custom
> SVG asset files.
>
> **Why:** Hardcoded dimensions within SVG source strings forced icons into
> rigid sizes, preventing them from scaling properly when nested inside generic
> buttons, dense lists, or varying display contexts. Failing to adhere to this
> typically results in **Layout Clipping / Unresponsive UI**.

**Trap 1: Embedding static pixel values for width and height inside the raw SVG
literal.**

**Don't:**

```typescript
const spark = svg`<svg width="24px" height="24px" viewBox="0 0 960 960">...</svg>`;
```

**Do:**

```typescript
const spark = svg`<svg viewBox="0 0 960 960">...</svg>`;

// Within gr-icon CSS:
// svg {
//   width: var(--gr-icon-size, 20px);
//   height: var(--gr-icon-size, 20px);
// }
```

--------------------------------------------------------------------------------

#### T5-02: Dynamic Width via Content-Driven CSS Sizing

> **Rule:** Always prefer `width: fit-content` over arbitrary fixed pixel limits
> for dynamically generated UI cards. Never enforce visual uniformity at the
> expense of content readability.
>
> **What:** Use `width: fit-content` for dynamically generated UI cards rather
> than uniform fixed widths when content length is variable and unpredictable.
>
> **Applies To:** UI component styling, specifically CSS rules for lists of
> cards or informational blocks.
>
> **Why:** Fixed-width cards were initially suggested for uniform alignment, but
> variable-length rules caused clipping or excessive whitespace. `fit-content`
> provided a more flexible baseline layout. Failing to adhere to this typically
> results in **Visual Clipping**.

**Trap 1: Hardcoding width in pixels or percentages to achieve visual uniformity
despite unpredictable content strings.**

**Don't:**

```css
/* BAD: Fixed width that might clip */
.flow-card {
  width: 300px;
}
```

**Do:**

```css
/* GOOD: Width determined by content */
.flow-card {
  width: fit-content;
}
```

**Exceptions:** Cases where uniform width is strictly mandated by UX mocks and
content is truncated gracefully with ellipses.

--------------------------------------------------------------------------------

#### T5-03: Declarative Conditional Visibility using Class Maps

> **Rule:** Must use standard CSS utility classes via Lit's `classMap` directive
> to toggle element visibility. Never bind ternary operators to inject inline
> style strings.
>
> **What:** Conditional visibility logic must rely on a standard CSS `.hidden`
> utility class applied via Lit's `classMap` directive, rather than injecting
> inline style strings.
>
> **Applies To:** Component rendering templates (`html` strings) handling
> dynamically hidden UI states.
>
> **Why:** Visibility was frequently controlled via verbose ternary operators
> injecting hardcoded CSS text into `style` attributes, making code harder to
> read and reuse. Failing to adhere to this typically results in **Brittle
> Inline Styling**.

**Trap 1: Controlling visibility by binding ternary operators to the inline
`style` attribute.**

**Don't:**

```typescript
// BAD: Inline conditional styles
<md-icon-button
  style=${this.supportsHistory ? '' : 'visibility:hidden; pointer-events:none;'}
>
```

**Do:**

```typescript
// GOOD: Using Lit classMap with a dedicated CSS rule
<md-icon-button
  class=${classMap({
    'history-button': true,
    'hidden': !this.supportsHistory
  })}
>
```

**Exceptions:** Truly dynamic, mathematically computed layout styles (e.g.,
precise pixel offsets or user-defined color themes).

--------------------------------------------------------------------------------

### Cross-Domain Dependencies

*   **Upstream:** T1 | Lit Framework Idioms & State Encapsulation - *Lit
    directives like `classMap` provide the declarative mechanism required to
    apply standard CSS utility classes effectively.*

## Chapter: API Integration & Error Handling

**Context:** This chapter governs the resilient integration of frontend logic
with REST APIs. It establishes strict constraints for maintaining backend
payload parity, explicitly modeling structural variances, and enforcing
centralized error handling over localized `try...catch` blocks.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T6-01** | Explicit Server Error     | High     | Firing mutative API       |
:           : Reporting in REST API     :          : requests without          :
:           : Calls                     :          : explicitly configuring    :
:           :                           :          : the fetch client to       :
:           :                           :          : report server-side HTTP   :
:           :                           :          : errors.                   :
| **T6-02** | Strict Frontend-Backend   | Critical | Using a descriptive but   |
:           : API Payload Parity        :          : incorrect property name   :
:           :                           :          : in the UI that does not   :
:           :                           :          : match the exact backend   :
:           :                           :          : JSON key.                 :
| **T6-03** | Delegation to Centralized | Medium   | Wrapping API calls in     |
:           : REST API Error Handlers   :          : verbose catch blocks      :
:           :                           :          : solely to surface generic :
:           :                           :          : network failure messages. :
| **T6-04** | Centralized Error         | Medium   | Wrapping an API call in a |
:           : Callbacks for API Calls   :          : try/catch block and       :
:           :                           :          : firing a custom error     :
:           :                           :          : event on catch alongside  :
:           :                           :          : null checks.              :
| **T6-05** | Modeling Inconsistent     | Medium   | Assuming a single payload |
:           : Backend API Payloads      :          : structure across          :
:           :                           :          : disparate endpoints and   :
:           :                           :          : experiencing undefined    :
:           :                           :          : runtime property access.  :

--------------------------------------------------------------------------------

### Rules

#### T6-01: Explicit Server Error Reporting in REST API Calls

> **Rule:** Always enable explicit server error reporting for mutating API
> requests (PUT, POST, DELETE) to guarantee backend failures reach the UI.
>
> **What:** State-mutating REST API calls (PUT, POST, DELETE) must explicitly
> enable server error reporting in their configuration to ensure backend
> failures are caught and surfaced to the UI.
>
> **Applies To:** REST API service layer (`gr-rest-api-impl.ts`), specifically
> when migrating to the new `fetch` helper structure.
>
> **Why:** During the migration to a modern REST API helper module, API failures
> for mutating operations could be silently swallowed unless `reportServerError:
> true` was explicitly passed in the request configuration. Failing to adhere to
> this typically results in **Silent API Failures**.

**Trap 1: Firing mutative API requests without explicitly configuring the fetch
client to report server-side HTTP errors.**

**Don't:**

```typescript
this._restApiHelperNew.fetch({
  fetchOptions: getFetchOptions({
    method: HttpMethod.PUT,
    body: config,
  }),
  url,
});
```

**Do:**

```typescript
this._restApiHelperNew.fetch({
  fetchOptions: getFetchOptions({
    method: HttpMethod.PUT,
    body: config,
  }),
  url,
  reportServerError: true,
});
```

--------------------------------------------------------------------------------

#### T6-02: Strict Frontend-Backend API Payload Parity

> **Rule:** Must perfectly mirror the exact naming conventions of backend JSON
> response fields within frontend TypeScript interfaces; never substitute
> assumed names.
>
> **What:** Frontend TypeScript interfaces must perfectly mirror the naming
> conventions of the backend JSON response fields; UI logic cannot substitute
> similar or assumed names.
>
> **Applies To:** REST API models, data layer mappings, and UI conditionals
> consuming backend payloads.
>
> **Why:** A UI feature designed to hide a component when a diff was too large
> failed silently in production because the frontend referenced
> `diffs_too_expensive_to_compute` while a previous implementation or
> documentation referred to `too_expensive_to_compute`, resulting in
> `undefined`. Failing to adhere to this typically results in **Silent UI State
> Failures**.

**Trap 1: Using a descriptive but incorrect property name in the UI that does
not match the exact backend JSON key.**

**Don't:**

```typescript
// BAD: Backend sends 'diffs_too_expensive_to_compute'
if (this.file?.too_expensive_to_compute) {
  renderWarning();
}
```

**Do:**

```typescript
// GOOD: Exact matching property name
if (this.file?.diffs_too_expensive_to_compute) {
  renderWarning();
}
```

--------------------------------------------------------------------------------

#### T6-03: Delegation to Centralized REST API Error Handlers

> **Rule:** Never wrap standard REST API calls in explicit `try...catch` blocks
> to handle generic network failures.
>
> **What:** Do not wrap standard REST API calls in explicit try...catch blocks
> to handle generalized network failures, as the global framework centrally
> intercepts these and dispatches user-visible network errors.
>
> **Applies To:** API service integrations and asynchronous handlers making
> network requests.
>
> **Why:** A reviewer expressed concern about unhandled promise rejections if a
> network call failed inside a finally block. The framework is designed
> specifically not to throw standard API errors back to the caller unless
> explicitly opted into via a custom errFn, handling generic network error
> alerting natively. Failing to adhere to this typically results in **Redundant
> Error Boilerplate**.

**Trap 1: Wrapping API calls in verbose catch blocks solely to surface generic
network failure messages.**

**Don't:**

```typescript
try {
  await this.restApiService.createFlow(changeNum);
} catch (e) {
  fireNetworkError(this, e);
}
```

**Do:**

```typescript
// Fire and let the framework's centralized errFn handle generic failures.
await this.restApiService.createFlow(changeNum);
```

**Exceptions:** When a specific component logic must intercept an error
silently, or when a custom `errFn` is explicitly provided to bypass default
handling.

--------------------------------------------------------------------------------

#### T6-04: Centralized Error Callbacks for API Calls

> **Rule:** Always use built-in centralized error callback mechanisms (`errFn`)
> provided by the REST API service instead of manual `try...catch` blocks.
>
> **What:** Use built-in centralized error callback mechanisms (`errFn`)
> provided by the REST API service instead of wrapping calls in manual
> `try...catch` blocks that result in redundant local error dispatching.
>
> **Applies To:** Asynchronous REST API calls within frontend components.
>
> **Why:** Manual `try...catch` blocks often resulted in duplicate error
> notifications being fired to the user—once for the native fetch rejection by
> the global handler, and once for the custom component fallback. Failing to
> adhere to this typically results in **Duplicate Error Notifications**.

**Trap 1: Wrapping an API call in a try/catch block and firing a custom error
event on catch alongside null checks.**

**Don't:**

```typescript
try {
  response = await this.restApiService.getPatchContent();
} catch (e) {
  fireError(this, 'Failed');
  return;
}
if (!response) { fireError(this, 'Failed'); return; }
```

**Do:**

```typescript
const response = await this.restApiService.getPatchContent(errFn);
if (!response) return;
```

--------------------------------------------------------------------------------

#### T6-05: Modeling Inconsistent Backend API Payloads

> **Rule:** Must explicitly document and type structural variances with optional
> fields when backend endpoints return disparate keys for the same entity.
>
> **What:** When backend endpoints return structurally differing payload keys
> for the same conceptual entity, the frontend interface must explicitly type
> the variance and track the technical debt, rather than forcing a singular,
> inaccurate type.
>
> **Applies To:** Frontend API interface definitions and REST service layers.
>
> **Why:** Two related backend chat endpoints returned data under different keys
> (`response` vs `chat_response`). Forcing the frontend to expect only
> `response` broke conversation loading, necessitating explicit optional types.
> Failing to adhere to this typically results in **Type Safety Violation**.

**Trap 1: Assuming a single payload structure across disparate endpoints and
experiencing undefined runtime property access.**

**Don't:**

```typescript
// BAD: Forces all endpoints to return 'response'
interface ConversationTurn {
  response: ChatResponse;
}
```

**Do:**

```typescript
// GOOD: Accurately model the backend variance and track the tech debt
interface ConversationTurn {
  response: ChatResponse;
  // TODO: Clean this up - when loadConversation is used we get chat_response instead of response
  chat_response?: ChatResponse;
}
```

## Chapter: AI Context & Telemetry Payload Optimization

**Context:** This domain governs the client-side lifecycle and optimization of
data structures sent to AI agents and telemetry pipelines. It strictly dictates
payload deduplication strategies, transparent AI token constraint surfacing, and
rigorous parsing validation to prevent silent context loss.

### Summary

| Rule ID   | Principle / Constraint    | Priority | Primary Symptom / Trap    |
| :-------- | :------------------------ | :------- | :------------------------ |
| **T7-01** | Elimination of Implicit   | Medium   | Manually injecting the    |
:           : Telemetry Payload Fields  :          : current domain/host into  :
:           :                           :          : the analytics event       :
:           :                           :          : detail object.            :
| **T7-02** | Deduplication of          | Medium   | Defining telemetry        |
:           : Telemetry Payload         :          : interfaces that manually  :
:           : Attributes                :          : require context variables :
:           :                           :          : easily derivable from the :
:           :                           :          : current session or        :
:           :                           :          : backend data warehouse.   :
| **T7-03** | Surface AI Context Prompt | Medium   | Rendering an empty        |
:           : Sizes in the UI           :          : container or entirely     :
:           :                           :          : omitting the size         :
:           :                           :          : calculation for AI prompt :
:           :                           :          : payloads.                 :
| **T7-04** | Validating AI Context     | High     | Filtering out undefined   |
:           : Data Parsing              :          : parsed items before       :
:           :                           :          : validating if the parsing :
:           :                           :          : step encountered          :
:           :                           :          : failures.                 :

--------------------------------------------------------------------------------

### Rules

#### T7-01: Elimination of Implicit Telemetry Payload Fields

> **Rule:** Never include implicit environmental data like domain or host origin
> in frontend telemetry payloads. Always rely entirely on backend log enrichment
> to capture network request origins.
>
> **What:** Exclude implicit environmental data (e.g., the browser's host or
> domain origin) from explicit frontend telemetry interaction payloads, relying
> instead on backend log enrichment.
>
> **Applies To:** Frontend telemetry and metrics reporting services.
>
> **Why:** Sending fields like `window.location.host` in interaction reporting
> payloads creates redundant data bloat, as the logging infrastructure
> automatically captures the origin of the network request. Failing to adhere to
> this typically results in **Telemetry Payload Bloat**.

**Trap 1: Manually injecting the current domain/host into the analytics event
detail object.**

**Don't:**

```typescript
const details: AiAgentEventDetails = {
  host: window.location.host,
  agentId,
  conversationId: this.conversationId,
};
this.reportingService.reportInteraction(Interaction.AI_AGENT, details);
```

**Do:**

```typescript
const details: Pick<AiAgentEventDetails, 'agentId' | 'conversationId'> = {
  agentId,
  conversationId: this.conversationId,
};
this.reportingService.reportInteraction(Interaction.AI_AGENT, details);
```

--------------------------------------------------------------------------------

#### T7-02: Deduplication of Telemetry Payload Attributes

> **Rule:** Must omit redundant context variables from telemetry payloads if
> they can be derived via backend SQL joins. Delegate session identity and role
> resolution to the downstream data pipeline.
>
> **What:** Telemetry payloads sent from frontend components must not include
> redundant context variables (like changeId or userRole) if they can be
> attached via global session context or derived via SQL joins on the backend.
>
> **Applies To:** Frontend reporting services, constants defining EventDetails,
> and UI components firing interaction events.
>
> **Why:** The telemetry payloads for AI interactions manually calculated and
> passed user roles and change IDs, unnecessarily bloating the payload and
> duplicating data already captured globally by the reporting service. Failing
> to adhere to this typically results in **Redundant Payload Bloat**.

**Trap 1: Defining telemetry interfaces that manually require context variables
easily derivable from the current session or backend data warehouse.**

**Don't:**

```typescript
export type AiAgentEventDetails = {
  changeId: string;
  userRole: 'author' | 'reviewer';
  // ... other fields
};
```

**Do:**

```typescript
export type AiAgentEventDetails = {
  // Rely on global session_id attached by reporting service
  agentId: string;
  turnIndex: number;
};
```

**Trap 2: Performing complex logic on the client to derive a metric that can be
natively handled using a SQL join during data analysis.**

**Don't:**

```typescript
const userRole = this.account?._account_id === this.change.owner._account_id ? 'author' : 'reviewer';
this.reportingService.reportInteraction('action', { userRole });
```

**Do:**

*   Fire the core event identifier with the minimal context required. Delegate
    the user role resolution to the data pipeline via SQL joins using the global
    session identity.

--------------------------------------------------------------------------------

#### T7-03: Surface AI Context Prompt Sizes in the UI

> **Rule:** Always calculate and display prompt capacities (such as word or
> token counts) directly in the UI to make context limits explicit to the user.
>
> **What:** Dialogs or interfaces handling AI prompt generation must calculate
> and explicitly display the prompt size (word or token count) to provide the
> user with clear context limits.
>
> **Applies To:** AI prompt dialogue components and context builders.
>
> **Why:** Failing to display prompt sizes left users blind to context
> limitations, leading to rejected backend payloads or silently truncated
> context when the payload exceeded token bounds. Failing to adhere to this
> typically results in **Unpredictable AI Truncation**.

**Trap 1: Rendering an empty container or entirely omitting the size calculation
for AI prompt payloads.**

**Don't:**

```html
<div class="actions">
  <div class="size"></div>
  <gr-button>Copy Prompt</gr-button>
</div>
```

**Do:**

```html
<div class="actions">
  <div class="size">
    ${this.calculatePromptWordCount(this.promptText)} words
  </div>
  <gr-button>Copy Prompt</gr-button>
</div>
```

--------------------------------------------------------------------------------

#### T7-04: Validating AI Context Data Parsing

> **Rule:** Must assert the full structural integrity of parsed AI context
> datasets before executing any filtering logic. Never silently drop undefined
> items without evaluating parsing failures.
>
> **What:** When parsing unstructured or semi-structured data for AI context
> payloads, validation logic must assert the integrity of the entire dataset
> before filtering out invalid entries.
>
> **Applies To:** Frontend chat panels and AI context item parsers.
>
> **Why:** Historically, the application filtered out undefined context items
> immediately after parsing. This swallowed partial parsing failures, meaning
> users were not alerted when some of their context links failed to load,
> leading to degraded AI prompts. Failing to adhere to this typically results in
> **Silent Data Loss**.

**Trap 1: Filtering out undefined parsed items before validating if the parsing
step encountered failures.**

**Don't:**

```typescript
// BAD: The check evaluates the array after undefined items are already removed.
const contextItems = (links ?? [])
  .map(link => parseLink(link))
  .filter(isDefined);

if (contextItems.some(item => !item)) {
  fireAlert('Failed to parse links.');
}
```

**Do:**

```typescript
// GOOD: Count items before and after filtering to detect failures, or check the mapped array prior to filtering.
const parsedItems = (links ?? []).map(link => parseLink(link));
const validItems = parsedItems.filter(isDefined);

if (links.length > 0 && validItems.length === 0) {
  fireAlert('Failed to parse one or more context item links.');
}
```
