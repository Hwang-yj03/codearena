package com.codearena.api.judge;

/** judge-worker/judge_worker/worker.py 의 큐 이름과 반드시 같아야 한다. */
public final class JudgeQueue {

    public static final String SUBMISSIONS = "codearena:submissions";
    public static final String RESULTS = "codearena:results";

    private JudgeQueue() {
    }
}
