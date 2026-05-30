-- V013: Add per-stall entity limit override columns (TDD-220).
-- extra_entities stores a JSON object mapping EntityType name → additive cap bonus.
-- extra_total stores an additive bonus for the region-kind total entity cap.
ALTER TABLE stalls ADD COLUMN extra_entities TEXT NOT NULL DEFAULT '{}';
ALTER TABLE stalls ADD COLUMN extra_total INTEGER NOT NULL DEFAULT 0;
