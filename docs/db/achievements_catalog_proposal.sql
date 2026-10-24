-- DRAFT FOR THE PRODUCT TEAM: a proposed achievement catalog. It is NOT a migration and is not applied by the application.
-- Edit the names, descriptions, icons, points and thresholds, then run it once with psql against the smartkin schema:
--   psql ... -f docs/db/achievements_catalog_proposal.sql
-- It is safe to run again: each row has a fixed id and `on conflict (achievement_id) do update` refreshes its text and rule.
-- `requirements` is a JSON rule {"type": ..., "threshold": N}. Supported types (docs/TECHNICAL_GUIDE.md, "Achievement rules"):
--   QUIZZES_PASSED       distinct lessons whose quiz the child passed
--   ACTIVITIES_COMPLETED distinct activities the child completed
--   POINTS_REACHED       the child's point balance
-- The three rows with ids a1000000-...-00000000000{1,2,3} are the placeholders of migration V5; this script replaces their text.
-- AchievementCatalogProposalTest keeps every rule below valid and the ids unique.

begin;

insert into smartkin.achievements (achievement_id, name, description, icon, points_value, requirements)
values ('a1000000-0000-4000-8000-000000000001', 'Primer quiz aprobado', 'Aprobaste tu primer quiz. ¡Ya empezaste a protegerte en internet!', 'quiz', 10,
        '{"type":"QUIZZES_PASSED","threshold":1}'),
       ('a1000000-0000-4000-8000-000000000002', 'Explorador', 'Completaste 3 actividades. Sigue explorando el mundo digital con cuidado.', 'explorer', 25,
        '{"type":"ACTIVITIES_COMPLETED","threshold":3}'),
       ('a1000000-0000-4000-8000-000000000003', 'Cien puntos', 'Llegaste a 100 puntos. ¡Vas muy bien!', 'trophy', 20,
        '{"type":"POINTS_REACHED","threshold":100}'),
       ('a1000000-0000-4000-8000-000000000004', 'Detective de contraseñas', 'Aprobaste 3 quizzes distintos. Ya sabes reconocer lo que es seguro y lo que no.', 'detective', 30,
        '{"type":"QUIZZES_PASSED","threshold":3}'),
       ('a1000000-0000-4000-8000-000000000005', 'Guardián digital', 'Aprobaste 5 quizzes distintos. Puedes ayudar a tu familia a cuidarse en internet.', 'shield', 50,
        '{"type":"QUIZZES_PASSED","threshold":5}'),
       ('a1000000-0000-4000-8000-000000000006', 'Constante', 'Completaste 5 actividades. La práctica hace al maestro.', 'streak', 40,
        '{"type":"ACTIVITIES_COMPLETED","threshold":5}'),
       ('a1000000-0000-4000-8000-000000000007', 'Experto en seguridad', 'Completaste 10 actividades. ¡Eres todo un experto!', 'medal', 75,
        '{"type":"ACTIVITIES_COMPLETED","threshold":10}'),
       ('a1000000-0000-4000-8000-000000000008', 'Quinientos puntos', 'Llegaste a 500 puntos. ¡Increíble constancia!', 'star', 60,
        '{"type":"POINTS_REACHED","threshold":500}')
on conflict (achievement_id) do update
    set name         = excluded.name,
        description  = excluded.description,
        icon         = excluded.icon,
        points_value = excluded.points_value,
        requirements = excluded.requirements;

commit;
