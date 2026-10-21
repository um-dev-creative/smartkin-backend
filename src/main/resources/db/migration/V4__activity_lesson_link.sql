-- BE-6: an activity plays and grades one lesson. Lessons and quizzes live in MongoDB, so the link is a plain id (no foreign key).
-- The column is nullable: activities created before this migration keep working and simply have no lesson.
alter table smartkin.activities add column lesson_id uuid;
create index ix_activities_lesson on smartkin.activities (lesson_id) where lesson_id is not null;
