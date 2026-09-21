CREATE TABLE service_metadata (
    service_name VARCHAR(64) PRIMARY KEY,
    schema_version INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO service_metadata(service_name, schema_version)
VALUES ('catalog-service', 1);
