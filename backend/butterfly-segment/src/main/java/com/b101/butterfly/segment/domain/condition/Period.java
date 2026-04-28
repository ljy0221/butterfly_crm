package com.b101.butterfly.segment.domain.condition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Period.Relative.class, name = "relative"),
        @JsonSubTypes.Type(value = Period.Absolute.class, name = "absolute"),
        @JsonSubTypes.Type(value = Period.Cumulative.class, name = "cumulative")
})
public sealed interface Period
        permits Period.Relative, Period.Absolute, Period.Cumulative {

    record Relative(int days) implements Period {}

    record Absolute(LocalDate startDate, LocalDate endDate) implements Period {}

    record Cumulative() implements Period {}
}
