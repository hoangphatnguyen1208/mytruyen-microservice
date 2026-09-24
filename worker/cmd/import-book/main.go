// import-book is an explicit, metadata-only canary command. It never consumes a queue.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"mytruyen-worker/internal/backend"
	"mytruyen-worker/internal/config"
	"mytruyen-worker/internal/httpclient"
	"mytruyen-worker/internal/jobs"
	"mytruyen-worker/internal/source"
)

func run(ctx context.Context, id string, apply bool) error {
	if !apply {
		return fmt.Errorf("no requests made: pass -apply to explicitly enable metadata writes")
	}
	if !backend.Identifier.MatchString(id) {
		return fmt.Errorf("valid -book-id required")
	}
	cfg, err := config.LoadImport(os.Getenv)
	if err != nil {
		return err
	}
	job := jobs.BookImporter{
		Backend: &backend.Client{HTTP: httpclient.New(ctx, cfg.Backend.URL, httpclient.Credentials{Email: cfg.Backend.Email, Password: cfg.Backend.Password}, false, cfg.HTTPTimeout)},
		Source:  &source.Client{HTTP: httpclient.New(ctx, cfg.Source.URL, httpclient.Credentials{Email: cfg.Source.Email, Password: cfg.Source.Password}, true, cfg.HTTPTimeout)},
	}
	result, err := job.Run(id)
	if err != nil {
		return err
	}
	return json.NewEncoder(os.Stdout).Encode(result)
}
func main() {
	id := flag.String("book-id", "", "upstream book ID (not the Catalog ID)")
	apply := flag.Bool("apply", false, "allow requests and metadata writes; never imports/publishes chapters")
	flag.Parse()
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	if err := run(ctx, *id, *apply); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
