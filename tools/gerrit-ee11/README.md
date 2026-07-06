# Gerrit EE11 (jakarta.servlet) flavour

Gerrit's default release is EE8 (`javax.servlet`, Jetty 12 ee8, Guice 6). The
EE11 flavour (`jakarta.servlet`, Jetty 12 ee11, Guice 7) is produced from the
**same source tree**, so both ship from one branch.

The flavour mechanism — the `//flags:flavour` build setting, its
`config_setting`s, the configuration transition, the `flavoured_*` macros
(`flavoured_library`, `flavoured_tests`, `flavoured_twin_alias`,
`flavoured_war`, …) and the servlet-flavour toolchain — lives in shared
**bazlets** (`@com_googlesource_gerrit_bazlets//tools:flavour.bzl`, which carries
the detailed docstrings). The design narrative and diagram are in the
servlet-flavoured-release design docs. This file records only the
Gerrit-specific gotchas.

## Building a flavour

Prefer the self-transitioning `-ee11` targets, which set the flag for their own
subgraph, so EE8 and EE11 build side by side in one invocation:

- WARs: `//:release-ee11`, `//:gerrit-ee11`, `//:headless-ee11`; the plain
  `//:release` etc. stay EE8.
- Plugins: `bazelisk build //plugins/foo:foo //plugins/foo:foo-ee11`.

`--@com_googlesource_gerrit_bazlets//flags:flavour=ee11` flips the *whole* build
to one flavour; it is the escape hatch, still used to run the EE11 test suites.
The EE11 libraries and test targets are flavour-gated (the `flavour` guard is
injected by `flavoured_java_library` / `flavoured_tests`), so a vanilla
`bazel test //…` stays EE8-green and CI runs the suite twice.

## Gotcha: overlay directory ≠ Java package (intentional)

A few files diverge in real API, not just imports, so the `to_jakarta` transform
cannot generate them (e.g. `HiddenErrorHandler`, `JettyServerFlavour`). Their
hand-written EE11 versions live in an `ee11/` subdirectory **with its own
`BUILD`**, but declare the **same** `package` as the canonical file — e.g.
`pgm/http/jetty/ee11/JettyServerFlavour.java` declares
`package com.google.gerrit.pgm.http.jetty` (NOT `...jetty.ee11`). The directory
exists only to be a separate Bazel package (so the parent `glob()` and the
transform input exclude it); the identical FQDN makes the two flavours drop-in
replacements, and only one is ever on a classpath. **Do not** "fix" the package
to match the directory, or move the file out of `ee11/` — either breaks the
build. (Gitiles uses the same pattern for its `DevServer` overlay.)

## Gotcha: JGit servlet is a backward-bridge

JGit master's canonical sources are already `jakarta.servlet`, so JGit
*generates* the `javax` `.ee8` bridge — the mirror of Gerrit, which generates
jakarta. Gerrit's neutral `//lib:jgit-servlet` therefore resolves to:

- `//lib:jgit-servlet-ee8` → `@jgit//…http.server.ee8:jgit-servlet-ee8` (javax bridge)
- `//lib:jgit-servlet-ee11` → `@jgit//…http.server:jgit-servlet` (**unsuffixed**
  jakarta canonical)

So the EE11 WAR's `libjgit-servlet.jar` is jakarta (JGit's canonical), not a
javax leak — the only jakarta jar in the EE11 WAR without an `-ee11` token,
precisely because JGit bridges the opposite direction. Gerrit's own tier is the
normal way round (`jgit-servlet-ee8` / `jgit-servlet-ee11`); only the underlying
JGit artifact is inverted.

## Guard

`:generated_srcs_test` asserts the generated EE11 httpd srcjar has no
`javax.servlet` residue and contains `jakarta.servlet` wherever the canonical
source did.
