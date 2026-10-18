ALTER TABLE insider_transactions
    ADD COLUMN IF NOT EXISTS accession_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS document_url VARCHAR(400),
    ADD COLUMN IF NOT EXISTS form_type VARCHAR(8);

CREATE UNIQUE INDEX IF NOT EXISTS insider_transactions_accession_line_idx
    ON insider_transactions (accession_number, id);
