// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"archive/zip"
	"bufio"
	"bytes"
	"compress/gzip"
	"encoding/json"
	"errors"
	"flag"
	"io"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"golang.org/x/tools/godoc/vfs/httpfs"
	"golang.org/x/tools/godoc/vfs/zipfs"
)

var (
	plugins    = flag.String("plugins", "", "comma seperated plugin paths to serve")
	port       = flag.String("port", "localhost:8081", "address to serve HTTP requests on")
	host       = flag.String("host", "gerrit-review.googlesource.com", "Host to proxy requests to")
	scheme     = flag.String("scheme", "https", "URL scheme")
	cdnPattern = regexp.MustCompile("https://cdn.googlesource.com/polygerrit_ui/[0-9.]*")
)

func main() {
	flag.Parse()

	fontsArchive, err := openDataArchive("fonts.zip")
	if err != nil {
		log.Fatal(err)
	}

	workspace := os.Getenv("BUILD_WORKSPACE_DIRECTORY")
	if err := os.Chdir(filepath.Join(workspace, "polygerrit-ui")); err != nil {
		log.Fatal(err)
	}

	compiledSrcPath := filepath.Join(workspace, "./.ts-out/server-go")

	tsInstance := newTypescriptInstance(
		filepath.Join(workspace, "./node_modules/.bin/tsc"),
		filepath.Join(workspace, "./polygerrit-ui/app/tsconfig.json"),
		compiledSrcPath,
	)

	if err := tsInstance.StartWatch(); err != nil {
		log.Fatal(err)
	}

	dirListingMux := http.NewServeMux()
	dirListingMux.Handle("/styles/", http.StripPrefix("/styles/", http.FileServer(http.Dir("app/styles"))))
	dirListingMux.Handle("/samples/", http.StripPrefix("/samples/", http.FileServer(http.Dir("app/samples"))))
	dirListingMux.Handle("/elements/", http.StripPrefix("/elements/", http.FileServer(http.Dir("app/elements"))))
	dirListingMux.Handle("/behaviors/", http.StripPrefix("/behaviors/", http.FileServer(http.Dir("app/behaviors"))))

	http.HandleFunc("/",
		func(w http.ResponseWriter, req *http.Request) {
			// If typescript compiler hasn't finished yet, wait for it
			tsInstance.WaitForCompilationComplete()
			handleSrcRequest(compiledSrcPath, dirListingMux, w, req)
		})

	http.Handle("/fonts/",
		addDevHeadersMiddleware(http.FileServer(httpfs.New(zipfs.New(fontsArchive, "fonts")))))

	http.HandleFunc("/index.html", handleIndex)
	http.HandleFunc("/changes/", handleProxy)
	http.HandleFunc("/accounts/", handleProxy)
	http.HandleFunc("/config/", handleProxy)
	http.HandleFunc("/projects/", handleProxy)
	http.HandleFunc("/static/", handleProxy)
	http.HandleFunc("/accounts/self/detail", handleAccountDetail)

	if len(*plugins) > 0 {
		http.Handle("/plugins/", http.StripPrefix("/plugins/",
			http.FileServer(http.Dir("../plugins"))))
		log.Println("Local plugins from", "../plugins")
	} else {
		http.HandleFunc("/plugins/", handleProxy)
		// Serve local plugins from `plugins_`
		http.Handle("/plugins_/", http.StripPrefix("/plugins_/",
			http.FileServer(http.Dir("../plugins"))))
	}
	log.Println("Serving on port", *port)
	log.Fatal(http.ListenAndServe(*port, &server{}))
}

func addDevHeadersMiddleware(h http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, req *http.Request) {
		addDevHeaders(writer)
		h.ServeHTTP(writer, req)
	})
}

func addDevHeaders(writer http.ResponseWriter) {
	writer.Header().Set("Access-Control-Allow-Origin", "*")
	writer.Header().Set("Access-Control-Allow-Headers", "cache-control,x-test-origin")
	writer.Header().Set("Cache-Control", "public, max-age=10, must-revalidate")
}

func handleSrcRequest(compiledSrcPath string, dirListingMux *http.ServeMux, writer http.ResponseWriter, originalRequest *http.Request) {
	parsedUrl, err := url.Parse(originalRequest.RequestURI)
	if err != nil {
		writer.WriteHeader(500)
		return
	}
	if parsedUrl.Path != "/" && strings.HasSuffix(parsedUrl.Path, "/") {
		dirListingMux.ServeHTTP(writer, originalRequest)
		return
	}

	normalizedContentPath := parsedUrl.Path

	if !strings.HasPrefix(normalizedContentPath, "/") {
		normalizedContentPath = "/" + normalizedContentPath
	}

	isJsFile := strings.HasSuffix(normalizedContentPath, ".js") || strings.HasSuffix(normalizedContentPath, ".mjs")
	isTsFile := strings.HasSuffix(normalizedContentPath, ".ts")

	// Source map in a compiled js file point to a file inside /app/... directory
	// Browser tries to load original file from the directory when debugger is
	// activated. In this case we return original content without any processing
	isOriginalFileRequest := strings.HasPrefix(normalizedContentPath, "/polygerrit-ui/app/") && (isTsFile || isJsFile)

	data, err := getContent(compiledSrcPath, normalizedContentPath, isOriginalFileRequest)
	if err != nil {
		if !isOriginalFileRequest {
			data, err = getContent(compiledSrcPath, normalizedContentPath+".js", false)
		}
		if err != nil {
			writer.WriteHeader(404)
			return
		}
		isJsFile = true
	}
	if isOriginalFileRequest {
		// Explicitly set text/html Content-Type. If live code tries
		// to import javascript from the /app/ folder accidentally, browser fails
		// with the import error, so we can catch this problem easily.
		writer.Header().Set("Content-Type", "text/html")
	} else if isJsFile {
		// import ... from '@polymer/decorators'
		// must be transformed into
		// import ... from '@polymer/decorators/lib/decorators.js'
		// The correct way to do it is to use value of the "main" property
		// from the @polymer/decorators/package.json. However, parsing package.json
		// is overcomplicated right now, hard-code exact path here.
		moduleImportRegexp := regexp.MustCompile("(?m)^(import.*)'@polymer/decorators';$")
		data = moduleImportRegexp.ReplaceAll(data, []byte("$1 '@polymer/decorators/lib/decorators.js';"))

		// The following code updates import statements.
		// 1. if an in imported file has .js or .mjs extension, the code keeps
		//	  the file extension unchanged. Otherwise, it adds .js extension
		// 2. For module imports it adds '/node_modules/' prefix.
		//   Examples:
		//   '@polymer/polymer.js' -> '/node_modules/@polymer/polymer.js'
		//   'page/page.mjs' -> '/node_modules/page.mjs'
		//   '@polymer/iron-icon' -> '/node_modules/@polymer/iron-icon.js'
		//   './element/file' -> './element/file.js'
		moduleImportRegexp = regexp.MustCompile(`(import[^'";]*|export[^'";]*from ?)['"]([^;\s]*?)(\.(m?)js)?['"];`)
		data = moduleImportRegexp.ReplaceAll(data, []byte("$1'$2.${4}js';"))

		moduleImportRegexp = regexp.MustCompile(`(import[^'";]*|export[^'";]*from ?)['"]([^/.;\s][^;\s]*)['"];`)
		data = moduleImportRegexp.ReplaceAll(data, []byte("$1'/node_modules/$2';"))

		// The es module version of rxjs can be found in the _esm2015/ directory.
		moduleImportRegexp = regexp.MustCompile("(?m)^((import|export).*'/node_modules/rxjs)(.*).js(';)$")
		data = moduleImportRegexp.ReplaceAll(data, []byte("$1/_esm2015$3/index.js$4"))

		// The es module version of tslib.js can be found in tslib.es6.js.
		moduleImportRegexp = regexp.MustCompile("(?m)^((import|export).*'/node_modules/)tslib.js';$")
		data = moduleImportRegexp.ReplaceAll(data, []byte("${1}tslib/tslib.es6.js';"))

		// 'lit.js' has to be resolved as 'lit/index.js'.
		moduleImportRegexp = regexp.MustCompile("(?m)^((import|export).*'/node_modules/)lit.js';$")
		data = moduleImportRegexp.ReplaceAll(data, []byte("${1}lit/index.js';"))
		// Some lit imports 'a.js' have to be resolved as 'a/a.js'.
		moduleImportRegexp = regexp.MustCompile(`((import|export)[^'";]*'/node_modules/(@lit/)?)(lit-element|lit-html|reactive-element).js';`)
		data = moduleImportRegexp.ReplaceAll(data, []byte("${1}${4}/${4}.js';"))

		// 'immer' imports and exports have to be resolved to 'immer/dist/immer.esm.js'.
		moduleImportRegexp = regexp.MustCompile("(?m)^((import|export).*'/node_modules/)immer.js';$")
		data = moduleImportRegexp.ReplaceAll(data, []byte("${1}/immer/dist/immer.esm.js';"))

		if strings.HasSuffix(normalizedContentPath, "/node_modules/page/page.js") {
			// Can't import page.js directly, because this is undefined.
			// Replace it with window
			// The same replace exists in karma.conf.js
			// Rollup makes this replacement automatically
			pageJsRegexp := regexp.MustCompile(`(?m)^}\(this, \(function \(\) { 'use strict';$`)
			newData := pageJsRegexp.ReplaceAll(data, []byte("}(window, (function () { 'use strict';"))
			if len(newData) == len(data) {
				log.Fatal("The page.js was updated. Please update regexp/replace accordingly")
			}
			data = newData
		}

		writer.Header().Set("Content-Type", "application/javascript")
	} else if strings.HasSuffix(normalizedContentPath, ".css") {
		writer.Header().Set("Content-Type", "text/css")
	} else if strings.HasSuffix(normalizedContentPath, "_test.html") {
		moduleImportRegexp := regexp.MustCompile("(?m)^(import.*)'([^/.].*)';$")
		data = moduleImportRegexp.ReplaceAll(data, []byte("$1 '/node_modules/$2';"))
		writer.Header().Set("Content-Type", "text/html")
	} else if strings.HasSuffix(normalizedContentPath, ".html") {
		writer.Header().Set("Content-Type", "text/html")
	}
	writer.WriteHeader(200)
	addDevHeaders(writer)
	writer.Write(data)
}

func getContent(compiledSrcPath string, normalizedContentPath string, isOriginalFileRequest bool) ([]byte, error) {
	// normalizedContentPath must always starts with '/'

	if isOriginalFileRequest {
		data, err := ioutil.ReadFile(normalizedContentPath[len("/polygerrit-ui/"):])
		if err != nil {
			return nil, errors.New("File not found")
		}
		return data, nil
	}

	// gerrit loads gr-app.js as an ordinary script, without type="module" attribute.
	// If server.go serves this file as is, browser shows the error:
	// Uncaught SyntaxError: Cannot use import statement outside a module
	//
	// To load non-bundled gr-app.js as a module, we "virtually" renames original
	// gr-app.js to gr-app.mjs and load it with dynamic import.
	//
	// Another option is to patch rewriteHostPage function and add type="module" attribute
	// to <script src=".../elements/gr-app.js"> tag, but this solution is incompatible
	// with --dev-cdn options. If you run local gerrit instance with --dev-cdn parameter,
	// the server.go is used as cdn and it doesn't handle host page (i.e. rewriteHostPage
	// method is not called).
	if normalizedContentPath == "/elements/gr-app.js" {
		return []byte("import('./gr-app.mjs')"), nil
	}

	if normalizedContentPath == "/elements/gr-app.mjs" {
		normalizedContentPath = "/elements/gr-app.js"
	}

	pathsToTry := []string{compiledSrcPath + normalizedContentPath, "app" + normalizedContentPath}
	bowerComponentsSuffix := "/bower_components/"
	nodeModulesPrefix := "/node_modules/"
	testComponentsPrefix := "/components/"

	if strings.HasPrefix(normalizedContentPath, testComponentsPrefix) {
		pathsToTry = append(pathsToTry, "node_modules/wct-browser-legacy/node_modules/"+normalizedContentPath[len(testComponentsPrefix):])
		pathsToTry = append(pathsToTry, "node_modules/"+normalizedContentPath[len(testComponentsPrefix):])
	}

	if strings.HasPrefix(normalizedContentPath, bowerComponentsSuffix) {
		pathsToTry = append(pathsToTry, "node_modules/@webcomponents/"+normalizedContentPath[len(bowerComponentsSuffix):])
	}

	if strings.HasPrefix(normalizedContentPath, nodeModulesPrefix) {
		pathsToTry = append(pathsToTry, "node_modules/"+normalizedContentPath[len(nodeModulesPrefix):])
	}

	for _, path := range pathsToTry {
		data, err := ioutil.ReadFile(path)
		if err == nil {
			return data, nil
		}
	}

	return nil, errors.New("File not found")
}

func openDataArchive(path string) (*zip.ReadCloser, error) {
	absBinPath, err := resourceBasePath()
	if err != nil {
		return nil, err
	}
	return zip.OpenReader(absBinPath + ".runfiles/gerrit/polygerrit-ui/" + path)
}

func resourceBasePath() (string, error) {
	return filepath.Abs(os.Args[0])
}

func handleIndex(writer http.ResponseWriter, originalRequest *http.Request) {
	fakeRequest := &http.Request{
		URL: &url.URL{
			Path:     "/",
			RawQuery: originalRequest.URL.RawQuery,
		},
	}
	handleProxy(writer, fakeRequest)
}

func handleProxy(writer http.ResponseWriter, originalRequest *http.Request) {
	patchedRequest := &http.Request{
		Method: "GET",
		URL: &url.URL{
			Scheme:   *scheme,
			Host:     *host,
			Opaque:   originalRequest.URL.EscapedPath(),
			RawQuery: originalRequest.URL.RawQuery,
		},
	}
	response, err := http.DefaultClient.Do(patchedRequest)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
	defer response.Body.Close()
	for name, values := range response.Header {
		for _, value := range values {
			if name != "Content-Length" {
				writer.Header().Add(name, value)
			}
		}
	}
	writer.WriteHeader(response.StatusCode)
	if _, err := io.Copy(writer, patchResponse(originalRequest, response)); err != nil {
		log.Println("Error copying response to ResponseWriter:", err)
		return
	}
}

func getJsonPropByPath(json map[string]interface{}, path []string) interface{} {
	prop, path := path[0], path[1:]
	if json[prop] == nil {
		return nil
	}
	switch json[prop].(type) {
	case map[string]interface{}: // map
		return getJsonPropByPath(json[prop].(map[string]interface{}), path)
	case []interface{}: // array
		return json[prop].([]interface{})
	default:
		return json[prop].(interface{})
	}
}

func setJsonPropByPath(json map[string]interface{}, path []string, value interface{}) {
	prop, path := path[0], path[1:]
	if json[prop] == nil {
		return // path not found
	}
	if len(path) > 0 {
		setJsonPropByPath(json[prop].(map[string]interface{}), path, value)
	} else {
		json[prop] = value
	}
}

func patchResponse(req *http.Request, res *http.Response) io.Reader {
	switch req.URL.EscapedPath() {
	case "/":
		return rewriteHostPage(res.Body)
	case "/config/server/info":
		return injectLocalPlugins(res.Body)
	default:
		return res.Body
	}
}

func rewriteHostPage(reader io.Reader) io.Reader {
	buf := new(bytes.Buffer)
	buf.ReadFrom(reader)
	original := buf.String()

	// Simply remove all CDN references, so files are loaded from the local file system  or the proxy
	// server instead.
	replaced := cdnPattern.ReplaceAllString(original, "")

	// Modify window.INITIAL_DATA so that it has the same effect as injectLocalPlugins. To achieve
	// this let's add JavaScript lines at the end of the <script>...</script> snippet that also
	// contains window.INITIAL_DATA=...
	// Here we rely on the fact that the <script> snippet that we want to append to is the first one.
	if len(*plugins) > 0 {
		insertionPoint := strings.Index(replaced, "</script>")
		builder := new(strings.Builder)
		builder.WriteString(
			"window.INITIAL_DATA['/config/server/info'].plugin.js_resource_paths = []; ")
		for _, p := range strings.Split(*plugins, ",") {
			if filepath.Ext(p) == ".js" {
				builder.WriteString(
					"window.INITIAL_DATA['/config/server/info'].plugin.js_resource_paths.push('" + p + "'); ")
			}
		}
		replaced = replaced[:insertionPoint] + builder.String() + replaced[insertionPoint:]
	}

	return strings.NewReader(replaced)
}

func injectLocalPlugins(reader io.Reader) io.Reader {
	if len(*plugins) == 0 {
		return reader
	}
	// Skip escape prefix
	io.CopyN(ioutil.Discard, reader, 5)
	dec := json.NewDecoder(reader)

	var response map[string]interface{}
	err := dec.Decode(&response)
	if err != nil {
		log.Fatal(err)
	}

	// Configuration path in the JSON server response
	jsPluginsPath := []string{"plugin", "js_resource_paths"}
	jsResources := getJsonPropByPath(response, jsPluginsPath).([]interface{})

	for _, p := range strings.Split(*plugins, ",") {
		if filepath.Ext(p) == ".js" {
			jsResources = append(jsResources, p)
		}
	}

	setJsonPropByPath(response, jsPluginsPath, jsResources)

	reader, writer := io.Pipe()
	go func() {
		defer writer.Close()
		io.WriteString(writer, ")]}'") // Write escape prefix
		err := json.NewEncoder(writer).Encode(&response)
		if err != nil {
			log.Fatal(err)
		}
	}()
	return reader
}

func handleAccountDetail(w http.ResponseWriter, r *http.Request) {
	http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
}

type gzipResponseWriter struct {
	io.WriteCloser
	http.ResponseWriter
}

func newGzipResponseWriter(w http.ResponseWriter) *gzipResponseWriter {
	gz := gzip.NewWriter(w)
	return &gzipResponseWriter{WriteCloser: gz, ResponseWriter: w}
}

func (w gzipResponseWriter) Write(b []byte) (int, error) {
	return w.WriteCloser.Write(b)
}

func (w gzipResponseWriter) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	h, ok := w.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, errors.New("gzipResponseWriter: ResponseWriter does not satisfy http.Hijacker interface")
	}
	return h.Hijack()
}

type server struct{}

// Any path prefixes that should resolve to index.html.
var (
	fePaths    = []string{"/q/", "/c/", "/id/", "/p/", "/x/", "/dashboard/", "/admin/", "/settings/"}
	issueNumRE = regexp.MustCompile(`^\/\d+\/?$`)
)

func (_ *server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	log.Printf("%s %s %s %s\n", r.Proto, r.Method, r.RemoteAddr, r.URL)
	for _, prefix := range fePaths {
		if strings.HasPrefix(r.URL.Path, prefix) || r.URL.Path == "/" {
			r.URL.Path = "/index.html"
			log.Println("Redirecting to /index.html")
			break
		} else if match := issueNumRE.Find([]byte(r.URL.Path)); match != nil {
			r.URL.Path = "/index.html"
			log.Println("Redirecting to /index.html")
			break
		}
	}
	if !strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
		http.DefaultServeMux.ServeHTTP(w, r)
		return
	}
	w.Header().Set("Content-Encoding", "gzip")
	addDevHeaders(w)
	gzw := newGzipResponseWriter(w)
	defer gzw.Close()
	http.DefaultServeMux.ServeHTTP(gzw, r)
}

// Typescript compiler support
// The code below runs typescript compiler in watch mode and redirect
// all output from the compiler to the standard logger with the prefix "TSC -"
// Additionally, the code analyzes messages produced by the typescript compiler
// and allows to wait until compilation is finished.
var (
	tsStartingCompilation   = "- Starting compilation in watch mode..."
	tsFileChangeDetectedMsg = "- File change detected. Starting incremental compilation..."
	// If there is only one error typescript outputs:
	// Found 1 error
	// In all other cases it outputs
	// Found X errors
	tsStartWatchingMsg        = regexp.MustCompile(`^.* - Found \d+ error(s)?\. Watching for file changes\.$`)
	waitForNextChangeInterval = 1 * time.Second
)

// typescriptLogWriter implements Writer interface and receives output
// (stdout and stderr) from the typescript compiler. It reads incoming
// data line-by-line, analyzes each line and updates compilationDoneWaiter
// according to the current compiler state. Additionally, the
// typescriptLogWriter passes all incoming lines to the underlying logger.
type typescriptLogWriter struct {
	// unfinishedLine stores the portion of line which was partially received
	// (i.e. all text received after the last EOL (\n) mark.
	unfinishedLine string
	// logger is used to pass-through all received strings
	logger *log.Logger
	// when WaitGroup counter is 0 the compilation is complete
	compilationDoneWaiter *sync.WaitGroup
}

func newTypescriptLogWriter(compilationCompleteWaiter *sync.WaitGroup) *typescriptLogWriter {
	return &typescriptLogWriter{
		logger:                log.New(log.Writer(), "TSC - ", log.Flags()),
		compilationDoneWaiter: compilationCompleteWaiter,
	}
}

func (lw typescriptLogWriter) Write(p []byte) (n int, err error) {
	// The input p can contain several lines and/or the partial line
	// Code splits the input by EOL marker (\n) and stores the unfinished line
	// for the next call to Write.
	partialText := lw.unfinishedLine + string(p)
	lines := strings.Split(partialText, "\n")
	fullLines := lines
	if strings.HasSuffix(partialText, "\n") {
		lw.unfinishedLine = ""
	} else {
		fullLines = lines[:len(lines)-1]
		lw.unfinishedLine = lines[len(lines)-1]
	}
	for _, fullLine := range fullLines {
		text := strings.TrimSpace(fullLine)
		if text == "" {
			continue
		}
		if strings.HasSuffix(text, tsFileChangeDetectedMsg) ||
			strings.HasSuffix(text, tsStartingCompilation) {
			lw.compilationDoneWaiter.Add(1)
		}
		if tsStartWatchingMsg.MatchString(text) {
			// A source code can be changed while previous compiler run is in progress.
			// In this case typescript reruns compilation again almost immediately
			// after the previous run finishes. To detect this situation, we are
			// waiting waitForNextChangeInterval before decreasing the counter.
			// If another compiler run is started in this interval, we will wait
			// again until it finishes.
			go func() {
				time.Sleep(waitForNextChangeInterval)
				lw.compilationDoneWaiter.Done()
			}()
		}
		lw.logger.Print(text)
	}
	return len(p), nil
}

type typescriptInstance struct {
	cmd                       *exec.Cmd
	compilationCompleteWaiter *sync.WaitGroup
}

func newTypescriptInstance(tscBinaryPath string, projectPath string, outdir string) *typescriptInstance {
	cmd := exec.Command(tscBinaryPath,
		"--watch",
		"--preserveWatchOutput",
		"--project",
		projectPath,
		"--outDir",
		outdir)

	compilationCompleteWaiter := &sync.WaitGroup{}
	logWriter := newTypescriptLogWriter(compilationCompleteWaiter)
	// Note 1: (from https://golang.org/pkg/os/exec/#Cmd)
	// If Stdout and Stderr are the same writer, and have a type that can
	// be compared with ==, at most one goroutine at a time will call Write.
	//
	// Note 2: The typescript compiler reports all compilation errors to
	// stdout by design (see https://github.com/microsoft/TypeScript/issues/615)
	// It writes to stderr only when something unexpected happens (like internal
	// exceptions). To print such errors in the same way as standard typescript
	// error, the same logWriter is used both for Stdout and Stderr.
	//
	// If Stderr arrives in the middle of ordinary typescript output (i.e.
	// something unexpected happens), the server.go can stop respond to http
	// requests. However, this is not a problem for us: typescript compiler and
	// server.go must be restarted anyway.
	cmd.Stdout = logWriter
	cmd.Stderr = logWriter

	return &typescriptInstance{
		cmd:                       cmd,
		compilationCompleteWaiter: compilationCompleteWaiter,
	}
}

func (ts *typescriptInstance) StartWatch() error {
	err := ts.cmd.Start()
	if err != nil {
		return err
	}
	go func() {
		ts.cmd.Wait()
		log.Fatal("Typescript exits unexpected")
	}()

	return nil
}

func (ts *typescriptInstance) WaitForCompilationComplete() {
	ts.compilationCompleteWaiter.Wait()
}
