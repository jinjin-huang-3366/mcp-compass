CREATE TABLE selection_event_daily (
    event_date date NOT NULL,
    event_name text NOT NULL CHECK (event_name IN (
        'SEARCH_SUBMITTED', 'SEARCH_WITH_MATCHES', 'SEARCH_WITHOUT_MATCHES', 'SEARCH_FAILED',
        'DETAIL_OPENED', 'SOURCE_OPENED', 'CHOICE_CHANGED', 'CHOICE_UNCHANGED', 'CHOICE_PENDING'
    )),
    total bigint NOT NULL CHECK (total > 0),
    PRIMARY KEY (event_date, event_name)
);
