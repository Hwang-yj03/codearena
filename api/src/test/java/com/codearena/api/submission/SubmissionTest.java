package com.codearena.api.submission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubmissionTest {

    @Test
    void newSubmissionIsPending() {
        var s = new Submission(1L, 1000L, Language.CPP, "int main(){}");

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(s.getVerdict()).isNull();
    }

    @Test
    void applyResultMarksDone() {
        var s = new Submission(1L, 1000L, Language.CPP, "int main(){}");

        boolean applied = s.applyResult(Verdict.AC, 3, 3500, 3, 3, "");

        assertThat(applied).isTrue();
        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(s.getVerdict()).isEqualTo(Verdict.AC);
        assertThat(s.getCompileLog()).isNull();
        assertThat(s.getJudgedAt()).isNotNull();
    }

    @Test
    void duplicateResultIsIgnored() {
        var s = new Submission(1L, 1000L, Language.CPP, "int main(){}");
        s.applyResult(Verdict.WA, 1, 3000, 2, 3, null);

        boolean applied = s.applyResult(Verdict.AC, 1, 3000, 3, 3, null);

        assertThat(applied).isFalse();
        assertThat(s.getVerdict()).isEqualTo(Verdict.WA);
        assertThat(s.getPassed()).isEqualTo(2);
    }
}
