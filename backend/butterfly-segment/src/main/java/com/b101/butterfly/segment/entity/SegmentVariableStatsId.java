package com.b101.butterfly.segment.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SegmentVariableStatsId implements Serializable {

    private Long segmentId;
    private String variableKey;
}
