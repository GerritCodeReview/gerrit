This directory contains bazel rules and CLI tools to preprocess HTML and JS files before bundling.

There 2 different tools here:
* links-updater (and update_links rule) - updates link in HTML files.
 Receives list of input and output files as well as a redirect.json file with information
 about redirects.
 
* preprocessor (and prepare_for_bundling rule) - split each HTML files to a pair of one HTML
 and one JS files. The output HTML doesn't contain `<script>` tags and JS file contains
  all scripts and imports from HTML file. For more details see source code.
   
  
