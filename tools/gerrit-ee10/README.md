# Gerrit EE10 (jakarta.servlet) flavour

Gerrit's default release is EE8 (`javax.servlet`, Jetty 12 ee8 adapter,
Guice 6). The EE10 flavour (`jakarta.servlet`, Jetty 12 ee10, Guice 7) is
produced from the **same source tree** so both can ship from one branch. See
the design repo `gerrit-ee8-ee10-flavoured-release-design` for the full plan
and status map.

## How the flavour is produced

* **Mechanical bulk — the transform.** The `httpd` package is Gerrit's servlet
  boundary and is almost entirely mechanical: its EE10 form is generated from
  the canonical javax sources by the shared bazlets `to_jakarta` transform
  (`@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl`), which
  rewrites only servlet/Jetty import prefixes (`javax.servlet` ->
  `jakarta.servlet`, jetty `ee8` -> `ee10`). Java package names and source line
  numbers are preserved, so the generated classes have the **same FQDNs** as the
  canonical ones — the two flavours must never share a classpath.
  See `//java/com/google/gerrit/httpd:httpd-ee10-srcs`.

* **Hand-written overlays — the genuine divergences.** A few files differ in
  real API, not just imports, so the sed transform cannot produce them: e.g.
  `HiddenErrorHandler` (ee10's error handler is a core `handle(Request, Response,
  Callback)` handler) and the `JettyServerFlavour` seam (ee8's
  `ServletContextHandler` is a `Supplier<Handler>`; ee10's is itself a `Handler`;
  plus the Servlet-6 ambiguous-URI handling). These get hand-written EE10
  overlays excluded from the transform — the same pattern Gitiles uses for its
  `DevServer` overlay. Large mostly-mechanical files are split so the bulk stays
  transform-generated and only a tiny seam is hand-written (e.g. `JettyServer`
  delegates its 3 divergences to `JettyServerFlavour`).

  **Overlay-directory convention (intentional, not an oversight).** An EE10
  overlay lives in an `ee10/` subdirectory **with its own `BUILD`**, but its
  `package` declaration is the **same** as the canonical file — e.g.
  `java/com/google/gerrit/pgm/http/jetty/ee10/JettyServerFlavour.java` declares
  `package com.google.gerrit.pgm.http.jetty` (NOT `...jetty.ee10`). The source
  directory deliberately does **not** match the Java package. This is on purpose:

  - The `ee10/` directory exists only to be a separate **Bazel package**, so the
    parent `glob()` (EE8 build) and the transform input both naturally exclude
    it, and the overlay can be wired into the EE10 library explicitly.
  - The **Java FQDN is identical** to the canonical file, so the
    transform-generated siblings reference it unchanged and the two flavours are
    drop-in replacements. Only one flavour's jar is ever on a classpath, so there
    is no duplicate-class conflict; selection happens at build time via the
    package's `:..-ee10` library and the `//tools:ee10` `select()` aliases, never
    at runtime.

  Do **not** "fix" the package to match the directory (`...ee10`) or move the
  file out of `ee10/` — either breaks the build. This mirrors Gitiles
  (`javatests/com/google/gitiles/ee10/` files declare `package
  com.google.gitiles`).

* **Dependencies — the flag.** The flavour is a string build setting,
  `@com_googlesource_gerrit_bazlets//flags:flavour` (`ee8` default, `ee10`).
  Gerrit's `//tools:ee10` config_setting keys off it and is the single seam every
  flavour-bearing `select()` flips on: the jakarta servlet-api, the Jetty ee10
  adapter, the isolated Guice 7 (`external_deps_ee10`), the canonical jakarta
  JGit servlet tier, and the generated `httpd-ee10`/plugin tiers.

  **The flag lives in bazlets, not Gerrit, on purpose.** That is what lets the
  shared `gerrit_plugin(flavour = "ee10")` macro give an EE10 plugin target its
  **own** `flavour=ee10` configuration transition (via bazlets'
  `ee10_flavour_jar`), so it self-selects the jakarta config without a
  command-line flag — a bazlets macro cannot reference a Gerrit `//tools:…`
  label. The transition + the `ee10_war` WAR wrapper both live in
  `@com_googlesource_gerrit_bazlets//tools:flavour.bzl`.

  **How to build a flavour** — prefer the transition-wrapped targets, which set
  the flag for their own subgraph (so EE8 and EE10 build side by side in one
  invocation):

  - WARs: `//:release-ee10`, `//:gerrit-ee10`, `//:headless-ee10` (`ee10_war`);
    the plain `//:release` etc. stay EE8.
  - Plugins: `bazelisk build //plugins/gitiles:gitiles //plugins/gitiles:gitiles-ee10`
    builds both flavours at once — the `-ee10` target self-transitions.

  Setting the flag by hand
  (`--@com_googlesource_gerrit_bazlets//flags:flavour=ee10`) is the escape hatch;
  it flips the *whole* build to one flavour and is rarely needed now that the
  `-ee10` targets carry their own transition.

## Bridge direction: the jgit-servlet naming asymmetry (read before wiring deps)

Each servlet-facing dependency bridges **from its own upstream canonical flavour
to the flavour its consumers need**, and the two go in opposite directions. This
makes the EE10 jar names *inconsistent across projects* — on purpose. Getting it
wrong does not just mislabel a jar; **it fails to compile**, because the
artifact you expect (`jgit-servlet-ee10`) does not exist.

* **Gerrit and Gitiles are FORWARD-bridges.** Their canonical sources are
  `javax.servlet` (EE8). They *generate* the `jakarta.servlet` (EE10) flavour.
  So the **EE10 artifact is the suffixed one** and the unsuffixed name is javax:
  - `//java/.../httpd:httpd` (javax) vs `:httpd-ee10` (jakarta, generated)
  - `gitiles-servlet` (javax) vs `gitiles-servlet-ee10` (jakarta, generated)

* **JGit is a BACKWARD-bridge.** JGit master's canonical sources are already
  `jakarta.servlet` (EE10). It *generates* the `javax.servlet` `.ee8` bridge for
  laggard consumers. So JGit's **unsuffixed artifact is the EE10/jakarta one**
  and the suffixed `.ee8` is the generated javax bridge — the mirror image of
  Gerrit/Gitiles:
  - `@jgit//org.eclipse.jgit.http.server:jgit-servlet` → **jakarta canonical**
  - `@jgit//org.eclipse.jgit.http.server.ee8:jgit-servlet-ee8` → javax bridge

Therefore, when wiring the **EE10** flavour, JGit is the odd one out:

| dep | EE8 (javax) | EE10 (jakarta) |
|---|---|---|
| Gerrit httpd | `:httpd` (canonical) | `:httpd-ee10` (generated, **suffixed**) |
| JGit servlet | `//lib:jgit-servlet-ee8` → `...ee8:jgit-servlet-ee8` | `//lib:jgit-servlet-jakarta` → `...http.server:jgit-servlet` (canonical, **unsuffixed**) |

`//lib:jgit-servlet` is a flavour alias resolving to those. There is **no**
`jgit-servlet-ee10` target — for JGit, jakarta *is* the unsuffixed canonical.

Consequence in the built WARs (one jgit-servlet jar each, never both):

* `gerrit.war` (EE8): `libjgit-servlet-ee8.jar` (javax) — verified by
  `unzip -p ... | javap` containing only `javax/servlet`.
* `gerrit-ee10.war` (EE10): `libjgit-servlet.jar` (jakarta) — `javap` shows only
  `jakarta/servlet`. The unsuffixed name is **not** a leak of the EE8 jar; it is
  JGit's canonical jakarta artifact. This is the only jakarta jar in the EE10
  WAR without an `-ee10`/`jakarta` token in its name, precisely because JGit
  bridges the opposite direction.

This asymmetry converges over time: once a project's canonical flavour flips to
`jakarta.servlet`, its bridge becomes a backward-bridge (generating `.ee8`) like
JGit's, and the suffix moves to the EE8 side everywhere. See
`gerrit-ee8-ee10-flavoured-release-design` (the "Architecture" /
forward-bridge-vs-backward-bridge section) for the full rationale.

## Guard

`:generated_srcs_test` asserts the generated EE10 httpd srcjar has no
`javax.servlet` residue and contains `jakarta.servlet` wherever the canonical
source did, protecting the rewrite against future tool/source changes.
