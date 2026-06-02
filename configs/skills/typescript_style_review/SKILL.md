# TypeScript Style Review

This skill checks the changelist against the TypeScript style guide.

## Guidelines

Ensure that properties used from outside the lexical scope of their containing class (like Angular template properties) do not use `private` visibility. Such properties should use `protected` or `public` as appropriate.

TypeScript code must not use `obj['foo']` to bypass the visibility of a property.

## Comment Format

Format all comments using the provided template:

### Problem
Clearly and succinctly describe the issue.

### Suggestion
Provide a suggestion for improvement, including a code snippet.

### Reference
Provide a list of reference quotes and links to the relevant sections in the provided context documents for further reading. If you cannot provide a quote and source link here, do not leave a comment.

Reference link: https://g3doc.corp.google.com/javascript/typescript/g3doc/dev/readability/styleguide.md?cl=head#properties-used-outside-of-class-lexical-scope
