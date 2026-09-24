package task

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/go-resty/resty/v2"
	"mytruyen-worker/internal/httpclient"
)

func TestCatalogCompatibilityContract(t *testing.T) {
	base := os.Getenv("WORKER_CONTRACT_URL")
	jwt := os.Getenv("WORKER_CONTRACT_JWT")
	if base == "" || jwt == "" {
		t.Skip("run via Catalog WorkerHandlerContractTests; no live systems are used")
	}
	var chapterCount atomic.Int64
	chapterCount.Store(2)
	source := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch {
		case r.URL.Path == "/books/options":
			fmt.Fprint(w, `{"data":{"filter":{"genres":{"group":{"data":{"41":"Compat genre"}}},"tags":{"group":{"name":"Theme","data":{"51":"Compat tag"}}},"status":{"data":{"9":"Compat status"}}}}}`)
		case r.URL.Path == "/books":
			fmt.Fprint(w, `{"data":[{"id":777},{"id":778}],"pagination":{"Last":1}}`)
		case strings.HasPrefix(r.URL.Path, "/books/"):
			id, err := strconv.Atoi(strings.TrimPrefix(r.URL.Path, "/books/"))
			if err != nil {
				t.Error(err)
				w.WriteHeader(400)
				return
			}
			book := map[string]any{"id": id, "name": "Source story", "slug": fmt.Sprintf("compat-source-%d", id), "status": 9, "kind": 1, "sex": 1,
				"synopsis": "Source synopsis", "poster": "", "note": []any{}, "published": true, "chapter_count": chapterCount.Load(), "word_count": 17,
				"view_count": 555, "review_count": 3, "review_score": 4.5,
				"genres": []any{map[string]any{"id": 41, "name": "Compat genre"}},
				"tags":   []any{map[string]any{"id": 51, "name": "Compat tag", "type": "Theme"}}}
			if id == 777 {
				book["author"] = map[string]any{"name": "Original author", "local_name": ""}
			} else {
				book["creator"] = map[string]any{"name": "Creator fallback", "local_name": ""}
			}
			json.NewEncoder(w).Encode(map[string]any{"data": book})
		case strings.TrimRight(r.URL.Path, "/") == "/chapters":
			chapters := []any{}
			for i := 1; i <= int(chapterCount.Load()); i++ {
				chapters = append(chapters, map[string]any{"index": i, "name": fmt.Sprintf("Chapter %d", i), "word_count": 8 + i})
			}
			json.NewEncoder(w).Encode(map[string]any{"data": chapters})
		default:
			t.Errorf("unexpected source path %s", r.URL.Path)
			w.WriteHeader(404)
		}
	}))
	defer source.Close()
	s := resty.New().SetBaseURL(source.URL).SetTimeout(10 * time.Second)
	b := resty.New().SetBaseURL(base).SetAuthToken(jwt).SetTimeout(10 * time.Second)
	httpclient.UseWorkerCompatibilityEndpoints(b, base)
	for i := 0; i < 2; i++ {
		if !GenresHandler(s, b, 1) || !TagsHandler(s, b, 1) || !BookStatusHandler(s, b, 1) {
			t.Fatal("reference handlers failed")
		}
	}
	for _, id := range []int{777, 778} {
		if !BookHandler(s, b, id, 1) {
			t.Fatalf("book handler failed for %d", id)
		}
		if !ChaptersHandler(s, b, id, 1) || !ChaptersHandler(s, b, id, 1) {
			t.Fatal("chapter create/replay failed")
		}
	}
	if !BookHandler(s, b, 777, 1) {
		t.Fatal("book update branch failed")
	}
	read := func(path string) map[string]any {
		t.Helper()
		var response struct {
			Data map[string]any `json:"data"`
		}
		r, err := b.R().SetResult(&response).Get(path)
		if err != nil || r.StatusCode() != 200 {
			t.Fatalf("read %s failed: %v %s", path, err, r.String())
		}
		return response.Data
	}
	book := read("books/id/777")
	if book["id"] != float64(777) || book["status_id"] != float64(9) || book["chapter_count"] != float64(2) || book["published"] != true {
		t.Fatalf("book semantics changed: %v", book)
	}
	if book["view_count"] != float64(555) || book["average_rating"] != 4.5 {
		t.Fatal("source counters changed")
	}
	fallback := read("books/id/778")["author"].(map[string]any)
	if fallback["name"] != "Creator fallback" {
		t.Fatal("author fallback changed")
	}
	chapter := read("chapters/id/777/2")
	if chapter["book_id"] != float64(777) || chapter["published"] != true || chapter["word_count"] != float64(10) {
		t.Fatal("chapter semantics changed")
	}
	chapterCount.Store(3)
	if !CheckNewChaptersHandler(s, b) || !AllBookHandler(s, b, "unused", 1) {
		t.Fatal("task discovery/chaining failed")
	}
	if !ChaptersHandler(s, b, 777, 1) {
		t.Fatal("incremental chapter import failed")
	}
	read("chapters/id/777/3")
}
