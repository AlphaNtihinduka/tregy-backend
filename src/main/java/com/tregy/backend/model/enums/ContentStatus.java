package com.tregy.backend.model.enums;

/** Only VALIDATED and later states are stored; a draft that fails validation is never persisted. */
public enum ContentStatus { VALIDATED, PUBLISHED, WITHDRAWN }
