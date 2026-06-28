# Gerrit EE11 (jakarta.servlet) flavour

Gerrit's default release is EE8 (`javax.servlet`, Jetty 12 ee8 adapter,
Guice 6). The EE11 flavour (`jakarta.servlet`, Jetty 12 ee11, Guice 7) is
produced from the **same source tree** so both can ship from one branch. See
the design repo `gerrit-ee8-ee11-flavoured-release-design` for the full plan
and status map.

## How the flavour is produced

* **Mechanical bulk — the transform.** The `httpd` package is Gerrit's servlet
  boundary and is almost entirely mechanical: its EE11 form is generated from
  the canonical javax sources by the shared bazlets `to_jakarta` transform
  (`@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl`), which
  rewrites only servlet/Jetty import prefixes (`javax.servlet` ->
  `jakarta.servlet`, jetty `ee8` -> `ee11`). Java package names and source line
  numbers are preserved, so the generated classes have the **same FQDNs** as the
  canonical ones — the two flavours must never share a classpath.
  See `//java/com/google/gerrit/httpd:httpd-ee11-srcs`.

* **Hand-written overlays — the genuine divergences.** A few Jetty-12 bootstrap
  files differ in real API, not just imports (e.g. `HiddenErrorHandler`,
  `JettyServer` in `//java/com/google/gerrit/pgm/http/jetty`, whose ee11 core
  API differs: `handle(Request, Response, Callback)`, the collapsed
  nested/security split, `UriCompliance`). These get hand-written EE11 overlays
  excluded from the transform — the same pattern Gitiles uses for its
  hand-written `DevServer` overlay. (Follow-up; not in this change.)

* **Dependencies — the flag.** `--define=flavour=ee11` (`//tools:ee11`) selects
  the jakarta servlet-api, Jetty ee11 adapter, and isolated Guice 7
  (`external_deps_ee11`) tiers.

## Guard

`:generated_srcs_test` asserts the generated EE11 httpd srcjar has no
`javax.servlet` residue and contains `jakarta.servlet` wherever the canonical
source did, protecting the rewrite against future tool/source changes.
