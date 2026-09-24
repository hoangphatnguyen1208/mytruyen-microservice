package jobs

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/go-resty/resty/v2"
	"mytruyen-worker/internal/backend"
	"mytruyen-worker/internal/source"
)

const bookID = "9007199254740993"
const authorID = "12345678-1234-1234-1234-123456789012"

func bookFixture() map[string]any {
	return map[string]any{"id": json.Number(bookID), "name": "Story", "slug": "story", "status": 2, "kind": 1, "sex": 1, "synopsis": "Description",
		"author": map[string]any{"id": 77, "name": "Author"},
		"genres": []any{map[string]any{"id": 88, "name": "Fantasy"}},
		"tags":   []any{map[string]any{"id": 99, "name": "Adventure", "type": "Theme"}},
		"poster": "https://example.test/cover.jpg", "note": []any{},
		"view_count": 999, "chapter_count": 100, "published": true}
}

type fixture struct {
	t               *testing.T
	book            map[string]any
	lookupStatus    int
	referenceStatus int
	existing        bool
	writes          []string
	metadata        map[string]any
	expected        any
	sourceCalls     int
}

func envelope(w http.ResponseWriter, value any) {
	json.NewEncoder(w).Encode(map[string]any{"success": true, "data": value})
}
func (f *fixture) source(w http.ResponseWriter, r *http.Request) {
	f.sourceCalls++
	w.Header().Set("Content-Type", "application/json")
	switch r.URL.Path {
	case "/books/" + bookID:
		envelope(w, f.book)
	case "/books/options":
		envelope(w, map[string]any{"filter": map[string]any{"status": map[string]any{"data": map[string]string{"2": "Ongoing"}}}})
	default:
		f.t.Errorf("unexpected source request %s", r.URL.Path)
		w.WriteHeader(404)
	}
}
func (f *fixture) backend(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method == "GET" {
		if r.URL.Path != "/internal/import/books/metruyencv/"+bookID {
			f.t.Errorf("legacy lookup %s", r.URL.Path)
		}
		if f.lookupStatus != 0 {
			w.WriteHeader(f.lookupStatus)
			return
		}
		if !f.existing {
			w.WriteHeader(404)
			return
		}
		envelope(w, map[string]any{"source": "metruyencv", "external_id": bookID, "book_id": 42, "version": 7, "outcome": "mapped"})
		return
	}
	if r.Method != "PUT" {
		f.t.Errorf("unexpected method %s", r.Method)
		w.WriteHeader(400)
		return
	}
	f.writes = append(f.writes, r.URL.Path)
	if r.URL.Path == "/internal/import/books/metruyencv/"+bookID {
		var body struct {
			Metadata map[string]any `json:"metadata"`
			Expected any            `json:"expected_version"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			f.t.Error(err)
		}
		f.metadata = body.Metadata
		f.expected = body.Expected
		envelope(w, map[string]any{"source": "metruyencv", "external_id": bookID, "book_id": 42, "version": 8, "outcome": "created"})
		return
	}
	if f.referenceStatus != 0 {
		w.WriteHeader(f.referenceStatus)
		return
	}
	parts := strings.Split(r.URL.Path, "/")
	if len(parts) != 6 || parts[1] != "internal" || parts[2] != "import" {
		f.t.Errorf("legacy write %s", r.URL.Path)
		w.WriteHeader(400)
		return
	}
	kind, id := parts[3], parts[5]
	ids := map[string]string{"authors": authorID, "book-statuses": "11", "genres": "22", "tags": "33"}
	if ids[kind] == "" {
		f.t.Errorf("unknown kind %s", kind)
	}
	envelope(w, map[string]any{"source": "metruyencv", "external_id": id, "kind": kind, "reference_id": ids[kind], "outcome": "created"})
}
func (f *fixture) run() (*backend.BookResult, error) {
	s := httptest.NewServer(http.HandlerFunc(f.source))
	defer s.Close()
	b := httptest.NewServer(http.HandlerFunc(f.backend))
	defer b.Close()
	j := BookImporter{Backend: &backend.Client{HTTP: resty.New().SetBaseURL(b.URL)}, Source: &source.Client{HTTP: resty.New().SetBaseURL(s.URL)}}
	return j.Run(bookID)
}
func TestImportMapsEveryIDAndDropsCountersAndPublication(t *testing.T) {
	for _, existing := range []bool{false, true} {
		t.Run(fmt.Sprint(existing), func(t *testing.T) {
			f := fixture{t: t, book: bookFixture(), existing: existing}
			result, err := f.run()
			if err != nil {
				t.Fatal(err)
			}
			if result.BookID != 42 {
				t.Fatal("wrong ID")
			}
			if len(f.writes) != 5 {
				t.Fatalf("writes=%v", f.writes)
			}
			if f.metadata["status_id"] != float64(11) || f.metadata["author_id"] != authorID {
				t.Fatalf("not mapped: %v", f.metadata)
			}
			if f.metadata["genre_ids"].([]any)[0] != float64(22) || f.metadata["tag_ids"].([]any)[0] != float64(33) {
				t.Fatal("wrong relation IDs")
			}
			for _, key := range []string{"id", "published", "view_count", "chapter_count", "word_count"} {
				if _, ok := f.metadata[key]; ok {
					t.Errorf("leaked field %s", key)
				}
			}
			if existing && f.expected != float64(7) {
				t.Fatalf("missing expected version: %v", f.expected)
			}
			if !existing && f.expected != nil {
				t.Fatal("unexpected version on create")
			}
		})
	}
}
func TestFailedLookupNeverFetchesSourceOrWrites(t *testing.T) {
	for _, status := range []int{401, 403, 409, 429, 500} {
		f := fixture{t: t, book: bookFixture(), lookupStatus: status}
		if _, err := f.run(); err == nil {
			t.Errorf("accepted %d", status)
		}
		if f.sourceCalls != 0 || len(f.writes) != 0 {
			t.Fatal("continued after failed lookup")
		}
	}
}
func TestInvalidSourceRelationsCannotClearCatalogRelations(t *testing.T) {
	for _, field := range []string{"author", "genres", "tags"} {
		f := fixture{t: t, book: bookFixture()}
		delete(f.book, field)
		if _, err := f.run(); err == nil {
			t.Errorf("accepted missing %s", field)
		}
		if len(f.writes) != 0 {
			t.Fatal("wrote incomplete snapshot")
		}
	}
}
func TestMissingAuthorIDAndReferenceConflictFailClosed(t *testing.T) {
	f := fixture{t: t, book: bookFixture()}
	f.book["author"] = map[string]any{"name": "Same name is not an identity"}
	if _, err := f.run(); err == nil || len(f.writes) != 0 {
		t.Fatal("accepted author without ID")
	}
	f = fixture{t: t, book: bookFixture(), referenceStatus: 409}
	if _, err := f.run(); err == nil || f.metadata != nil {
		t.Fatal("continued after reference conflict")
	}
}
