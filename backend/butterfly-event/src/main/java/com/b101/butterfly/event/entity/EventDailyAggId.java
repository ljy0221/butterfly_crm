package com.b101.butterfly.event.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class EventDailyAggId implements Serializable {

    private LocalDate logDate;
    private Long projectId;
    private Long categoryId;
    private Long userId;
}
