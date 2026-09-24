package jobs

import (
	"bytes"
	"encoding/json"
	"fmt"
	"sort"
	"strings"

	"github.com/gosimple/slug"
	"mytruyen-worker/internal/backend"
	"mytruyen-worker/internal/source"
)

type BookImporter struct {
	Backend *backend.Client
	Source  *source.Client
}
type reference struct {
	kind, id string
	taxon    backend.Taxon
}

// Run imports metadata only. It never schedules chapters or publishes a book.
func (j *BookImporter) Run(id string) (*backend.BookResult, error) {
	// Read the version before fetching source data, not after a slow crawl.
	mapped, err := j.Backend.LookupBook(id)
	if err != nil {
		return nil, err
	}
	b, err := j.Source.Book(id)
	if err != nil {
		return nil, err
	}
	options, err := j.Source.Options()
	if err != nil {
		return nil, err
	}
	statusName := options.Filter.Status.Data[string(b.Status)]
	if strings.TrimSpace(statusName) == "" {
		return nil, fmt.Errorf("source status missing from options")
	}
	metadata := backend.Metadata{Name: b.Name, Slug: b.Slug, Synopsis: b.Synopsis, Kind: 1, Sex: 1,
		ChapterPerWeek: b.ChapterPerWeek, GenreIDs: []int64{}, TagIDs: []int64{}}
	if b.Kind != nil {
		metadata.Kind = *b.Kind
	}
	if b.Sex != nil {
		metadata.Sex = *b.Sex
	}
	if b.ChapterPerWeek < 0 {
		return nil, fmt.Errorf("invalid chapter frequency")
	}
	if raw := bytes.TrimSpace(b.Poster); len(raw) > 0 && !bytes.Equal(raw, []byte("null")) {
		if raw[0] == '"' {
			var value string
			if json.Unmarshal(raw, &value) != nil {
				return nil, fmt.Errorf("invalid poster")
			}
			if value != "" {
				metadata.Poster = map[string]any{"default": value}
			}
		} else if raw[0] != '{' || json.Unmarshal(raw, &metadata.Poster) != nil {
			return nil, fmt.Errorf("unsupported poster shape")
		}
	}
	if raw := bytes.TrimSpace(b.Note); len(raw) > 0 && !bytes.Equal(raw, []byte("null")) && !bytes.Equal(raw, []byte("[]")) {
		var value string
		if json.Unmarshal(raw, &value) != nil {
			return nil, fmt.Errorf("unsupported note shape")
		}
		metadata.Note = &value
	}
	refs := []reference{{"book-statuses", string(b.Status), backend.Taxon{Name: statusName, Slug: slug.Make(statusName)}}}
	for _, g := range b.Genres {
		refs = append(refs, reference{"genres", string(g.ID), backend.Taxon{Name: g.Name, Slug: slug.Make(g.Name)}})
	}
	for _, t := range b.Tags {
		tagType := t.Type
		for _, group := range options.Filter.Tags {
			if _, ok := group.Data[string(t.ID)]; ok {
				if tagType != "" && tagType != group.Name {
					return nil, fmt.Errorf("ambiguous tag type")
				}
				tagType = group.Name
			}
		}
		if strings.TrimSpace(tagType) == "" {
			return nil, fmt.Errorf("source tag type missing")
		}
		refs = append(refs, reference{"tags", string(t.ID), backend.Taxon{Name: t.Name, Slug: slug.Make(t.Name), Type: &tagType}})
	}
	// Validate reference identities before the first write; Catalog validates field limits.
	seen := map[string]bool{}
	for _, r := range refs {
		if !backend.Identifier.MatchString(r.id) || strings.TrimSpace(r.taxon.Name) == "" || r.taxon.Slug == "" {
			return nil, fmt.Errorf("invalid source reference")
		}
		key := r.kind + ":" + r.id
		if seen[key] {
			return nil, fmt.Errorf("duplicate source reference")
		}
		seen[key] = true
	}
	if b.Author != nil {
		if !backend.Identifier.MatchString(string(b.Author.ID)) || strings.TrimSpace(b.Author.Name) == "" {
			return nil, fmt.Errorf("source author needs a stable ID and name")
		}
		value, err := j.Backend.ImportAuthor(string(b.Author.ID), backend.Author{Name: b.Author.Name, LocalName: b.Author.LocalName, Avatar: b.Author.Avatar})
		if err != nil {
			return nil, err
		}
		metadata.AuthorID = &value
	}
	for _, r := range refs {
		value, err := j.Backend.ImportTaxon(r.kind, r.id, r.taxon)
		if err != nil {
			return nil, err
		}
		switch r.kind {
		case "book-statuses":
			metadata.StatusID = value
		case "genres":
			metadata.GenreIDs = append(metadata.GenreIDs, value)
		case "tags":
			metadata.TagIDs = append(metadata.TagIDs, value)
		}
	}
	// Relation order has no semantic meaning; keep retry payloads stable.
	sort.Slice(metadata.GenreIDs, func(i, k int) bool { return metadata.GenreIDs[i] < metadata.GenreIDs[k] })
	sort.Slice(metadata.TagIDs, func(i, k int) bool { return metadata.TagIDs[i] < metadata.TagIDs[k] })
	var expected *int64
	if mapped != nil {
		expected = mapped.Version
	}
	return j.Backend.ImportBook(id, metadata, expected)
}
