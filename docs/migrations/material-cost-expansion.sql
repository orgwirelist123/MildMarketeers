BEGIN;
ALTER TABLE public."Material"
    ALTER COLUMN material_cost TYPE NUMERIC(12, 2);
COMMIT;
