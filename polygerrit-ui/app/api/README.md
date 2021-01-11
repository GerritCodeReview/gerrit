# API

In this folder, we declare the API of various parts of the Gerrit webclient.
There are two primary use cases for this:

* apps that embed our diff viewer, gr-diff
* Gerrit plugins that need to access some part of Gerrit to extend it

Both may be built as a separate bundle, but would like to type check against
the same types the Gerrit/gr-diff bundle uses. For this reason, this folder
should contain only types, with the exception of enums, where having the
value side is deemed an acceptable duplication.

All types in here should use the `declare` keyword to prevent bundlers from
renaming fields, which would break communication across separately built
bundles. Again enums are the exception, because their keys are not referenced
across bundles, and values will not be renamed by bundlers as they are strings.

This API is used by other apps embedding gr-diff and any breaking changes
should be discussed with the Gerrit core team and properly versioned.

Gerrit types should either directly use or extend these types, so that
breaking changes to the implementation require changes to these files.
