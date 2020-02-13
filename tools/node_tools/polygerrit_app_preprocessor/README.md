This directory contains bazel rule and CLI tool to preprocess HTML and JS files before bundling.

The preprocessor.ts (and prepare_for_bundling rule) splits each HTML files to a pair of one HTML
 and one JS files. The output HTML doesn't contain `<script>` tags and JS file contains
  all scripts and imports from HTML file. For more details see source code.
