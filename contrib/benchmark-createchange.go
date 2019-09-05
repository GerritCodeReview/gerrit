// Program to benchmark Gerrit.  Creates pending changes in a loop,
// which tests performance of BatchRefUpdate and Lucene indexing
package main

import (
	"bytes"
	"encoding/base64"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"sort"
	"time"
)

func main() {
	user := flag.String("user", "admin", "username for basic auth")
	pw := flag.String("password", "secret", "HTTP password for basic auth")
	project := flag.String("project", "", "project to create changes in")
	gerritURL := flag.String("url", "http://localhost:8080/", "URL to gerrit instance")
	numChanges := flag.Int("n", 100, "number of changes to create")
	flag.Parse()
	if *gerritURL == "" {
		log.Fatal("provide --url")
	}
	if *project == "" {
		log.Fatal("provide --project")
	}

	u, err := url.Parse(*gerritURL)
	if err != nil {
		log.Fatal(err)
	}

	basicAuth := fmt.Sprintf("%s:%s", *user, *pw)
	authHeader := base64.StdEncoding.EncodeToString([]byte(basicAuth))

	client := &http.Client{}

	var dts []time.Duration
	startAll := time.Now()
	var lastSec int
	for i := 0; i < *numChanges; i++ {
		body := fmt.Sprintf(`{
    "project" : "%s",
    "subject" : "change %d",
    "branch" : "master",
    "status" : "NEW"
  }`, *project, i)
		start := time.Now()

		thisSec := int(start.Sub(startAll) / time.Second)
		if thisSec != lastSec {
			log.Printf("change %d", i)
		}
		lastSec = thisSec

		u.Path = "/a/changes/"
		req, err := http.NewRequest("POST", u.String(), bytes.NewBufferString(body))
		if err != nil {
			log.Fatal(err)
		}
		req.Header.Add("Authorization", "Basic "+authHeader)
		req.Header.Add("Content-Type", "application/json; charset=UTF-8")
		resp, err := client.Do(req)
		if err != nil {
			log.Fatal(err)
		}
		dt := time.Now().Sub(start)
		dts = append(dts, dt)

		if resp.StatusCode/100 == 2 {
			continue
		}
		log.Println("code", resp.StatusCode)
		io.Copy(os.Stdout, resp.Body)
	}

	sort.Slice(dts, func(i, j int) bool { return dts[i] < dts[j] })

	var total time.Duration
	for _, dt := range dts {
		total += dt
	}
	log.Printf("min %v max %v median %v avg %v", dts[0], dts[len(dts)-1], dts[len(dts)/2], total/time.Duration(len(dts)))
}
