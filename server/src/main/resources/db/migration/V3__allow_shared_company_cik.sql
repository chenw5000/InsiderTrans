-- One issuer (CIK) can have more than one ticker (GOOGL/GOOG, PBR/PBR-A).
ALTER TABLE companies DROP CONSTRAINT IF EXISTS companies_cik_key;
CREATE INDEX IF NOT EXISTS companies_cik_idx ON companies (cik);
