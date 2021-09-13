# Gerrit TypeScript Plugin API

This package contains the types for developing browser plugins for the
Gerrit Code Review web application. General documentation for plugin
developers can be found at
[gerrit-review.googlesource.com](https://gerrit-review.googlesource.com/Documentation/pg-plugin-dev.html).

The `.ts` files only contain types, interfaces and enums, and thus the compiled
`.js` files only contain the enums. For JavaScript plugins this package is not
really useful or necessary, but it also serves as the source of truth for
what plugin APIs are actually supported.

Versioning of this API matches the MAJOR and MINOR versions of the general
Gerrit releases, but the PATCH version is independent. When you are building
a plugin for Gerrit x.y.z, then you should use the API package x.y.n, where
n is the highest available patch version of the API. Patch versions will only
contain additions and fixes, minor versions may include API removals.

All types in here should use the `declare` keyword to prevent bundlers from
renaming fields, which would break communication across separately built
bundles. enums are the exception, because their keys are not referenced
across bundles, and values will not be renamed by bundlers as they are strings.

This API is also used by other apps embedding gr-diff and any breaking changes
should be discussed with the Gerrit core team and properly versioned.
