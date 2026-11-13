BEGIN;
ALTER TABLE public."Tag"
    ADD COLUMN IF NOT EXISTS color varchar(10) NOT NULL DEFAULT 'green'
    CONSTRAINT tag_color_allowed CHECK (color IN
        ('green', 'blue', 'purple', 'pink', 'red', 'orange', 'yellow', 'gray'));
COMMIT;
