package dev.mcpcompass.selection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SelectionEventCounter {
    private final JdbcTemplate jdbc;

    SelectionEventCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void increment(SelectionEvent event) {
        jdbc.update("""
                INSERT INTO selection_event_daily (event_date, event_name, total)
                VALUES ((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date, ?, 1)
                ON CONFLICT (event_date, event_name)
                DO UPDATE SET total = selection_event_daily.total + 1
                """, event.name());
    }
}
