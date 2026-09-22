package com.createyourpizza.catalog.menu;

import java.time.Instant;

public record LatestMenu(byte[] pdf, int version, Instant updatedAt) {
}
