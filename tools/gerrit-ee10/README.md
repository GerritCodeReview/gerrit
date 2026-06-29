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
  (`@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl`), a
  line-oriented `sed` that rewrites the servlet/Jetty package prefixes
  (`javax.servlet` -> `jakarta.servlet`, jetty `ee8` -> `ee10`) wherever they
  occur — imports, fully-qualified references, and string constants alike. Java
  package names and source line numbers are preserved, so the generated classes have the **same FQDNs** as the
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

  **Why the build setting is required (and what "remove the flag" can and cannot
  mean).** The setting is the *variable* the whole mechanism reads and writes:
  `config_setting`/`select()` are its readers, the transitions and the
  command-line flag are its writers. You cannot have `select({"//tools:ee10":
  …})` without a setting behind `//tools:ee10`, so the **build setting cannot be
  removed** — everything (selects, transitions, `-ee10` targets) collapses
  without it. What *is* optional is the **command-line settability**: the setting
  is a `string_flag`, which is what makes `--…flags:flavour=ee10` typeable. It
  could be converted to a non-flag build setting (written only by transitions),
  which would remove the manual hatch entirely — but two things depend on the
  manual hatch today: the EE10 *code* test suites
  (`javatests/.../httpd`, `.../util/http`) are run with it, and it is handy for
  ad-hoc whole-tree experiments. So treat the manual flag as **advanced/internal**
  and prefer the `-ee10` targets; do not remove its settability until those test
  suites also have self-transitioning wrappers.

  **The manual flag's one sharp edge — flavour-pinned golden tests.** Flipping
  the *whole* build with the flag also flips targets whose *expected output*
  encodes a flavour. The release WAR jar-set guard
  (`//Documentation:check_release_war_jars`) is the example: under the flag the
  WAR becomes jakarta but the guard would compare it to the EE8 jar allowlist.
  This is handled two ways, so the flag degrades gracefully and both flavours are
  guarded without it:
  - the EE8 guard's allowlist is **flavour-aware** (a `select()` behind an alias),
    so the one target stays correct under the flag; and
  - a **self-transitioning** `//Documentation:check_release_ee10_war_jars`
    (via `ee10_flavour_file` on the WAR's jar manifest) validates the EE10 WAR's
    jar set with *no* flag, so a plain `bazel test //…` covers both flavours.

  **Keeping `bazel test //…` green: the EE10 test/library targets are
  flavour-gated.** The EE10 servlet libraries (`httpd-ee10`, `oauth-ee10`,
  `openid-ee10`, `init-ee10`, `jetty-ee10`) and the EE10 test targets
  (`httpd_tests_ee10`, `http_tests_ee10`) only compile in the EE10 configuration
  (their jakarta sources need the Guice-7/jakarta tier). A bare `bazel test //…`
  in the default flavour would otherwise drag them into the EE8 config and fail.
  They carry `target_compatible_with = EE10_ONLY`
  (`//tools/bzl:flavour.bzl` — `select({"//tools:ee10": [], "//conditions:default":
  ["@platforms//:incompatible"]})`), so in the default flavour Bazel **skips**
  them (reports them incompatible, not failed) and a vanilla `bazel test //…`
  stays EE8-green; under `--…flavour=ee10` they become compatible and run. The
  guard is *conditional on the flavour*, so each target shows up exactly when its
  flavour is active. CI therefore runs the suite **twice** —
  `bazel test //…` for EE8 and `bazel test --…flavour=ee10 //…:<ee10 targets>` for
  EE10. (`http-ee10`/`testutil-ee10` and the `ee10/` overlays compile in either
  config — self-contained jakarta with no flavour-aware deps — so they need no
  guard.)

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
