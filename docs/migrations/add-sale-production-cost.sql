BEGIN;

-- NULL means the production cost was not recorded for this sale.
-- SaleHistory inherits this column from Sale.
ALTER TABLE public."Sale"
    ADD COLUMN production_cost_per_unit numeric(12,2)
    CHECK (production_cost_per_unit >= 0);

COMMIT;
