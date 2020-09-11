ALTER TABLE core.network
    ADD COLUMN cx2metadata jsonb;

ALTER TABLE core.network
    ADD COLUMN cxformat character varying(20);    