package main

import (
	"context"
	"testing"
)

func TestRequiresExplicitApplyBeforeConfigOrNetwork(t *testing.T) {
	if err := run(context.Background(), "123", false); err == nil {
		t.Fatal("expected explicit apply guard")
	}
}
