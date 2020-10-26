ALTER TABLE core.network
    ADD COLUMN cx2metadata jsonb,
    ADD COLUMN cxformat character varying(20),
    ADD COLUMN cx2_file_size bigint;        


-- ALTER TABLE core.network
--    ADD COLUMN cx2metadata jsonb;

-- ALTER TABLE core.network
--    ADD COLUMN cxformat character varying(20);    
    
-- ALTER TABLE core.network
--    ADD COLUMN cx2_file_size bigint;        