-- KF-2: Kafka delivers at least once, so the consumers must not apply the same event twice.
-- These indexes are the last line of defence behind the existence checks the consumers make first.
create unique index uq_points_source on smartkin.points (source_type, source_id, transaction_type);
create unique index uq_notifications_related on smartkin.notifications (user_id, type, related_entity_id)
    where related_entity_id is not null;
