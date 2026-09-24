package source

import (
	"encoding/json"
	"testing"
)

func TestIDPreservesLargeIntegersAndRejectsInvalidShapes(t *testing.T) {
	for _, raw := range []string{`9007199254740993`, `"9007199254740993"`} {
		var id ID
		if err := json.Unmarshal([]byte(raw), &id); err != nil || string(id) != "9007199254740993" {
			t.Fatalf("%s: %s %v", raw, id, err)
		}
	}
	for _, raw := range []string{`1.5`, `1e3`, `null`, `{}`, `"../../secret"`, `""`} {
		var id ID
		if json.Unmarshal([]byte(raw), &id) == nil {
			t.Errorf("accepted %s", raw)
		}
	}
}
