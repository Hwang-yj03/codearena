package com.codearena.api.problem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    /** 문제 번호. 백준처럼 사람이 정하는 번호라 자동 생성하지 않는다. */
    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_description", nullable = false, columnDefinition = "TEXT")
    private String inputDescription;

    @Column(name = "output_description", nullable = false, columnDefinition = "TEXT")
    private String outputDescription;

    @Column(name = "time_limit_ms", nullable = false)
    private int timeLimitMs;

    @Column(name = "memory_limit_mb", nullable = false)
    private int memoryLimitMb;

    @ElementCollection
    @CollectionTable(name = "problem_sample", joinColumns = @JoinColumn(name = "problem_id"))
    @OrderBy("seq ASC")
    private List<ProblemSample> samples = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Problem(Long id, String title, String description, String inputDescription,
                   String outputDescription, int timeLimitMs, int memoryLimitMb) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.inputDescription = inputDescription;
        this.outputDescription = outputDescription;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
    }

    public void addSample(String input, String output) {
        samples.add(new ProblemSample(samples.size() + 1, input, output));
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
