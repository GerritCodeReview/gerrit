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
	"path/filepath"
	"regexp"
	"strings"

	"golang.org/x/tools/godoc/vfs/httpfs"
	"golang.org/x/tools/godoc/vfs/zipfs"
)

var (
	plugins               = flag.String("plugins", "", "comma seperated plugin paths to serve")
	port                  = flag.String("port", ":8081", "Port to serve HTTP requests on")
	host                  = flag.String("host", "gerrit-review.googlesource.com", "Host to proxy requests to")
	scheme                = flag.String("scheme", "https", "URL scheme")
	cdnPattern            = regexp.MustCompile("https://cdn.googlesource.com/polygerrit_ui/[0-9.]*")
	webComponentPattern   = regexp.MustCompile("webcomponentsjs-p2")
	grAppPattern = regexp.MustCompile("gr-app-p2")
	bundledPluginsPattern = regexp.MustCompile("https://cdn.googlesource.com/polygerrit_assets/[0-9.]*")
)

func main() {
	flag.Parse()

	fontsArchive, err := openDataArchive("fonts.zip")
	if err != nil {
		log.Fatal(err)
	}

	componentsArchive, err := openDataArchive("app/test_components.zip")
	if err != nil {
		log.Fatal(err)
	}

	workspace := os.Getenv("BUILD_WORKSPACE_DIRECTORY")
	if err := os.Chdir(filepath.Join(workspace, "polygerrit-ui")); err != nil {
		log.Fatal(err)
	}

	http.Handle("/", http.FileServer(http.Dir("app")))
	http.Handle("/bower_components/",
		http.FileServer(httpfs.New(zipfs.New(componentsArchive, "bower_components"))))
	http.Handle("/fonts/",
		http.FileServer(httpfs.New(zipfs.New(fontsArchive, "fonts"))))

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

	// Replace the webcomponentsjs-p2 with webcomponentsjs
	replaced := webComponentPattern.ReplaceAllString(original, "webcomponentsjs")
	replaced = grAppPattern.ReplaceAllString(replaced, "gr-app")

	// Simply remove all CDN references, so files are loaded from the local file system  or the proxy
	// server instead.
	replaced = cdnPattern.ReplaceAllString(replaced, "")

	// Modify window.INITIAL_DATA so that it has the same effect as injectLocalPlugins. To achieve
	// this let's add JavaScript lines at the end of the <script>...</script> snippet that also
	// contains window.INITIAL_DATA=...
	// Here we rely on the fact that the <script> snippet that we want to append to is the first one.
	if len(*plugins) > 0 {
		// If the host page contains a reference to a plugin bundle that would be preloaded, then remove it.
		replaced = bundledPluginsPattern.ReplaceAllString(replaced, "")

		insertionPoint := strings.Index(replaced, "</script>")
		builder := new(strings.Builder)
		builder.WriteString(
			"window.INITIAL_DATA['/config/server/info'].plugin.html_resource_paths = []; ")
		builder.WriteString(
			"window.INITIAL_DATA['/config/server/info'].plugin.js_resource_paths = []; ")
		for _, p := range strings.Split(*plugins, ",") {
			if filepath.Ext(p) == ".html" {
				builder.WriteString(
					"window.INITIAL_DATA['/config/server/info'].plugin.html_resource_paths.push('" + p + "'); ")
			}
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
	htmlPluginsPath := []string{"plugin", "html_resource_paths"}
	htmlResources := getJsonPropByPath(response, htmlPluginsPath).([]interface{})
	jsResources := getJsonPropByPath(response, jsPluginsPath).([]interface{})

	for _, p := range strings.Split(*plugins, ",") {
		if filepath.Ext(p) == ".html" {
			htmlResources = append(htmlResources, p)
		}

		if filepath.Ext(p) == ".js" {
			jsResources = append(jsResources, p)
		}
	}

	setJsonPropByPath(response, jsPluginsPath, jsResources)
	setJsonPropByPath(response, htmlPluginsPath, htmlResources)

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
	fePaths    = []string{"/q/", "/c/", "/p/", "/x/", "/dashboard/", "/admin/", "/settings/"}
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
	gzw := newGzipResponseWriter(w)
	defer gzw.Close()
	http.DefaultServeMux.ServeHTTP(gzw, r)
}
