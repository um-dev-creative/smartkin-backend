-- BE-11: achievements are awarded by rules kept as JSON in achievements.requirements, for example {"type":"QUIZZES_PASSED","threshold":3}.
-- Supported types: QUIZZES_PASSED (distinct lessons whose quiz was passed), ACTIVITIES_COMPLETED (distinct activities completed) and
-- POINTS_REACHED (child.total_points). An achievement is awarded once per child; the unique index makes that true even under concurrency.
create unique index uq_child_achievements_child_achievement on smartkin.child_achievements (child_id, achievement_id);

-- Starter catalog. These three are placeholders for the product team: edit the text, points and thresholds with plain SQL or add more rows;
-- the fixed ids only make this migration safe to run twice.
insert into smartkin.achievements (achievement_id, name, description, icon, points_value, requirements)
values ('a1000000-0000-4000-8000-000000000001', 'Primer quiz aprobado', 'Aprobaste tu primer quiz.', 'quiz', 10,
        '{"type":"QUIZZES_PASSED","threshold":1}'),
       ('a1000000-0000-4000-8000-000000000002', 'Explorador', 'Completaste 3 actividades.', 'explorer', 25,
        '{"type":"ACTIVITIES_COMPLETED","threshold":3}'),
       ('a1000000-0000-4000-8000-000000000003', 'Cien puntos', 'Llegaste a 100 puntos.', 'trophy', 20,
        '{"type":"POINTS_REACHED","threshold":100}')
on conflict (achievement_id) do nothing;
