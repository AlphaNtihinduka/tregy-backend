package com.tregy.backend.dto.response;

/**
 * One downloadable pack. {@code version} rises whenever the pack's contents change (a publish or a
 * withdrawal), so a phone downloads again only when the number is higher than the one it holds.
 */
public record PackManifestEntryDTO(String packId, String theme, String level, long version, int itemCount) {
}
