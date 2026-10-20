-- Baseline of the SmartKin schema (task DB-3). Derived from the JPA entities in com.umdc.smartkin.jpa.domain.
-- Flyway runs it inside the `smartkin` schema (spring.flyway.default-schema), so object names are unqualified here.
-- Identity lives in backbone: parent_id, child_id and notifications.user_id hold backbone user ids with no foreign key (decision D-1).
-- Enum labels are an initial proposal (decision D-2); extend them with `alter type ... add value` in a later migration.

create type reward_status as enum ('PENDING', 'APPROVED', 'DELIVERED', 'REJECTED');
create type goal_status as enum ('ACTIVE', 'COMPLETED', 'CANCELLED');
create type notification_type as enum ('ASSIGNMENT', 'REMINDER', 'ACHIEVEMENT', 'REWARD', 'GOAL', 'SYSTEM');
create type transaction_type as enum ('EARN', 'SPEND');

create table topics
(
    topic_id      uuid primary key default gen_random_uuid(),
    name          varchar(100) not null,
    description   text,
    icon          varchar(255),
    age_range_min integer,
    age_range_max integer
);

create table achievements
(
    achievement_id uuid primary key default gen_random_uuid(),
    name           varchar(100) not null,
    description    text         not null,
    icon           varchar(255),
    points_value   integer      not null,
    requirements   text         not null
);

create table rewards
(
    reward_id   uuid primary key default gen_random_uuid(),
    name        varchar(100) not null,
    description text         not null,
    icon        varchar(255),
    points_cost integer      not null,
    is_digital  boolean      not null default false
);

create table parent
(
    parent_id       uuid primary key,
    phone_number    varchar(20),
    address         text,
    profile_picture varchar(255)
);

create table child
(
    child_id     uuid primary key,
    parent_id    uuid    not null references parent (parent_id) on delete cascade,
    grade_level  varchar(50),
    avatar       varchar(255),
    total_points integer not null default 0
);

create table activities
(
    activity_id      uuid primary key         default gen_random_uuid(),
    topic_id         uuid         not null references topics (topic_id) on delete cascade,
    title            varchar(255) not null,
    description      text         not null,
    content          text         not null,
    duration_minutes integer      not null,
    points_reward    integer      not null,
    created_at       timestamp with time zone not null default now(),
    updated_at       timestamp with time zone not null default now(),
    is_active        boolean      not null    default true
);

create table assigned_activities
(
    assignment_id uuid primary key         default gen_random_uuid(),
    child_id      uuid not null references child (child_id) on delete cascade,
    activity_id   uuid not null references activities (activity_id) on delete cascade,
    assigned_by   uuid not null references parent (parent_id) on delete cascade,
    assigned_at   timestamp with time zone not null default now(),
    due_date      timestamp with time zone
);

create table activity_progress
(
    progress_id           uuid primary key default gen_random_uuid(),
    assignment_id         uuid    not null unique references assigned_activities (assignment_id) on delete cascade,
    completion_percentage integer not null default 0,
    start_time            timestamp with time zone,
    last_accessed         timestamp with time zone,
    completion_time       timestamp with time zone,
    score                 integer,
    parent_feedback       text
);

create table child_achievements
(
    child_achievement_id uuid primary key default gen_random_uuid(),
    child_id             uuid not null references child (child_id) on delete cascade,
    achievement_id       uuid not null references achievements (achievement_id) on delete cascade,
    earned_at            timestamp with time zone not null default now()
);

create table child_rewards
(
    child_reward_id uuid primary key default gen_random_uuid(),
    child_id        uuid not null references child (child_id) on delete cascade,
    reward_id       uuid not null references rewards (reward_id) on delete cascade,
    redeemed_at     timestamp with time zone not null default now(),
    status          reward_status not null default 'PENDING'
);

create table goals
(
    goal_id         uuid primary key default gen_random_uuid(),
    child_id        uuid         not null references child (child_id) on delete cascade,
    title           varchar(255) not null,
    description     text,
    created_by      uuid         not null references parent (parent_id) on delete cascade,
    target_value    integer      not null,
    current_value   integer      not null default 0,
    unit_of_measure varchar(50)  not null,
    start_date      date         not null,
    target_date     date         not null,
    reward_id       uuid references rewards (reward_id) on delete set null,
    status          goal_status  not null default 'ACTIVE'
);

create table points
(
    point_id         uuid primary key default gen_random_uuid(),
    child_id         uuid        not null references child (child_id) on delete cascade,
    amount           integer     not null,
    source_type      varchar(50) not null,
    source_id        uuid        not null,
    transaction_date timestamp with time zone not null default now(),
    description      text,
    transaction_type transaction_type not null
);

create table notifications
(
    notification_id   uuid primary key default gen_random_uuid(),
    user_id           uuid         not null,
    title             varchar(255) not null,
    message           text         not null,
    related_entity_id uuid,
    created_at        timestamp with time zone not null default now(),
    read_at           timestamp with time zone,
    is_read           boolean      not null default false,
    type              notification_type not null
);

create index idx_child_parent on child (parent_id);
create index idx_activities_topic on activities (topic_id);
create index idx_assigned_activities_child on assigned_activities (child_id);
create index idx_assigned_activities_activity on assigned_activities (activity_id);
create index idx_child_achievements_child on child_achievements (child_id);
create index idx_child_rewards_child on child_rewards (child_id);
create index idx_goals_child on goals (child_id);
create index idx_points_child on points (child_id, transaction_date);
create index idx_notifications_user on notifications (user_id, is_read);
