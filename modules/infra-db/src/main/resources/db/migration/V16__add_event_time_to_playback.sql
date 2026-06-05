ALTER TABLE playback ADD COLUMN event_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
UPDATE playback SET event_time = modified_date;
