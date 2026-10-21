package com.oakfield.clinic.model;

/** Patient document lifecycle: SUBMITTED -> {VERIFIED, REJECTED, ARCHIVED}; REJECTED -> {VERIFIED, ARCHIVED}; VERIFIED -> ARCHIVED. */
public enum DocumentStatus { SUBMITTED, VERIFIED, REJECTED, ARCHIVED }
