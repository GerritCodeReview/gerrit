---
name: typescript-style-review
description: Provides guidance on TypeScript coding standards, specifically focusing on property visibility and access modifiers.
---

# TypeScript Style Review Guide

## Executive Summary

This guide serves as the authoritative source for TypeScript style compliance. It enforces strict access modifiers and forbids visibility bypasses to ensure code maintainability and prevent runtime issues.

## Summary

| Chapter Theme / Title | Scope & Objective |
| :--- | :--- |
| **TypeScript Style & Visibility** | Enforce strict property visibility rules and forbid access modifier bypasses. |

--------------------------------------------------------------------------------
--------------------------------------------------------------------------------

## Chapter: TypeScript Style & Visibility

**Context:** Ensure proper encapsulation by using correct access modifiers and avoiding bypasses of visibility rules.

### Summary

| Rule ID | Principle / Constraint | Priority | Primary Symptom / Trap |
| :--- | :--- | :--- | :--- |
| **TS1-01** | Correct Access Modifiers | High | Using `private` for properties accessed outside the class lexical scope (e.g. Lit templates). |
| **TS1-02** | No Visibility Bypasses | High | Using string element access `obj['foo']` to bypass property visibility. |

--------------------------------------------------------------------------------

### Rules

#### TS1-01: Correct Access Modifiers

> **Rule:** Properties used outside the lexical scope of their containing class must not be private. They must be public or protected, as appropriate.
>
> **What:** Ensure that properties used from outside the lexical scope of their containing class (like Lit element properties accessed by parent components or templates) do not use `private` visibility.
>
> **Applies To:** TypeScript classes, specifically Lit components and their properties.
>
> **Why:** Declaring properties as `private` when they are accessed externally (e.g., by parent components or external template renderers) breaks compilation under strict settings and violates encapsulation boundaries. Using `protected` or `public` maintains type safety and compiler compliance.

**Trap 1: Marking a property as `private` when it is accessed by a parent component.**

**Don't:**

```typescript
@customElement('my-element')
class MyElement extends LitElement {
  @property({type: String})
  private value = ''; // BAD: private but accessed by parent
}

// In parent component:
// const el = this.shadowRoot.querySelector('my-element');
// console.log(el.value); // Compilation error or runtime bypass
```

**Do:**

```typescript
@customElement('my-element')
class MyElement extends LitElement {
  @property({type: String})
  value = ''; // GOOD: public (default) or protected if appropriate
}
```

#### TS1-02: No Visibility Bypasses

> **Rule:** TypeScript code must not use string element access `obj['foo']` to bypass the visibility of a property.
>
> **What:** Avoid using `obj['foo']` or similar dynamic access to read or write private/protected properties of a class.
>
> **Applies To:** All TypeScript code.
>
> **Why:** Bypassing visibility rules using string keys defeats the purpose of access modifiers, breaks type safety, and makes refactoring difficult as compilers cannot track these dynamic references.

**Trap 1: Using string index access to read a private property of another class.**

**Don't:**

```typescript
const helper = new Helper();
const value = helper['privateField']; // BAD: Bypassing visibility
```

**Do:**

```typescript
// If the field needs to be accessed, make it public or protected, or expose a public getter.
const helper = new Helper();
const value = helper.publicField; // GOOD: Accessing public API
```

Reference link: https://google.github.io/styleguide/tsguide.html#properties-used-outside-of-class-lexical-scope
