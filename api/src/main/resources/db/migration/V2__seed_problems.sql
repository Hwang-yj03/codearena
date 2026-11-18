-- judge-worker/problems/ 의 테스트케이스와 같은 번호를 사용한다
INSERT INTO problem (id, title, description, input_description, output_description,
                     time_limit_ms, memory_limit_mb, created_at)
VALUES (1000, 'A+B',
        '두 정수 A와 B를 입력받은 다음, A+B를 출력하세요.',
        '첫째 줄에 A와 B가 주어집니다. (-2,000,000,000 ≤ A, B ≤ 2,000,000,000)',
        '첫째 줄에 A+B를 출력합니다.',
        1000, 128, NOW(6)),
       (1001, 'N개의 수의 합',
        'N개의 정수가 주어질 때, 그 합을 구하세요.',
        '첫째 줄에 N(1 ≤ N ≤ 200,000)이 주어집니다. 둘째 줄에 N개의 정수가 공백으로 구분되어 주어집니다. 각 정수의 절댓값은 1,000,000,000 이하입니다.',
        '첫째 줄에 N개의 정수의 합을 출력합니다.',
        1000, 128, NOW(6));

INSERT INTO problem_sample (problem_id, seq, input, output)
VALUES (1000, 1, '1 2', '3'),
       (1000, 2, '-5 5', '0'),
       (1001, 1, '3\n1 2 3', '6');
