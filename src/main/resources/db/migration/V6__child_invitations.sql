-- S-2: a parent no longer links an arbitrary user as a child. The parent creates an invitation; the user accepts it with a one-time code.
-- Only the SHA-256 of the code is stored, so a database read does not reveal usable codes.
create table smartkin.child_invitation
(
    invitation_id uuid primary key         default gen_random_uuid(),
    parent_id     uuid                     not null references smartkin.parent (parent_id) on delete cascade,
    code_hash     varchar(64)              not null unique,
    grade_level   varchar(50),
    avatar        varchar(255),
    status        varchar(20)              not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REVOKED')),
    expires_at    timestamp with time zone not null,
    created_at    timestamp with time zone not null default now(),
    accepted_by   uuid,
    accepted_at   timestamp with time zone
);

create index ix_child_invitation_parent on smartkin.child_invitation (parent_id, created_at desc);

alter table smartkin.child_invitation enable row level security;
create policy smartkin_app_all on smartkin.child_invitation for all to smartkin_app using (true) with check (true);
grant select, insert, update, delete on smartkin.child_invitation to smartkin_app;
