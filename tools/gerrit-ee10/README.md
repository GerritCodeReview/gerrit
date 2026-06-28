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

* **Hand-written overlays — the genuine divergences.** A few Jetty-12 bootstrap
  files differ in real API, not just imports (e.g. `HiddenErrorHandler`,
  `JettyServer` in `//java/com/google/gerrit/pgm/http/jetty`, whose ee10 core
  API differs: `handle(Request, Response, Callback)`, the collapsed
  nested/security split, `UriCompliance`). These get hand-written EE10 overlays
  excluded from the transform — the same pattern Gitiles uses for its
  hand-written `DevServer` overlay. (Follow-up; not in this change.)

* **Dependencies — the flag.** `--define=flavour=ee10` (`//tools:ee10`) selects
  the jakarta servlet-api, Jetty ee10 adapter, and isolated Guice 7
  (`external_deps_ee10`) tiers.

## Guard

`:generated_srcs_test` asserts the generated EE10 httpd srcjar has no
`javax.servlet` residue and contains `jakarta.servlet` wherever the canonical
source did, protecting the rewrite against future tool/source changes.
