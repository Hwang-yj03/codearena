package com.codearena.api.submission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** idx_submission_member (member_id, id) 인덱스를 탄다 */
    Page<Submission> findByMemberId(Long memberId, Pageable pageable);
}
