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
	"bufio"
	"compress/gzip"
	"encoding/json"
	"errors"
	"flag"
	"github.com/robfig/soy"
	"io"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
)

var (
	restHost = flag.String("host", "gerrit-review.googlesource.com", "Host to proxy requests to")
	port     = flag.String("port", ":8081", "Port to serve HTTP requests on")
	prod     = flag.Bool("prod", false, "Serve production assets")
	scheme   = flag.String("scheme", "https", "URL scheme")
	plugins  = flag.String("plugins", "", "comma seperated plugin paths to serve")

	tofu, _ = soy.NewBundle().
		AddTemplateFile("../resources/com/google/gerrit/httpd/raw/PolyGerritIndexHtml.soy").
		CompileToTofu()
)

func main() {
	flag.Parse()

	http.HandleFunc("/index.html", handleIndex)

	if *prod {
		http.Handle("/", http.FileServer(http.Dir("dist")))
	} else {
		http.Handle("/", http.FileServer(http.Dir("app")))
	}

	http.HandleFunc("/changes/", handleRESTProxy)
	http.HandleFunc("/accounts/", handleRESTProxy)
	http.HandleFunc("/config/", handleRESTProxy)
	http.HandleFunc("/projects/", handleRESTProxy)
	http.HandleFunc("/accounts/self/detail", handleAccountDetail)
	if len(*plugins) > 0 {
		http.Handle("/plugins/", http.StripPrefix("/plugins/",
			http.FileServer(http.Dir("../plugins"))))
		log.Println("Local plugins from", "../plugins")
	} else {
		http.HandleFunc("/plugins/", handleRESTProxy)
	}
	log.Println("Serving on port", *port)
	log.Fatal(http.ListenAndServe(*port, &server{}))
}

func handleIndex(w http.ResponseWriter, r *http.Request) {
	var obj = map[string]interface{}{
		"canonicalPath":      "",
		"staticResourcePath": "",
	}
	w.Header().Set("Content-Type", "text/html")
	tofu.Render(w, "com.google.gerrit.httpd.raw.Index", obj)
}

func handleRESTProxy(w http.ResponseWriter, r *http.Request) {
	if strings.HasSuffix(r.URL.Path, ".html") {
		w.Header().Set("Content-Type", "text/html")
	} else if strings.HasSuffix(r.URL.Path, ".css") {
		w.Header().Set("Content-Type", "text/css")
	} else {
		w.Header().Set("Content-Type", "application/json")
	}
	req := &http.Request{
		Method: "GET",
		URL: &url.URL{
			Scheme:   *scheme,
			Host:     *restHost,
			Opaque:   r.URL.EscapedPath(),
			RawQuery: r.URL.RawQuery,
		},
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer res.Body.Close()
	w.WriteHeader(res.StatusCode)
	if _, err := io.Copy(w, patchResponse(r, res)); err != nil {
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

func patchResponse(r *http.Request, res *http.Response) io.Reader {
	switch r.URL.EscapedPath() {
	case "/config/server/info":
		return injectLocalPlugins(res.Body)
	default:
		return res.Body
	}
}

func injectLocalPlugins(r io.Reader) io.Reader {
	if len(*plugins) == 0 {
		return r
	}
	// Skip escape prefix
	io.CopyN(ioutil.Discard, r, 5)
	dec := json.NewDecoder(r)

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
		if strings.HasSuffix(p, ".html") {
			htmlResources = append(htmlResources, p)
		}

		if strings.HasSuffix(p, ".js") {
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
	fePaths    = []string{"/q/", "/c/", "/p/", "/x/", "/dashboard/", "/admin/"}
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
